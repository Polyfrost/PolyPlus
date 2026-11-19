package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 26.1 {
import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.RealmsMainScreen
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.gui.RealmsServerList
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.LoadingOverlay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldCallback
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.DataPackReloadCookie
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext
import net.minecraft.client.renderer.Panorama
import net.minecraft.network.chat.Component
import net.minecraft.server.WorldLoader
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.ReloadInstance
import net.minecraft.util.Util
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldGenSettings
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.OptionalLong
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

private val LOG = LoggerFactory.getLogger("polyplus/render-screen")

internal object RenderScreenVanilla {
    private var worldContext: CompletableFuture<WorldCreationContext>? = null

    fun worlds(mc: Minecraft): Screen? {
        val context = worldContext ?: loadWorldContext(mc).also { worldContext = it }
        if (!context.isDone || context.isCompletedExceptionally) return null
        return runCatching {
            val constructor = CreateWorldScreen::class.java.getDeclaredConstructor(
                Minecraft::class.java,
                Runnable::class.java,
                WorldCreationContext::class.java,
                Optional::class.java,
                OptionalLong::class.java,
                CreateWorldCallback::class.java,
            ).apply { isAccessible = true }
            val callback = CreateWorldCallback { _, _, _, _, _ -> false }
            constructor.newInstance(mc, Runnable {}, context.join(), Optional.of(WorldPresets.NORMAL), OptionalLong.empty(), callback) as Screen
        }.onFailure { LOG.warn("[render-screen] could not build the create world screen", it) }.getOrNull()
    }

    private fun loadWorldContext(mc: Minecraft): CompletableFuture<WorldCreationContext> = runCatching {
        val repository = PackRepository(ServerPacksSource(mc.directoryValidator()))
        val config = CreateWorldScreen::class.java
            .getDeclaredMethod("createDefaultLoadConfig", PackRepository::class.java, WorldDataConfiguration::class.java)
            .apply { isAccessible = true }
            .invoke(null, repository, WorldDataConfiguration.DEFAULT) as WorldLoader.InitConfig
        WorldLoader.load(
            config,
            { context ->
                //? if >= 26.3 {
                val settings = WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(context.datapackWorldRegistries()))
                //?} else {
                /*val settings = WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(context.datapackWorldgen()))
                *///?}
                WorldLoader.DataLoadOutput(DataPackReloadCookie(settings, context.dataConfiguration()), context.datapackDimensions())
            },
            { resources, reloadable, registries, cookie: DataPackReloadCookie ->
                resources.close()
                WorldCreationContext(cookie.worldGenSettings(), registries, reloadable, cookie.dataConfiguration())
            },
            Util.backgroundExecutor(),
            mc,
        )
    }.getOrElse { CompletableFuture.failedFuture(it) }

    fun connecting(): Screen? = runCatching {
        val constructor = ConnectScreen::class.java.getDeclaredConstructor(Screen::class.java, Component::class.java).apply { isAccessible = true }
        val screen = constructor.newInstance(null, Component.translatable("connect.failed")) as ConnectScreen
        ConnectScreen::class.java.getDeclaredField("lastNarration").apply { isAccessible = true }.setLong(screen, Long.MAX_VALUE)
        screen
    }.onFailure { LOG.warn("[render-screen] could not build the connect screen", it) }.getOrNull()

    fun realms(): Screen? = runCatching { RenderScreenRealms.create() }
        .onFailure { LOG.warn("[render-screen] could not build the Realms screen", it) }
        .getOrNull()
}

private class RenderScreenParent : Screen(Component.empty())

internal class RenderScreenRealms private constructor() : RealmsMainScreen(RenderScreenParent()) {
    override fun init() {
        super.init()
        val list = RealmsMainScreen::class.java.getDeclaredField("serverList").apply { isAccessible = true }.get(this) as RealmsServerList
        list.updateServersList(placeholderRealms())
        RealmsMainScreen::class.java.getDeclaredMethod("refreshListAndLayout").apply { isAccessible = true }.invoke(this)
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit

    companion object {
        fun create(): RenderScreenRealms {
            val field = RealmsAvailability::class.java.getDeclaredField("future").apply { isAccessible = true }
            val saved = field.get(null)
            field.set(null, CompletableFuture<RealmsAvailability.Result>())
            return try {
                RenderScreenRealms()
            } finally {
                field.set(null, saved)
            }
        }

        private fun placeholderRealms(): List<RealmsServer> = listOf(
            realm(1L, "Weekend survival", "Back at the base by nine", RealmsServer.State.OPEN),
            realm(2L, "Skyblock with friends", "New islands every season", RealmsServer.State.OPEN),
            realm(3L, "Creative builds", "City project, plot 14", RealmsServer.State.CLOSED),
        )

        private fun realm(id: Long, name: String, motd: String, state: RealmsServer.State) = RealmsServer().apply {
            this.id = id
            this.name = name
            this.motd = motd
            owner = ClientPlatform.localPlayerName()
            ownerUUID = ClientPlatform.localPlayerUuid()
            this.state = state
            players = emptyList()
            slots = emptyMap()
            worldType = RealmsServer.WorldType.NORMAL
            compatibility = RealmsServer.Compatibility.COMPATIBLE
            activeVersion = SharedConstants.getCurrentVersion().name()
            daysLeft = DAYS_LEFT
        }

        private const val DAYS_LEFT = 24
    }
}

internal class RenderScreenBackdrop(private val panorama: Panorama) : Screen(Component.empty()) {
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        panorama.extractInto(graphics, width, height)
    }

    override fun extractPanorama(graphics: GuiGraphicsExtractor, a: Float) {
        panorama.extractInto(graphics, width, height)
    }

    override fun extractBlurredBackground(graphics: GuiGraphicsExtractor) = Unit

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit
}

internal class RenderScreenLoading : Screen(Component.empty()) {
    private val overlay = LoadingOverlay(Minecraft.getInstance(), StuckReload, {}, false).also {
        LobbyScene.noteReload(BooleanSupplier { true })
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) = Unit

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        overlay.extractRenderState(graphics, mouseX, mouseY, a)
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit

    private object StuckReload : ReloadInstance {
        private val never = CompletableFuture<Unit>()

        override fun done(): CompletableFuture<*> = never

        override fun getActualProgress(): Float = PROGRESS
    }

    private companion object {
        const val PROGRESS = 0.6f
    }
}
//?}
