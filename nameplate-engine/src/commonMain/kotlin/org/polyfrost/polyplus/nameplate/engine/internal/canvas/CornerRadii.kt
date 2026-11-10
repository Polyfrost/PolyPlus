package org.polyfrost.polyplus.nameplate.engine.internal.canvas

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class CornerRadii(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int) {
    val uniform: Boolean
        get() = topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft

    fun grownBy(amount: Int): CornerRadii =
        CornerRadii(topLeft + amount, topRight + amount, bottomRight + amount, bottomLeft + amount)

    fun fittedTo(rect: PixelRect): CornerRadii {
        var num = 1
        var den = 1
        val sides = intArrayOf(
            rect.width, topLeft + topRight,
            rect.width, bottomLeft + bottomRight,
            rect.height, topLeft + bottomLeft,
            rect.height, topRight + bottomRight,
        )
        var scaled = false
        for (i in sides.indices step 2) {
            val length = maxOf(sides[i], 0)
            val sum = sides[i + 1]
            if (sum > 0 && length * den < num * sum) {
                num = length
                den = sum
                scaled = true
            }
        }
        if (!scaled) return this

        return CornerRadii(topLeft * num / den, topRight * num / den, bottomRight * num / den, bottomLeft * num / den)
    }

    fun left(rect: PixelRect, y: Int): Int = maxOf(inset(topLeft, y - rect.y0), inset(bottomLeft, rect.y1 - 1 - y))

    fun right(rect: PixelRect, y: Int): Int = maxOf(inset(topRight, y - rect.y0), inset(bottomRight, rect.y1 - 1 - y))

    fun fill(canvas: PixelCanvas, rect: PixelRect, color: PixelCanvas.PixelColor) {
        val fitted = fittedTo(rect)
        if (fitted.uniform) {
            canvas.roundedRect(rect, fitted.topLeft, color)
            return
        }

        for (y in rect.y0 until rect.y1) {
            for (x in rect.x0 + fitted.left(rect, y) until rect.x1 - fitted.right(rect, y)) {
                canvas[x, y] = color.colorAt(x, y)
            }
        }
    }

    fun clear(canvas: PixelCanvas, rect: PixelRect) {
        val fitted = fittedTo(rect)
        for (y in rect.y0 until rect.y1) {
            for (x in rect.x0 + fitted.left(rect, y) until rect.x1 - fitted.right(rect, y)) {
                canvas.clear(x, y)
            }
        }
    }

    companion object {
        fun uniform(radius: Int): CornerRadii = CornerRadii(radius, radius, radius, radius)

        fun inset(radius: Int, rowFromEdge: Int): Int {
            if (rowFromEdge >= radius) return 0

            val dy = radius - rowFromEdge - 0.5
            return radius - sqrt(radius * radius - dy * dy).roundToInt()
        }
    }
}
