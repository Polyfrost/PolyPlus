package org.polyfrost.polyplus.client.nameplate

import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.nameplate.engine.NameplateBundleInfo
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawSources
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawTag
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.UUID

object NameplateBadges {
    const val NONE: Int = NameplateDrawTag.BADGE_NONE
    const val RESERVE: Int = NameplateBundleInfo.BADGE_RESERVE
    const val CARRIED: Int = NameplateBundleInfo.BADGE_CARRIED
    const val PLACED: Int = NameplateBundleInfo.BADGE_PLACED

    @JvmStatic
    fun placement(wearer: UUID): Int {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Nameplate, wearer)) return NONE
        val equip = NameplateWearers.resolve(wearer) ?: return NONE
        return try {
            NameplateDrawSources.current.badgePlacement(equip)
        } catch (e: RuntimeException) {
            NONE
        }
    }

    @JvmStatic
    fun shown(wearer: UUID): Boolean = PolyPlusBadge.shouldBadge(wearer)
}
