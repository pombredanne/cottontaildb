package org.vitrivr.cottontail.database.queries.planning.nodes.physical.recordset

import org.vitrivr.cottontail.database.queries.components.Projection
import org.vitrivr.cottontail.database.queries.planning.QueryPlannerContext
import org.vitrivr.cottontail.database.queries.planning.cost.Cost
import org.vitrivr.cottontail.database.queries.planning.cost.Costs
import org.vitrivr.cottontail.execution.tasks.basics.ExecutionStage
import org.vitrivr.cottontail.execution.tasks.recordset.projection.*
import org.vitrivr.cottontail.model.basics.ColumnDef
import org.vitrivr.cottontail.model.basics.Name
import org.vitrivr.cottontail.model.exceptions.QueryException

/**
 * Formalizes a [RecordsetProjectionPhysicalNodeExpression] operation in the Cottontail DB query execution engine.
 *
 * @author Ralph Gasser
 * @version 1.1
 */
data class RecordsetProjectionPhysicalNodeExpression(val type: Projection, val fields: List<Pair<ColumnDef<*>, Name.ColumnName?>>) : AbstractRecordsetPhysicalNodeExpression() {
    init {
        /* Sanity check. */
        when (type) {
            Projection.SELECT,
            Projection.SELECT_DISTINCT,
            Projection.COUNT_DISTINCT -> if (this.fields.isEmpty()) {
                throw QueryException.QuerySyntaxException("Projection of type $type must specify at least one column.")
            }
            Projection.MEAN,
            Projection.SUM,
            Projection.MIN,
            Projection.MAX -> if (fields.isEmpty()) {
                throw QueryException.QuerySyntaxException("Projection of type $type must specify a column.")
            } else if (!fields.first().first.type.numeric) {
                throw QueryException.QueryBindException("Projection of type $type can only be applied on a numeric column, which ${fields.first().first.name} is not.")
            }
            else -> {
            }
        }
    }

    override val outputSize: Long
        get() = this.input.outputSize

    override val cost: Cost
        get() = Cost(
                io = this.outputSize * this.fields.size * Costs.MEMORY_ACCESS_READ,
                memory = (this.outputSize * this.fields.map { it.first.physicalSize }.sum()).toFloat()
        )

    override fun copy() = RecordsetProjectionPhysicalNodeExpression(this.type, this.fields)

    override fun toStage(context: QueryPlannerContext): ExecutionStage = when (this.type) {
        Projection.SELECT -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetSelectProjectionTask(this.fields))
        Projection.SELECT_DISTINCT -> {
            val distinct = ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetDistinctTask())
            ExecutionStage(ExecutionStage.MergeType.ONE, distinct).addTask(RecordsetSelectProjectionTask(this.fields))
        }
        Projection.COUNT -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetCountProjectionTask())
        Projection.COUNT_DISTINCT -> {
            val distinct = ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetDistinctTask())
            ExecutionStage(ExecutionStage.MergeType.ONE, distinct).addTask(RecordsetCountProjectionTask())
        }
        Projection.EXISTS -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetExistsProjectionTask())
        Projection.SUM -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetSumProjectionTask(this.fields.first().first, this.fields.first().second))
        Projection.MAX -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetMaxProjectionTask(this.fields.first().first, this.fields.first().second))
        Projection.MIN -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetMinProjectionTask(this.fields.first().first, this.fields.first().second))
        Projection.MEAN -> ExecutionStage(ExecutionStage.MergeType.ONE, this.input.toStage(context)).addTask(RecordsetMeanProjectionTask(this.fields.first().first, this.fields.first().second))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecordsetProjectionPhysicalNodeExpression

        if (type != other.type) return false
        if (fields != other.fields) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + fields.hashCode()
        return result
    }
}