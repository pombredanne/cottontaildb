package org.vitrivr.cottontail.storage.serialization

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.vitrivr.cottontail.TestConstants
import org.vitrivr.cottontail.database.column.ColumnType
import org.vitrivr.cottontail.database.general.begin
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.recordset.StandaloneRecord
import org.vitrivr.cottontail.model.values.IntValue
import org.vitrivr.cottontail.model.values.IntVectorValue
import org.vitrivr.cottontail.utilities.VectorUtility
import java.util.*

/**
 * Test case that tests for correctness of [IntVectorValue] serialization and deserialization.
 *
 * @author Ralph Gasser
 * @version 1.0
 */
class IntVectorValueSerializationTest : AbstractSerializationTest() {

    @AfterEach
    fun teardown() = this.cleanup()

    /**
     * Executes the test.
     */
    @ParameterizedTest
    @MethodSource("dimensions")
    fun test(dimension: Int) {
        val nameEntity = this.schema.name.entity("intvector-test")
        val idCol = ColumnDef(nameEntity.column("id"), ColumnType.forName("INTEGER"), -1, false)
        val vectorCol = ColumnDef(nameEntity.column("vector"), ColumnType.forName("INT_VEC"), dimension, false)

        /* Prepare entity. */
        val columns = arrayOf(idCol, vectorCol)
        this.schema.createEntity(nameEntity, *columns)
        val schema = this.schema.entityForName(nameEntity)

        /* Prepare random number generator. */
        val seed = System.currentTimeMillis()
        val r1 = SplittableRandom(seed)

        /* Insert data into column. */
        schema.Tx(false).begin { tx1 ->
            var i1 = 1L
            VectorUtility.randomIntVectorSequence(dimension, TestConstants.collectionSize, r1).forEach {
                tx1.insert(StandaloneRecord(columns = columns, values = arrayOf(IntValue(++i1), it)))
            }
            true
        }

        /* Read data from column. */
        val r2 = SplittableRandom(seed)
        schema.Tx(true).begin { tx2 ->
            var i2 = 1L
            VectorUtility.randomIntVectorSequence(dimension, TestConstants.collectionSize, r2).forEach {
                val rec2 = tx2.read(++i2, columns)
                Assertions.assertEquals(i2, (rec2[idCol] as IntValue).asLong().value) /* Compare IDs. */
                Assertions.assertArrayEquals(it.data, (rec2[vectorCol] as IntVectorValue).data) /* Compare to some random vector and serialized vector; match very unlikely! */
                Assertions.assertFalse(IntVectorValue.random(dimension, r1).data.contentEquals((rec2[vectorCol] as IntVectorValue).data)) /* Compare to some random vector and serialized vector; match very unlikely! */
            }
            true
        }
    }
}