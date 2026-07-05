//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.core.Direction
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockBone
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockCube
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockCubeFace
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockFaceUv
import org.polyfrost.polyplus.client.bedrock.geometry.bedrockRotationRadians
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class BedrockMesh private constructor(
    val quads: List<BedrockQuad>,
) {
    fun render(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        lightCoords: Int,
        overlayCoords: Int,
        color: Int = -1,
    ) {
        if (quads.isEmpty())
            return

        val scratch = Vector3f()
        for (quad in quads) {
            quad.emit(pose, buffer, lightCoords, overlayCoords, color, scratch)
        }
    }

    companion object {
        val EMPTY: BedrockMesh = BedrockMesh(emptyList())

        fun fromBone(bone: BedrockBone, textureWidth: Int, textureHeight: Int, lightLevel: Int = -1): BedrockMesh {
            if (bone.cubes.isEmpty())
                return EMPTY

            return merge(bone.cubes.map { fromCube(it, bone, textureWidth, textureHeight, lightLevel) })
        }

        fun fromCube(cube: BedrockCube, bone: BedrockBone, texW: Int, texH: Int, lightLevel: Int = -1): BedrockMesh {
            val bounds = computeBounds(cube, bone)
            val mirror = cube.size.x < 0f
            val flipU = cube.mirror
            val rotation = cubeRotation(cube)
            val pivot = cubeRotationPivot(cube, bone)

            val quads = if (cube.uv.faces.isNotEmpty()) {
                buildFaceQuads(bounds, cube.uv.faces, texW.toFloat(), texH.toFloat(), mirror, flipU, rotation, pivot)
            } else {
                buildBoxQuads(cube, bounds, cube.uv.box, texW.toFloat(), texH.toFloat(), mirror, flipU, rotation, pivot)
            }

            return BedrockMesh(if (lightLevel < 0) quads else quads.map { it.copy(lightLevel = lightLevel) })
        }

        fun merge(meshes: List<BedrockMesh>): BedrockMesh {
            return BedrockMesh(meshes.flatMap { it.quads })
        }

        private data class CubeBounds(
            val minX: Float,
            val minY: Float,
            val minZ: Float,
            val maxX: Float,
            val maxY: Float,
            val maxZ: Float,
        )

        private fun computeBounds(cube: BedrockCube, bone: BedrockBone): CubeBounds {
            val endX = cube.origin.x + cube.size.x
            val endY = cube.origin.y + cube.size.y
            val endZ = cube.origin.z + cube.size.z
            val pivot = bone.pivot
            val inflate = cube.inflate

            val minX = min(cube.origin.x, endX) - pivot.x - inflate
            val minY = min(cube.origin.y, endY)
            val maxY = max(cube.origin.y, endY)
            val minZ = min(cube.origin.z, endZ) - pivot.z - inflate

            val width = abs(cube.size.x)
            val depth = abs(cube.size.z)

            // Bedrock Y-up is Java Y-down
            val modelMinY = -(maxY - pivot.y) - inflate
            val modelMaxY = -(minY - pivot.y) + inflate

            var modelMinZ = minZ
            var modelMaxZ = minZ + depth
            if (depth < BILLBOARD_EPSILON) {
                val centerZ = (min(cube.origin.z, endZ) + max(cube.origin.z, endZ)) * 0.5f - pivot.z
                modelMinZ = centerZ - BILLBOARD_HALF_THICKNESS
                modelMaxZ = centerZ + BILLBOARD_HALF_THICKNESS
            }

            return CubeBounds(
                minX = minX,
                minY = modelMinY,
                minZ = modelMinZ,
                maxX = minX + width,
                maxY = modelMaxY,
                maxZ = modelMaxZ,
            )
        }

        private fun cubeRotation(cube: BedrockCube): Quaternionf? {
            val radians = cube.rotation.bedrockRotationRadians()
            if (radians.lengthSquared() < 1e-8f)
                return null

            return Quaternionf().rotationXYZ(radians.x, radians.y, radians.z)
        }

        private fun cubeRotationPivot(cube: BedrockCube, bone: BedrockBone): Vector3f {
            val pivot = cube.pivot ?: run {
                val endX = cube.origin.x + cube.size.x
                val endY = cube.origin.y + cube.size.y
                val endZ = cube.origin.z + cube.size.z
                Vector3f(
                    (cube.origin.x + endX) * 0.5f,
                    (cube.origin.y + endY) * 0.5f,
                    (cube.origin.z + endZ) * 0.5f,
                )
            }
            return bedrockPointToModel(pivot, bone.pivot)
        }

        private fun bedrockPointToModel(point: Vector3f, bonePivot: Vector3f): Vector3f {
            return Vector3f(
                point.x - bonePivot.x,
                bonePivot.y - point.y,
                point.z - bonePivot.z,
            )
        }

        private fun transformCorners(
            corners: Array<BedrockMeshVertex>,
            rotation: Quaternionf?,
            pivot: Vector3f,
        ) {
            if (rotation == null)
                return

            val scratch = Vector3f()
            for (i in corners.indices) {
                val corner = corners[i]
                scratch.set(corner.x, corner.y, corner.z)
                scratch.sub(pivot)
                rotation.transform(scratch)
                scratch.add(pivot)
                corners[i] = corner.copy(x = scratch.x, y = scratch.y, z = scratch.z)
            }
        }

        private fun copyCorners(corners: Array<BedrockMeshVertex>): Array<BedrockMeshVertex> {
            return Array(corners.size) { i -> corners[i].copy() }
        }

        private fun buildFaceQuads(
            bounds: CubeBounds,
            faceUvs: Map<BedrockCubeFace, BedrockFaceUv>,
            texW: Float,
            texH: Float,
            mirror: Boolean,
            flipU: Boolean,
            rotation: Quaternionf?,
            pivot: Vector3f,
        ): List<BedrockQuad> {
            val north0 = corner(bounds.minX, bounds.minY, bounds.minZ)
            val north1 = corner(bounds.maxX, bounds.minY, bounds.minZ)
            val north2 = corner(bounds.maxX, bounds.maxY, bounds.minZ)
            val north3 = corner(bounds.minX, bounds.maxY, bounds.minZ)

            val south0 = corner(bounds.minX, bounds.minY, bounds.maxZ)
            val south1 = corner(bounds.maxX, bounds.minY, bounds.maxZ)
            val south2 = corner(bounds.maxX, bounds.maxY, bounds.maxZ)
            val south3 = corner(bounds.minX, bounds.maxY, bounds.maxZ)

            val quads = ArrayList<BedrockQuad>(faceUvs.size)

            for ((face, uv) in faceUvs) {
                val u0 = uv.uv.x
                val v0 = uv.uv.y
                val u1 = u0 + uv.size.x
                val v1 = v0 + uv.size.y

                val corners = when (face) {
                    BedrockCubeFace.NORTH -> arrayOf(north1, north0, north3, north2)
                    BedrockCubeFace.SOUTH -> arrayOf(south0, south1, south2, south3)
                    BedrockCubeFace.EAST -> arrayOf(south1, north1, north2, south2)
                    BedrockCubeFace.WEST -> arrayOf(north0, south0, south3, north3)
                    BedrockCubeFace.UP -> arrayOf(north2, north3, south3, south2)
                    BedrockCubeFace.DOWN -> arrayOf(south1, south0, north0, north1)
                }

                val facing = when (face) {
                    BedrockCubeFace.NORTH -> Direction.NORTH
                    BedrockCubeFace.SOUTH -> Direction.SOUTH
                    BedrockCubeFace.EAST -> Direction.EAST
                    BedrockCubeFace.WEST -> Direction.WEST
                    BedrockCubeFace.UP -> Direction.UP
                    BedrockCubeFace.DOWN -> Direction.DOWN
                }

                val copied = copyCorners(corners)
                transformCorners(copied, rotation, pivot)
                buildPolygon(copied, u0, v0, u1, v1, texW, texH, mirror, flipU, facing)?.let(quads::add)
            }

            return quads
        }

        private fun buildBoxQuads(
            cube: BedrockCube,
            bounds: CubeBounds,
            boxUv: List<Int>,
            texW: Float,
            texH: Float,
            mirror: Boolean,
            flipU: Boolean,
            rotation: Quaternionf?,
            pivot: Vector3f,
        ): List<BedrockQuad> {
            val width = abs(cube.size.x)
            val height = abs(cube.size.y)
            val depth = abs(cube.size.z)

            // Blockbench floors box-UV dimensions - if we dont, then it results
            // in fucked up UV
            val uvWidth = floor(width)
            val uvHeight = floor(height)
            val uvDepth = floor(depth)

            val north0 = corner(bounds.minX, bounds.minY, bounds.minZ)
            val north1 = corner(bounds.maxX, bounds.minY, bounds.minZ)
            val north2 = corner(bounds.maxX, bounds.maxY, bounds.minZ)
            val north3 = corner(bounds.minX, bounds.maxY, bounds.minZ)

            val south0 = corner(bounds.minX, bounds.minY, bounds.maxZ)
            val south1 = corner(bounds.maxX, bounds.minY, bounds.maxZ)
            val south2 = corner(bounds.maxX, bounds.maxY, bounds.maxZ)
            val south3 = corner(bounds.minX, bounds.maxY, bounds.maxZ)

            val u0 = (if (boxUv.size >= 2) boxUv[0] else 0).toFloat()
            val v0 = (if (boxUv.size >= 2) boxUv[1] else 0).toFloat()
            val u1 = u0 + uvDepth
            val u2 = u0 + uvDepth + uvWidth
            val u22 = u0 + uvDepth + uvWidth + uvWidth
            val u3 = u0 + uvDepth + uvWidth + uvDepth
            val u4 = u0 + uvDepth + uvWidth + uvDepth + uvWidth
            val v1 = v0 + uvDepth
            val v2 = v0 + uvDepth + uvHeight

            val quads = ArrayList<BedrockQuad>(6)

            fun addFace(
                corners: Array<BedrockMeshVertex>,
                faceU0: Float,
                faceV0: Float,
                faceU1: Float,
                faceV1: Float,
                facing: Direction,
            ) {
                buildBoxPolygon(corners, faceU0, faceV0, faceU1, faceV1, texW, texH, mirror, flipU, facing, rotation, pivot)
                    ?.let(quads::add)
            }

            if (depth < BILLBOARD_EPSILON) {
                if (width >= BILLBOARD_EPSILON && height >= BILLBOARD_EPSILON) {
                    addFace(
                        arrayOf(north1, north0, north3, north2),
                        u0 + uvDepth, v0 + uvDepth, u0 + uvDepth + uvWidth, v0 + uvDepth + uvHeight,
                        Direction.NORTH,
                    )
                    addFace(
                        arrayOf(south0, south1, south2, south3),
                        u0 + uvDepth, v0 + uvDepth, u0 + uvDepth + uvWidth, v0 + uvDepth + uvHeight,
                        Direction.SOUTH,
                    )
                }
                return quads
            }

            addFace(arrayOf(south1, south0, north0, north1), u1, v1, u2, v0, Direction.DOWN)
            addFace(arrayOf(north2, north3, south3, south2), u2, v0, u22, v1, Direction.UP)
            addFace(arrayOf(north0, south0, south3, north3), u0, v1, u1, v2, Direction.WEST)
            addFace(arrayOf(north1, north0, north3, north2), u1, v1, u2, v2, Direction.NORTH)
            addFace(arrayOf(south1, north1, north2, south2), u2, v1, u3, v2, Direction.EAST)
            addFace(arrayOf(south0, south1, south2, south3), u3, v1, u4, v2, Direction.SOUTH)
            return quads
        }

        private fun buildBoxPolygon(
            corners: Array<BedrockMeshVertex>,
            u0: Float,
            v0: Float,
            u1: Float,
            v1: Float,
            texW: Float,
            texH: Float,
            mirror: Boolean,
            flipU: Boolean,
            facing: Direction,
            rotation: Quaternionf?,
            pivot: Vector3f,
        ): BedrockQuad? {
            val copied = copyCorners(corners)
            transformCorners(copied, rotation, pivot)
            return buildPolygon(copied, u0, v0, u1, v1, texW, texH, mirror, flipU, facing)
        }

        private fun corner(x: Float, y: Float, z: Float): BedrockMeshVertex {
            return BedrockMeshVertex(x, y, z, 0f, 0f)
        }

        private fun buildPolygon(
            corners: Array<BedrockMeshVertex>,
            u0In: Float,
            v0: Float,
            u1In: Float,
            v1: Float,
            texW: Float,
            texH: Float,
            mirror: Boolean,
            flipU: Boolean,
            facing: Direction,
        ): BedrockQuad? {
            // flipU swaps the U span so the texture mirrors horizontally (Bedrock
            // `"mirror"`), without touching geometry winding.
            val u0 = if (flipU) u1In else u0In
            val u1 = if (flipU) u0In else u1In
            if (abs(u1 - u0) < BILLBOARD_EPSILON || abs(v1 - v0) < BILLBOARD_EPSILON) {
                return null
            }

            val normalFacing = if (mirror) mirrorFacing(facing) else facing
            val normal = normalFacing.step()

            val us = 0f / texW
            val vs = 0f / texH
            val mapped = arrayOf(
                corners[0].copy(u = u1 / texW - us, v = v0 / texH + vs),
                corners[1].copy(u = u0 / texW + us, v = v0 / texH + vs),
                corners[2].copy(u = u0 / texW + us, v = v1 / texH - vs),
                corners[3].copy(u = u1 / texW - us, v = v1 / texH - vs),
            )

            if (mirror) {
                mapped.reverse()
            }

            return BedrockQuad(
                v0 = mapped[0],
                v1 = mapped[1],
                v2 = mapped[2],
                v3 = mapped[3],
                nx = normal.x(),
                ny = normal.y(),
                nz = normal.z(),
            )
        }

        private fun mirrorFacing(facing: Direction): Direction {
            return if (facing.axis == Direction.Axis.X) facing.opposite else facing
        }

        private const val BILLBOARD_EPSILON = 0.0001f
        private const val BILLBOARD_HALF_THICKNESS = 0.01f
    }
}
//?}
