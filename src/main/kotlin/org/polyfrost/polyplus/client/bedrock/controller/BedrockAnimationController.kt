//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.controller

import org.polyfrost.polyplus.client.bedrock.molang.MolangExpr
import org.polyfrost.polyplus.client.bedrock.molang.MolangStatement

data class BedrockAnimationControllerFile(
    val controllers: Map<String, BedrockAnimationController>,
)

data class BedrockAnimationController(
    val name: String,
    val initialState: String,
    val states: Map<String, ControllerState>,
)

data class ControllerState(
    val name: String,
    val animations: List<StateAnimation>,
    val onEntry: List<MolangStatement> = emptyList(),
    val onExit: List<MolangStatement> = emptyList(),
    val transitions: List<ControllerTransition> = emptyList(),
)

data class StateAnimation(
    val animationName: String,
    val weight: MolangExpr = MolangExpr.Number(1.0),
)

data class ControllerTransition(
    val targetState: String,
    val condition: MolangExpr,
)
//?}
