//? if >= 1.21.10 {
package org.polyfrost.polyplus.client.cosmetics.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.EntityModelSet
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.CapeLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.resources.model.EquipmentAssetManager
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel
//?} else {
/*import net.minecraft.client.model.PlayerModel
*///?}

class PreviewCapeLayer(
    parent: RenderLayerParent<AvatarRenderState, PlayerModel>,
    models: EntityModelSet,
    equipment: EquipmentAssetManager,
) : CapeLayer(parent, models, equipment) {
    override fun submit(
        pose: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (PlayerPreviewRenderer.isDirectPreview()) super.submit(pose, collector, light, state, yRot, xRot)
    }
}
//?}
