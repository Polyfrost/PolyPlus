package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SimplePreparableReloadListener.class)
public interface SimplePreparableReloadListenerInvoker {
    @Invoker("prepare")
    Object polyplus$prepare(ResourceManager manager, ProfilerFiller profiler);

    @Invoker("apply")
    void polyplus$apply(Object prepared, ResourceManager manager, ProfilerFiller profiler);
}
//?}
