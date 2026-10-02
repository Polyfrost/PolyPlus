package org.polyfrost.polyplus.mixin.compat.iris;

//? if >= 26.2 {
import com.bawnorton.mixinsquared.TargetHandler;
import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.irisshaders.iris.pbr.texture.PBRSpriteHolder;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SpriteContents.class, priority = 1500)
public class MixinSpriteContents {
    @Shadow(remap = false)
    private PBRSpriteHolder pbrHolder;

    @Unique
    private static boolean polyplus$anyPbrHolder;

    @Inject(method = "getOrCreatePBRHolder", at = @At(value = "NEW", target = "net/irisshaders/iris/pbr/texture/PBRSpriteHolder"), remap = false, require = 0, expect = 0)
    private void polyplus$markPbrHolder(CallbackInfoReturnable<PBRSpriteHolder> cir) {
        polyplus$anyPbrHolder = true;
    }

    @TargetHandler(mixin = "net.irisshaders.iris.mixin.texture.pbr.MixinSpriteContents", name = "iris$onTailMarkActive")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void polyplus$markPbrSpritesActive(CallbackInfo ci) {
        ci.cancel();
        if (!polyplus$anyPbrHolder) return;

        PBRSpriteHolder holder = this.pbrHolder;
        if (holder == null) return;

        TextureAtlasSprite normal = holder.getNormalSprite();
        TextureAtlasSprite specular = holder.getSpecularSprite();
        if (normal != null) SpriteUtil.INSTANCE.markSpriteActive(normal);
        if (specular != null) SpriteUtil.INSTANCE.markSpriteActive(specular);
    }
}
//?}
