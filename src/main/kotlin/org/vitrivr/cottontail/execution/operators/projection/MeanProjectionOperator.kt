package org.vitrivr.cottontail.execution.operators.projection

import org.vitrivr.cottontail.execution.ExecutionEngine
import org.vitrivr.cottontail.execution.exceptions.OperatorExecutionException
import org.vitrivr.cottontail.execution.operators.basics.Operator
import org.vitrivr.cottontail.execution.operators.basics.PipelineBreaker
import org.vitrivr.cottontail.execution.operators.basics.ProducingOperator
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.basics.Name
import org.vitrivr.cottontail.model.basics.Record
import org.vitrivr.cottontail.model.recordset.Recordset
import org.vitrivr.cottontail.model.values.*
import java.util.concurrent.Callable

/**
 * An [Operator.PipelineBreaker] used during query execution. It calculates the MEAN of all values
 * it has encountered  and returns it as a [Record].
 *
 * Only produces a single [Record].
 *
 * @author Ralph Gasser
 * @version 1.0.0
 */
class MeanProjectionOperator(parent: ProducingOperator, context: ExecutionEngine.ExecutionContext, val column: ColumnDef<*>) : PipelineBreaker(parent, context) {

    override val columns: Array<ColumnDef<*>> = arrayOf(ColumnDef.withAttributes(parent.columns.first().name.entity()?.column("mean(${column.name})")
            ?: Name.ColumnName("mean(${column.name})"), "DOUBLE"))

    override fun prepareOpen() {}

    override fun incomingOperator() = Callable {
        /* Determine maximum. */
        var sum = 0.0
        var count = 0L
        while (!this.parent.depleted) {
            val next = this.parent.next()
            if (next != null) {
                count += 1
                val value = next[this.column]
                sum += when (value) {
                    is ByteValue -> value.value.toDouble()
                    is ShortValue -> value.value.toDouble()
                    is IntValue -> value.value.toDouble()
                    is LongValue -> value.value.toDouble()
                    is FloatValue -> value.value.toDouble()
                    is DoubleValue -> value.value
                    else -> throw OperatorExecutionException("The provided column $column cannot be used for a MEAN projection.")
                }
            }
        }

        /* Generate recordset. */
        val recordset = Recordset(this.columns)
        recordset.addRowUnsafe(arrayOf(DoubleValue(sum / count)))
        recordset
    }
}