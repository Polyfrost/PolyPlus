package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
//? if >= 26.2 {
import net.minecraft.world.entity.EntityTypes
//?} else {
/*import net.minecraft.world.entity.EntityType as EntityTypes
*///?}
import net.minecraft.world.entity.ai.attributes.Attributes
//? if >= 1.21.11 {
import net.minecraft.world.entity.animal.equine.Horse
//?} else {
/*import net.minecraft.world.entity.animal.horse.Horse
*///?}
import net.minecraft.world.entity.player.PlayerModelPart
//? if >= 1.21.10 {
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
//?} else {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
//? if >= 1.21.5 {
import net.minecraft.world.level.GameType
//?}
//? if = 1.21.1 {
/*import net.minecraft.world.entity.player.Player
*///?}
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.LocalAccountSkin
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.slf4j.LoggerFactory
import sun.misc.Unsafe

internal class LivePlayerAvatar(level: ClientLevel, profile: GameProfile) : RemotePlayer(level, profile) {
    var shownSkin: PlayerSkin? = null
    var driving = false
    var wantSprint = false

    init {
        isSilent = true
    }

    override fun getSkin(): PlayerSkin = shownSkin ?: DefaultPlayerSkin.get(uuid)

    override fun getPlayerInfo(): PlayerInfo? = null

    //? if >= 1.21.5 {
    override fun gameMode(): GameType? = null
    //?}

    override fun distanceToSqr(pos: Vec3): Double = Double.MAX_VALUE

    override fun isModelPartShown(part: PlayerModelPart): Boolean = true

    //? if >= 1.21.5 {
    override fun isLocalClientAuthoritative(): Boolean = true
    //?} else {
    /*override fun isControlledByLocalInstance(): Boolean = true
    *///?}

    override fun pushEntities() = Unit

    override fun playStepSound(pos: BlockPos, blockState: BlockState) = Unit

    override fun aiStep() {
        super.aiStep()
        if (!driving || isPassenger) return
        if (isSprinting != wantSprint) isSprinting = wantSprint
        speed = getAttributeValue(Attributes.MOVEMENT_SPEED).toFloat()
        travel(Vec3(xxa.toDouble(), 0.0, zza.toDouble()))
    }
}

internal class LivePlayerHorse(level: ClientLevel) : Horse(EntityTypes.HORSE, level) {
    init {
        isSilent = true
    }

    //? if < 1.21.5 {
    /*override fun isControlledByLocalInstance(): Boolean = true
    *///?}

    //? if = 1.21.1 {
    /*override fun isInvisibleTo(player: Player?): Boolean = false
    *///?}

    override fun pushEntities() = Unit

    override fun playStepSound(pos: BlockPos, blockState: BlockState) = Unit
}

internal object LivePlayerAvatars {
    private val LOG = LoggerFactory.getLogger("polyplus/live-player")
    private var detached: ClientLevel? = null
    private val warned = HashSet<String>()

    fun lobbyLevel(): ClientLevel? = LiveSceneWorld.level()

    fun level(): ClientLevel? = lobbyLevel() ?: detachedLevel()

    private var nextId = FIRST_ID

    fun player(level: ClientLevel): LivePlayerAvatar? =
        runCatching { LivePlayerAvatar(level, LocalAccountSkin.profile()).also { it.id = nextId++ } }.onFailure { warn("player", it) }.getOrNull()

    fun horse(level: ClientLevel): LivePlayerHorse? =
        runCatching { LivePlayerHorse(level).also { it.id = nextId++ } }.onFailure { warn("horse", it) }.getOrNull()

    private const val FIRST_ID = Int.MIN_VALUE + 4096

    fun warn(key: String, error: Throwable) {
        if (warned.add(key)) LOG.warn("[live-player] {} preview failed", key, error)
    }

    fun release() {
        detached = null
    }

    private fun detachedLevel(): ClientLevel? {
        detached?.let { return it }
        return runCatching { unsafe().allocateInstance(ClientLevel::class.java) as ClientLevel }
            .onFailure { warn("detached level", it) }
            .getOrNull()
            .also { detached = it }
    }

    fun unsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return field.get(null) as Unsafe
    }
}

internal object LivePlayerSkins {
    //? if >= 1.21.10 {
    private var fallbackCape: ClientAsset.Texture? = null
    //?} else {
    /*private var fallbackCape: Identifier? = null
    *///?}

    fun base(): PlayerSkin =
        runCatching { LocalAccountSkin.skin() }.getOrNull()
            ?: DefaultPlayerSkin.get(LocalAccountSkin.profile())

    //? if >= 1.21.10 {
    fun texture(skin: PlayerSkin): Identifier = skin.body().texturePath()

    fun slim(skin: PlayerSkin): Boolean = skin.model() == PlayerModelType.SLIM
    //?} else {
    /*fun texture(skin: PlayerSkin): Identifier = skin.texture()

    fun slim(skin: PlayerSkin): Boolean = skin.model() == PlayerSkin.Model.SLIM
    *///?}

    private var showcase: PlayerSkin? = null
    private var showcaseMissing = false

    fun layered(skin: PlayerSkin): PlayerSkin {
        if (LivePlayerMods.hasOuterLayer(skin) != false) return skin
        return showcaseSkin() ?: skin
    }

    private fun showcaseSkin(): PlayerSkin? {
        showcase?.let { return it }
        if (showcaseMissing) return null
        val loaded = runCatching {
            val mc = Minecraft.getInstance()
            val file = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, SHOWCASE_FILE)
            val image = mc.resourceManager.getResource(file).orElse(null)?.open()?.use { NativeImage.read(it) } ?: return@runCatching null
            if (image.width != SKIN_SIZE || image.height != SKIN_SIZE) {
                image.close()
                return@runCatching null
            }
            val id = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, SHOWCASE_TEXTURE)
            //? if >= 1.21.10 {
            val asset = ClientAsset.ResourceTexture(id)
            register(asset.texturePath(), id, image)
            PlayerSkin(asset, null, null, PlayerModelType.WIDE, true)
            //?} else {
            /*register(id, id, image)
            PlayerSkin(id, null, null, null, PlayerSkin.Model.WIDE, true)
            *///?}
        }.getOrNull()
        if (loaded == null) showcaseMissing = true
        showcase = loaded
        return loaded
    }

    private fun register(path: Identifier, id: Identifier, image: NativeImage) {
        //? if >= 1.21.5 {
        Minecraft.getInstance().textureManager.register(path, DynamicTexture({ id.toString() }, image))
        //?} else {
        /*Minecraft.getInstance().textureManager.register(path, DynamicTexture(image))
        *///?}
    }

    //? if >= 1.21.10 {
    fun withCape(skin: PlayerSkin): PlayerSkin {
        val cape = localCape()?.let { ClientAsset.ResourceTexture(it) } ?: skin.cape() ?: fallback() ?: return skin
        return PlayerSkin(skin.body(), cape, skin.elytra(), skin.model(), skin.secure())
    }
    //?} else {
    /*fun withCape(skin: PlayerSkin): PlayerSkin {
        val cape = localCape() ?: skin.capeTexture() ?: fallback() ?: return skin
        return PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure())
    }
    *///?}

    private fun localCape(): Identifier? = runCatching {
        CosmeticCatalog.localEquipped().cape?.let { CosmeticAssetCache.getCapeResource(it) }
            ?: MojangCapeService.localCapeTexture()
    }.getOrNull()

    //? if >= 1.21.10 {
    private fun fallback(): ClientAsset.Texture? {
    //?} else {
    /*private fun fallback(): Identifier? {
    *///?}
        fallbackCape?.let { return it }
        return runCatching {
            val id = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "onboarding/live_cape")
            //? if >= 1.21.10 {
            val asset = ClientAsset.ResourceTexture(id)
            val path = asset.texturePath()
            //?} else {
            /*val asset = id
            val path = id
            *///?}
            val image = NativeImage(CAPE_WIDTH, CAPE_HEIGHT, true)
            val cloth = blockTexture(CLOTH_TEXTURE)
            val trim = blockTexture(TRIM_TEXTURE)
            try {
                for (y in 0 until CAPE_HEIGHT) {
                    for (x in 0 until CAPE_WIDTH) {
                        val face = y in 1..CAPE_FACE_HEIGHT && (x in 1..CAPE_FACE_WIDTH || x in CAPE_BACK_U until CAPE_BACK_U + CAPE_FACE_WIDTH)
                        val u = if (x <= CAPE_FACE_WIDTH) x else x - CAPE_BACK_U + 1
                        val border = face && (y == 1 || y == CAPE_FACE_HEIGHT || u == 1 || u == CAPE_FACE_WIDTH)
                        val source = if (face && !border) cloth else trim
                        val fallbackColor = if (face && !border) CLOTH_COLOR else TRIM_COLOR
                        val color = source?.let { readPixel(it, x % it.width, y % it.height) } ?: fallbackColor
                        writePixel(image, x, y, color or OPAQUE)
                    }
                }
            } finally {
                cloth?.close()
                trim?.close()
            }
            register(path, id, image)
            asset
        }.getOrNull().also { fallbackCape = it }
    }

    //? if >= 1.21.4 {
    fun readPixel(image: NativeImage, x: Int, y: Int): Int = image.getPixel(x, y)

    private fun writePixel(image: NativeImage, x: Int, y: Int, argb: Int) = image.setPixel(x, y, argb)
    //?} else {
    /*fun readPixel(image: NativeImage, x: Int, y: Int): Int = swapRedBlue(image.getPixelRGBA(x, y))

    private fun writePixel(image: NativeImage, x: Int, y: Int, argb: Int) = image.setPixelRGBA(x, y, swapRedBlue(argb))

    private fun swapRedBlue(color: Int): Int =
        (color and GREEN_ALPHA) or ((color shr RED_SHIFT) and CHANNEL) or ((color and CHANNEL) shl RED_SHIFT)

    private const val GREEN_ALPHA = 0xFF00FF00.toInt()
    private const val CHANNEL = 0xFF
    private const val RED_SHIFT = 16
    *///?}

    private fun blockTexture(path: String): NativeImage? = runCatching {
        Minecraft.getInstance().resourceManager.getResource(Identifier.withDefaultNamespace(path)).orElse(null)
            ?.open()?.use { NativeImage.read(it) }
    }.getOrNull()

    private const val SHOWCASE_FILE = "onboarding/showcase-skin.png"
    private const val SHOWCASE_TEXTURE = "onboarding/showcase_skin"
    private const val SKIN_SIZE = 64
    private const val CAPE_WIDTH = 64
    private const val CAPE_HEIGHT = 32
    private const val CAPE_BACK_U = 12
    private const val CAPE_FACE_WIDTH = 10
    private const val CAPE_FACE_HEIGHT = 16
    private const val CLOTH_TEXTURE = "textures/block/red_wool.png"
    private const val TRIM_TEXTURE = "textures/block/white_wool.png"
    private const val CLOTH_COLOR = 0xFFA12722.toInt()
    private const val TRIM_COLOR = 0xFFE9ECEC.toInt()
    private const val OPAQUE = 0xFF000000.toInt()
}
//?}
