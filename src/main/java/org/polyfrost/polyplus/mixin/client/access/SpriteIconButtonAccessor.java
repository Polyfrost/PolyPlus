package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.SpriteIconButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >= 1.21.9 {
import net.minecraft.client.gui.components.WidgetSprites;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

@Mixin(SpriteIconButton.class)
public interface SpriteIconButtonAccessor {
    //? if >= 1.21.9 {
    @Accessor("sprite")
    WidgetSprites polyplus$sprite();
    //?} else {
    /*@Accessor("sprite")
    ResourceLocation polyplus$sprite();
    *///?}

    @Accessor("spriteWidth")
    int polyplus$spriteWidth();

    @Accessor("spriteHeight")
    int polyplus$spriteHeight();
}
//?}
