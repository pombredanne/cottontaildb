package org.vitrivr.cottontail.execution.operators.predicates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.vitrivr.cottontail.database.queries.components.BooleanPredicate
import org.vitrivr.cottontail.execution.ExecutionEngine
import org.vitrivr.cottontail.execution.operators.basics.Operator
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.basics.Record

/**
 * An [Operator.MergingPipelineOperator] used during query execution. Filters the input generated
 * by the parent [Operator]s using the given [BooleanPredicate].
 *
 * This [Operator.MergingPipelineOperator] merges input generated by multiple [Operator]s.
 * Their output order may be arbitrary.
 *
 * @author Ralph Gasser
 * @version 1.0.0
 */
class ParallelFilterOperator(parents: List<Operator>, private val predicate: BooleanPredicate) : Operator.MergingPipelineOperator(parents) {

    /** Columns returned by [ParallelFilterOperator] depend on the parent [Operator]. */
    override val columns: Array<ColumnDef<*>> = this.parents.first().columns

    /** [ParallelFilterOperator] does not act as a pipeline breaker. */
    override val breaker: Boolean = false

    /**
     * Converts this [FilterOperator] to a [Flow] and returns it.
     *
     * @param context The [ExecutionEngine.ExecutionContext] used for execution
     * @return [Flow] representing this [FilterOperator]
     */
    @ExperimentalCoroutinesApi
    override fun toFlow(context: ExecutionEngine.ExecutionContext): Flow<Record> {
        val parentFlows = this.parents.map { it.toFlow(context) }.toTypedArray()
        return parentFlows.map { flow ->
            flow.filter { r -> this@ParallelFilterOperator.predicate.matches(r) }.flowOn(context.coroutineDispatcher)
        }.merge()
    }
}