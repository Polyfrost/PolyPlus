package org.polyfrost.polyplus.mixin.client.access;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

@Mixin(Holder.Reference.class)
public interface HolderReferenceInvoker {
    @Invoker("bindKey")
    void polyplus$bindKey(ResourceKey<?> key);

    @Invoker("bindTags")
    void polyplus$bindTags(Collection<?> tags);
}
