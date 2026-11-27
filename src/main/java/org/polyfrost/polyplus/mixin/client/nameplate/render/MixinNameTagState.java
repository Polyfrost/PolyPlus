package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if >= 1.21.4 {
import org.polyfrost.polyplus.client.nameplate.render.NameTagBinding;
import org.polyfrost.polyplus.client.nameplate.render.NameTagState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

//? if >= 1.21.10 {
@Mixin(AvatarRenderState.class)
//?} else {
/*@Mixin(PlayerRenderState.class)
*///?}
public class MixinNameTagState implements NameTagState {
    @Unique
    private NameTagBinding polyplus$nameTagBinding;

    @Override
    public NameTagBinding polyplus$nameTag() {
        return polyplus$nameTagBinding;
    }

    @Override
    public void polyplus$setNameTag(NameTagBinding binding) {
        polyplus$nameTagBinding = binding;
    }
}
//?}
