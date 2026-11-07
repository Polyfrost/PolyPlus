package org.polyfrost.polyplus.client.lobby

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.EyesLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.PoseStack
//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
//?} else {
/*import net.minecraft.client.renderer.RenderType
*///?}

class LobbyHerobrineLayer(renderer: RenderLayerParent<AvatarRenderState, PlayerModel>) : EyesLayer<AvatarRenderState, PlayerModel>(renderer) {
    override fun submit(
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (LobbyHerobrine.shows(state.id)) super.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot)
    }

    override fun renderType(): RenderType = EYES

    private companion object {
        //? if >= 1.21.11 {
        val EYES: RenderType by lazy { RenderTypes.eyes(LobbyHerobrine.EYES) }
        //?} else {
        /*val EYES: RenderType by lazy { RenderType.eyes(LobbyHerobrine.EYES) }
        *///?}
    }
}
//?}
