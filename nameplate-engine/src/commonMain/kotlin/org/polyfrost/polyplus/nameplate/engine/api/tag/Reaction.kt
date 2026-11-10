package org.polyfrost.polyplus.nameplate.engine.api.tag

enum class Reaction(val id: String) {
    SNEAKING("sneaking"),
    SPRINTING("sprinting"),
    HURT("hurt"),
    ON_FIRE("on_fire"),
    SWIMMING("swimming"),
    GLIDING("gliding"),
    RIDING("riding"),
    SLEEPING("sleeping"),
    EMOTE("emote"),
    ;

    val bit: Int
        get() = 1 shl ordinal

    companion object {
        fun of(id: String): Reaction? = entries.firstOrNull { it.id == id }
    }
}

class TagInputs private constructor(val mask: Int) {
    val isEmpty: Boolean
        get() = mask == 0

    fun has(reaction: Reaction): Boolean = mask and reaction.bit != 0

    fun with(reaction: Reaction, on: Boolean = true): TagInputs =
        ofMask(if (on) mask or reaction.bit else mask and reaction.bit.inv())

    fun reactions(): List<Reaction> = Reaction.entries.filter(::has)

    override fun equals(other: Any?): Boolean = other is TagInputs && other.mask == mask

    override fun hashCode(): Int = mask

    override fun toString(): String = "TagInputs(${reactions().joinToString { it.id }})"

    companion object {
        private val ALL: Int = (1 shl Reaction.entries.size) - 1

        val NONE: TagInputs = TagInputs(0)

        fun ofMask(mask: Int): TagInputs = if (mask and ALL == 0) NONE else TagInputs(mask and ALL)

        fun of(vararg reactions: Reaction): TagInputs = ofMask(reactions.fold(0) { mask, reaction -> mask or reaction.bit })
    }
}
