@file:Suppress("FunctionName")

package org.polyfrost.polyplus.client.gui

import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.events
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.extensions.namedId
import org.polyfrost.polyui.component.extensions.onClick
import org.polyfrost.polyui.component.extensions.setPalette
import org.polyfrost.polyui.component.extensions.withHoverStates
import org.polyfrost.polyui.component.impl.Block
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.component.impl.Image
import org.polyfrost.polyui.component.impl.Text
import org.polyfrost.polyui.data.PolyImage
import org.polyfrost.polyui.event.Event
import org.polyfrost.polyui.event.State
import org.polyfrost.polyui.unit.Align
import org.polyfrost.polyui.unit.Vec2
import org.polyfrost.polyui.utils.image

fun CartControls(count: State<Int>): Drawable {
    var cartButton: Drawable? = null
    var checkoutButton: Drawable? = null

    val countListener = countListener@ { count: Int ->
        cartButton?.let {
            updateCartIcon(it, count)
        }

        checkoutButton?.let {
            updateCheckoutButtonText(it, count)
        }

        false
    }

    count.listen(countListener)

    val currentCartCount = count.value
    return Group(
        PlusButton(
            image = createCartIconPath(currentCartCount).image(),
            text = "Cart",
            radii = floatArrayOf(6f),
            size = Vec2(146f, 32f)
        ).named("CartButton").also { cartButton = it },
        PlusButton(
            image = "/assets/polyplus/ico/shopping-bag.svg".image(),
            text = createCheckoutButtonText(currentCartCount),
            radii = floatArrayOf(6f),
            size = Vec2(306f, 32f)
        ).onClick {
            count.value++
            Unit
        }.setPalette { brand.fg }.named("CheckoutButton").also { checkoutButton = it },
        size = Vec2(465f, 32f),
        alignment = Align(padBetween = Vec2(13f, 0f), padEdges = Vec2.ZERO, wrap = Align.Wrap.NEVER)
    ).events {
        Event.Lifetime.Removed then {
            count.removeListener(countListener)
            Unit
        }
    }.named("CartControls")
}

private fun PlusButton(
    image: PolyImage,
    text: String,
    radii: FloatArray,
    padding: Vec2 = Vec2(12f, 6f),
    size: Vec2 = Vec2.ZERO,
): Block {
    return Block(
        Image(image).named("Icon"),
        Text(text, fontSize = 12f),
        radii = radii,
        size = size,
        alignment = Align(main = Align.Content.Center, wrap = Align.Wrap.NEVER, pad = padding)
    ).withHoverStates().namedId("PlusButton")
}

private fun createCartIconPath(count: Int): String {
    if (true) {
        // Just until the rest are done
        return "/assets/polyplus/ico/shopping-cart/0.svg"
    }

    return when {
        count <= 0 -> "/assets/polyplus/ico/shopping-cart/0.svg"
        count in 1..9 -> "/assets/polyplus/ico/shopping-cart/$count.svg"
        else -> "/assets/polyplus/ico/shopping-cart/9+.svg"
    }
}

private fun updateCartIcon(button: Drawable, count: Int) {
    var icon = button[0]
    if (icon !is Image) {
        icon = button[1]
    }

    (icon as? Image)?.image = createCartIconPath(count).image()
}

private fun createCheckoutButtonText(count: Int): String {
    return if (count > 0) {
        "Checkout $count items"
    } else {
        "Checkout"
    }
}

private fun updateCheckoutButtonText(button: Drawable, count: Int) {
    var text = button[0]
    if (text !is Text) {
        text = button[1]
    }

    (text as? Text)?.text = createCheckoutButtonText(count)
}
