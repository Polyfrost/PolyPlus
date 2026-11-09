package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import com.mojang.blaze3d.audio.Channel
import org.lwjgl.openal.AL10
import org.polyfrost.polyplus.mixin.client.access.ChannelAccessor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object LobbySoundGate {
    class Group internal constructor(internal val after: Group?, internal val delay: Long) {
        internal val waiting = HashSet<Channel>()
        internal val ready = ArrayList<Int>()
        internal val created = System.nanoTime()
        @Volatile
        internal var startedAt = 0L
    }

    private const val SPIN_NANOS = 20_000_000L
    private const val EXPIRE_NANOS = 3_000_000_000L

    private val lock = Any()
    private val groups = ArrayList<Group>()
    private val timer: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { Thread(it, "PolyPlus Lobby Audio").apply { isDaemon = true } }
    }

    fun hold(channels: Collection<Channel>, after: Group? = null, delayNanos: Long = 0L): Group = synchronized(lock) {
        val group = Group(after, delayNanos)
        for (channel in channels) {
            if (channel.playing()) group.ready += source(channel) else group.waiting += channel
        }
        groups += group
        when {
            after == null -> settle(group)
            after.startedAt != 0L -> schedule(group, after.startedAt + delayNanos)
        }
        group
    }

    fun cancel(group: Group?) {
        if (group == null) return
        synchronized(lock) { groups.removeAll { it === group || it.after === group } }
    }

    fun expire() = synchronized(lock) {
        val now = System.nanoTime()
        groups.filter { it.after == null && now - it.created > EXPIRE_NANOS }.forEach(::start)
    }

    @JvmStatic
    fun play(channel: Channel): Boolean = synchronized(lock) {
        val group = groups.firstOrNull { channel in it.waiting } ?: return false
        group.waiting -= channel
        group.ready += source(channel)
        settle(group)
        true
    }

    private fun settle(group: Group) {
        if (group.after == null && group.waiting.isEmpty()) start(group)
    }

    private fun start(group: Group) {
        if (!groups.remove(group)) return
        group.startedAt = System.nanoTime()
        if (group.ready.isNotEmpty()) AL10.alSourcePlayv(group.ready.toIntArray())
        groups.filter { it.after === group }.forEach { schedule(it, group.startedAt + it.delay) }
    }

    private fun schedule(group: Group, due: Long) {
        timer.schedule({
            while (System.nanoTime() < due) Thread.onSpinWait()
            synchronized(lock) { start(group) }
        }, (due - System.nanoTime() - SPIN_NANOS).coerceAtLeast(0L), TimeUnit.NANOSECONDS)
    }

    private fun source(channel: Channel): Int = (channel as ChannelAccessor).`polyplus$source`()
}
//?}
