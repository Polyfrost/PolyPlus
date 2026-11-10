package org.polyfrost.polyplus.client.bedrock.controller

import org.polyfrost.polyplus.client.bedrock.molang.MolangScalar
import org.polyfrost.polyplus.client.bedrock.molang.MolangScript

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
    val onEntry: List<MolangScript> = emptyList(),
    val onExit: List<MolangScript> = emptyList(),
    val transitions: List<ControllerTransition> = emptyList(),
)

data class StateAnimation(
    val animationName: String,
    val weight: MolangScalar = MolangScalar.ONE,
)

data class ControllerTransition(
    val targetState: String,
    val condition: MolangScalar,
)
