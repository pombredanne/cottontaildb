package org.vitrivr.cottontail.database.queries.planning.nodes.logical.merge

import org.vitrivr.cottontail.database.queries.planning.nodes.logical.BinaryLogicalNodeExpression

/**
 * A [BinaryLogicalNodeExpression] that formalizes application of a cartesian product between two
 * input [org.vitrivr.cottontail.model.recordset.Recordset]s. Used for JOIN operations.
 *
 * Since the cartesian product always takes two inputs, the input arity is always two.
 *
 * @author Ralph Gasser
 * @version 1.0
 */
class CartesianProductLogicalNodeExpression: BinaryLogicalNodeExpression() {
    /**
     * Returns a copy of this [KnnLogicalNodeExpression]
     *
     * @return Copy of this [KnnLogicalNodeExpression]
     */
    override fun copy(): CartesianProductLogicalNodeExpression = CartesianProductLogicalNodeExpression()
}