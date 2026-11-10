package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.1 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.polyfrost.polyplus.client.nameplate.render.NameplateSubmitAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//?} elif >= 1.21.1 && < 1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.polyfrost.polyplus.client.nameplate.render.NameplateBufferAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
*///?} elif = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateFacing;
import org.polyfrost.polyplus.client.nameplate.render.legacy.LegacyNameplateRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if >= 26.1 {
@Mixin(EntityRenderer.class)
//?} elif >= 1.21.1 && < 1.21.10 {
/*@Mixin(EntityRenderer.class)
*///?} elif = 1.8.9 {
/*@Mixin(value = EntityRenderer.class, priority = 1100)
*///?}
public class MixinEntityRenderer {
    //? if >= 26.2 {
    @WrapOperation(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            ordinal = 1
        )
    )
    private void polyplus$wrapNameTag(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        Vec3 attachment,
        int offset,
        Component name,
        boolean seeThrough,
        int lightCoords,
        CameraRenderState camera,
        Operation<Void> original,
        @Local(argsOnly = true) EntityRenderState state
    ) {
        NameplateSubmitAdapter.submitName(state, collector, poseStack, attachment, offset, name, seeThrough, lightCoords, camera, original);
    }
    //?} elif = 26.1 {
    /*@WrapOperation(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZIDLnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            ordinal = 1
        )
    )
    private void polyplus$wrapNameTag(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        Vec3 attachment,
        int offset,
        Component name,
        boolean seeThrough,
        int lightCoords,
        double distanceToCameraSq,
        CameraRenderState camera,
        Operation<Void> original,
        @Local(argsOnly = true) EntityRenderState state
    ) {
        NameplateSubmitAdapter.submitName(state, collector, poseStack, attachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera, original);
    }
    *///?} elif >= 1.21.8 && < 1.21.10 {
    /*@WrapOperation(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
        ),
        require = 2
    )
    private void polyplus$wrapNameText(
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
        NameplateBufferAdapter.drawName(font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords, original);
    }
    *///?} elif >= 1.21.4 && < 1.21.8 {
    /*@WrapOperation(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        ),
        require = 2
    )
    private int polyplus$wrapNameText(
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
        return NameplateBufferAdapter.drawName(font, text, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords, original);
    }
    *///?} elif = 1.21.1 {
    /*@WrapOperation(
        method = "renderNameTag",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
        ),
        require = 2,
        allow = 2
    )
    private int polyplus$wrapNameText(
        Font font,
        Component name,
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
        return NameplateBufferAdapter.drawName(font, name, x, y, color, dropShadow, pose, buffers, displayMode, backgroundColor, lightCoords, original);
    }
    *///?} elif = 1.8.9 {
    /*@Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("HEAD"))
    private void polyplus$resetNameplate(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        LegacyNameplateRenderer.reset();
    }

    @ModifyExpressionValue(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;cameraPitch:F")
    )
    private float polyplus$faceCamera(float pitch) {
        return LegacyNameplateFacing.pitch(pitch);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V")
    )
    private void polyplus$nameplateBackground(
        Tesselator tesselator,
        Operation<Void> original,
        @Local(argsOnly = true) Entity entity,
        @Local(argsOnly = true) String name
    ) {
        LegacyNameplateRenderer.background(entity, name, tesselator, original);
    }

    @Inject(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableTexture()V", ordinal = 0, shift = At.Shift.AFTER)
    )
    private void polyplus$seeThroughDecorations(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        LegacyNameplateRenderer.beforeSeeThrough(entity);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 0)
    )
    private int polyplus$seeThroughText(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original, @Local(argsOnly = true) Entity entity) {
        return LegacyNameplateRenderer.seeThroughText(font, entity, text, x, y, color, original);
    }

    @Inject(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;depthMask(Z)V", ordinal = 1, shift = At.Shift.AFTER)
    )
    private void polyplus$opaqueDecorations(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        LegacyNameplateRenderer.beforeOpaque(entity);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 1)
    )
    private int polyplus$opaqueText(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original, @Local(argsOnly = true) Entity entity) {
        return LegacyNameplateRenderer.opaqueText(font, entity, text, x, y, color, original);
    }
    *///?}
}
