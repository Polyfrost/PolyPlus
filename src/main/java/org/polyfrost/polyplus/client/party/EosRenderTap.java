package org.polyfrost.polyplus.client.party;

import gg.sona.eos.EosPlatform;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface EosRenderTap {
    interface Sink {
        void accept(short[] samples, int frames, int channels, int rate);
    }

    void remove();

    static EosRenderTap register(EosPlatform platform, long localUser, String room, Sink sink) {
        if (available("org.polyfrost.polyplus.client.party.LwjglRenderTap")) {
            return invoke("org.polyfrost.polyplus.client.party.LwjglRenderTap", platform, localUser, room, sink);
        }
        if (Runtime.version().feature() >= 22 && available("org.polyfrost.polyplus.client.party.FfmRenderTap")) {
            return invoke("org.polyfrost.polyplus.client.party.FfmRenderTap", platform, localUser, room, sink);
        }
        throw new UnsupportedOperationException("no way to receive EOS audio in this build");
    }

    private static boolean available(String name) {
        try {
            Class.forName(name, false, EosRenderTap.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    private static EosRenderTap invoke(String name, EosPlatform platform, long localUser, String room, Sink sink) {
        try {
            Method register = Class.forName(name).getMethod("register", EosPlatform.class, long.class, String.class, Sink.class);
            return (EosRenderTap) register.invoke(null, platform, localUser, room, sink);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException error) {
            throw new UnsupportedOperationException("not available in this build", error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new IllegalStateException(cause);
        }
    }
}
