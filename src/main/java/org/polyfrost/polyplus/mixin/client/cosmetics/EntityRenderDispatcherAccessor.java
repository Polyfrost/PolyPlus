package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if >= 1.21.4 && < 1.21.5 {
/*import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.gen.Accessor;
*///?}
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    //? if >= 1.21.4 && < 1.21.5 {
    /*@Accessor("playerRenderers")
    Map<PlayerSkin.Model, EntityRenderer<? extends Player, ?>> polyplus$playerRenderers();
    *///?}
}
