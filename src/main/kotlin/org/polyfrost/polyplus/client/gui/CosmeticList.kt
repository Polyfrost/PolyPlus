@file:Suppress("FunctionName")

package org.polyfrost.polyplus.client.gui

import kotlinx.coroutines.future.asCompletableFuture
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.http.responses.Cosmetic
import org.polyfrost.polyui.color.rgba
import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.hide
import org.polyfrost.polyui.component.extensions.ignoreLayout
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.extensions.setPalette
import org.polyfrost.polyui.component.impl.Block
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.component.impl.Text
import org.polyfrost.polyui.unit.Align
import org.polyfrost.polyui.unit.Vec2

private const val TEST_PRICE = "12.99"
private const val CARD_WIDTH = 180f
private const val CARD_HEIGHT = 258f

fun CosmeticList(size: Vec2): Drawable {
    val columnCount = (size.x / CARD_WIDTH).toInt()
    val rowCount = (size.y / CARD_HEIGHT).toInt()

    val cosmetics = mutableListOf<Cosmetic>()
    PolyCosmetics.getAll().asCompletableFuture().thenAccept { result ->
        result.onSuccess { list ->
            cosmetics.addAll(list.contents)
        }
    }.join()

    return Group(
        children = List(rowCount) { rowIndex ->
            Group(
                children = List(columnCount) { columnIndex ->
                    val cosmeticIndex = rowIndex * columnCount + columnIndex
                    if (cosmeticIndex < cosmetics.size) {
                        CosmeticCard(cosmetics[cosmeticIndex])
                    } else {
                        null
                    }
                }.filterNotNull().toTypedArray(),

                alignment = Align(padEdges = Vec2.ZERO, padBetween = Vec2(18f, 14f)),
                size = Vec2(CARD_WIDTH * columnCount, CARD_HEIGHT),
            )
        }.toTypedArray(),

        alignment = Align(padEdges = Vec2(13f, 17f)),
        size = size,
    ).named("CosmeticList")
}

private fun CosmeticCard(cosmetic: Cosmetic): Drawable {
    return Block(
        // Tag
        Block(

        ).ignoreLayout().hide(),

        // Preview
        Block(
            size = Vec2(144f, 144f),
            color = rgba(40, 40, 40),
        ),

        Group(
            // Name
            Text(
                text = "Cosmetic ${cosmetic.id}",
                fontSize = 14f
            ),

            // Price
            Text(
                text = "$${TEST_PRICE}",
                fontSize = 14f,
            ),

            size = Vec2(144f, 42f),
            alignment = Align(
                line = Align.Line.Start,
                mode = Align.Mode.Vertical,
                padBetween = Vec2(0f, 2f),
                padEdges = Vec2.ZERO
            ),
        ),

        size = Vec2(CARD_WIDTH, CARD_HEIGHT),
        alignment = Align(main = Align.Content.Center, cross = Align.Content.Center, padEdges = Vec2.ZERO),
        radii = floatArrayOf(12f),
    ).named("CosmeticCard")
}
