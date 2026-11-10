package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} elif = 26.2 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif >= 1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif >= 1.21.10 {
/*import net.minecraft.client.renderer.RenderType;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif >= 1.21.4 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.polyfrost.polyplus.client.nameplate.render.NameplateExtraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif > 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}

//? if > 1.8.9 {
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
//?}
public class MixinTextDisplayRenderer {
    //? if >= 1.21.4 {
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void polyplus$bindDisplayTag(Display.TextDisplay entity, TextDisplayEntityRenderState state, float partialTicks, CallbackInfo ci) {
        NameplateExtraction.bindDisplay(entity, state);
    }
    //?}

    //? if >= 26.3 {
    @WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitTextBackground(Lcom/mojang/blaze3d/vertex/PoseStack;FFFFILnet/minecraft/client/gui/Font$DisplayMode;I)V"
        )
    )
    private void polyplus$wrapDisplayBackground(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        float x0,
        float y0,
        float x1,
        float y1,
        int color,
        Font.DisplayMode displayMode,
        int lightCoords,
        Operation<Void> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state
    ) {
        NameplateSubmitAdapter.submitDisplayBackground(state, collector, poseStack, x0, y0, x1, y1, color, displayMode, lightCoords, original);
    }

    @WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"
        )
    )
    private void polyplus$wrapDisplayText(
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
        NameplateSubmitAdapter.submitDisplayText(state, collector, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor, original);
    }
    //?} elif = 26.2 {
    /*@ModifyVariable(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;last()Lcom/mojang/blaze3d/vertex/PoseStack$Pose;",
            ordinal = 0
        ),
        name = "backgroundColor"
    )
    private int polyplus$ownDisplayBackground(int backgroundColor, @Local(argsOnly = true) TextDisplayEntityRenderState state) {
        return NameplateSubmitAdapter.displayBackground(state, backgroundColor);
    }

    @WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"
        )
    )
    private void polyplus$wrapDisplayText(
        OrderedSubmitNodeCollector collector,
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
        @Local(argsOnly = true) SubmitNodeCollector root
    ) {
        NameplateSubmitAdapter.submitDisplayText(
            state, root, collector, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor, original
        );
    }
    *///?} elif >= 1.21.10 {
    /*@WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitText(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V"
        )
    )
    private void polyplus$wrapDisplayText(
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
        @Local(argsOnly = true) SubmitNodeCollector root
    ) {
        NameplateSubmitAdapter.submitDisplayText(state, root, target, poseStack, x, y, text, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor, original);
    }
    *///?} elif >= 1.21.4 {
    /*@Inject(method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("HEAD"))
    private void polyplus$beginDisplayTag(TextDisplayEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int lightCoords, float partialTicks, CallbackInfo ci) {
        NameplateBufferAdapter.beginDisplay(state);
    }

    @Inject(method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V", at = @At("RETURN"))
    private void polyplus$endDisplayTag(TextDisplayEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int lightCoords, float partialTicks, CallbackInfo ci) {
        NameplateBufferAdapter.endDisplay();
    }

    @WrapOperation(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        ),
        require = 4
    )
    private VertexConsumer polyplus$wrapDisplayBackground(
        VertexConsumer consumer,
        int color,
        Operation<VertexConsumer> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state
    ) {
        return original.call(consumer, NameplateBufferAdapter.displayBackground(state, color));
    }
    *///?} elif > 1.8.9 {
    /*@ModifyExpressionValue(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Display$TextDisplay;cacheDisplay(Lnet/minecraft/world/entity/Display$TextDisplay$LineSplitter;)Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
        )
    )
    private Display.TextDisplay.CachedInfo polyplus$bindDisplayTag(
        Display.TextDisplay.CachedInfo info,
        @Local(argsOnly = true) Display.TextDisplay entity,
        @Local(argsOnly = true) Display.TextDisplay.TextRenderState state,
        @Local(argsOnly = true) float interpolationProgress
    ) {
        return NameplateBufferAdapter.bindDisplay(entity, state, info, interpolationProgress);
    }

    @ModifyArg(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        ),
        require = 4,
        allow = 4
    )
    private int polyplus$wrapDisplayBackground(int color) {
        return NameplateBufferAdapter.displayBackground(color);
    }

    @WrapOperation(
        method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        )
    )
    private int polyplus$wrapDisplayText(
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
        Operation<Integer> original,
        @Local(argsOnly = true) PoseStack poseStack
    ) {
        return NameplateBufferAdapter.drawDisplayText(
            font,
            text,
            x,
            y,
            color,
            dropShadow,
            pose,
            buffers,
            displayMode,
            backgroundColor,
            lightCoords,
            poseStack,
            original
        );
    }
    *///?}

    //? if >= 1.21.11 && < 26.2 {
    /*@WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
        )
    )
    private void polyplus$wrapDisplayBackground(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer renderer,
        Operation<Void> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state
    ) {
        if (!NameplateSubmitAdapter.ownsDisplayBackground(state)) {
            original.call(collector, poseStack, renderType, renderer);
        }
    }
    *///?} elif >= 1.21.10 && < 1.21.11 {
    /*@WrapOperation(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"
        )
    )
    private void polyplus$wrapDisplayBackground(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer renderer,
        Operation<Void> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state
    ) {
        if (!NameplateSubmitAdapter.ownsDisplayBackground(state)) {
            original.call(collector, poseStack, renderType, renderer);
        }
    }
    *///?} elif >= 1.21.8 && < 1.21.10 {
    /*@WrapOperation(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        )
    )
    private void polyplus$wrapDisplayText(
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
        Operation<Void> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state,
        @Local(argsOnly = true) PoseStack poseStack
    ) {
        NameplateBufferAdapter.drawDisplayLine(state, poseStack, font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords, original);
    }
    *///?} elif >= 1.21.4 && < 1.21.8 {
    /*@WrapOperation(
        method = "renderInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        )
    )
    private int polyplus$wrapDisplayText(
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
        Operation<Integer> original,
        @Local(argsOnly = true) TextDisplayEntityRenderState state,
        @Local(argsOnly = true) PoseStack poseStack
    ) {
        return NameplateBufferAdapter.drawDisplayLine(state, poseStack, font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords, original);
    }
    *///?}
}
