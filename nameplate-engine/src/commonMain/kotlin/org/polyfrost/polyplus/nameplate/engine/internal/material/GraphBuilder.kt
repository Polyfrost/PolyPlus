package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlinx.serialization.json.JsonPrimitive
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput

internal class GraphBuilder(
    private val path: String,
    private val context: GraphContext,
    private val issues: MutableList<BundleIssue>,
) {
    private val synthetic = ArrayList<GraphNode>()

    fun build(json: List<NodeJson>, outline: Boolean): MaterialGraph? {
        val start = issues.size
        if (json.size > MaterialLimits.MAX_NODES) {
            report(
                "nodes",
                "has ${json.size} nodes; at most ${MaterialLimits.MAX_NODES}",
                IssueCode.MATERIAL_NODES,
                issueValues("nodes" to json.size, "limit" to MaterialLimits.MAX_NODES),
            )
        }
        val nodes = if (issues.size == start) declare(json) else emptyMap()
        if (issues.size == start) {
            link(json, nodes)
        }
        val sorted = GraphOrder.sort(nodes.values + synthetic)
        sorted.cycle?.let { report("nodes.${it.id}", "is part of a cycle", IssueCode.MATERIAL_CYCLE) }
        if (issues.size == start) {
            sorted.order.forEachIndexed { i, node -> node.index = i }
            sorted.order.forEach(::type)
        }
        val outputs = nodes.values.filter { it.kind === OutputKind }
        if (issues.size == start && outputs.size != 1) {
            report(
                "nodes",
                "needs exactly one Output node, not ${outputs.size}",
                IssueCode.MATERIAL_OUTPUTS,
                issueValues("count" to outputs.size),
            )
        }
        return if (issues.size == start) finish(outputs.first(), outline, start) else null
    }

    private fun declare(json: List<NodeJson>): Map<String, GraphNode> {
        val nodes = LinkedHashMap<String, GraphNode>()
        for (node in json) {
            val kind = NodeCatalog.kind(node.type)
            when {
                !ID.matches(node.id) ->
                    report(
                        "nodes",
                        "node id '${node.id}' must be 1 to 64 of a-z, 0-9 and _",
                        IssueCode.NODE_ID,
                        names = mapOf(NODE to node.id),
                    )
                node.id in nodes -> report("nodes.${node.id}", "is declared twice", IssueCode.NODE_DUPLICATE)
                kind == null ->
                    report(
                        "nodes.${node.id}.type",
                        "unknown node type '${node.type}'",
                        IssueCode.NODE_TYPE,
                        names = mapOf(TYPE to node.type),
                    )
                else -> {
                    val reader = SettingsReader(node.set, "$path#nodes.${node.id}.set", context, issues)
                    kind.read(reader)
                    reader.finish()
                    nodes[node.id] = GraphNode(node.id, kind, reader.settings)
                }
            }
        }
        return nodes
    }

    private fun link(json: List<NodeJson>, nodes: Map<String, GraphNode>) {
        for (spec in json) {
            val node = nodes.getValue(spec.id)
            val at = "nodes.${spec.id}"
            for (key in spec.inputs.keys) {
                if (node.kind.sockets.none { it.name == key }) {
                    report(
                        "$at.in.$key",
                        "is not an input of ${node.kind.type}",
                        IssueCode.NODE_INPUT,
                        names = mapOf(TYPE to node.kind.type),
                    )
                }
            }
            node.inputs = node.kind.sockets.map { socket -> input(spec, socket, nodes, "$at.in.${socket.name}") }
            node.settings.source?.let { id ->
                val source = nodes[id]
                when {
                    source == null -> report("$at.set.source", "no node '$id'", IssueCode.NODE_LINK, names = mapOf(NODE to id))
                    source.kind === OutputKind -> report("$at.set.source", "can't sample the Output node", IssueCode.NODE_OUTPUT_LINK)
                    else -> node.source = source
                }
            }
        }
    }

    private fun input(spec: NodeJson, socket: Socket, nodes: Map<String, GraphNode>, at: String): GraphInput {
        val value = spec.inputs[socket.name]
        val ref = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        val node = ref?.let(nodes::get)
        val literal = if (value != null && ref == null) Literals.parse(value) else null
        when {
            ref != null && node == null -> report(at, "no node '$ref'", IssueCode.NODE_LINK, names = mapOf(NODE to ref))
            node?.kind === OutputKind -> report(at, "the Output node can't feed another node", IssueCode.NODE_OUTPUT_LINK)
            value != null && ref == null && literal == null ->
                report(at, "must be a node id, a number or up to 4 numbers", IssueCode.NODE_LITERAL)
            value == null && socket.default == SocketDefault.Required -> report(at, "is required", IssueCode.NODE_REQUIRED)
        }
        return when {
            value != null -> GraphInput(node, literal)
            else -> defaultInput(socket.default)
        }
    }

    private fun defaultInput(default: SocketDefault): GraphInput = when (default) {
        is SocketDefault.Value -> GraphInput(null, default.values)
        is SocketDefault.Input -> {
            val kind = NodeCatalog.kinds.getValue(default.type)
            val node = GraphNode("~${default.type}", kind, NodeSettings())
            synthetic += node
            GraphInput(node, null)
        }
        else -> GraphInput(null, null)
    }

    private fun type(node: GraphNode) {
        val ready = node.inputs.all { it.node?.typed != false } && node.source?.typed != false
        if (!ready) return
        when (val typing = node.kind.typing(node, node.inputs.map { it.sourceType })) {
            is Typing.Ok -> {
                node.type = typing.output
                node.typed = true
                node.inputs.forEachIndexed { i, input -> input.target = typing.targets[i] }
            }
            is Typing.Error -> report("nodes.${node.id}", typing.message, IssueCode.NODE_TYPING)
        }
    }

    private fun finish(output: GraphNode, outline: Boolean, start: Int): MaterialGraph? {
        val load = GraphCost { node, message -> report("nodes.${node.id}", message, IssueCode.NODE_REPEAT) }.of(output, inside = false)
        val cost = if (outline) load.cost * MaterialLimits.OUTLINE_FACTOR else load.cost
        if (cost > MaterialLimits.MAX_COST) {
            val detail = if (outline) " (the outline runs the graph 5 times)" else ""
            report(
                "nodes",
                "costs about $cost operations per pixel$detail; at most ${MaterialLimits.MAX_COST}",
                IssueCode.MATERIAL_COST,
                issueValues(
                    "ops" to cost,
                    "limit" to MaterialLimits.MAX_COST,
                    "graph" to load.cost,
                    "factor" to if (outline) MaterialLimits.OUTLINE_FACTOR else 1,
                ),
            )
        }
        if (load.samples > MaterialLimits.MAX_TEXTURE_SAMPLES) {
            val limit = MaterialLimits.MAX_TEXTURE_SAMPLES
            report(
                "nodes",
                "samples textures ${load.samples} times per pixel; at most $limit",
                IssueCode.MATERIAL_SAMPLES,
                issueValues("samples" to load.samples, "limit" to limit),
            )
        }
        val reachable = GraphOrder.reachable(output).sortedBy { it.index }
        val inputs = inputs(reachable)
        val textures = ArrayList<TextureRef>()
        for (node in reachable) {
            val texture = node.settings.texture ?: continue
            if (texture !in textures) {
                textures += texture
            }
            node.settings.sampler = textures.indexOf(texture)
        }
        val graph = MaterialGraph(output, inputs, textures, cost, load.samples)
        return graph.takeIf { issues.size == start }
    }

    private fun inputs(reachable: List<GraphNode>): List<MaterialInput> {
        val values = reachable.filter { it.kind === ParamKind || it.kind === ExprKind }
        val edges = reachable.filter { it.kind === BodyEdgeKind }
        val lanes = if (edges.isEmpty()) 0 else MaterialInput.BodyEdgeInput.LANES
        val total = values.size + lanes
        if (total > MaterialLimits.MAX_INPUTS) {
            val detail = if (edges.isEmpty()) "" else " (the distance to the body edge takes $lanes)"
            report(
                "nodes",
                "reads $total params and expressions$detail; at most ${MaterialLimits.MAX_INPUTS}",
                IssueCode.MATERIAL_INPUTS,
                issueValues("inputs" to total, "limit" to MaterialLimits.MAX_INPUTS),
            )
        }
        values.forEachIndexed { i, node -> node.settings.input = i }
        edges.forEach { it.settings.input = values.size }
        return values.map(::materialInput) + List(lanes, MaterialInput::BodyEdgeInput)
    }

    private fun materialInput(node: GraphNode): MaterialInput {
        val expression = node.settings.expression
        return if (expression != null) {
            MaterialInput.Expression(node.settings.name, expression)
        } else {
            MaterialInput.Param(node.settings.name)
        }
    }

    private fun report(
        at: String,
        message: String,
        code: IssueCode = IssueCode.MATERIAL,
        values: Map<String, Double> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ) {
        issues += BundleIssue("$path#$at", message, code, values, names)
    }

    private companion object {
        const val NODE = "node"
        const val TYPE = "type"
        val ID = Regex("[a-z0-9_]{1,64}")
    }
}

internal class GraphCost(private val report: (GraphNode, String) -> Unit) {
    class Load(val cost: Int, val samples: Int)

    private val memo = HashMap<GraphNode, Load>()
    private val insideMemo = HashMap<GraphNode, Load>()
    private val reported = HashSet<Pair<GraphNode, String>>()

    fun of(root: GraphNode, inside: Boolean): Load {
        val cache = if (inside) insideMemo else memo
        return cache[root] ?: compute(root, inside).also { cache[root] = it }
    }

    private fun compute(root: GraphNode, inside: Boolean): Load {
        var cost = 0L
        var samples = 0L
        for (node in GraphOrder.cone(root)) {
            val source = node.source
            val repeat = node.kind === RepeatKind
            if (repeat && inside) {
                once(node, "a Repeat can't sit inside another Repeat's source")
            }
            if (node.kind.type == "RepeatIndex" && !inside) {
                once(node, "RepeatIndex only works inside a Repeat's source")
            }
            val sub = source?.let { of(it, inside || repeat) }
            val times = if (repeat) node.settings.count.toLong() else 1L
            cost += node.kind.cost(node) + times * (sub?.cost ?: 0)
            samples += node.kind.samples + times * (sub?.samples ?: 0)
        }
        return Load(minOf(cost, CAP).toInt(), minOf(samples, CAP).toInt())
    }

    private fun once(node: GraphNode, message: String) {
        if (reported.add(node to message)) {
            report(node, message)
        }
    }

    private companion object {
        const val CAP = 1_000_000_000L
    }
}
