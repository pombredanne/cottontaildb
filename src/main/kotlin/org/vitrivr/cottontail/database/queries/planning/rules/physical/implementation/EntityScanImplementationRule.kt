package org.vitrivr.cottontail.database.queries.planning.rules.physical.implementation

import org.vitrivr.cottontail.database.queries.planning.nodes.interfaces.NodeExpression
import org.vitrivr.cottontail.database.queries.planning.nodes.interfaces.RewriteRule
import org.vitrivr.cottontail.database.queries.planning.nodes.logical.sources.EntityScanLogicalNodeExpression
import org.vitrivr.cottontail.database.queries.planning.nodes.physical.sources.EntityScanPhysicalNodeExpression

/**
 * A [RewriteRule] that replaces a [EntityScanLogicalNodeExpression] by a [EntityScanPhysicalNodeExpression].
 *
 * @author Ralph Gasser
 * @version 1.0
 */
object EntityScanImplementationRule : RewriteRule {
    override fun canBeApplied(node: NodeExpression): Boolean = node is EntityScanLogicalNodeExpression
    override fun apply(node: NodeExpression): NodeExpression? {
        if (node is EntityScanLogicalNodeExpression) {
            val children = node.copyOutput()
            val p = EntityScanPhysicalNodeExpression(node.entity, node.columns)
            children?.addInput(p)
            return p
        }
        return null
    }
}