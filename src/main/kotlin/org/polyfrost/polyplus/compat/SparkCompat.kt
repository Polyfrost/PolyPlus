package org.polyfrost.polyplus.compat

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
//? if = 1.8.9
//import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

internal class SparkServerStats(
    val tps10s: Double?,
    val tps1m: Double?,
    val msptMean: Double?,
    val msptMax: Double?,
)

internal class SparkStats(
    val cpuProcess: Double?,
    val cpuSystem: Double?,
    val server: SparkServerStats?,
)

internal sealed interface SparkProfile {
    data object Idle : SparkProfile
    data class Running(val startedAt: Long, val seconds: Int) : SparkProfile
    data object Uploading : SparkProfile
    data class Done(val url: String) : SparkProfile
}

internal data class SparkLink(val label: String, val url: String)

internal object SparkCompat {
    const val SHORT_PROFILE_SECONDS = 30
    const val LONG_PROFILE_SECONDS = 120

    private const val PROVIDER = "me.lucko.spark.api.SparkProvider"
    private const val API = "me.lucko.spark.common.api.SparkApi"
    private const val PLATFORM = "me.lucko.spark.common.SparkPlatform"
    private const val WINDOW = "me.lucko.spark.api.statistic.StatisticWindow$"
    private const val ORNITHE_CLIENT = "me.lucko.spark.ornithe.plugin.OrnitheClientSparkPlugin"
    private val CLIENT_COMMANDS = listOf(
        "net.fabricmc.fabric.api.client.command.v2.ClientCommands",
        "net.fabricmc.fabric.api.client.command.v2.ClientCommandManager",
    )
    private const val CLIENT_COMMAND = "sparkc"
    private const val LOOKUP_RETRY_MILLIS = 2_000L
    private const val WATCH_MILLIS = 500L
    private const val START_GRACE_MILLIS = 3_000L
    private const val UPLOAD_LIMIT_MILLIS = 120_000L
    private const val HEALTH_LIMIT_MILLIS = 60_000L
    private const val PROFILER_ACTIVITY = "Profiler"
    private const val HEALTH_ACTIVITY = "Health report"
    private const val SENDER = "me.lucko.spark.common.command.sender.CommandSender"
    private const val SENDER_NAME = "PolyPlus"
    private val SPARK_LINK = Regex("""https://spark\.lucko\.me/[A-Za-z0-9]+""")

    private val methods = ConcurrentHashMap<String, Method>()
    private val windows = ConcurrentHashMap<String, Any>()

    val installed: Boolean by lazy {
        Reflect.find(PROVIDER) != null
    }

    @Volatile
    var profile: SparkProfile = SparkProfile.Idle
        private set

    @Volatile
    var lastProfileUrl: String? = null
        private set

    @Volatile
    var lastHealthUrl: String? = null
        private set

    @Volatile
    var healthRunning: Boolean = false
        private set

    @Volatile
    var ready: SparkLink? = null
        private set

    fun openReady() {
        val link = ready ?: return
        ready = null
        ClientPlatform.openUri(link.url)
    }

    @Volatile
    private var watcher: Job? = null

    @Volatile
    private var clientPlatform: Any? = null

    @Volatile
    private var clientApi: Pair<Any, Any>? = null

    @Volatile
    private var lastLookup = 0L

    val available: Boolean get() = installed && platform() != null

    fun warm() {
        if (installed && clientPlatform == null) platform()
    }

    fun stats(singleplayer: Boolean): SparkStats? {
        if (!installed) return null
        val platform = platform() ?: return null
        val client = api(platform)
        return SparkStats(
            client?.let { cpu(it, "cpuProcess") },
            client?.let { cpu(it, "cpuSystem") },
            if (singleplayer) serverStats() else null,
        )
    }

    fun busy(): Boolean {
        val platform = platform() ?: return false
        return runCatching { activeForeground(platform) }.getOrDefault(false)
    }

    fun startProfile(seconds: Int): Boolean {
        if (profile is SparkProfile.Running || profile is SparkProfile.Uploading) return false
        val platform = platform() ?: return false
        return runCatching {
            if (activeForeground(platform)) return false
            val sender = sender(platform) ?: return false
            val startedAt = System.currentTimeMillis()
            call(platform, "executeCommand", sender, arrayOf("profiler", "start", "--timeout", seconds.toString()))
            profile = SparkProfile.Running(startedAt, seconds)
            watch(platform, startedAt, seconds)
            true
        }.getOrDefault(false)
    }

    fun stopProfile(): Boolean {
        val platform = platform() ?: return false
        return runCatching {
            if (!activeForeground(platform)) return false
            val sender = sender(platform) ?: return false
            val since = (profile as? SparkProfile.Running)?.startedAt ?: System.currentTimeMillis()
            profile = SparkProfile.Uploading
            call(platform, "executeCommand", sender, arrayOf("profiler", "stop"))
            if (watcher?.isActive != true) watch(platform, since, 0)
            true
        }.getOrDefault(false)
    }

    fun healthReport(): Boolean {
        if (healthRunning) return false
        val platform = platform() ?: return false
        return runCatching {
            val sender = sender(platform) ?: return false
            val since = System.currentTimeMillis()
            healthRunning = true
            call(platform, "executeCommand", sender, arrayOf("health", "upload"))
            watchHealth(platform, since)
            true
        }.getOrElse {
            healthRunning = false
            false
        }
    }

    fun copy(text: String) {
        ClientPlatform.runOnMain {
            runCatching {
                //? if > 1.8.9 {
                Minecraft.getInstance().keyboardHandler.setClipboard(text)
                //?} else {
                /*Screen.setClipboard(text)
                *///?}
            }
        }
    }

    private fun sender(platform: Any): Any? {
        val plugin = call(platform, "getPlugin") ?: return null
        return (call(plugin, "getCommandSenders") as? Stream<*>)?.findFirst()?.orElse(null) ?: menuSender
    }

    private val menuSender: Any? by lazy {
        runCatching {
            val type = Class.forName(SENDER)
            val data = Class.forName("$SENDER\$Data").getConstructor(String::class.java, UUID::class.java).newInstance(SENDER_NAME, null)
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "getName", "toString" -> SENDER_NAME
                    "isPlayer" -> false
                    "hasPermission" -> true
                    "toData" -> data
                    "sendMessage" -> {
                        args?.firstOrNull()?.toString()?.let { SPARK_LINK.find(it)?.value }?.let(::senderLink)
                        null
                    }
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun providerPlatform(): Any? {
        val api = Class.forName(PROVIDER).getMethod("get").invoke(null) ?: return null
        return field(api, "platform")
    }

    private fun watch(platform: Any, startedAt: Long, seconds: Int) {
        watcher = PolyPlusClient.SCOPE.launch {
            while (true) {
                delay(WATCH_MILLIS)
                val elapsed = System.currentTimeMillis() - startedAt
                when (val result = result(platform, startedAt, PROFILER_ACTIVITY)) {
                    is Result.Url -> return@launch finished(result.value)
                    is Result.File -> return@launch failed("The profile couldn't be uploaded. spark saved it to its folder instead.")
                    null -> Unit
                }
                if (elapsed > seconds * 1000L + UPLOAD_LIMIT_MILLIS) return@launch failed("The profile didn't finish uploading.")
                if (profile !is SparkProfile.Running) continue
                val running = runCatching { activeForeground(platform) }.getOrDefault(false)
                if (elapsed < seconds * 1000L) {
                    if (!running && elapsed > START_GRACE_MILLIS) return@launch failed("spark couldn't start the profiler.")
                } else if (!running) {
                    profile = SparkProfile.Uploading
                }
            }
        }
    }

    private fun watchHealth(platform: Any, since: Long) {
        PolyPlusClient.SCOPE.launch {
            while (healthRunning && System.currentTimeMillis() - since < HEALTH_LIMIT_MILLIS) {
                delay(WATCH_MILLIS)
                val result = result(platform, since, HEALTH_ACTIVITY) as? Result.Url ?: continue
                healthFinished(result.value)
            }
            if (healthRunning) {
                healthRunning = false
                ClientPlatform.runOnMain { runCatching { SocialErrors.emit("The health report couldn't be uploaded.") } }
            }
        }
    }

    private fun senderLink(url: String) {
        when {
            profile is SparkProfile.Running || profile == SparkProfile.Uploading -> {
                watcher?.cancel()
                finished(url)
            }
            healthRunning -> healthFinished(url)
        }
    }

    @Synchronized
    private fun healthFinished(url: String) {
        if (!healthRunning) return
        healthRunning = false
        lastHealthUrl = url
        ready = SparkLink("Health report", url)
        copy(url)
        notify("Health report link copied", url)
    }

    @Synchronized
    private fun finished(url: String) {
        if (profile == SparkProfile.Done(url)) return
        lastProfileUrl = url
        profile = SparkProfile.Done(url)
        ready = SparkLink("Profile", url)
        copy(url)
        notify("Profile link copied", url)
    }

    private fun notify(message: String, url: String) {
        ClientPlatform.runOnMain {
            runCatching {
                if (ClientPlatform.currentScreen() is OneClientPauseScreen) {
                    SocialNotices.emit(message)
                } else {
                    Notifications.builder("PolyPlus", message)
                        .action(NotificationAction("Open", primary = true, onClick = { ClientPlatform.openUri(url) }))
                        .send()
                }
            }
        }
    }

    private fun failed(message: String) {
        profile = SparkProfile.Idle
        ClientPlatform.runOnMain { runCatching { SocialErrors.emit(message) } }
    }

    private sealed interface Result {
        class Url(val value: String) : Result
        class File(val value: String) : Result
    }

    private fun result(platform: Any, since: Long, type: String): Result? = runCatching {
        val log = call(call(platform, "getActivityLog") ?: return null, "getLog") as? List<*> ?: return null
        for (entry in log) {
            if (entry == null) continue
            val time = call(entry, "getTime") as? Long ?: continue
            if (time < since) break
            if (call(entry, "getType") != type) continue
            val value = call(entry, "getDataValue") as? String ?: continue
            return when (call(entry, "getDataType")) {
                "url" -> Result.Url(value)
                "file" -> Result.File(value)
                else -> null
            }
        }
        null
    }.getOrNull()

    private fun activeForeground(platform: Any): Boolean {
        val container = call(platform, "getSamplerContainer") ?: return false
        val sampler = call(container, "getActiveSampler") ?: return false
        return call(sampler, "isRunningInBackground") != true
    }

    private fun platform(): Any? {
        clientPlatform?.let { return it }
        val now = System.currentTimeMillis()
        if (now - lastLookup < LOOKUP_RETRY_MILLIS) return null
        lastLookup = now
        val found = runCatching { ornithePlatform() }.getOrNull() ?: runCatching { fabricPlatform() }.getOrNull() ?: runCatching { providerPlatform() }.getOrNull()
        if (found != null && platformType(found) == "CLIENT") clientPlatform = found
        return clientPlatform
    }

    private fun ornithePlatform(): Any? {
        val plugin = staticField(ORNITHE_CLIENT, "instance") ?: return null
        return field(plugin, "platform")
    }

    private fun fabricPlatform(): Any? {
        for (name in CLIENT_COMMANDS) {
            val type = Reflect.find(name) ?: continue
            val dispatcher = runCatching { type.getMethod("getActiveDispatcher").invoke(null) }.getOrNull() ?: continue
            val root = call(dispatcher, "getRoot") ?: continue
            val node = call(root, "getChild", CLIENT_COMMAND) ?: continue
            val plugin = call(node, "getCommand") ?: continue
            return field(plugin, "platform")
        }
        return null
    }

    private fun platformType(platform: Any): String? = runCatching {
        val plugin = call(platform, "getPlugin") ?: return null
        val info = call(plugin, "getPlatformInfo") ?: return null
        (call(info, "getType") as? Enum<*>)?.name
    }.getOrNull()

    private fun api(platform: Any): Any? {
        clientApi?.let { (owner, api) -> if (owner === platform) return api }
        return runCatching {
            Class.forName(API).getConstructor(Class.forName(PLATFORM)).newInstance(platform)
        }.getOrNull()?.also { clientApi = platform to it }
    }

    private fun serverStats(): SparkServerStats? = runCatching {
        val api = Class.forName(PROVIDER).getMethod("get").invoke(null) ?: return null
        val platform = field(api, "platform") ?: return null
        if (platformType(platform) != "SERVER") return null
        val tps = call(api, "tps")
        val mspt = call(api, "mspt")
        val mspt10s = mspt?.let { call(it, "poll", window("MillisPerTick", "SECONDS_10")) }
        SparkServerStats(
            tps?.let { number(call(it, "poll", window("TicksPerSecond", "SECONDS_10"))) },
            tps?.let { number(call(it, "poll", window("TicksPerSecond", "MINUTES_1"))) },
            mspt10s?.let { number(call(it, "mean")) },
            mspt10s?.let { number(call(it, "max")) },
        )
    }.getOrNull()

    private fun cpu(api: Any, name: String): Double? = runCatching {
        val stat = call(api, name) ?: return null
        number(call(stat, "poll", window("CpuUsage", "SECONDS_10")))
    }.getOrNull()

    private fun number(value: Any?): Double? = (value as? Double)?.takeIf { it.isFinite() && it >= 0.0 }

    private fun window(type: String, name: String): Any = windows.getOrPut("$type.$name") {
        Class.forName(WINDOW + type).enumConstants.first { (it as Enum<*>).name == name }
    }

    private fun call(target: Any, name: String, vararg args: Any?): Any? {
        val key = "${target.javaClass.name}#$name/${args.size}"
        val method = methods.getOrPut(key) {
            Reflect.methodByArity(target.javaClass, name, args.size).also { it.isAccessible = true }
        }
        return method.invoke(target, *args)
    }

    private fun field(target: Any, name: String): Any? = Reflect.field(target.javaClass, name)?.get(target)

    private fun staticField(owner: String, name: String): Any? {
        val type = Reflect.find(owner) ?: return null
        return Reflect.field(type, name)?.get(null)
    }
}
