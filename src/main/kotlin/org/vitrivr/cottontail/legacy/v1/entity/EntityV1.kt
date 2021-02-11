package org.vitrivr.cottontail.legacy.v1.entity

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.mapdb.CottontailStoreWAL
import org.mapdb.DBException
import org.mapdb.Serializer
import org.mapdb.StoreWAL
import org.vitrivr.cottontail.database.column.Column
import org.vitrivr.cottontail.database.column.ColumnDef
import org.vitrivr.cottontail.database.column.ColumnTx
import org.vitrivr.cottontail.database.entity.Entity
import org.vitrivr.cottontail.database.entity.EntityTx
import org.vitrivr.cottontail.database.general.AbstractTx
import org.vitrivr.cottontail.database.index.Index
import org.vitrivr.cottontail.database.index.IndexTx
import org.vitrivr.cottontail.database.index.IndexType
import org.vitrivr.cottontail.execution.TransactionContext
import org.vitrivr.cottontail.legacy.BrokenIndex
import org.vitrivr.cottontail.legacy.v1.column.ColumnV1
import org.vitrivr.cottontail.legacy.v1.schema.SchemaV1
import org.vitrivr.cottontail.model.basics.*
import org.vitrivr.cottontail.model.exceptions.DatabaseException
import org.vitrivr.cottontail.model.exceptions.TxException
import org.vitrivr.cottontail.model.values.types.Value
import org.vitrivr.cottontail.utilities.extensions.write
import java.nio.file.Path
import java.util.*
import java.util.concurrent.locks.StampedLock

/**
 * Represents a single entity in the Cottontail DB data model. An [Entity] has name that must remain
 * unique within a [SchemaV1]. The [Entity] contains one to many [Column]s holding the actual data.
 * Hence, it can be seen as a table containing tuples.
 *
 * Calling the default constructor for [Entity] opens that [Entity]. It can only be opened once due
 * to file locks and it will remain open until the [Entity.close()] method is called.
 *
 * @see SchemaV1
 * @see Column
 * @see EntityTx
 *
 * @author Ralph Gasser
 * @version 1.6.0
 */
class EntityV1(override val name: Name.EntityName, override val parent: SchemaV1) : Entity {
    /**
     * Companion object of the [Entity]
     */
    companion object {
        /** Filename for the [Entity] catalogue.  */
        const val FILE_CATALOGUE = "index.db"

        /** Filename for the [Entity] catalogue.  */
        const val HEADER_RECORD_ID = 1L
    }

    /** The [Path] to the [Entity]'s main folder. */
    override val path: Path = this.parent.path.resolve("entity_${name.simple}")

    /** Internal reference to the [StoreWAL] underpinning this [Entity]. */
    private val store: CottontailStoreWAL = try {
        this.parent.parent.config.mapdb.store(this.path.resolve(FILE_CATALOGUE))
    } catch (e: DBException) {
        throw DatabaseException("Failed to open entity '$name': ${e.message}'.")
    }

    /** The header of this [Entity]. */
    private val header: EntityV1Header
        get() = this.store.get(HEADER_RECORD_ID, EntityV1Header.Serializer)
            ?: throw DatabaseException.DataCorruptionException("Failed to open header of entity '$name'!")

    /** An internal lock that is used to synchronize access to this [Entity] and [EntityTx] and it being closed or dropped. */
    private val closeLock = StampedLock()

    /** List of all the [Column]s associated with this [Entity]; Iteration order of entries as defined in schema! */
    private val columns: MutableMap<Name.ColumnName, Column<*>> = Object2ObjectLinkedOpenHashMap()

    /** List of all the [Index]es associated with this [Entity]. */
    private val indexes: MutableMap<Name.IndexName, Index> = Object2ObjectOpenHashMap()

    init {
        /* Initialize columns. */
        this.header.columns.map {
            val columnName = this.name.column(
                this.store.get(it, Serializer.STRING)
                    ?: throw DatabaseException.DataCorruptionException("Failed to open entity '$name': Could not read column definition at position $it!")
            )
            this.columns[columnName] =
                ColumnV1<Value>(columnName, this)
        }

        /* Initialize indexes (broken). */
        this.header.indexes.forEach { idx ->
            val indexEntry = this.store.get(idx, IndexV1Entry.Serializer)
                ?: throw DatabaseException.DataCorruptionException("Failed to open entity '$name': Could not read index definition at position $idx!")
            val indexName = this.name.index(indexEntry.name)
            val columns = indexEntry.columns.map { col ->
                val split = col.split(".").last()
                this.columns[this.name.column(split)]?.columnDef
                    ?: throw DatabaseException.DataCorruptionException("Column '$col' does not exist on the entity!")
            }.toTypedArray()
            this.indexes[indexName] = BrokenIndex(
                this.name.index(indexEntry.name),
                this,
                this.path.resolve(indexEntry.name),
                indexEntry.type,
                columns
            )
        }
    }

    override val numberOfColumns: Int
        get() = this.header.columns.size

    override val numberOfRows: Long
        get() = this.header.size

    override val maxTupleId: TupleId
        get() {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

    /**
     * Status indicating whether this [Entity] is open or closed.
     */
    @Volatile
    override var closed: Boolean = false
        private set

    /**
     * Creates and returns a new [EntityTx] for the given [TransactionContext].
     *
     * @param context The [TransactionContext] to create the [EntityTx] for.
     * @return New [EntityTx]
     */
    override fun newTx(context: TransactionContext) = this.Tx(context)

    /**
     * Closes the [Entity]. Closing an [Entity] is a delicate matter since ongoing [EntityTx] objects as well as all involved [Column]s are involved.
     * Therefore, access to the method is mediated by an global [Entity] wide lock.
     */
    override fun close() = this.closeLock.write {
        if (!this.closed) {
            this.columns.values.forEach { it.close() }
            this.store.close()
            this.closed = true
        }
    }

    /**
     * Handles finalization, in case the Garbage Collector reaps a cached [Entity] soft-reference.
     */
    @Synchronized
    protected fun finalize() {
        if (!this.closed) {
            this.close()
        }
    }

    /**
     * A [Tx] that affects this [Entity]. Opening a [EntityTx] will automatically spawn [ColumnTx]
     * and [IndexTx] for every [Column] and [IndexTx] associated with this [Entity].
     *
     * @author Ralph Gasser
     * @version 1.0.0
     */
    inner class Tx(context: TransactionContext) : AbstractTx(context), EntityTx {

        /** Obtains a global (non-exclusive) read-lock on [Entity]. Prevents enclosing [Entity] from being closed. */
        private val closeStamp = this@EntityV1.closeLock.readLock()

        /** Reference to the surrounding [Entity]. */
        override val dbo: Entity
            get() = this@EntityV1

        /** Tries to acquire a global read-lock on this entity. */
        init {
            if (this@EntityV1.closed) {
                throw TxException.TxDBOClosedException(this.context.txId)
            }
        }

        /**
         * Lists all [Column]s for the [Entity] associated with this [EntityTx].
         *
         * @return List of all [Column]s.
         */
        override fun listColumns(): List<Column<*>> = this.withReadLock {
            return this@EntityV1.columns.values.toList()
        }

        /**
         * Returns the [ColumnDef] for the specified [Name.ColumnName].
         *
         * @param name The [Name.ColumnName] of the [Column].
         * @return [ColumnDef] of the [Column].
         */
        override fun columnForName(name: Name.ColumnName): Column<*> = this.withReadLock {
            if (!name.wildcard) {
                this@EntityV1.columns[name] ?: throw DatabaseException.ColumnDoesNotExistException(
                    name
                )
            } else {
                val fqn = this@EntityV1.name.column(name.simple)
                this@EntityV1.columns[fqn] ?: throw DatabaseException.ColumnDoesNotExistException(
                    fqn
                )
            }
        }

        /**
         * Lists all [Index] implementations that belong to this [EntityTx].
         *
         * @return List of [Name.IndexName] managed by this [EntityTx]
         */
        override fun listIndexes(): List<Index> = this.withReadLock {
            return this@EntityV1.indexes.values.toList()
        }

        /**
         * Lists [Name.IndexName] for all [Index] implementations that belong to this [EntityTx].
         *
         * @return List of [Name.IndexName] managed by this [EntityTx]
         */
        override fun indexForName(name: Name.IndexName): Index = this.withReadLock {
            this@EntityV1.indexes[name] ?: throw DatabaseException.IndexDoesNotExistException(name)
        }

        override fun maxTupleId(): TupleId {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }


        override fun createIndex(
            name: Name.IndexName,
            type: IndexType,
            columns: Array<ColumnDef<*>>,
            params: Map<String, String>
        ): Index {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun dropIndex(name: Name.IndexName) {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun read(tupleId: TupleId, columns: Array<ColumnDef<*>>): Record {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun scan(columns: Array<ColumnDef<*>>): CloseableIterator<Record> {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun scan(
            columns: Array<ColumnDef<*>>,
            range: LongRange
        ): CloseableIterator<Record> {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun count(): Long {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun insert(record: Record): TupleId? {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun update(record: Record) {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        override fun delete(tupleId: TupleId) {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        /**
         * Performs a COMMIT of all changes made through this [EntityTx].
         */
        override fun performCommit() {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        /**
         * Performs a ROLLBACK of all changes made through this [EntityTx].
         */
        override fun performRollback() {
            throw UnsupportedOperationException("Operation not supported on legacy DBO.")
        }

        /**
         * Closes all the [ColumnTx] and [IndexTx] and releases the [closeLock] on the [Entity].
         */
        override fun cleanup() {
            this@EntityV1.closeLock.unlockRead(this.closeStamp)
        }
    }
}
