package org.polyfrost.polyplus.compat

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.internal.ui.api.Tooltip
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

object WWaypointsCompat {
    const val DISABLED_REASON = "Disabled by PolyPlus"

    const val DISABLED_REASON_METADATA = "disabledReason"

    private const val STAY_SNEAKED_PROPERTY = "wwaypoints.toggleSneakStaySneakedInContainers"

    private val logger = LogManager.getLogger("PolyPlus/WWaypointsCompat")

    @JvmStatic
    fun lockStaySneaked(tree: Tree) {
        val prop = tree.getProp(STAY_SNEAKED_PROPERTY)
        if (prop == null) {
            logger.warn("OneConfig's wWaypoints tree has no {} property, it will not be locked", STAY_SNEAKED_PROPERTY)
            return
        }
        prop.addMetadata(DISABLED_REASON_METADATA, DISABLED_REASON)
        prop.addDisplayCondition { Property.Display.DISABLED }
    }

    @JvmStatic
    fun disabledReason(prop: Property<*>): String? =
        if (prop.display == Property.Display.DISABLED) prop.getMetadata<String>(DISABLED_REASON_METADATA) else null

    @Composable
    @JvmStatic
    fun DisabledReasonTooltip(reason: String, content: @Composable () -> Unit) {
        Tooltip(
            text = { Text(reason, color = LocalTheme.current.textColor, fontSize = 12.sp) },
            modifier = Modifier.widthIn(max = 260.dp),
            anchor = Alignment.TopCenter,
            content = content,
        )
    }
}
