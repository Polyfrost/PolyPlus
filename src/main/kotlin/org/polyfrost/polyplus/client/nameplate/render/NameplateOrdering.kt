package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.1 {
import com.mojang.blaze3d.vertex.PoseStack
//? if >= 26.3 {
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialUniforms
//?}

object NameplateOrdering {
    const val MAX_RANKED: Int = 32

    private const val INSERTION_LIMIT = 48

    fun interface Runs {
        fun submit(frame: NameplateFrame, last: Int)
    }

    private class Entry {
        val poseStack = PoseStack()
        var frame: NameplateFrame? = null
        var task: Runs? = null
        var count = 0
        var distance = 0f
        var last = 0
    }

    private val entries = ArrayList<Entry>()
    private val byDistance = Comparator<Entry> { a, b -> b.distance.compareTo(a.distance) }
    private var size = 0
    private var collector: NameplateTarget? = null
    //? if >= 26.3 {
    private val shared = HashMap<NameplateLayer, Shared>()
    private val sharedOrder = ArrayList<Shared>()
    private val sharedPose = PoseStack()
    //?}

    @JvmStatic
    fun begin(target: NameplateTarget) {
        clear()
        collector = target
        //? if >= 26.3 {
        for (batch in sharedOrder) {
            batch.clear()
        }
        sharedOrder.clear()
        NameplateMaterialUniforms.beginBatch()
        //?}
    }

    fun defer(frame: NameplateFrame, runs: Int, task: Runs): Boolean {
        val target = collector
        if (target == null || frame.collector !== target) {
            return false
        }
        if (size == entries.size) {
            entries += Entry()
        }
        val entry = entries[size++]
        entry.frame = frame.detached(entry.poseStack)
        entry.task = task
        entry.count = runs
        entry.distance = frame.cameraDistance
        return true
    }

    //? if >= 26.3 {
    fun share(frame: NameplateFrame, renderType: NameplateLayer, renderer: NameplateGeometry): Boolean {
        val target = collector
        if (target == null || frame.collector !== target) {
            return false
        }
        val batch = shared.getOrPut(renderType) { Shared(renderType) }
        if (batch.size == 0) {
            sharedOrder += batch
        }
        batch.add(frame, renderer)
        return true
    }
    //?}

    @JvmStatic
    fun flush() {
        val target = collector
        collector = null
        try {
            //? if >= 26.3 {
            if (target != null) {
                for (batch in sharedOrder) {
                    batch.submit(target, sharedPose)
                }
            }
            //?}
            submitRanked()
        } finally {
            clear()
            //? if >= 26.3 {
            NameplateMaterialUniforms.flushBatch()
            //?}
        }
    }

    private fun submitRanked() {
        if (size == 0) {
            return
        }
        sortByDistance()

        val overflow = maxOf(0, size - MAX_RANKED)
        var next = NameplateCanvas.ORDER - 1
        for (i in size - 1 downTo overflow) {
            val entry = entries[i]
            entry.last = next
            next -= entry.count
        }
        for (i in 0 until size) {
            val entry = entries[i]
            val frame = entry.frame ?: continue
            entry.task?.submit(frame, if (i < overflow) next else entry.last)
        }
    }

    private fun sortByDistance() {
        if (size > INSERTION_LIMIT) {
            entries.subList(0, size).sortWith(byDistance)
            return
        }
        for (i in 1 until size) {
            val entry = entries[i]
            var j = i - 1
            while (j >= 0 && entries[j].distance.compareTo(entry.distance) < 0) {
                entries[j + 1] = entries[j]
                j--
            }
            entries[j + 1] = entry
        }
    }

    private fun clear() {
        for (i in 0 until size) {
            val entry = entries[i]
            entry.frame = null
            entry.task = null
        }
        size = 0
    }

    //? if >= 26.3 {
    private class Shared(private val renderType: NameplateLayer) {
        private val poses = ArrayList<PoseStack>()
        private val renderers = ArrayList<NameplateGeometry?>()
        var size = 0
            private set

        fun add(frame: NameplateFrame, renderer: NameplateGeometry) {
            if (size == poses.size) {
                poses += PoseStack()
                renderers += null
            }
            val pose = poses[size]
            pose.last().set(frame.poseStack.last())
            pose.translate(frame.x, frame.y, 0f)
            renderers[size] = renderer
            size++
        }

        fun submit(target: NameplateTarget, pose: PoseStack) {
            val count = size
            target.order(NameplateCanvas.ORDER).submitCustomGeometry(pose, renderType) { _, buffer ->
                for (i in 0 until count) {
                    renderers[i]?.render(poses[i].last(), buffer)
                }
            }
        }

        fun clear() {
            renderers.fill(null)
            size = 0
        }
    }
    //?}
}
//?}
