@file:Suppress("FunctionName")

package org.polyfrost.polyplus.client.gui

import kotlinx.coroutines.future.asCompletableFuture
import org.polyfrost.polyplus.client.network.http.PolyCosmetics
import org.polyfrost.polyplus.client.network.http.responses.Cosmetic
import org.polyfrost.polyui.PolyUI
import org.polyfrost.polyui.color.rgba
import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.events
import org.polyfrost.polyui.component.extensions.hide
import org.polyfrost.polyui.component.extensions.ignoreLayout
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.extensions.setPalette
import org.polyfrost.polyui.component.impl.Block
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.component.impl.Image
import org.polyfrost.polyui.component.impl.Text
import org.polyfrost.polyui.event.Event
import org.polyfrost.polyui.event.State
import org.polyfrost.polyui.unit.Align
import org.polyfrost.polyui.unit.Vec2
import org.polyfrost.polyui.utils.image
import kotlin.math.max

private const val TEST_PRICE = "12.99"
private const val BOX_HORIZONTAL_PADDING = 13f
private const val BOX_VERTICAL_PADDING = 17f
private const val CARD_WIDTH = 180f
private const val CARD_HEIGHT = 258f

fun CosmeticList(polyUI: PolyUI, size: Vec2): Drawable {
    // yay magic numbies :3
    val rawColumns = ((size.x - 2 * BOX_HORIZONTAL_PADDING + 18f) / (CARD_WIDTH + 18f)).toInt()
    val rawRows = ((size.y - 2 * BOX_VERTICAL_PADDING + 14f) / (CARD_HEIGHT + 14f)).toInt()
    val columnCount = max(1, rawColumns)
    val rowCount = max(1, rawRows)
    println("CosmeticList will have $columnCount columns and $rowCount rows for size $size")

    val cosmetics = State<List<Cosmetic>>(emptyList())
    val completableFuture = PolyCosmetics.getAll()
        .asCompletableFuture()
        .thenAccept { result ->
            result.onSuccess { list ->
                cosmetics.value = list.contents
            }
        }

    var rowsContainer: Drawable? = null
    val cosmeticsListener = cosmeticsListener@ { newCosmetics: List<Cosmetic> ->
        println("Received ${newCosmetics.size} cosmetics:\n${newCosmetics.joinToString("\n")}")
        val theRows = rowsContainer ?: return@cosmeticsListener false
        val maxVisible = rowCount * columnCount
        val showing = if (newCosmetics.size > maxVisible) {
            newCosmetics.subList(0, maxVisible)
        } else {
            newCosmetics
        }

        // Clear existing cards
        for (rowIndex in 0 until rowCount) {
            val rowDrawable = theRows[rowIndex] as? Group ?: continue

            val existingChildren = rowDrawable.children ?: emptyList()
            for (child in existingChildren) {
                rowDrawable.removeChild(child)
            }

            for (columnIndex in 0 until columnCount) {
                val cosmeticIndex = rowIndex * columnCount + columnIndex
                val cosmetic = showing.getOrNull(cosmeticIndex) ?: break
                rowDrawable.addChild(CosmeticCard(cosmetic))
            }
        }

        false
    }

    cosmetics.listen(cosmeticsListener)

    val list = Group(
        children = Array(rowCount) { rowIndex ->
            Group(
                children = emptyArray(), // start empty; listener will populate
                alignment = Align(
                    padEdges = Vec2.ZERO,
                    padBetween = Vec2(18f, 14f),
                ),
                size = Vec2(CARD_WIDTH * columnCount, CARD_HEIGHT),
            ).named("CosmeticListRow$rowIndex")
        },

        alignment = Align(padEdges = Vec2(BOX_HORIZONTAL_PADDING, BOX_VERTICAL_PADDING)),
        size = size,
    ).events {
        Event.Lifetime.Removed then {
            cosmetics.removeListener(cosmeticsListener)
            completableFuture.cancel(true)
            Unit
        }
    }.named("CosmeticList")

    // Hook the container so the listener can see it
    list.setup(polyUI)
    rowsContainer = list

    // Optionally, populate immediately with whatever is currently in state
    if (cosmetics.value.isNotEmpty()) {
        cosmeticsListener(cosmetics.value)
    }

    return list
}

private fun CosmeticCard(cosmetic: Cosmetic): Drawable {
    return Block(
        // Tag
        Block(

        ).ignoreLayout().hide(),

        // Content
        Group(
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

            size = Vec2(CARD_WIDTH, 198f),
            alignment = Align(padEdges = Vec2(18f, 18f), padBetween = Vec2(0f, 8f)),
        ),

        // Add to Cart Button
        Block(
            Image("/assets/polyplus/ico/shopping-cart/0.svg".image()),
            Text("Add to cart", fontSize = 14f),

            size = Vec2(180f, 36f),
            alignment = Align(main = Align.Content.Center, cross = Align.Content.Center),
            radii = floatArrayOf(0f, 0f, 12f, 12f),
        ).setPalette { component.bg },

        size = Vec2(CARD_WIDTH, CARD_HEIGHT),
        alignment = Align(main = Align.Content.Center, cross = Align.Content.SpaceBetween, padEdges = Vec2.ZERO),
        radii = floatArrayOf(12f),
    ).named("CosmeticCard")
}
