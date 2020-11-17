package org.vitrivr.cottontail.database.index

import org.vitrivr.cottontail.database.entity.Entity
import org.vitrivr.cottontail.database.index.greedygrouping.GreedyGroupingIndex
import org.vitrivr.cottontail.database.index.greedygrouping.GreedyGroupingIndexConfig
import org.vitrivr.cottontail.database.index.hash.NonUniqueHashIndex
import org.vitrivr.cottontail.database.index.hash.UniqueHashIndex
import org.vitrivr.cottontail.database.index.lsh.superbit.NonBucketingSuperBitLSHIndex
import org.vitrivr.cottontail.database.index.lsh.superbit.NonBucketingSuperBitLSHIndexConfig
import org.vitrivr.cottontail.database.index.lsh.superbit.SuperBitLSHIndex
import org.vitrivr.cottontail.database.index.lsh.superbit.SuperBitLSHIndexConfig
import org.vitrivr.cottontail.database.index.lucene.LuceneIndex
import org.vitrivr.cottontail.database.index.pq.PQIndex
import org.vitrivr.cottontail.database.index.pq.PQIndexConfig
import org.vitrivr.cottontail.database.index.va.VAFIndex
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.basics.Name
import org.vitrivr.cottontail.model.values.types.VectorValue

enum class IndexType(val inexact: Boolean) {
    HASH_UQ(false), /* A hash based index with unique values. */
    HASH(false), /* A hash based index. */
    BTREE(false), /* A BTree based index. */
    LUCENE(false), /* A Lucene based index (fulltext search). */
    VAF(false), /* A VA file based index (for exact kNN lookup). */
    PQ(true), /* A product quantization based index (for approximate kNN lookup). */
    SH(true), /* A spectral hashing based index (for approximate kNN lookup). */
    LSH(true), /* A locality sensitive hashing based index for approximate kNN lookup with Lp distance. */
    SUPERBIT_LSH(true), /* A locality sensitive hashing based index for approximate kNN lookup with cosine distance. */
    NONBUCKETING_SUPERBIT_LSH(true), /* A locality sensitive hashing based index for approximate kNN lookup with cosine distance. */
    GG(true); /* A Greedy grouping index */

    /**
     * Opens an index of this [IndexType] using the given name and [Entity].
     *
     * @param name [Name.IndexName] of the [Index]
     * @param entity The [Entity] the desired [Index] belongs to.
     */
    fun open(name: Name.IndexName, entity: Entity, columns: Array<ColumnDef<*>>): Index = when (this) {
        HASH_UQ -> UniqueHashIndex(name, entity, columns)
        HASH -> NonUniqueHashIndex(name, entity, columns)
        LUCENE -> LuceneIndex(name, entity, columns)
        SUPERBIT_LSH -> SuperBitLSHIndex<VectorValue<*>>(name, entity, columns, null)
        NONBUCKETING_SUPERBIT_LSH -> NonBucketingSuperBitLSHIndex<VectorValue<*>>(name, entity, columns, null)
        VAF -> VAFIndex(name, entity, columns)
        PQ -> PQIndex(name, entity, columns, null)
        GG -> GreedyGroupingIndex(name, entity, columns, null)
        else -> TODO()
    }

    /**
     * Creates an index of this [IndexType] using the given name and [Entity].
     *
     * @param name [Name.IndexName] of the [Index]
     * @param entity The [Entity] the desired [Index] belongs to.
     * @param columns The [ColumnDef] for which to create the [Index]
     * @param params Additions configuration params.
     */
    fun create(name: Name.IndexName, entity: Entity, columns: Array<ColumnDef<*>>, params: Map<String, String> = emptyMap()) = when (this) {
        HASH_UQ -> UniqueHashIndex(name, entity, columns)
        HASH -> NonUniqueHashIndex(name, entity, columns)
        LUCENE -> LuceneIndex(name, entity, columns)
        SUPERBIT_LSH -> SuperBitLSHIndex<VectorValue<*>>(name, entity, columns, SuperBitLSHIndexConfig.fromParamMap(params))
        NONBUCKETING_SUPERBIT_LSH -> NonBucketingSuperBitLSHIndex<VectorValue<*>>(name, entity, columns, NonBucketingSuperBitLSHIndexConfig.fromParamMap(params))
        VAF -> VAFIndex(name, entity, columns)
        PQ -> PQIndex(name, entity, columns, PQIndexConfig.fromParamsMap(params))
        GG -> GreedyGroupingIndex(name, entity, columns, GreedyGroupingIndexConfig.fromParamsMap(params))
        else -> TODO()
    }
}