package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.nio.IntBuffer
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.ALC11
import org.lwjgl.openal.ALUtil
import org.lwjgl.openal.EXTThreadLocalContext

internal interface VoiceStream {
    fun start(): Boolean

    fun stop()
}

internal object OpenAlDevices {
    private val LOGGER = LogManager.getLogger()

    fun inputs(): List<String> = list(capture = true)

    fun outputs(): List<String> = list(capture = false)

    private fun list(capture: Boolean): List<String> = runCatching {
        ALUtil.getStringList(0L, if (capture) ALC11.ALC_CAPTURE_DEVICE_SPECIFIER else ALC11.ALC_ALL_DEVICES_SPECIFIER).orEmpty()
    }.onFailure { LOGGER.debug("Could not list OpenAL devices", it) }.getOrDefault(emptyList())
}

internal class OpenAlCapture(
    private val device: String,
    private val gain: () -> Float,
    private val onFrame: (frame: ShortArray, level: Float) -> Unit,
) : VoiceStream {
    private val logger = LogManager.getLogger()

    @Volatile private var running = false

    override fun start(): Boolean {
        val name = device.takeIf { it.isNotBlank() && it in OpenAlDevices.inputs() }
        val opened = runCatching {
            ALC11.alcCaptureOpenDevice(name, PartyAudio.SAMPLE_RATE, AL10.AL_FORMAT_MONO16, PartyAudio.FRAME_SAMPLES * BUFFERED_FRAMES).takeIf { it != 0L }
        }.onFailure { logger.warn("OpenAL could not open the microphone", it) }.getOrNull() ?: return false
        runCatching { ALC11.alcCaptureStart(opened) }.onFailure {
            logger.warn("OpenAL could not start the microphone", it)
            ALC11.alcCaptureCloseDevice(opened)
            return false
        }
        running = true
        Thread({
            runCatching {
                loop(
                    available = { ALC10.alcGetInteger(opened, ALC11.ALC_CAPTURE_SAMPLES) },
                    read = { buffer -> ALC11.alcCaptureSamples(opened, buffer, PartyAudio.FRAME_SAMPLES) },
                )
            }
            val died = running
            running = false
            runCatching { ALC11.alcCaptureStop(opened) }
            runCatching { ALC11.alcCaptureCloseDevice(opened) }
            if (died) PartyVoice.onCaptureFailed()
        }, "polyplus-party-mic").apply {
            isDaemon = true
            start()
        }
        logger.info("Party voice microphone: OpenAL ({})", name ?: "default")
        return true
    }

    private fun loop(available: () -> Int, read: (ByteBuffer) -> Unit) {
        val buffer = ByteBuffer.allocateDirect(PartyAudio.FRAME_SAMPLES * 2).order(ByteOrder.nativeOrder())
        val raw = ShortArray(PartyAudio.FRAME_SAMPLES)
        while (running) {
            val count = runCatching(available).getOrDefault(-1)
            if (count < 0) break
            if (count < PartyAudio.FRAME_SAMPLES) {
                Thread.sleep(POLL_MS)
                continue
            }
            buffer.clear()
            read(buffer)
            buffer.rewind()
            buffer.asShortBuffer().get(raw)
            PartyAudio.deliver(raw, gain(), onFrame)
        }
    }

    override fun stop() {
        running = false
    }

    private companion object {
        const val BUFFERED_FRAMES = 8
        const val POLL_MS = 5L
    }
}

internal class OpenAlSpeaker(private val device: String, private val next: (ShortArray) -> Unit) : VoiceStream {
    private val logger = LogManager.getLogger()

    @Volatile private var running = false

    override fun start(): Boolean {
        val name = device.takeIf { it.isNotBlank() && it in OpenAlDevices.outputs() }
        val ready = CountDownLatch(1)
        var ok = false
        running = true
        Thread({
            val opened = runCatching { ALC10.alcOpenDevice(name) }.getOrDefault(0L)
            if (opened == 0L) {
                ready.countDown()
                return@Thread
            }
            var context = 0L
            try {
                val alc = ALC.createCapabilities(opened)
                if (!alc.ALC_EXT_thread_local_context) error("OpenAL has no thread-local contexts")
                context = ALC10.alcCreateContext(opened, null as IntBuffer?)
                if (context == 0L || !EXTThreadLocalContext.alcSetThreadContext(context)) error("OpenAL could not create a context")
                AL.createCapabilities(alc)
                ok = true
                ready.countDown()
                play()
            } catch (error: Throwable) {
                logger.warn("OpenAL could not open the speaker", error)
            } finally {
                ready.countDown()
                runCatching { EXTThreadLocalContext.alcSetThreadContext(0L) }
                runCatching { AL.setCurrentThread(null) }
                if (context != 0L) runCatching { ALC10.alcDestroyContext(context) }
                runCatching { ALC10.alcCloseDevice(opened) }
            }
        }, "polyplus-party-speaker").apply {
            isDaemon = true
            start()
        }
        ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ok) running = false else logger.info("Party voice speaker: OpenAL ({})", name ?: "default")
        return ok
    }

    private fun play() {
        val source = AL10.alGenSources()
        val buffers = IntArray(QUEUED_BUFFERS).also(AL10::alGenBuffers)
        val frame = ShortArray(PartyAudio.FRAME_SAMPLES * 2)
        val data = ByteBuffer.allocateDirect(frame.size * 2).order(ByteOrder.nativeOrder())
        fun fill(buffer: Int) {
            frame.fill(0)
            runCatching { next(frame) }.onFailure { logger.warn("Party voice mixer failed", it) }
            data.clear()
            data.asShortBuffer().put(frame)
            data.limit(frame.size * 2)
            AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, data, PartyAudio.SAMPLE_RATE)
            AL10.alSourceQueueBuffers(source, buffer)
        }
        try {
            buffers.forEach(::fill)
            AL10.alSourcePlay(source)
            while (running) {
                var processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED)
                while (processed-- > 0) fill(AL10.alSourceUnqueueBuffers(source))
                if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(source)
                Thread.sleep(POLL_MS)
            }
        } finally {
            AL10.alSourceStop(source)
            AL10.alDeleteSources(source)
            AL10.alDeleteBuffers(buffers)
        }
    }

    override fun stop() {
        running = false
    }

    private companion object {
        const val QUEUED_BUFFERS = 4
        const val POLL_MS = 5L
        const val START_TIMEOUT_SECONDS = 3L
    }
}
