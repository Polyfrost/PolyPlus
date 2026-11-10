package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} elif >= 1.21.4 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}
//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.nameplate.render.NameplateTag;
import org.polyfrost.polyplus.client.nameplate.render.NameplateTagAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderState.class)
//?} elif >= 1.21.4 {
/*@Mixin(PlayerRenderState.class)
*///?}
public class MixinAvatarRenderState
    //? if >= 1.21.4
    implements NameplateTagAccess
{
    //? if >= 1.21.4 {
    @Unique
    private NameplateTag polyplus$nameplateTag;

    @Override
    public NameplateTag polyplus$nameplateTag() {
        return polyplus$nameplateTag;
    }

    @Override
    public void polyplus$setNameplateTag(NameplateTag tag) {
        polyplus$nameplateTag = tag;
    }
    //?}
}
