package org.polyfrost.polyplus.mixin.compat.clothconfig;

import com.google.gson.Gson;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.Reader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "me.shedaniel.autoconfig.serializer.GsonConfigSerializer", remap = false)
public class MixinGsonConfigSerializer {
    @Unique
    private static final Logger polyplus$LOGGER = LogManager.getLogger("PolyPlus/Compat");

    // Gson returns null for a 0 byte file causing AutoConfig to crash
    @WrapOperation(
            method = "deserialize",
            at = @At(value = "INVOKE", target = "Lcom/google/gson/Gson;fromJson(Ljava/io/Reader;Ljava/lang/Class;)Ljava/lang/Object;"),
            remap = false,
            require = 0,
            expect = 0
    )
    private Object polyplus$defaultsForEmptyFile(Gson gson, Reader reader, Class<?> type, Operation<Object> original) {
        Object config = original.call(gson, reader, type);
        if (config != null) return config;
        polyplus$LOGGER.warn("The config file for {} is empty, loading its defaults", type.getName());
        return gson.fromJson("{}", type);
    }
}
