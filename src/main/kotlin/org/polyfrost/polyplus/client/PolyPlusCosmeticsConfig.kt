package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.nameplate.NameplateBinding
import org.polyfrost.polyplus.client.nameplate.NameplateFeature
import org.polyfrost.polyplus.client.nameplate.NameplateMotion
import org.polyfrost.polyplus.client.nameplate.NameplateRenderSettings
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

private const val DEFAULT_NAMEPLATE_DETAIL_DISTANCE = 24
private const val DEFAULT_NAMEPLATE_CROWD_LIMIT = 32
private val NAMEPLATE_OPTIONS =
    listOf("nameplatesVisibility", "nameplateMotion", "nameplateAnimatedNames", "nameplateDetailDistance", "nameplateCrowdLimit")

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
    @Switch(
        title = "Force PolyPlus Capes",
        description = "Show the capes picked in the wardrobe even when another mod sets its own cape.",
    )
    var forcePolyPlusCapes = false

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
    @Dropdown(
        title = "Nameplates",
        description = "Choose whose nameplates you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var nameplatesVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Nameplate Motion",
        description = "Reduced keeps the loop and colors but drops shakes, flashes and moving text. Off shows each nameplate still.",
        options = ["Full", "Reduced", "Off"],
        subcategory = "Nameplates",
    )
    var nameplateMotion: NameplateMotion = NameplateMotion.FULL

    @JvmStatic
    @Switch(
        title = "Animated Names",
        description = "Off keeps every name in plain vanilla text, so names stay easy to read.",
        subcategory = "Nameplates",
    )
    var nameplateAnimatedNames = true

    @JvmStatic
    @Slider(
        title = "Nameplate Detail Distance",
        description = "Nameplates further away than this many blocks drop their extra effects.",
        min = 8f,
        max = 64f,
        step = 4f,
        subcategory = "Nameplates",
    )
    var nameplateDetailDistance = DEFAULT_NAMEPLATE_DETAIL_DISTANCE

    @JvmStatic
    @Slider(
        title = "Full-Detail Nameplates",
        description = "Only the nearest this many nameplates show their extra effects.",
        min = 4f,
        max = 128f,
        step = 4f,
        subcategory = "Nameplates",
    )
    var nameplateCrowdLimit = DEFAULT_NAMEPLATE_CROWD_LIMIT

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
        BodySlot.Nameplate -> nameplatesVisibility
        BodySlot.Glasses, BodySlot.Pet, BodySlot.Unknown -> CosmeticVisibility.EVERYONE
    }

    private fun isSelf(owner: UUID): Boolean =
        runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() == owner

    fun publishNameplateSettings() {
        NameplateBinding.settings = NameplateRenderSettings(
            enabled = nameplatesVisibility != CosmeticVisibility.NOBODY,
            motion = nameplateMotion,
            animatedNames = nameplateAnimatedNames,
            detailDistance = nameplateDetailDistance,
            crowdLimit = nameplateCrowdLimit,
        )
    }

    init {
        preload()
        migrateFromLegacyConfig()

        for (option in NAMEPLATE_OPTIONS) {
            if (!NameplateFeature.LISTED) hideIf(option) { true }
            addCallback(option) { publishNameplateSettings() }
        }
        publishNameplateSettings()
    }

    private fun migrateFromLegacyConfig() {
        if (migratedFromLegacyConfig) return
        loadLegacyPolyPlusOptions("cosmetics", LOGGER, ::loadFrom)
        migratedFromLegacyConfig = true
        save()
    }
}
