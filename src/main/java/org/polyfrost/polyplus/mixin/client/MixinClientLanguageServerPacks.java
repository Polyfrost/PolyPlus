package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.polyfrost.polyplus.client.gui.pausescreen.ClientTranslationsAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

// servers often relabel pause menu buttons with glyphs from their own font, which only the vanilla screen can draw, so
// the client's own pause menu reads translations as they'd be without the server's packs
@Mixin(ClientLanguage.class)
public class MixinClientLanguageServerPacks implements ClientTranslationsAccess {
    @Unique
    private static final ThreadLocal<Map<String, String>> polyplus$loadingClient = new ThreadLocal<>();

    @Unique
    private Map<String, String> polyplus$clientTranslations = Map.of();

    @Override
    public String polyplus$clientTranslation(String key) {
        return polyplus$clientTranslations.get(key);
    }

    @WrapMethod(method = "loadFrom")
    private static ClientLanguage polyplus$collectClientTranslations(ResourceManager resourceManager, List<String> languages, boolean defaultRightToLeft, Operation<ClientLanguage> original) {
        Map<String, String> client = new HashMap<>();
        polyplus$loadingClient.set(client);
        try {
            ClientLanguage language = original.call(resourceManager, languages, defaultRightToLeft);
            ((MixinClientLanguageServerPacks) (Object) language).polyplus$clientTranslations = Map.copyOf(client);
            return language;
        } finally {
            polyplus$loadingClient.remove();
        }
    }

    @WrapOperation(
            method = "appendFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V")
    )
    private static void polyplus$trackClientTranslations(InputStream stream, BiConsumer<String, String> output, Operation<Void> original, @Local Resource resource) {
        Map<String, String> client = polyplus$loadingClient.get();
        if (client == null || polyplus$fromServer(resource)) {
            original.call(stream, output);
            return;
        }
        original.call(stream, (BiConsumer<String, String>) (key, value) -> {
            output.accept(key, value);
            client.put(key, value);
        });
    }

    @Unique
    private static boolean polyplus$fromServer(Resource resource) {
        PackSource source = resource.source().location().source();
        return source == PackSource.SERVER || source == PackSource.WORLD;
    }
}
//?}
