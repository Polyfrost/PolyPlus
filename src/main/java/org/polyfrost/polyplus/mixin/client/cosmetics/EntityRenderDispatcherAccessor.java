package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if > 1.8.9 {
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
//?} else {
/*import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
*///?}
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

    //? if = 1.8.9 {
    /*@Accessor("renderers")
    Map<Class<? extends Entity>, EntityRenderer<? extends Entity>> polyplus$renderers();
    *///?}
}
