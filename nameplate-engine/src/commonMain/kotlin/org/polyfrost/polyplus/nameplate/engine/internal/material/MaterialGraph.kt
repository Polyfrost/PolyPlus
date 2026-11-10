package org.polyfrost.polyplus.nameplate.engine.internal.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialExpression
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput

internal class GraphNode(val id: String, val kind: NodeKind, val settings: NodeSettings) {
    var inputs: List<GraphInput> = emptyList()
    var source: GraphNode? = null
    var type: SocketType = SocketType.FLOAT
    var typed: Boolean = false
    var index: Int = 0
}

internal class GraphInput(val node: GraphNode?, val literal: DoubleArray?) {
    var target: SocketType? = null

    val sourceType: SocketType?
        get() = node?.type ?: literal?.let { SocketType.vector(it.size) }
}

internal enum class GraphBlend {
    ALPHA,
    ADDITIVE,
}

internal enum class Reduce {
    SUM,
    MIN,
    MAX,
    FIRST,
}

internal class NodeSettings {
    var seed: Int = 0
    var periodX: Int = 0
    var periodY: Int = 0
    var octaves: Int = 1
    var count: Int = 1
    var size: Int = 1
    var width: Int = 1
    var directionX: Int = 1
    var directionY: Int = 0
    var slot: Int = 0
    var tone: Int = 0
    var tones: Int = 1
    var source: String? = null
    var reduce: Reduce = Reduce.SUM
    var component: Int = 0
    var value: DoubleArray = DoubleArray(1)
    var name: String = ""
    var blend: GraphBlend = GraphBlend.ALPHA
    var sprite: MaterialSprite? = null
    var texture: TextureRef? = null
    var expression: MaterialExpression? = null
    var input: Int = -1
    var sampler: Int = -1
    var edge: String = "fill"
}

internal class MaterialGraph(
    val output: GraphNode,
    val inputs: List<MaterialInput>,
    val textures: List<TextureRef>,
    val cost: Int,
    val samples: Int,
) {
    val reachable: List<GraphNode> = GraphOrder.reachable(output)

    val blend: GraphBlend
        get() = output.settings.blend

    val outputsSlots: Boolean
        get() = output.inputs[0].target == SocketType.SLOT

    val readsPalette: Boolean = reachable.any { node ->
        node.inputs.any { it.node?.type == SocketType.SLOT && it.target == SocketType.VEC4 }
    }

    val readsBodyPosition: Boolean = reachable.any { it.kind.type == "BodyRect" }

    val readsBodyEdge: Boolean = reachable.any { it.kind === BodyEdgeKind }

    val valueInputs: List<MaterialInput> = inputs.filter { it !is MaterialInput.BodyEdgeInput }

    val readsFrame: Boolean = reachable.any { it.kind.type == "Frame" || it.kind.type == "SteppedTime" }

    val coverageReads: CoverageReads = CoverageReads.of(output.inputs.getOrNull(1)?.node)
}

internal enum class CoverageReads {
    FIXED,
    FRAME,
    ANY,
    ;

    companion object {
        fun of(root: GraphNode?): CoverageReads {
            val cone = root?.let(GraphOrder::reachable).orEmpty()
            return when {
                cone.any(::varies) -> ANY
                cone.any { it.kind.type == "Frame" || it.kind.type == "SteppedTime" } -> FRAME
                else -> FIXED
            }
        }

        private fun varies(node: GraphNode): Boolean =
            node.kind is TextureKind || node.kind === ParamKind || node.kind === ExprKind ||
                node.inputs.any { it.node?.type == SocketType.SLOT && it.target == SocketType.VEC4 }
    }
}

internal object GraphOrder {
    class Sorted(val order: List<GraphNode>, val cycle: GraphNode?)

    fun dependencies(node: GraphNode): List<GraphNode> = node.inputs.mapNotNull { it.node } + listOfNotNull(node.source)

    fun sort(nodes: List<GraphNode>): Sorted {
        val state = HashMap<GraphNode, Int>()
        val order = ArrayList<GraphNode>(nodes.size)
        var cycle: GraphNode? = null
        fun visit(node: GraphNode) {
            when (state[node]) {
                VISITING -> cycle = cycle ?: node
                DONE -> Unit
                else -> {
                    state[node] = VISITING
                    dependencies(node).forEach(::visit)
                    state[node] = DONE
                    order.add(node)
                }
            }
        }
        for (node in nodes) {
            if (cycle == null) {
                visit(node)
            }
        }
        return Sorted(order, cycle)
    }

    fun cone(root: GraphNode): List<GraphNode> {
        val seen = HashSet<GraphNode>()
        val order = ArrayList<GraphNode>()
        fun visit(node: GraphNode) {
            if (seen.add(node)) {
                node.inputs.forEach { input -> input.node?.let(::visit) }
                order.add(node)
            }
        }
        visit(root)
        return order
    }

    fun reachable(root: GraphNode): List<GraphNode> {
        val seen = LinkedHashSet<GraphNode>()
        fun visit(node: GraphNode) {
            if (seen.add(node)) {
                dependencies(node).forEach(::visit)
            }
        }
        visit(root)
        return seen.toList()
    }

    private const val VISITING = 1
    private const val DONE = 2
}
