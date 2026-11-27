package org.polyfrost.polyplus.client.party;

import gg.sona.eos.EosPlatform;
import gg.sona.eos.internal.Native;
import gg.sona.eos.internal.foreign.Arena;
import gg.sona.eos.internal.foreign.MemorySegment;
import gg.sona.eos.internal.foreign.ValueLayout;
import java.util.List;

final class EosRenderNative {
    static final long BUFFER_OFFSET = 24L;
    static final long FRAMES_OFFSET = 8L;
    static final long COUNT_OFFSET = 16L;
    static final long RATE_OFFSET = 20L;
    static final long CHANNELS_OFFSET = 24L;
    static final long INFO_SIZE = 40L;
    static final long BUFFER_SIZE = 32L;
    static final int MAX_FRAMES = 4800;
    static final int MAX_CHANNELS = 8;

    private static final int API_VERSION = 1;
    private static final long OPTIONS_SIZE = 32L;

    private EosRenderNative() {
    }

    static long audioInterface(EosPlatform platform) {
        long rtc = handle("EOS_Platform_GetRTCInterface", platform.handle);
        if (rtc == 0L) throw new IllegalStateException("EOS has no RTC interface");
        long audio = handle("EOS_RTC_GetAudioInterface", rtc);
        if (audio == 0L) throw new IllegalStateException("EOS has no RTC audio interface");
        return audio;
    }

    static long register(long audio, long localUser, String room, long callback) {
        try (Arena arena = Arena.Companion.ofConfined()) {
            MemorySegment options = arena.allocate(OPTIONS_SIZE);
            options.set(ValueLayout.Companion.getJAVA_INT(), 0L, API_VERSION);
            options.set(ValueLayout.Companion.getJAVA_INT(), 4L, 0);
            options.set(ValueLayout.Companion.getJAVA_LONG(), 8L, localUser);
            options.set(ValueLayout.Companion.getADDRESS(), 16L, arena.allocateFrom(room));
            options.set(ValueLayout.Companion.getJAVA_INT(), 24L, 0);
            options.set(ValueLayout.Companion.getJAVA_INT(), 28L, 0);
            Object result = Native.INSTANCE.invoke(
                "EOS_RTCAudio_AddNotifyAudioBeforeRender",
                List.of(audio, options, MemorySegment.Companion.getNULL(), MemorySegment.Companion.ofAddress(callback)),
                List.of(ValueLayout.Companion.getJAVA_LONG(), ValueLayout.Companion.getADDRESS(), ValueLayout.Companion.getADDRESS(), ValueLayout.Companion.getADDRESS()),
                ValueLayout.Companion.getJAVA_LONG()
            );
            return result instanceof Long value ? value : 0L;
        }
    }

    static void unregister(long audio, long notification) {
        Native.INSTANCE.invokeVoid(
            "EOS_RTCAudio_RemoveNotifyAudioBeforeRender",
            List.of(audio, notification),
            List.of(ValueLayout.Companion.getJAVA_LONG(), ValueLayout.Companion.getJAVA_LONG())
        );
    }

    static boolean valid(long frames, int count, int channels) {
        return frames != 0L && count > 0 && count <= MAX_FRAMES && channels > 0 && channels <= MAX_CHANNELS;
    }

    private static long handle(String function, long owner) {
        Object result = Native.INSTANCE.invoke(
            function,
            List.of(owner),
            List.of(ValueLayout.Companion.getJAVA_LONG()),
            ValueLayout.Companion.getJAVA_LONG()
        );
        return result instanceof Long value ? value : 0L;
    }
}
