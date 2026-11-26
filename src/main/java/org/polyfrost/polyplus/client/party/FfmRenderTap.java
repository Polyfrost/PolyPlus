package org.polyfrost.polyplus.client.party;

import gg.sona.eos.EosPlatform;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class FfmRenderTap implements EosRenderTap {
    private static final long FREE_DELAY_SECONDS = 5L;

    private final Sink sink;
    private final long audio;
    private volatile long notification;
    private volatile boolean removed;
    private volatile boolean failed;
    private final Arena arena = Arena.ofShared();
    private short[] samples = new short[0];

    private FfmRenderTap(Sink sink, long audio) {
        this.sink = sink;
        this.audio = audio;
    }

    public static EosRenderTap register(EosPlatform platform, long localUser, String room, Sink sink) throws ReflectiveOperationException {
        long audio = EosRenderNative.audioInterface(platform);
        FfmRenderTap tap = new FfmRenderTap(sink, audio);
        MethodHandle target = MethodHandles.lookup()
            .findVirtual(FfmRenderTap.class, "onRender", MethodType.methodType(void.class, MemorySegment.class))
            .bindTo(tap);
        try {
            MemorySegment callback = Linker.nativeLinker().upcallStub(target, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), tap.arena);
            tap.notification = EosRenderNative.register(audio, localUser, room, callback.address());
        } catch (RuntimeException | Error error) {
            tap.removed = true;
            tap.arena.close();
            throw error;
        }
        if (tap.notification == 0L) {
            tap.removed = true;
            tap.arena.close();
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
            CompletableFuture.delayedExecutor(FREE_DELAY_SECONDS, TimeUnit.SECONDS).execute(arena::close);
        }
    }

    private void onRender(MemorySegment data) {
        try {
            if (removed || failed || data.address() == 0L) return;
            MemorySegment info = data.reinterpret(EosRenderNative.INFO_SIZE);
            MemorySegment pointer = info.get(ValueLayout.ADDRESS, EosRenderNative.BUFFER_OFFSET);
            if (pointer.address() == 0L) return;
            MemorySegment buffer = pointer.reinterpret(EosRenderNative.BUFFER_SIZE);
            MemorySegment frames = buffer.get(ValueLayout.ADDRESS, EosRenderNative.FRAMES_OFFSET);
            int count = buffer.get(ValueLayout.JAVA_INT, EosRenderNative.COUNT_OFFSET);
            int rate = buffer.get(ValueLayout.JAVA_INT, EosRenderNative.RATE_OFFSET);
            int channels = buffer.get(ValueLayout.JAVA_INT, EosRenderNative.CHANNELS_OFFSET);
            if (!EosRenderNative.valid(frames.address(), count, channels)) {
                sink.accept(new short[0], count, channels, rate);
                return;
            }
            int total = count * channels;
            if (samples.length < total) samples = new short[total];
            MemorySegment.copy(frames.reinterpret(total * 2L), ValueLayout.JAVA_SHORT, 0L, samples, 0, total);
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
