package org.polyfrost.polyplus.mixin.client.nameplate.render;

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import org.polyfrost.polyplus.client.nameplate.render.DisplayTagBinding;
import org.polyfrost.polyplus.client.nameplate.render.DisplayTagState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(TextDisplayEntityRenderState.class)
public class MixinDisplayTagState implements DisplayTagState {
    @Unique
    private DisplayTagBinding polyplus$displayTagBinding;

    @Override
    public DisplayTagBinding polyplus$displayTag() {
        return polyplus$displayTagBinding;
    }

    @Override
    public void polyplus$setDisplayTag(DisplayTagBinding binding) {
        polyplus$displayTagBinding = binding;
    }
}
//?}
