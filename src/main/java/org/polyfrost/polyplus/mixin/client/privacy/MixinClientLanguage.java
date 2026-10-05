package org.polyfrost.polyplus.mixin.client.privacy;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.polyfrost.polyplus.client.privacy.RichTextPrivacy;
import org.polyfrost.polyplus.client.privacy.UnblockedTranslationsAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(ClientLanguage.class)
public class MixinClientLanguage implements UnblockedTranslationsAccess {
    @Unique
    private static final ThreadLocal<Map<String, String>> polyplus$loading = new ThreadLocal<>();

    @Unique
    private Map<String, String> polyplus$unblockedTranslations = Map.of();

    @Override
    public String polyplus$unblockedTranslation(String key) {
        return polyplus$unblockedTranslations.get(key);
    }

    @WrapMethod(method = "loadFrom")
    private static ClientLanguage polyplus$collectUnblockedTranslations(ResourceManager resourceManager, List<String> languages, boolean defaultRightToLeft, Operation<ClientLanguage> original) {
        Map<String, String> unblocked = new HashMap<>();
        polyplus$loading.set(unblocked);
        try {
            ClientLanguage language = original.call(resourceManager, languages, defaultRightToLeft);
            ((MixinClientLanguage) (Object) language).polyplus$unblockedTranslations = Map.copyOf(unblocked);
            return language;
        } finally {
            polyplus$loading.remove();
        }
    }

    @WrapOperation(
            method = "appendFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V")
    )
    private static void polyplus$trackUnblockedTranslations(InputStream stream, BiConsumer<String, String> output, Operation<Void> original, @Local Resource resource) {
        Map<String, String> unblocked = polyplus$loading.get();
        original.call(stream, unblocked == null ? output : RichTextPrivacy.trackUnblocked(resource.source(), output, unblocked));
    }
}
//?}
