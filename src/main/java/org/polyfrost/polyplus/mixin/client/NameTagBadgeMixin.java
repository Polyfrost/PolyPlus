package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public class NameTagBadgeMixin {
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
