package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import org.polyfrost.polyplus.client.nameplate.render.DisplayTagAccess;
import org.polyfrost.polyplus.client.nameplate.render.DisplayTagBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//?}

//? if >= 1.21.4 {
@Mixin(TextDisplayEntityRenderState.class)
//?}
public class MixinTextDisplayEntityRenderState
    //? if >= 1.21.4
    implements DisplayTagAccess
{
    //? if >= 1.21.4 {
    @Unique
    private DisplayTagBinding polyplus$displayTag;

    @Override
    public DisplayTagBinding polyplus$displayTag() {
        return polyplus$displayTag;
    }

    @Override
    public void polyplus$setDisplayTag(DisplayTagBinding binding) {
        polyplus$displayTag = binding;
    }
    //?}
}
