@file:Suppress("FunctionName")

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.internal.ui.OneConfigUI
import org.polyfrost.polyui.component.Component
import org.polyfrost.polyui.component.Drawable
import org.polyfrost.polyui.component.extensions.named
import org.polyfrost.polyui.component.extensions.onClick
import org.polyfrost.polyui.component.extensions.padded
import org.polyfrost.polyui.component.impl.Button
import org.polyfrost.polyui.component.impl.Group
import org.polyfrost.polyui.unit.Align
import org.polyfrost.polyui.unit.Vec2
import org.polyfrost.polyui.utils.image

object LockerUI {
    @JvmStatic
    fun createCosmeticsPageButton(): Component {
        return OneConfigUI.SidebarButton("assets/polyplus/ico/stars.svg".image(), "Cosmetics")
            .onClick { OneConfigUI.openPage(LockerPage()) }
    }
}

private fun LockerPage(): Drawable {
    return Group(
        // Cosmetics List
//        CosmeticList(
//            size = Vec2(610f, 614f),
//        ),

        // Right sidebar
        Group(
            // Purchasing options
            Group(
                Button(
                    text = "Cart",
                    radii = floatArrayOf(6f),
                    padding = Vec2(42.75f, 5.5f),
                    size = Vec2(146f, 32f)
                ),
                Button(
                    text = "Checkout items",
                    radii = floatArrayOf(6f),
                    padding = Vec2(75.5f, 5.5f),
                    size = Vec2(306f, 32f)
                ),
                size = Vec2(465f, 32f),
                alignment = Align(padBetween = Vec2(13f, 0f))
            ),

            // Player preview
            Group(
                visibleSize = Vec2(465f, 558f)
            ).padded(0f, 12f, 0f, 0f),

            visibleSize = Vec2(465f, 602f)
        ).padded(13f, 0f, 0f, 0f),

        visibleSize = Vec2(1088f, 614f),
        alignment = Align(padBetween = Vec2(13f, 0f), wrap = Align.Wrap.NEVER)
    ).named("Locker")
}
