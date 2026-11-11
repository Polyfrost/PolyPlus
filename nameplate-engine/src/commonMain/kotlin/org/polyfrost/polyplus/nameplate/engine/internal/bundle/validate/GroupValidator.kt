package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.GroupJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink

internal class GroupValidator(
    private val manifest: ManifestJson,
    private val report: IssueSink,
) {
    private val layerIds = manifest.layers.mapTo(HashSet()) { it.id }
    private val parents = HashMap<String, String?>()

    fun validate() {
        val groups = manifest.groups ?: return
        val claimed = HashMap<String, String>()
        for ((i, group) in groups.withIndex()) {
            checkId("$PATH[$i].id", group)
            checkLayers("$PATH[$i].layers", group, claimed)
        }
        for ((i, group) in groups.withIndex()) {
            checkParent("$PATH[$i].parent", group)
        }
    }

    private fun checkId(path: String, group: GroupJson) {
        when {
            !BundleFiles.isSpriteId(group.id) -> report(path, "id must be 1 to 64 of a-z, 0-9 and _")
            group.id in layerIds -> report(path, "'${group.id}' is already a layer")
            group.id in parents -> report(path, "duplicate group '${group.id}'")
        }
        parents.getOrPut(group.id) { group.parent }
    }

    private fun checkLayers(path: String, group: GroupJson, claimed: MutableMap<String, String>) {
        for ((j, layer) in group.layers.withIndex()) {
            val owner = claimed[layer]
            when {
                layer !in layerIds -> report("$path[$j]", "no layer '$layer'")
                owner != null -> report("$path[$j]", "'$layer' is already in group '$owner'; a layer belongs to one group")
                else -> claimed[layer] = group.id
            }
        }
    }

    private fun checkParent(path: String, group: GroupJson) {
        val parent = group.parent ?: return
        when {
            parent !in parents -> report(path, "no group '$parent'")
            inCycle(group.id) -> report(path, "'${group.id}' would be its own ancestor")
        }
    }

    private fun inCycle(start: String): Boolean {
        var current = parents[start]
        var hops = 0
        while (current != null && hops++ <= parents.size) {
            if (current == start) return true
            current = parents[current]
        }
        return false
    }

    private companion object {
        const val PATH = "${BundleFiles.MANIFEST}#groups"
    }
}
