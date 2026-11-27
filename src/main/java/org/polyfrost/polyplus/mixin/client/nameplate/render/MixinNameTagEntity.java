package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if >= 26.1 || = 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.polyfrost.polyplus.client.nameplate.render.NameTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 26.1 {
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
//?} else {
/*import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

//? if >= 26.1 {
@Mixin(EntityRenderer.class)
//?} else {
/*@Mixin(value = EntityRenderer.class, priority = 1100)
*///?}
public class MixinNameTagEntity {
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
        @Local(name = "state") EntityRenderState state
    ) {
        Object handle = NameTags.beginName(state, collector);
        boolean completed = false;
        try {
            original.call(collector, poseStack, attachment, offset, name, seeThrough, lightCoords, camera);
            completed = true;
        } finally {
            NameTags.endName(handle, collector, completed);
        }
    }
    //?} elif >= 26.1 {
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
        @Local(name = "state") EntityRenderState state
    ) {
        Object handle = NameTags.beginName(state, collector);
        boolean completed = false;
        try {
            original.call(collector, poseStack, attachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera);
            completed = true;
        } finally {
            NameTags.endName(handle, collector, completed);
        }
    }
    *///?} else {
    /*@Inject(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V", at = @At("HEAD"))
    private void polyplus$beginNameTag(Entity entity, String name, double x, double y, double z, int maxDistance, CallbackInfo ci) {
        NameTags.begin(entity);
    }

    @ModifyArg(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;rotatef(FFFF)V", ordinal = 1),
        index = 0
    )
    private float polyplus$faceCamera(float pitch) {
        return NameTags.cameraPitch(pitch);
    }

    @ModifyArg(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;"),
        index = 3
    )
    private float polyplus$hideBackground(float alpha) {
        return NameTags.backgroundAlpha(alpha);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 0)
    )
    private int polyplus$drawSeeThroughNameText(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original) {
        return NameTags.drawSeeThroughName(font, text, x, y, color, original);
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 1)
    )
    private int polyplus$drawNameText(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original) {
        return NameTags.drawName(font, text, x, y, color, original);
    }
    *///?}
}
//?}
