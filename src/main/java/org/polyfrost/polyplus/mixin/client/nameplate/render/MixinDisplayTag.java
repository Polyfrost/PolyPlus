package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.polyfrost.polyplus.client.nameplate.render.DisplayTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >= 1.21.10 {
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
*///?}
//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
//?} else {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
*///?}
//? if >= 26.3 || < 1.21.10 {
import org.spongepowered.asm.mixin.injection.ModifyArg;
//?} else {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
*///?}
//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} elif >= 1.21.10 && < 26.3 {
/*import net.minecraft.client.renderer.RenderType;
*///?}

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class MixinDisplayTag {
    //? if >= 1.21.4 {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void polyplus$bindDisplayTag(Display.TextDisplay entity, TextDisplayEntityRenderState state, float partialTick, CallbackInfo ci) {
        DisplayTags.bind(entity, state, partialTick);
    }
    //?}

    //? if >= 26.3 {
    @ModifyArg(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitTextBackground(Lcom/mojang/blaze3d/vertex/PoseStack;FFFFILnet/minecraft/client/gui/Font$DisplayMode;I)V"
        ),
        index = 5
    )
    private int polyplus$ownDisplayBackground(int color, @Local(argsOnly = true) TextDisplayEntityRenderState state) {
        return DisplayTags.backgroundColor(state, color);
    }

    @WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"
        )
    )
    private void polyplus$submitDisplayLine(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        float x,
        float y,
        FormattedCharSequence text,
        boolean dropShadow,
        Font.DisplayMode displayMode,
        int lightCoords,
        int color,
        int backgroundColor,
        int outlineColor,
        Operation<Void> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state
    ) {
        DisplayTags.submitLine(
            state, collector, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, outlineColor,
            (lineX, lineY, line, shadow, lineColor, outline) -> original.call(collector, poseStack, lineX, lineY, line, shadow, displayMode, lightCoords, lineColor, backgroundColor, outline)
        );
    }
    //?} elif >= 1.21.10 {
    /*//? if >= 1.21.11 {
    @WrapWithCondition(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
        )
    )
    //?} else {
    /^@WrapWithCondition(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
        )
    )
    ^///?}
    private boolean polyplus$drawDisplayBackground(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer renderer,
        @Local(argsOnly = true) TextDisplayEntityRenderState state
    ) {
        return DisplayTags.drawsBackground(state);
    }

    @WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"
        )
    )
    private void polyplus$submitDisplayLine(
        OrderedSubmitNodeCollector target,
        PoseStack poseStack,
        float x,
        float y,
        FormattedCharSequence text,
        boolean dropShadow,
        Font.DisplayMode displayMode,
        int lightCoords,
        int color,
        int backgroundColor,
        int outlineColor,
        Operation<Void> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state,
        @Local(argsOnly = true) SubmitNodeCollector collector
    ) {
        DisplayTags.submitLine(
            state, collector, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, outlineColor,
            (lineX, lineY, line, shadow, lineColor, outline) -> original.call(target, poseStack, lineX, lineY, line, shadow, displayMode, lightCoords, lineColor, backgroundColor, outline)
        );
    }
    *///?} elif >= 1.21.4 {
    /*@Inject(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At("HEAD")
    )
    private void polyplus$beginDisplayTag(TextDisplayEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int lightCoords, float partialTick, CallbackInfo ci) {
        DisplayTags.begin(state);
    }

    @Inject(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At("RETURN")
    )
    private void polyplus$endDisplayTag(TextDisplayEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int lightCoords, float partialTick, CallbackInfo ci) {
        DisplayTags.end();
    }

    @ModifyArg(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
        require = 4,
        allow = 4
    )
    private int polyplus$ownDisplayBackground(int color) {
        return DisplayTags.backgroundColor(color);
    }

    //? if >= 1.21.8 {
    @WrapOperation(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        )
    )
    private void polyplus$drawDisplayLine(
        Font font,
        FormattedCharSequence text,
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
        DisplayTags.drawLine(font, text, x, y, color, dropShadow, pose, buffers, displayMode, lightCoords, (line, lineX, lineY, shadow, lineColor) -> {
            original.call(font, line, lineX, lineY, lineColor, shadow, pose, buffers, displayMode, backgroundColor, lightCoords);
            return 0;
        });
    }
    //?} else {
    /^@WrapOperation(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        )
    )
    private int polyplus$drawDisplayLine(
        Font font,
        FormattedCharSequence text,
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
        return DisplayTags.drawLine(font, text, x, y, color, dropShadow, pose, buffers, displayMode, lightCoords,
            (line, lineX, lineY, shadow, lineColor) -> original.call(font, line, lineX, lineY, lineColor, shadow, pose, buffers, displayMode, backgroundColor, lightCoords));
    }
    ^///?}
    *///?} else {
    /*@ModifyExpressionValue(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Display$TextDisplay;cacheDisplay(Lnet/minecraft/world/entity/Display$TextDisplay$LineSplitter;)Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
        )
    )
    private Display.TextDisplay.CachedInfo polyplus$beginDisplayTag(
        Display.TextDisplay.CachedInfo info,
        @Local(argsOnly = true) Display.TextDisplay entity,
        @Local(argsOnly = true) Display.TextDisplay.TextRenderState state,
        @Local(argsOnly = true) float partialTick
    ) {
        return DisplayTags.begin(entity, info, state, partialTick);
    }

    @Inject(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At("RETURN")
    )
    private void polyplus$endDisplayTag(
        Display.TextDisplay entity,
        Display.TextDisplay.TextRenderState state,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int lightCoords,
        float partialTick,
        CallbackInfo ci
    ) {
        DisplayTags.end();
    }

    @ModifyArg(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
        require = 4,
        allow = 4
    )
    private int polyplus$ownDisplayBackground(int color) {
        return DisplayTags.backgroundColor(color);
    }

    @WrapOperation(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        )
    )
    private int polyplus$drawDisplayLine(
        Font font,
        FormattedCharSequence text,
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
        return DisplayTags.drawLine(font, text, x, y, color, dropShadow, pose, buffers, displayMode, lightCoords,
            (line, lineX, lineY, shadow, lineColor) -> original.call(font, line, lineX, lineY, lineColor, shadow, pose, buffers, displayMode, backgroundColor, lightCoords));
    }
    *///?}
}
//?}
