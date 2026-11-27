package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.emotes.duo.DuoEmoteRequests
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.nameplate.NameplateEffects
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.pets.PetPlay
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
    @Switch(
        title = "Force PolyPlus Capes",
        description = "Show the capes picked in the wardrobe even when another mod sets its own cape.",
    )
    var forcePolyPlusCapes = false

    @JvmStatic
    @Dropdown(
        title = "Cosmetic Motion",
        description = "How much cosmetics move. Reduced turns off particles, trails and flashing. Off keeps every cosmetic still.",
        options = ["Full", "Reduced", "Off"],
    )
    var cosmeticMotion: CosmeticMotion = CosmeticMotion.FULL

    @JvmStatic
    @Dropdown(
        title = "Cosmetic Sounds",
        description = "Whose cosmetics can play sounds. Own means only yours.",
        options = ["Off", "Own", "Everyone"],
    )
    var cosmeticSounds: CosmeticSounds = CosmeticSounds.OWN

    @JvmStatic
    @Dropdown(
        title = "Cosmetic Physics",
        description = "Lets tails, ears and ribbons swing as players move. Own Only keeps it to your own cosmetics.",
        options = ["On", "Own Only", "Off"],
    )
    var cosmeticPhysics: CosmeticPhysics = CosmeticPhysics.ON

    @JvmStatic
    @Dropdown(
        title = "Pet Play",
        description = "Choose whose pets yours can greet, nap and play with when you stand near each other.",
        options = ["Everyone", "Friends Only", "Off"],
    )
    var petPlay: PetPlay = PetPlay.EVERYONE

    @JvmStatic @Include
    var petInteractions = true

    @JvmStatic @Include
    var migratedPetPlay = false

    @JvmStatic
    @Slider(
        title = "Detail Distance",
        description = "Players within this many blocks show every cosmetic detail. Further away, particles, trails and swinging parts are left out.",
        min = 0f,
        max = 64f,
        step = 1f,
        subcategory = "Detail",
    )
    var cosmeticDetailDistance = 24f

    @JvmStatic
    @Slider(
        title = "Full Detail Players",
        description = "How many nearby players show every cosmetic detail at once. Lower it if busy places feel slow.",
        min = 0f,
        max = 128f,
        step = 1f,
        subcategory = "Detail",
    )
    var cosmeticFullDetailPlayers = 32f

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
        title = "Head",
        description = "Choose whose head cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var hatsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Feet",
        description = "Choose whose feet cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var bootsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Back",
        description = "Choose whose back cosmetics you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var backsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Tails",
        description = "Choose whose tails you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var tailsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

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
        title = "Effects",
        description = "Choose whose effects you can see.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var effectsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Event Effects",
        description = "Choose whose event effects you can see. These play when something happens to a player.",
        options = ["Everyone", "Friends Only", "Self", "No One"],
        subcategory = "Visibility",
    )
    var eventEffectsVisibility: CosmeticVisibility = CosmeticVisibility.EVERYONE

    @JvmStatic
    @Dropdown(
        title = "Nameplate Effects",
        description = "How nameplate effects are drawn. Try CPU if they look wrong with your graphics card or shaders. Off hides them.",
        options = ["GPU", "CPU", "Off"],
        subcategory = "Nameplates",
    )
    var nameplateEffects: NameplateEffects = NameplateEffects.GPU

    @JvmStatic
    @Switch(
        title = "Animated Names",
        description = "Let nameplates move and restyle the letters of player names. Off keeps names as plain text.",
        subcategory = "Nameplates",
    )
    var nameplateAnimatedNames = true

    @JvmStatic
    @Dropdown(
        title = "Duo Emote Requests",
        description = "Choose who can ask you to do a duo emote. Requests from players you blocked are always turned down.",
        options = ["Everyone", "Friends Only", "No One"],
        subcategory = "Emotes",
    )
    var duoEmoteRequests: DuoEmoteRequests = DuoEmoteRequests.EVERYONE

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
        BodySlot.Tail -> tailsVisibility
        BodySlot.Shoulder -> shouldersVisibility
        BodySlot.Aura -> aurasVisibility
        BodySlot.Nameplate -> nameplatesVisibility
        BodySlot.EffectA -> effectsVisibility
        BodySlot.EffectB -> eventEffectsVisibility
        BodySlot.Glasses, BodySlot.Pet, BodySlot.Unknown -> CosmeticVisibility.EVERYONE
    }

    private fun isSelf(owner: UUID): Boolean =
        owner == Minecraft.getInstance().player?.uuid ||
            runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() == owner

    init {
        preload()
        migrateFromLegacyConfig()
        migratePetPlay()
        addCallback<DuoEmoteRequests>("duoEmoteRequests") { value ->
            DuoEmotes.onPrivacyPicked(value)
            false
        }
        addCallback<PetPlay>("petPlay") { value ->
            CosmeticSync.syncPetPreferences(value)
            false
        }
    }

    private fun migratePetPlay() {
        if (migratedPetPlay) return
        petPlay = PetPlay.ofSwitch(petInteractions)
        migratedPetPlay = true
        save()
    }

    private fun migrateFromLegacyConfig() {
        if (migratedFromLegacyConfig) return
        loadLegacyPolyPlusOptions("cosmetics", LOGGER, ::loadFrom)
        migratedFromLegacyConfig = true
        save()
    }
}
