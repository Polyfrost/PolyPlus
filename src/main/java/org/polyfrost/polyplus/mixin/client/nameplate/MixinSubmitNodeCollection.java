package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//?} elif = 26.2 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
*///?}

//? if >= 26.2 {
@Mixin(SubmitNodeCollection.class)
//?}
public class MixinSubmitNodeCollection {
    //? if >= 26.3 {
    @WrapOperation(
        method = "submitNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"
        ),
        require = 3
    )
    private TextFeatureRenderer.Submit polyplus$observeNameTag(
        Matrix4f pose,
        float x,
        float y,
        FormattedCharSequence name,
        int lightCoords,
        int color,
        int backgroundColor,
        Font.DisplayMode displayMode,
        Operation<TextFeatureRenderer.Submit> original
    ) {
        return NameplateSubmitAdapter.observe(original.call(pose, x, y, name, lightCoords, color, backgroundColor, displayMode));
    }
    //?} elif = 26.2 {
    /*@ModifyExpressionValue(
        method = "submitNameTag",
        at = @At(
            value = "NEW",
            target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;"
        ),
        require = 3
    )
    private NameTagFeatureRenderer.Submit polyplus$observeNameTag(NameTagFeatureRenderer.Submit submit) {
        return NameplateSubmitAdapter.observe(submit);
    }
    *///?}
}
