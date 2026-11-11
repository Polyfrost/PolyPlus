package org.polyfrost.polyplus.client.utils

//? if > 1.8.9 {
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import net.minecraft.client.Minecraft

object MacSafeArea {
    private val supported = System.getProperty("os.name").orEmpty().startsWith("Mac") && System.getProperty("os.arch") == "aarch64"
    private var failed = false

    @Structure.FieldOrder("top", "left", "bottom", "right")
    class Insets : Structure(), Structure.ByValue {
        @JvmField var top = 0.0
        @JvmField var left = 0.0
        @JvmField var bottom = 0.0
        @JvmField var right = 0.0
    }

    @Structure.FieldOrder("x", "y", "width", "height")
    class Rect : Structure(), Structure.ByValue {
        @JvmField var x = 0.0
        @JvmField var y = 0.0
        @JvmField var width = 0.0
        @JvmField var height = 0.0
    }

    fun fullscreen(): Boolean = Minecraft.getInstance().options.fullscreen().get()

    fun topShare(): Float {
        if (!supported || failed || !fullscreen()) return 0f
        return runCatching { query() }.getOrElse {
            failed = true
            0f
        }
    }

    private fun query(): Float {
        val objc = NativeLibrary.getInstance("objc")
        val send = objc.getFunction("objc_msgSend")
        val select = objc.getFunction("sel_registerName")
        fun sel(name: String) = select.invoke(Pointer::class.java, arrayOf(name)) as Pointer
        val nsScreen = objc.getFunction("objc_getClass").invoke(Pointer::class.java, arrayOf("NSScreen")) as Pointer? ?: return 0f
        val screen = send.invoke(Pointer::class.java, arrayOf(nsScreen, sel("mainScreen"))) as Pointer? ?: return 0f
        val responds = send.invoke(Byte::class.javaPrimitiveType, arrayOf(screen, sel("respondsToSelector:"), sel("safeAreaInsets"))) as Byte
        if (responds.toInt() == 0) return 0f
        val insets = send.invoke(Insets::class.java, arrayOf(screen, sel("safeAreaInsets"))) as Insets
        val frame = send.invoke(Rect::class.java, arrayOf(screen, sel("frame"))) as Rect
        if (frame.height <= 0.0 || insets.top <= 0.0) return 0f
        return (insets.top / frame.height).toFloat().coerceIn(0f, 0.2f)
    }
}
//?} else {
/*object MacSafeArea {
    fun fullscreen(): Boolean = false

    fun topShare(): Float = 0f
}
*///?}
