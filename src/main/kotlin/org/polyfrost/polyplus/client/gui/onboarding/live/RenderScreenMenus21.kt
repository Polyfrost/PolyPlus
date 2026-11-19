package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 && < 26.1 {
/*import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.RealmsMainScreen
import com.mojang.realmsclient.dto.RealmsServer
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.WorldLoader
import net.minecraft.server.packs.repository.PackRepository
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.WorldGenSettings
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.mixin.client.access.RealmsMainScreenAccessor
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Optional
import java.util.OptionalLong
import java.util.concurrent.CompletableFuture
//? if >= 1.21.4 {
import net.minecraft.client.gui.screens.worldselection.CreateWorldCallback
//?}
//? if >= 1.21.11 {
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.util.Util
//?} else {
/^import net.minecraft.Util
^///?}

private val LOG = LoggerFactory.getLogger("polyplus/render-screen")

internal object RenderScreenVanilla {
    private var worldContext: CompletableFuture<WorldCreationContext>? = null

    fun worlds(mc: Minecraft): Screen? {
        val context = worldContext ?: loadWorldContext(mc).also { worldContext = it }
        if (!context.isDone || context.isCompletedExceptionally) return null
        return runCatching {
            val constructor = CreateWorldScreen::class.java.declaredConstructors
                .first { WorldCreationContext::class.java in it.parameterTypes }
                .apply { isAccessible = true }
            val args = constructor.parameterTypes.map { type -> worldArgument(type, mc, context.join()) }
            constructor.newInstance(*args.toTypedArray()) as Screen
        }.onFailure { LOG.warn("[render-screen] could not build the create world screen", it) }.getOrNull()
    }

    private fun worldArgument(type: Class<*>, mc: Minecraft, context: WorldCreationContext): Any? = when (type) {
        Minecraft::class.java -> mc
        WorldCreationContext::class.java -> context
        Optional::class.java -> Optional.of(WorldPresets.NORMAL)
        OptionalLong::class.java -> OptionalLong.empty()
        Runnable::class.java -> Runnable {}
        //? if >= 1.21.4 {
        CreateWorldCallback::class.java -> CreateWorldCallback { _, _, _, _ -> false }
        //?}
        else -> null
    }

    private fun loadWorldContext(mc: Minecraft): CompletableFuture<WorldCreationContext> = runCatching {
        val repository = PackRepository(ServerPacksSource(mc.directoryValidator()))
        val packs = WorldLoader.PackConfig(repository, WorldDataConfiguration.DEFAULT, false, true)
        //? if >= 1.21.11 {
        val config = WorldLoader.InitConfig(packs, Commands.CommandSelection.INTEGRATED, LevelBasedPermissionSet.GAMEMASTER)
        //?} else {
        /^val config = WorldLoader.InitConfig(packs, Commands.CommandSelection.INTEGRATED, GAMEMASTER_LEVEL)
        ^///?}
        WorldLoader.load(
            config,
            { context ->
                val settings = WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(context.datapackWorldgen()))
                WorldLoader.DataLoadOutput(settings to context.dataConfiguration(), context.datapackDimensions())
            },
            { resources, reloadable, registries, cookie: Pair<WorldGenSettings, WorldDataConfiguration> ->
                resources.close()
                WorldCreationContext(cookie.first, registries, reloadable, cookie.second)
            },
            Util.backgroundExecutor(),
            mc,
        )
    }.getOrElse { CompletableFuture.failedFuture(it) }

    fun connecting(): Screen? = runCatching {
        val constructor = ConnectScreen::class.java.declaredConstructors
            .first { it.parameterTypes.contentEquals(arrayOf(Screen::class.java, Component::class.java)) }
            .apply { isAccessible = true }
        val screen = constructor.newInstance(null, Component.translatable("connect.failed")) as ConnectScreen
        ConnectScreen::class.java.declaredFields
            .firstOrNull { it.type == Long::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) }
            ?.apply { isAccessible = true }
            ?.setLong(screen, Long.MAX_VALUE)
        screen
    }.onFailure { LOG.warn("[render-screen] could not build the connect screen", it) }.getOrNull()

    fun realms(): Screen? = runCatching { RenderScreenRealms.create() }
        .onFailure { LOG.warn("[render-screen] could not build the Realms screen", it) }
        .getOrNull()

    private const val GAMEMASTER_LEVEL = 2
}

private class RenderScreenParent : Screen(Component.empty())

internal class RenderScreenRealms private constructor() : RealmsMainScreen(RenderScreenParent()) {
    override fun init() {
        super.init()
        val access = this as RealmsMainScreenAccessor
        access.`polyplus$serverList`().updateServersList(placeholderRealms())
        access.`polyplus$refreshListAndLayout`()
    }

    override fun triggerImmediateNarration(onlyChanged: Boolean) = Unit

    companion object {
        private val availabilityField: Field by lazy {
            RealmsAvailability::class.java.declaredFields
                .first { Modifier.isStatic(it.modifiers) && it.type == CompletableFuture::class.java }
                .apply { isAccessible = true }
        }

        fun create(): RenderScreenRealms {
            val field = availabilityField
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
            //? if >= 1.21.8 {
            activeVersion = SharedConstants.getCurrentVersion().name()
            //?} else {
            /^activeVersion = SharedConstants.getCurrentVersion().name
            ^///?}
            daysLeft = DAYS_LEFT
        }

        private const val DAYS_LEFT = 24
    }
}
*///?}
