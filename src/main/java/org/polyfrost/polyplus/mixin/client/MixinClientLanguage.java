package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public class MixinClientLanguage {
    @Shadow @Final private static Logger LOGGER;

    // vanilla only catches IOException here, so a single malformed file in any pack aborts loading the whole language
    @WrapOperation(
            method = "appendFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V")
    )
    private static void polyplus$skipMalformedFile(
            InputStream stream,
            BiConsumer<String, String> output,
            Operation<Void> original,
            //? if >= 26.1 {
            @Local(name = "languageCode") String languageCode,
            @Local(name = "resource") Resource resource
            //?} else {
            /*@Local(argsOnly = true) String languageCode,
            @Local Resource resource
            *///?}
    ) {
        try {
            original.call(stream, output);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to load translations for {} from pack {}", languageCode, resource.sourcePackId(), e);
        }
    }
}
//?}
