package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >= 1.21.4 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//?}

//? if = 1.21.1 {
/*import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
*///?}

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    //? if >= 1.21.4 {
    @ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
    private Component polyplus$badgeNameTag(Component original, Entity entity) {
        if (original != null && entity instanceof AbstractClientPlayer player) {
            return PolyPlusBadge.decorate(original, player.getUUID());
        }
        return original;
    }
    //?} else {
    /*@ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component polyplus$badgeNameTag(Component displayName, @Local(argsOnly = true, ordinal = 0) Entity entity) {
        if (displayName != null && entity instanceof AbstractClientPlayer player) {
            return PolyPlusBadge.decorate(displayName, player.getUUID());
        }
        return displayName;
    }
    *///?}
}
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;getWidth(Ljava/lang/String;)I")
    )
    private int polyplus$badgeWidth(TextRenderer font, String text, Operation<Integer> original, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) String name) {
        int width = original.call(font, text);
        return PolyPlusBadge.badgesNameTag(entity, name) ? width + PolyPlusBadge.BADGE_ADVANCE : width;
    }

    @WrapOperation(
        method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderer;draw(Ljava/lang/String;III)I")
    )
    private int polyplus$badgeDraw(TextRenderer font, String text, int x, int y, int color, Operation<Integer> original, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) String name) {
        if (!PolyPlusBadge.badgesNameTag(entity, name)) return original.call(font, text, x, y, color);
        if (color == -1) PolyPlusBadge.drawBadge(x + 1, y);
        return original.call(font, text, x + PolyPlusBadge.BADGE_ADVANCE, y, color);
    }
}
*///?}
