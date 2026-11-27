package org.polyfrost.polyplus.client.nameplate.material

import org.polyfrost.plusrenderer.Nameplates
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_CLIP_AT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_CLIP_COUNT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_MIRRORED
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_UV_FLOATS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_X0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_X1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_Y0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_Y1

fun interface NameplateQuadSink {
    fun quad(x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float)
}

object NameplateMaterialQuads {
    @JvmStatic
    fun emit(list: NameplateDrawList, index: Int, dx: Float, dy: Float, sink: NameplateQuadSink): Int {
        val m = list.materials
        val at = index * MATERIAL_WORDS
        val ax0 = m[at + MATERIAL_X0]
        val ay0 = m[at + MATERIAL_Y0]
        val ax1 = m[at + MATERIAL_X1]
        val ay1 = m[at + MATERIAL_Y1]
        val uv = list.materialUv
        val ut = index * MATERIAL_UV_FLOATS
        val u0 = uv[ut]
        val v0 = uv[ut + 1]
        val u1 = uv[ut + 2]
        val v1 = uv[ut + 3]
        val mirrored = m[at + MATERIAL_MIRRORED] != 0
        val clipCount = m[at + MATERIAL_CLIP_COUNT]
        if (clipCount == 0) {
            if (mirrored) sink.quad(ax0 + dx, ay0 + dy, ax1 + dx, ay1 + dy, u1, v0, u0, v1)
            else sink.quad(ax0 + dx, ay0 + dy, ax1 + dx, ay1 + dy, u0, v0, u1, v1)
            return 1
        }
        val su = (u1 - u0) / (ax1 - ax0)
        val sv = (v1 - v0) / (ay1 - ay0)
        val clips = list.clips
        var drawn = 0
        var c = m[at + MATERIAL_CLIP_AT]
        for (k in 0 until clipCount) {
            val cx0 = maxOf(ax0, clips[c])
            val cy0 = maxOf(ay0, clips[c + 1])
            val cx1 = minOf(ax1, clips[c + 2])
            val cy1 = minOf(ay1, clips[c + 3])
            c += Nameplates.CLIP_WORDS
            if (cx1 <= cx0 || cy1 <= cy0) continue
            val left = if (mirrored) u1 - (cx0 - ax0) * su else u0 + (cx0 - ax0) * su
            val right = if (mirrored) u1 - (cx1 - ax0) * su else u0 + (cx1 - ax0) * su
            val top = v0 + (cy0 - ay0) * sv
            val bottom = v0 + (cy1 - ay0) * sv
            sink.quad(cx0 + dx, cy0 + dy, cx1 + dx, cy1 + dy, left, top, right, bottom)
            drawn++
        }
        return drawn
    }
}
