package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.1 {
import net.minecraft.client.StringSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//?}

//? if >= 1.21.1 {
@Mixin(StringSplitter.class)
public interface StringSplitterAccessor {
    @Accessor("widthProvider")
    StringSplitter.WidthProvider polyplus$widthProvider();
}
//?} else {
/*public interface StringSplitterAccessor {
}
*///?}
