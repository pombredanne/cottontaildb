package org.vitrivr.cottontail.database.index

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.vitrivr.cottontail.TestConstants
import org.vitrivr.cottontail.database.catalogue.Catalogue
import org.vitrivr.cottontail.database.entity.Entity
import org.vitrivr.cottontail.database.general.begin
import org.vitrivr.cottontail.database.queries.components.AtomicBooleanPredicate
import org.vitrivr.cottontail.database.queries.components.ComparisonOperator
import org.vitrivr.cottontail.database.schema.Schema
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.basics.Name
import org.vitrivr.cottontail.model.recordset.StandaloneRecord
import org.vitrivr.cottontail.model.values.FloatVectorValue
import org.vitrivr.cottontail.model.values.StringValue
import org.vitrivr.cottontail.model.values.types.Value
import java.nio.file.Files
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.HashMap

/**
 * This is a collection of test cases to test the correct behaviour of [UniqueHashIndex].
 *
 * @author Ralph Gasser
 * @param 1.1.1
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UniqueHashIndexTest {

    private val collectionSize = 1_000_000
    private val schemaName = Name.SchemaName("test")
    private val entityName = schemaName.entity("entity")
    private val indexName = entityName.index("id_hash_index")

    private val columns = arrayOf(
            ColumnDef.withAttributes(entityName.column("id"), "STRING", -1, false),
            ColumnDef.withAttributes(entityName.column("feature"), "FLOAT_VEC", 128, false)
    )

    /** Catalogue used for testing. */
    private var catalogue: Catalogue = Catalogue(TestConstants.config)

    /** Schema used for testing. */
    private var schema: Schema? = null

    /** Schema used for testing. */
    private var entity: Entity? = null

    /** Schema used for testing. */
    private var index: Index? = null

    /** List of values stored in this [UniqueHashIndexTest]. */
    private var list = HashMap<StringValue, FloatVectorValue>(1000)

    @BeforeAll
    fun initialize() {
        /* Create schema. */
        this.catalogue.createSchema(schemaName)
        this.schema = this.catalogue.schemaForName(schemaName)

        /* Create entity. */
        this.schema?.createEntity(this.entityName, *this.columns)
        this.entity = this.schema?.entityForName(this.entityName)

        /* Create index. */
        this.entity?.createIndex(indexName, IndexType.HASH_UQ, arrayOf(this.columns[0]))
        this.index = entity?.allIndexes()?.find { it.name == indexName }

        /* Populates the database with test values. */
        this.populateDatabase()
    }

    @AfterAll
    fun teardown() {
        this.catalogue.dropSchema(this.schemaName)
        this.catalogue.close()
        val pathsToDelete = Files.walk(TestConstants.config.root).sorted(Comparator.reverseOrder()).collect(Collectors.toList())
        pathsToDelete.forEach { Files.delete(it) }
        this.list.clear()
    }

    /**
     * Tests basic metadata information regarding the [UniqueHashIndex]
     */
    @Test
    fun testMetadata() {
        assertNotNull(this.index)
        assertArrayEquals(arrayOf(this.columns[0]), this.index?.columns)
        assertArrayEquals(arrayOf(this.columns[0]), this.index?.produces)
        assertEquals(this.indexName, this.index?.name)
    }

    /**
     * Tests if Index#filter() returns the values that have been stored.
     */
    @RepeatedTest(100)
    fun testFilterEqualPositive() {
        this.entity?.Tx(readonly = true)?.begin { tx ->
            for (entry in this.list.entries) {
                val predicate = AtomicBooleanPredicate(this.columns[0] as ColumnDef<StringValue>, ComparisonOperator.EQUAL, false, listOf(entry.key))
                val index = tx.indexes().first()
                index.filter(predicate).use {
                    it.forEach { r ->
                        val rec = tx.read(r.tupleId, this.columns)
                        assertEquals(entry.key, rec[this.columns[0]])
                        assertArrayEquals(entry.value.data, (rec[this.columns[1]] as FloatVectorValue).data)
                    }
                }
            }
            true
        }
    }

    /**
     * Tests if Index#filter() only returns stored values.
     */
    @RepeatedTest(100)
    fun testFilterEqualNegative() {
        this.entity?.Tx(readonly = true)?.begin { tx ->
            val index = tx.indexes().first()
            var count = 0
            index.filter(AtomicBooleanPredicate(this.columns[0] as ColumnDef<StringValue>, ComparisonOperator.EQUAL, false, listOf(StringValue(UUID.randomUUID().toString())))).use {
                it.forEach { count += 1 }
            }
            assertEquals(0, count)
            true
        }
    }

    /**
     * Populates the test database with data.
     */
    private fun populateDatabase() {
        val random = SplittableRandom()
        this.entity?.Tx(readonly = false)?.begin { tx ->
            /* Insert data and track how many entries have been stored for the test later. */
            var stored = 0
            for (i in 0..this.collectionSize) {
                val uuid = StringValue(UUID.randomUUID().toString())
                val vector = FloatVectorValue.random(128, random)
                val values: Array<Value?> = arrayOf(uuid, vector)
                if (random.nextBoolean() && stored <= 1000) {
                    this.list[uuid] = vector
                    stored++
                }
                tx.insert(StandaloneRecord(columns = this.columns, values = values))
            }
            true
        }
        this.entity!!.updateAllIndexes()
    }
}
