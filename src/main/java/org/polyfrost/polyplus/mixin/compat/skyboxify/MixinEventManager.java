package org.polyfrost.polyplus.mixin.compat.skyboxify;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "btw.lowercase.skyboxify.events.EventManager", remap = false)
public class MixinEventManager {
    @Unique
    private volatile Map<Class<?>, Set<Map.Entry<Class<?>, List<Object>>>> polyplus$resolved = new ConcurrentHashMap<>();

    @Inject(method = "listen", at = @At("TAIL"), require = 0)
    private void polyplus$invalidateResolved(Class<?> eventClass, Consumer<?> consumer, CallbackInfo ci) {
        this.polyplus$resolved = new ConcurrentHashMap<>();
    }

    @WrapOperation(
            method = "dispatch",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"),
            require = 0
    )
    private Set<Map.Entry<Class<?>, List<Object>>> polyplus$resolvedEntries(
            Map<Class<?>, List<Object>> listeners,
            Operation<Set<Map.Entry<Class<?>, List<Object>>>> original,
            @Local Class<?> eventClass
    ) {
        return this.polyplus$resolved.computeIfAbsent(eventClass, key -> {
            List<Object> matching = new ArrayList<>();
            for (Map.Entry<Class<?>, List<Object>> entry : original.call(listeners)) {
                if (entry.getKey().isAssignableFrom(key)) {
                    matching.addAll(entry.getValue());
                }
            }

            return matching.isEmpty() ? Set.of() : Set.of(Map.entry(key, List.copyOf(matching)));
        });
    }
}
