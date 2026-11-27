package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if > 1.8.9 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 1.21.10 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.polyfrost.polyplus.client.nameplate.render.NameTagCapture;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.NameTags;
*///?}
//? if >= 26.3 {
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
//?} elif = 26.2 {
/*import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
*///?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
*///?}

//? if >= 26.2 {
@Mixin(SubmitNodeCollection.class)
//?} elif >= 1.21.10 {
/*@Mixin(NameTagFeatureRenderer.Storage.class)
*///?} else {
/*@Mixin(value = EntityRenderer.class, priority = 1100)
*///?}
public class MixinNameTagCapture {
    //? if >= 26.3 {
    @ModifyExpressionValue(
        method = "submitNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;nameTag(Lorg/joml/Matrix4f;FFLnet/minecraft/util/FormattedCharSequence;IIILnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;"
        ),
        require = 3
    )
    private TextFeatureRenderer.Submit polyplus$observeNameTag(TextFeatureRenderer.Submit submit) {
        return NameTagCapture.observe(submit);
    }
    //?} elif = 26.2 {
    /*@ModifyExpressionValue(
        method = "submitNameTag",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit"),
        require = 3
    )
    private NameTagFeatureRenderer.Submit polyplus$observeNameTag(NameTagFeatureRenderer.Submit submit) {
        return NameTagCapture.observe(submit);
    }
    *///?} elif >= 1.21.10 {
    /*@ModifyExpressionValue(
        method = "add",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit", ordinal = 0)
    )
    private SubmitNodeStorage.NameTagSubmit polyplus$observeNameTag(SubmitNodeStorage.NameTagSubmit submit) {
        return NameTagCapture.observe(submit, false);
    }

    @ModifyExpressionValue(
        method = "add",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit", ordinal = 1)
    )
    private SubmitNodeStorage.NameTagSubmit polyplus$observeSeeThroughNameTag(SubmitNodeStorage.NameTagSubmit submit) {
        return NameTagCapture.observe(submit, true);
    }

    @ModifyExpressionValue(
        method = "add",
        at = @At(value = "NEW", target = "net/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit", ordinal = 2)
    )
    private SubmitNodeStorage.NameTagSubmit polyplus$observeSneakingNameTag(SubmitNodeStorage.NameTagSubmit submit) {
        return NameTagCapture.observe(submit, false);
    }
    *///?} elif >= 1.21.8 {
    /*@WrapOperation(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        ),
        require = 2,
        allow = 2
    )
    private void polyplus$drawNameText(
        Font font,
        Component text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource buffers,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int lightCoords,
        Operation<Void> original
    ) {
        if (NameTags.deferLine(displayMode == Font.DisplayMode.SEE_THROUGH)) {
            Matrix4f held = new Matrix4f(pose);
            NameTags.defer(() -> original.call(font, text, x, y, color, dropShadow, held, buffers, displayMode, backgroundColor, lightCoords));
            return;
        }
        boolean changed = NameTags.observe(font, text, x, y, color, displayMode == Font.DisplayMode.SEE_THROUGH, pose, buffers, backgroundColor, lightCoords);
        if (!changed) {
            original.call(font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords);
            return;
        }
        original.call(font, text, x, y, NameTags.getDrawColor(), dropShadow, pose, buffers, displayMode, NameTags.getDrawBackground(), lightCoords);
        NameTags.afterName(font, x, y, color, pose, buffers, lightCoords);
    }
    *///?} else {
    /*@WrapOperation(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        ),
        require = 2,
        allow = 2
    )
    private int polyplus$drawNameText(
        Font font,
        Component text,
        float x,
        float y,
        int color,
        boolean dropShadow,
        Matrix4f pose,
        MultiBufferSource buffers,
        Font.DisplayMode displayMode,
        int backgroundColor,
        int lightCoords,
        Operation<Integer> original
    ) {
        if (NameTags.deferLine(displayMode == Font.DisplayMode.SEE_THROUGH)) {
            Matrix4f held = new Matrix4f(pose);
            NameTags.defer(() -> original.call(font, text, x, y, color, dropShadow, held, buffers, displayMode, backgroundColor, lightCoords));
            return 0;
        }
        boolean changed = NameTags.observe(font, text, x, y, color, displayMode == Font.DisplayMode.SEE_THROUGH, pose, buffers, backgroundColor, lightCoords);
        if (!changed) {
            return original.call(font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords);
        }
        int advance = original.call(font, text, x, y, NameTags.getDrawColor(), dropShadow, pose, buffers, displayMode, NameTags.getDrawBackground(), lightCoords);
        NameTags.afterName(font, x, y, color, pose, buffers, lightCoords);
        return advance;
    }
    *///?}
}
//?}
