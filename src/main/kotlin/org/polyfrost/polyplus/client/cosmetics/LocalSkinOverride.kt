package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.client.gui.preview.PreviewSkin

//? if >= 1.21.10 {
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?}

object LocalSkinOverride {
    //? if >= 1.21.10 {
    fun withSkin(original: PlayerSkin, skin: PreviewSkin): PlayerSkin = PlayerSkin(
        ClientAsset.ResourceTexture(skin.texture),
        original.cape(),
        original.elytra(),
        if (skin.slim) PlayerModelType.SLIM else PlayerModelType.WIDE,
        original.secure(),
    )
    //?} elif > 1.8.9 {
    /*fun withSkin(original: PlayerSkin, skin: PreviewSkin): PlayerSkin = PlayerSkin(
        skin.texture,
        null,
        original.capeTexture(),
        original.elytraTexture(),
        if (skin.slim) PlayerSkin.Model.SLIM else PlayerSkin.Model.WIDE,
        original.secure(),
    )
    *///?}
}
