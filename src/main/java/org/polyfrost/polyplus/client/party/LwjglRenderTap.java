package org.polyfrost.polyplus.client.party;

import gg.sona.eos.EosPlatform;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.lwjgl.system.jemalloc.MallocMessageCallback;
import org.lwjgl.system.MemoryUtil;

public final class LwjglRenderTap implements EosRenderTap {
    private static final long FREE_DELAY_SECONDS = 5L;

    private final Sink sink;
    private final long audio;
    private volatile long notification;
    private volatile boolean removed;
    private volatile boolean failed;
    private MallocMessageCallback callback;
    private short[] samples = new short[0];

    private LwjglRenderTap(Sink sink, long audio) {
        this.sink = sink;
        this.audio = audio;
    }

    public static EosRenderTap register(EosPlatform platform, long localUser, String room, Sink sink) {
        long audio = EosRenderNative.audioInterface(platform);
        LwjglRenderTap tap = new LwjglRenderTap(sink, audio);
        MallocMessageCallback callback = MallocMessageCallback.create((data, unused) -> tap.onRender(data));
        tap.callback = callback;
        try {
            tap.notification = EosRenderNative.register(audio, localUser, room, callback.address());
        } catch (RuntimeException error) {
            callback.free();
            throw error;
        }
        if (tap.notification == 0L) {
            tap.removed = true;
            callback.free();
            throw new IllegalStateException("EOS refused the render notification");
        }
        return tap;
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        try {
            EosRenderNative.unregister(audio, notification);
        } finally {
            MallocMessageCallback owned = callback;
            CompletableFuture.delayedExecutor(FREE_DELAY_SECONDS, TimeUnit.SECONDS).execute(owned::free);
        }
    }

    private void onRender(long data) {
        try {
            if (removed || failed || data == 0L) return;
            long buffer = MemoryUtil.memGetAddress(data + EosRenderNative.BUFFER_OFFSET);
            if (buffer == 0L) return;
            long frames = MemoryUtil.memGetAddress(buffer + EosRenderNative.FRAMES_OFFSET);
            int count = MemoryUtil.memGetInt(buffer + EosRenderNative.COUNT_OFFSET);
            int rate = MemoryUtil.memGetInt(buffer + EosRenderNative.RATE_OFFSET);
            int channels = MemoryUtil.memGetInt(buffer + EosRenderNative.CHANNELS_OFFSET);
            if (!EosRenderNative.valid(frames, count, channels)) {
                sink.accept(new short[0], count, channels, rate);
                return;
            }
            int total = count * channels;
            if (samples.length < total) samples = new short[total];
            MemoryUtil.memShortBuffer(frames, total).get(samples, 0, total);
            sink.accept(samples, count, channels, rate);
        } catch (Throwable error) {
            failed = true;
            try {
                sink.accept(new short[0], 0, 0, 0);
            } catch (Throwable ignored) {
                return;
            }
        }
    }
}
