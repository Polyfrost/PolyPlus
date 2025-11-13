package org.polyfrost.polyplus.client.gui

import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.events
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.extensions.setPalette
import org.polyfrost.polyui.component.impl.Button
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.component.impl.Text
import org.polyfrost.polyui.event.Event
import org.polyfrost.polyui.event.State
import org.polyfrost.polyui.unit.Align
import org.polyfrost.polyui.unit.Vec2

fun CartControls(count: State<Int>): Drawable {
    var checkoutButton: Drawable? = null

    val countListener = countListener@ { count: Int ->
        var text = checkoutButton?.get(0)
        if (text !is Text) {
            text = checkoutButton?.get(1)
        }

        if (text == null) {
            return@countListener false
        }

        (text as? Text)?.text = createCheckoutButtonText(count)
        false
    }

    count.listen(countListener)
    return Group(
        Button(
            text = "Cart",
            radii = floatArrayOf(6f),
            padding = Vec2(42.75f, 5.5f),
            size = Vec2(146f, 32f)
        ).named("CartButton"),
        Button(
            text = createCheckoutButtonText(count.value),
            radii = floatArrayOf(6f),
            padding = Vec2(75.5f, 5.5f),
            size = Vec2(306f, 32f)
        ).setPalette { brand.fg }.named("CheckoutButton").also { checkoutButton = it },
        size = Vec2(465f, 32f),
        alignment = Align(padBetween = Vec2(13f, 0f))
    ).events {
        Event.Lifetime.Removed then {
            count.removeListener(countListener)
            Unit
        }
    }.named("CartControls")
}

private fun createCheckoutButtonText(count: Int): String {
    return if (count > 0) {
        "Checkout $count items"
    } else {
        "Checkout"
    }
}
