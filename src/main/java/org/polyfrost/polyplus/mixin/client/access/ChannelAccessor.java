package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("source")
    int polyplus$source();
}
//?}
