package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

object PolyPlusCosmeticsConfig : Config(
    "${PolyPlusConstants.ID}-cosmetics.json",
    "Cosmetics (OneClient)",
    Category.OTHER,
) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var migratedFromLegacyConfig = false

    @JvmStatic
    @Switch(
        title = "Hide Head Cosmetics With Helmet",
        description = "Automatically hide hat cosmetics when a helmet is equipped to avoid clipping.",
    )
    var hideHeadCosmeticsWithHelmet = false

    @JvmStatic
    @Switch(
        title = "Hide Feet Cosmetics With Boots",
        description = "Automatically hide boots cosmetics when boots are equipped to avoid clipping.",
    )
    var hideFeetCosmeticsWithBoots = true

    @JvmStatic
    @Dropdown(
        title = "Capes",
        description = "Choose whose capes you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var capesVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Wings",
        description = "Choose whose wings you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var wingsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Gloves",
        description = "Choose whose glove cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var glovesVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Hats",
        description = "Choose whose hat cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var hatsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Boots",
        description = "Choose whose boots cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var bootsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Backs",
        description = "Choose whose back cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var backsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Shoulders",
        description = "Choose whose shoulder cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var shouldersVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Auras",
        description = "Choose whose auras you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var aurasVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    fun isVisible(slot: BodySlot, owner: UUID): Boolean = when (visibilityFor(slot)) {
        CosmeticVisibility.EVERYONE -> true
        CosmeticVisibility.FRIENDS -> isSelf(owner) || FriendsRepository.isFriend(owner)
        CosmeticVisibility.SELF -> isSelf(owner)
        CosmeticVisibility.NOBODY -> false
    }

    private fun visibilityFor(slot: BodySlot): CosmeticVisibility = when (slot) {
        BodySlot.Cape -> capesVisibility
        BodySlot.Wings -> wingsVisibility
        BodySlot.LeftHand, BodySlot.RightHand -> glovesVisibility
        BodySlot.Hat -> hatsVisibility
        BodySlot.Boots -> bootsVisibility
        BodySlot.Backpack -> backsVisibility
        BodySlot.Shoulder -> shouldersVisibility
        BodySlot.Aura -> aurasVisibility
        BodySlot.Glasses, BodySlot.Pet, BodySlot.Unknown -> CosmeticVisibility.EVERYONE
    }

    private fun isSelf(owner: UUID): Boolean =
        runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() == owner

    init {
        preload()
        migrateFromLegacyConfig()
    }

    private fun migrateFromLegacyConfig() {
        if (migratedFromLegacyConfig) return
        loadLegacyPolyPlusOptions("cosmetics", LOGGER, ::loadFrom)
        migratedFromLegacyConfig = true
        save()
    }
}
