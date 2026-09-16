package org.polyfrost.polyplus.mixin.client.cosmetics;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;

//? if = 1.21.4 {
/*import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
*///?}

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    //? if >= 1.21.4 && < 1.21.5 {
    /*@Accessor("playerRenderers")
    Map<PlayerSkin.Model, EntityRenderer<? extends Player, ?>> polyplus$playerRenderers();
    *///?}
}
