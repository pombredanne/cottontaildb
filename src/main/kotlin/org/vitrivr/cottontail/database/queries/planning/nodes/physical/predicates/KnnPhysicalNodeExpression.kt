package org.vitrivr.cottontail.database.queries.planning.nodes.physical.predicates

import org.vitrivr.cottontail.database.queries.components.KnnPredicate
import org.vitrivr.cottontail.database.queries.planning.cost.Cost
import org.vitrivr.cottontail.database.queries.planning.nodes.physical.UnaryPhysicalNodeExpression
import org.vitrivr.cottontail.database.queries.predicates.KnnPredicateHint
import org.vitrivr.cottontail.execution.ExecutionEngine
import org.vitrivr.cottontail.execution.operators.basics.Operator
import org.vitrivr.cottontail.execution.operators.predicates.KnnOperator
import org.vitrivr.cottontail.execution.operators.predicates.ParallelKnnOperator
import java.lang.Integer.max
import java.lang.Integer.min

/**
 * A [UnaryPhysicalNodeExpression] that represents the application of a [KnnPredicate] on some intermediate result.
 *
 * @author Ralph Gasser
 * @version 1.0.1
 */
class KnnPhysicalNodeExpression(val knn: KnnPredicate<*>) : UnaryPhysicalNodeExpression() {
    override val outputSize: Long
        get() = (this.knn.k * this.knn.query.size).toLong()

    override val cost: Cost
        get() = Cost(cpu = this.input.outputSize * this.knn.cost, memory = (this.outputSize * this.knn.columns.map { it.physicalSize }.sum()).toFloat())

    override fun copy() = KnnPhysicalNodeExpression(this.knn)
    override fun toOperator(context: ExecutionEngine.ExecutionContext): Operator {
        val hint = this.knn.hint
        val parallelisation = if (hint is KnnPredicateHint.ParallelKnnPredicateHint) {
            max(hint.min, min(context.availableThreads, hint.max))
        } else {
            min(this.cost.parallelisation(), context.availableThreads)
        }
        return if (parallelisation > 1) {
            if (this.input.canBePartitioned) {
                val partitions = this.input.partition(parallelisation)
                val operators = partitions.map {
                    it.toOperator(context)
                }
                ParallelKnnOperator(operators, context, this.knn)
            } else {
                KnnOperator(this.input.toOperator(context), context, this.knn)
            }
        } else {
            KnnOperator(this.input.toOperator(context), context, this.knn)
        }
    }
}

