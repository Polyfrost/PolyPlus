package org.polyfrost.polyplus.client.pets

import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

internal class PetPlacement {
    var base = 0f
        private set
    var stop = Double.NaN
        private set
    private var nextBase = 0f
    private var nextStop = Double.NaN

    fun weigh(role: PetSharedRole?, age: Float, clock: Double): Float {
        if (role != null) {
            val weight = PetPose.roleWeight(role, age)
            nextBase = weight
            nextStop = Double.NaN
            return weight
        }
        nextBase = base
        nextStop = stop
        if (!(base > 0f)) return 0f
        if (stop.isNaN()) nextStop = clock
        val weight = PetPose.fadeWeight(base, nextStop, clock)
        if (!(weight > 0f)) nextBase = 0f
        return weight
    }

    fun commit() {
        base = nextBase
        stop = nextStop
    }

    fun reset() {
        base = 0f
        stop = Double.NaN
        nextBase = 0f
        nextStop = Double.NaN
    }
}

internal object PetPose {
    private const val PIXELS_PER_BLOCK = 16f
    private const val CROUCH_RENDER_DROP = 2f
    private const val CROUCH_BODY_DROP = 3.2f
    private const val CROUCH_HEAD_DROP = 4.2f
    private const val CROUCH_LEG_DROP = 0.2f
    private const val CROUCH_LEG_BACK = 4f
    private const val CROUCH_BODY_LEAN = 0.5f
    private const val CROUCH_ARM_LEAN = 0.4f
    private const val LIMB_FREQUENCY = 0.6662f
    private const val LEG_SWING = 1.4f
    private const val NECK = 24f
    private const val SHOULDER = 22f
    private const val ARM_SIDE = 5f
    private const val HIP = 12f
    private const val LEG_SIDE = 1.9f
    private const val HEAD_TOP = 8f
    private const val ARM_TOP = 2f
    private const val ARM_OUTER = 1f
    private const val MODEL_TOP = 24f
    private const val SLEEP_ROLL = 90f
    private const val SLEEP_TURN = 90f
    private const val SWIM_DROP = -1f
    private const val SWIM_BACK = -0.3f
    private const val ROLE_BLEND_SECONDS = 0.14f
    const val RIGHT_ARM = "rightArm"
    const val LEFT_ARM = "leftArm"
    //? if >= 1.21.5 {
    private const val VANILLA_FOLDS_GLIDE = true
    //?} else {
    /*private const val VANILLA_FOLDS_GLIDE = false
    *///?}

    private val bone = FloatArray(12)
    private val base = FloatArray(12)
    private val scratch = FloatArray(12)
    private val rootPart = FloatArray(12)
    private val limbPart = FloatArray(12)
    private val modelPart = FloatArray(12)
    private val MODEL_FLIP = floatArrayOf(1f, -1f, -1f)

    fun root(out: FloatArray, dx: Float, dy: Float, dz: Float, yawRadians: Float, pitchRadians: Float, k: Float) {
        val c = cos(yawRadians)
        val s = sin(yawRadians)
        val cp = cos(pitchRadians)
        val sp = sin(pitchRadians)
        out[0] = c * k
        out[1] = -s * sp * k
        out[2] = -s * cp * k
        out[3] = dx
        out[4] = 0f
        out[5] = cp * k
        out[6] = -sp * k
        out[7] = dy
        out[8] = s * k
        out[9] = c * sp * k
        out[10] = c * cp * k
        out[11] = dz
    }

    fun roleAnchor(
        out: FloatArray,
        anchorX: Double,
        anchorY: Double,
        anchorZ: Double,
        anchorYaw: Float,
        originX: Double,
        originY: Double,
        originZ: Double,
    ) {
        out[0] = (anchorX - originX).toFloat()
        out[1] = (anchorY - originY).toFloat()
        out[2] = (anchorZ - originZ).toFloat()
        out[3] = anchorYaw
    }

    fun roleWeight(role: PetSharedRole, age: Float): Float =
        ((age - role.startDelay.toFloat()) / ROLE_BLEND_SECONDS).coerceIn(0f, 1f)

    fun fadeWeight(base: Float, stop: Double, clock: Double): Float {
        if (!(base > 0f)) return 0f
        val fade = 1.0 - (clock - stop) / ROLE_BLEND_SECONDS
        if (!(fade > 0.0)) return 0f
        return base * min(fade, 1.0).toFloat()
    }

    fun placeRole(out: FloatArray, at: Int, role: PetSharedRole, age: Float, anchor: FloatArray) {
        val weight = roleWeight(role, age)
        if (!(weight > 0f)) return
        val radians = Math.toRadians(anchor[3].toDouble())
        val s = sin(radians)
        val c = cos(radians)
        val x = anchor[0] + role.offsetX * c - role.offsetZ * s
        val y = anchor[1] + role.offsetY
        val z = anchor[2] + role.offsetX * s + role.offsetZ * c
        out[at + 3] += ((x - out[at + 3]) * weight).toFloat()
        out[at + 7] += ((y - out[at + 7]) * weight).toFloat()
        out[at + 11] += ((z - out[at + 11]) * weight).toFloat()
    }

    fun sleepYaw(stepX: Int, stepZ: Int): Float = when {
        stepZ > 0 -> 90f
        stepZ < 0 -> 270f
        stepX > 0 -> 180f
        else -> 0f
    }

    fun glideYaw(viewX: Double, viewZ: Double, moveX: Double, moveZ: Double): Float {
        val move = moveX * moveX + moveZ * moveZ
        val view = viewX * viewX + viewZ * viewZ
        if (!(move > 0.0 && view > 0.0)) return 0f
        val along = (moveX * viewX + moveZ * viewZ) / sqrt(move * view)
        val across = moveX * viewZ - moveZ * viewX
        return glideTurn(along, across, VANILLA_FOLDS_GLIDE)
    }

    fun glideTurn(along: Double, across: Double, folds: Boolean): Float {
        val angle = if (folds) acos(min(1.0, abs(along))) else acos(along.coerceIn(-1.0, 1.0))
        return (sign(across) * angle).toFloat()
    }

    fun body(out: FloatArray, owner: PetOwner, dx: Float, dy: Float, dz: Float) {
        identity(out)
        out[3] = dx
        out[7] = dy
        out[11] = dz
        if (!owner.sleepYaw.isNaN()) {
            out[3] += owner.sleepShiftX
            out[7] += owner.sleepShiftY
            out[11] += owner.sleepShiftZ
            turnY(out, radians(owner.sleepYaw))
            turnZ(out, radians(SLEEP_ROLL))
            turnY(out, radians(SLEEP_TURN))
        } else {
            turnY(out, -radians(owner.renderBodyYaw))
            if (owner.leanPitch != 0f) turnX(out, -radians(owner.leanPitch))
            if (owner.leanYaw != 0f) turnY(out, owner.leanYaw)
            if (owner.swimShift) lift(out, 0f, SWIM_DROP, SWIM_BACK)
        }
        for (row in 0 until 3) {
            val r = row * 4
            out[r] /= PIXELS_PER_BLOCK
            out[r + 1] /= PIXELS_PER_BLOCK
            out[r + 2] /= PIXELS_PER_BLOCK
        }
    }

    fun shoulderAnchor(anchor: String?, owner: PetOwner): String =
        anchor ?: if (owner.leftHanded) LEFT_ARM else RIGHT_ARM

    fun shoulder(
        out: FloatArray,
        owner: PetOwner,
        anchor: String?,
        dx: Float,
        dy: Float,
        dz: Float,
        scale: Float,
        parts: FloatArray? = null,
    ) {
        val crouching = owner.flag(PlayerSignals.IS_SNEAKING)
        val c = if (crouching) 1f else 0f
        body(base, owner, dx, dy, dz)
        base[3] += base[1] * -CROUCH_RENDER_DROP * c
        base[7] += base[5] * -CROUCH_RENDER_DROP * c
        base[11] += base[9] * -CROUCH_RENDER_DROP * c
        if (parts != null && posed(bone, parts, anchor)) {
            mul(base, bone, scratch)
            scale(scratch, scale, out)
            return
        }
        val swing = owner.limbSwing * LIMB_FREQUENCY
        val amount = owner.limbAmount
        when (anchor) {
            "body" -> joint(bone, 0f, NECK, 0f, 0f, -CROUCH_BODY_DROP * c, 0f, 0f, CROUCH_BODY_LEAN * c)
            "head", "hat" -> joint(
                bone, 0f, NECK, 0f, 0f, -CROUCH_HEAD_DROP * c, 0f,
                -Math.toRadians(PetSimulation.wrapDegrees(owner.renderHeadYaw - owner.renderBodyYaw).toDouble()).toFloat(),
                Math.toRadians(owner.renderPitch.toDouble()).toFloat(),
            )
            "rightArm" -> joint(
                bone, -ARM_SIDE, SHOULDER, 0f, 0f, -CROUCH_BODY_DROP * c, 0f, 0f,
                cos(swing + PI.toFloat()) * amount + CROUCH_ARM_LEAN * c,
            )
            "leftArm" -> joint(
                bone, ARM_SIDE, SHOULDER, 0f, 0f, -CROUCH_BODY_DROP * c, 0f, 0f,
                cos(swing) * amount + CROUCH_ARM_LEAN * c,
            )
            "rightLeg" -> joint(
                bone, -LEG_SIDE, HIP, 0f, 0f, -CROUCH_LEG_DROP * c, -CROUCH_LEG_BACK * c, 0f,
                cos(swing) * LEG_SWING * amount,
            )
            "leftLeg" -> joint(
                bone, LEG_SIDE, HIP, 0f, 0f, -CROUCH_LEG_DROP * c, -CROUCH_LEG_BACK * c, 0f,
                cos(swing + PI.toFloat()) * LEG_SWING * amount,
            )
            else -> identity(bone)
        }
        when (anchor) {
            "body" -> lift(bone, 0f, NECK, 0f)
            "head", "hat" -> lift(bone, 0f, NECK + HEAD_TOP, 0f)
            "rightArm" -> lift(bone, -ARM_SIDE - ARM_OUTER, SHOULDER + ARM_TOP, 0f)
            "leftArm" -> lift(bone, ARM_SIDE + ARM_OUTER, SHOULDER + ARM_TOP, 0f)
        }
        mul(base, bone, scratch)
        scale(scratch, scale, out)
    }

    private fun posed(out: FloatArray, parts: FloatArray, anchor: String?): Boolean {
        val index = when (anchor) {
            "root" -> PetOwnerParts.ROOT
            "body" -> PetOwnerParts.BODY
            "head" -> PetOwnerParts.HEAD
            "hat" -> PetOwnerParts.HAT
            RIGHT_ARM -> PetOwnerParts.RIGHT_ARM
            LEFT_ARM -> PetOwnerParts.LEFT_ARM
            "rightLeg" -> PetOwnerParts.RIGHT_LEG
            "leftLeg" -> PetOwnerParts.LEFT_LEG
            else -> return false
        }
        val lx: Float
        val ly: Float
        when (index) {
            PetOwnerParts.ROOT -> {
                lx = 0f
                ly = MODEL_TOP
            }
            PetOwnerParts.HEAD, PetOwnerParts.HAT -> {
                lx = 0f
                ly = -HEAD_TOP
            }
            PetOwnerParts.RIGHT_ARM -> {
                lx = -ARM_OUTER
                ly = -ARM_TOP
            }
            PetOwnerParts.LEFT_ARM -> {
                lx = ARM_OUTER
                ly = -ARM_TOP
            }
            PetOwnerParts.RIGHT_LEG -> {
                lx = LEG_SIDE
                ly = HIP
            }
            PetOwnerParts.LEFT_LEG -> {
                lx = -LEG_SIDE
                ly = HIP
            }
            else -> {
                lx = 0f
                ly = 0f
            }
        }
        part(rootPart, parts, PetOwnerParts.ROOT)
        when {
            index == PetOwnerParts.ROOT -> rootPart.copyInto(modelPart)
            index == PetOwnerParts.HAT && PetOwnerParts.HAT_ON_HEAD -> {
                part(limbPart, parts, PetOwnerParts.HEAD)
                mul(rootPart, limbPart, scratch)
                part(limbPart, parts, index)
                mul(scratch, limbPart, modelPart)
            }
            else -> {
                part(limbPart, parts, index)
                mul(rootPart, limbPart, modelPart)
            }
        }
        val mx = modelPart[0] * lx + modelPart[1] * ly + modelPart[3]
        val my = modelPart[4] * lx + modelPart[5] * ly + modelPart[7]
        val mz = modelPart[8] * lx + modelPart[9] * ly + modelPart[11]
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                out[row * 4 + col] = modelPart[row * 4 + col] * MODEL_FLIP[row] * MODEL_FLIP[col]
            }
        }
        out[3] = mx
        out[7] = MODEL_TOP - my
        out[11] = -mz
        return true
    }

    private fun part(out: FloatArray, parts: FloatArray, index: Int) {
        val at = index * PetOwnerParts.PART_FLOATS
        val cx = cos(parts[at + 3])
        val sx = sin(parts[at + 3])
        val cy = cos(parts[at + 4])
        val sy = sin(parts[at + 4])
        val cz = cos(parts[at + 5])
        val sz = sin(parts[at + 5])
        val kx = parts[at + 6]
        val ky = parts[at + 7]
        val kz = parts[at + 8]
        out[0] = cz * cy * kx
        out[1] = (cz * sy * sx - sz * cx) * ky
        out[2] = (cz * sy * cx + sz * sx) * kz
        out[3] = parts[at]
        out[4] = sz * cy * kx
        out[5] = (sz * sy * sx + cz * cx) * ky
        out[6] = (sz * sy * cx - cz * sx) * kz
        out[7] = parts[at + 1]
        out[8] = -sy * kx
        out[9] = cy * sx * ky
        out[10] = cy * cx * kz
        out[11] = parts[at + 2]
    }

    private fun joint(
        out: FloatArray,
        px: Float,
        py: Float,
        pz: Float,
        ox: Float,
        oy: Float,
        oz: Float,
        yaw: Float,
        pitch: Float,
    ) {
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        out[0] = cy
        out[1] = sy * sp
        out[2] = sy * cp
        out[4] = 0f
        out[5] = cp
        out[6] = -sp
        out[8] = -sy
        out[9] = cy * sp
        out[10] = cy * cp
        out[3] = px + ox - (out[0] * px + out[1] * py + out[2] * pz)
        out[7] = py + oy - (out[4] * px + out[5] * py + out[6] * pz)
        out[11] = pz + oz - (out[8] * px + out[9] * py + out[10] * pz)
    }

    private fun radians(degrees: Float): Float = Math.toRadians(degrees.toDouble()).toFloat()

    private fun turnX(m: FloatArray, angle: Float) {
        val c = cos(angle)
        val s = sin(angle)
        for (row in 0 until 3) {
            val r = row * 4
            val y = m[r + 1]
            val z = m[r + 2]
            m[r + 1] = y * c + z * s
            m[r + 2] = z * c - y * s
        }
    }

    private fun turnY(m: FloatArray, angle: Float) {
        val c = cos(angle)
        val s = sin(angle)
        for (row in 0 until 3) {
            val r = row * 4
            val x = m[r]
            val z = m[r + 2]
            m[r] = x * c - z * s
            m[r + 2] = x * s + z * c
        }
    }

    private fun turnZ(m: FloatArray, angle: Float) {
        val c = cos(angle)
        val s = sin(angle)
        for (row in 0 until 3) {
            val r = row * 4
            val x = m[r]
            val y = m[r + 1]
            m[r] = x * c + y * s
            m[r + 1] = y * c - x * s
        }
    }

    private fun lift(out: FloatArray, x: Float, y: Float, z: Float) {
        out[3] += out[0] * x + out[1] * y + out[2] * z
        out[7] += out[4] * x + out[5] * y + out[6] * z
        out[11] += out[8] * x + out[9] * y + out[10] * z
    }

    private fun identity(out: FloatArray) {
        out.fill(0f)
        out[0] = 1f
        out[5] = 1f
        out[10] = 1f
    }

    private fun mul(a: FloatArray, b: FloatArray, out: FloatArray) {
        for (row in 0 until 3) {
            val r = row * 4
            for (col in 0 until 3) {
                out[r + col] = a[r] * b[col] + a[r + 1] * b[4 + col] + a[r + 2] * b[8 + col]
            }
            out[r + 3] = a[r] * b[3] + a[r + 1] * b[7] + a[r + 2] * b[11] + a[r + 3]
        }
    }

    private fun scale(a: FloatArray, k: Float, out: FloatArray) {
        for (row in 0 until 3) {
            val r = row * 4
            out[r] = a[r] * k
            out[r + 1] = a[r + 1] * k
            out[r + 2] = a[r + 2] * k
            out[r + 3] = a[r + 3]
        }
    }
}
