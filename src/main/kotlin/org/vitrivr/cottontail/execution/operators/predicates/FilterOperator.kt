package org.vitrivr.cottontail.execution.operators.predicates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import org.vitrivr.cottontail.database.queries.components.BooleanPredicate
import org.vitrivr.cottontail.execution.ExecutionEngine
import org.vitrivr.cottontail.execution.operators.basics.Operator
import org.vitrivr.cottontail.execution.operators.basics.OperatorStatus
import org.vitrivr.cottontail.execution.operators.basics.PipelineOperator
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.basics.Record

/**
 * Filters the input generated by the parent [Operator] using the given [BooleanPredicate].
 *
 * This is a [Operator.PipelineOperator].
 *
 * @author Ralph Gasser
 * @version 1.1.0
 */
class FilterOperator(parent: Operator, context: ExecutionEngine.ExecutionContext, private val predicate: BooleanPredicate) : PipelineOperator(parent, context) {
    /** Columns returned by [FilterOperator] depend on the parent [Operator]. */
    override val columns: Array<ColumnDef<*>> = this.parent.columns

    override fun prepareOpen() { /* NoOp. */
    }

    override fun prepareClose() {/* NoOp. */
    }

    /**
     * Converts this [FilterOperator] to a [Flow] and returns it.
     *
     * @param scope The [CoroutineScope] used for execution
     * @return [Flow] representing this [FilterOperator]
     *
     * @throws IllegalStateException If this [Operator.status] is not [OperatorStatus.OPEN]
     */
    override fun toFlow(scope: CoroutineScope): Flow<Record> {
        check(this.status == OperatorStatus.OPEN) { "Cannot convert operator $this to flow because it is in state ${this.status}." }
        return this.parent.toFlow(scope).filter { this.predicate.matches(it) }
    }
}