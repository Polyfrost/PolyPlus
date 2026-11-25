package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import com.mojang.blaze3d.audio.Channel
import net.minecraft.client.resources.sounds.SoundInstance
import org.lwjgl.openal.AL10
import org.polyfrost.polyplus.mixin.client.access.ChannelAccessor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object LobbySoundGate {
    class Group internal constructor(internal val after: Group?, internal val delay: Long) {
        internal val pending = HashSet<SoundInstance>()
        internal val waiting = HashSet<Channel>()
        internal val ready = ArrayList<Channel>()
        internal val created = System.nanoTime()
        internal var released = false
        @Volatile
        internal var startedAt = 0L

        internal fun empty(): Boolean = pending.isEmpty() && waiting.isEmpty()
    }

    private const val SPIN_NANOS = 20_000_000L
    private const val EXPIRE_NANOS = 3_000_000_000L

    private val lock = Any()
    private val groups = ArrayList<Group>()
    private val timer: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { Thread(it, "PolyPlus Lobby Audio").apply { isDaemon = true } }
    }

    fun hold(sounds: Collection<SoundInstance>, after: Group? = null, delayNanos: Long = 0L): Group = synchronized(lock) {
        val group = Group(after, delayNanos)
        group.pending += sounds
        groups += group
        if (after != null && after.startedAt != 0L) schedule(group, after.startedAt + delayNanos)
        group
    }

    fun forget(group: Group, sound: SoundInstance) = synchronized(lock) {
        if (group.pending.remove(sound)) settle(group)
    }

    fun cancel(group: Group?) {
        if (group == null) return
        synchronized(lock) {
            val dropped = groups.filter { it === group || it.after === group }
            groups.removeAll(dropped)
            val held = sources(dropped.filter { !it.released }.flatMap { it.ready })
            if (held.isNotEmpty()) {
                AL10.alSourcePlayv(held)
                AL10.alSourceStopv(held)
            }
        }
    }

    fun expire() = synchronized(lock) {
        val now = System.nanoTime()
        for (group in groups.filter { it.after == null && now - it.created > EXPIRE_NANOS }) {
            start(group)
            if (now - group.created > 2 * EXPIRE_NANOS) begin(group, emptyList())
        }
    }

    @JvmStatic
    fun bind(sound: SoundInstance, channel: Channel) = synchronized(lock) {
        val group = groups.firstOrNull { sound in it.pending } ?: return
        group.pending -= sound
        group.waiting += channel
    }

    @JvmStatic
    fun play(channel: Channel): Boolean = synchronized(lock) {
        val group = groups.firstOrNull { channel in it.waiting } ?: return false
        group.waiting -= channel
        if (group.released) {
            if (group.empty()) groups -= group
            begin(group, listOf(channel))
        } else {
            group.ready += channel
            settle(group)
        }
        true
    }

    private fun settle(group: Group) {
        if (group.after == null && group.empty()) start(group)
    }

    private fun start(group: Group) {
        if (group.released || group !in groups) return
        group.released = true
        if (group.empty()) groups -= group
        if (group.ready.isNotEmpty()) begin(group, group.ready)
    }

    private fun begin(group: Group, channels: List<Channel>) {
        val live = sources(channels)
        if (live.isNotEmpty()) AL10.alSourcePlayv(live)
        if (group.startedAt != 0L) return
        group.startedAt = System.nanoTime()
        groups.filter { it.after === group }.forEach { schedule(it, group.startedAt + it.delay) }
    }

    private fun schedule(group: Group, due: Long) {
        timer.schedule({
            while (System.nanoTime() < due) Thread.onSpinWait()
            synchronized(lock) { start(group) }
        }, (due - System.nanoTime() - SPIN_NANOS).coerceAtLeast(0L), TimeUnit.NANOSECONDS)
    }

    private fun sources(channels: List<Channel>): IntArray =
        channels.map { it as ChannelAccessor }.filter { it.`polyplus$initialized`().get() }.map { it.`polyplus$source`() }.toIntArray()
}
//?}
