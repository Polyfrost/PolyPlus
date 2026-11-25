package org.polyfrost.polyplus.mixin.client;

//? if >= 26.1 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Dark vanilla buttons, switched instantly with the theme button instead of through a resource pack reload. */
@Mixin(AbstractButton.class)
public abstract class MixinAbstractButtonDark {
    @Unique
    private static final WidgetSprites POLYPLUS$DARK = new WidgetSprites(
        Identifier.fromNamespaceAndPath("polyplus", "widget/dark_button"),
        Identifier.fromNamespaceAndPath("polyplus", "widget/dark_button_disabled"),
        Identifier.fromNamespaceAndPath("polyplus", "widget/dark_button_highlighted")
    );

    @ModifyExpressionValue(
        method = "extractDefaultSprite",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/AbstractButton;SPRITES:Lnet/minecraft/client/gui/components/WidgetSprites;")
    )
    private WidgetSprites polyplus$darkSprites(WidgetSprites original) {
        return PolyPlusMainMenuConfig.getVanillaDarkTheme() ? POLYPLUS$DARK : original;
    }
}
//?}
