package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.render.vertex.BufferBuilder
import net.minecraft.client.render.vertex.BufferUploader
import org.apache.logging.log4j.LogManager
import java.lang.reflect.Method

object LegacyTagBatch {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val BATCH = "dev.rdh.argentum.impl.render.entity.NameTagBatch"

    private val uploader = BufferUploader()
    private var capturing: Method? = null
    private var flush: Method? = null

    init {
        val type = try {
            Class.forName(BATCH, false, LegacyTagBatch::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: LinkageError) {
            null
        }
        if (type != null) {
            try {
                capturing = type.getMethod("capturing")
                flush = type.getMethod("flush")
                LOGGER.info("Name tag batching found ({}): nameplate decorations flush it and draw directly", BATCH)
            } catch (e: ReflectiveOperationException) {
                LOGGER.warn("Name tag batching {} not readable; nameplate decorations inside it may draw out of order", BATCH, e)
            }
        }
    }

    fun active(): Boolean = batch() != null

    fun flush(): Boolean {
        val batch = batch() ?: return false
        val method = flush ?: return false
        return try {
            method.invoke(batch)
            true
        } catch (e: ReflectiveOperationException) {
            fail(e)
            false
        }
    }

    fun draw(buffer: BufferBuilder) {
        buffer.end()
        uploader.end(buffer)
    }

    fun discard(buffer: BufferBuilder) {
        buffer.end()
        buffer.clear()
    }

    private fun batch(): Any? {
        val method = capturing ?: return null
        return try {
            method.invoke(null)
        } catch (e: ReflectiveOperationException) {
            fail(e)
            null
        }
    }

    private fun fail(error: ReflectiveOperationException) {
        capturing = null
        flush = null
        LOGGER.error("Name tag batching query failed; nameplate decorations no longer bypass it", error)
    }
}
*///?}
