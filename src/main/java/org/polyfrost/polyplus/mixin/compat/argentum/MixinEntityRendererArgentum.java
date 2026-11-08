package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.polyfrost.polyplus.compat.ArgentumNameTags;
import org.spongepowered.asm.mixin.Mixin;

// Argentum 1.0.3+ wraps renderNameTag to capture it into its NameTagBatch, which records the GL matrices and draws the
// glyphs itself, so the badge never reaches MixinEmojiTextRenderer. badged nametags render unbatched instead.
// priority > 1000 so this wrapper sits outside Argentum's
@Mixin(value = EntityRenderer.class, priority = 2000)
public class MixinEntityRendererArgentum {
    @WrapMethod(method = "renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V")
    private void polyplus$unbatchBadged(Entity entity, String name, double x, double y, double z, int maxDistance, Operation<Void> original) {
        if (!ArgentumNameTags.ENABLED || !(entity instanceof PlayerEntity) || !PolyPlusBadge.badgesNameTag(entity, name)) {
            original.call(entity, name, x, y, z, maxDistance);
            return;
        }
        ArgentumNameTags.beginUnbatched();
        try {
            original.call(entity, name, x, y, z, maxDistance);
        } finally {
            ArgentumNameTags.endUnbatched();
        }
    }
}
*///?}
