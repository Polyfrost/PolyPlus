package org.polyfrost.polyplus.client.network.eos

data class EosP2PSocketId(val name: String) {
    init {
        require(name.length in 1..32) { "EOS P2P socket names must be 1-32 characters" }
    }
}
