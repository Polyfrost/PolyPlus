package org.polyfrost.polyplus.client.render

//? if >= 1.21.11 {
import net.minecraft.client.model.player.PlayerModel
//?}

//? if < 1.21.11 {
/*import net.minecraft.client.model.PlayerModel
*///?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}

//? if >= 1.21.11 {
typealias PolyPlayerModel = PlayerModel
//?} elif >= 1.21.4 {
/*typealias PolyPlayerModel = PlayerModel
*///?} else {
/*typealias PolyPlayerModel = PlayerModel<AbstractClientPlayer>
*///?}
