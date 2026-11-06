package org.polyfrost.polyplus.client.lobby

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

//? if >= 1.21.5 {
fun CompoundTag.optCompound(name: String): CompoundTag? = getCompound(name).orElse(null)

fun CompoundTag.optIntArray(name: String): IntArray? = getIntArray(name).orElse(null)

fun CompoundTag.optLongArray(name: String): LongArray? = getLongArray(name).orElse(null)

fun CompoundTag.optByteArray(name: String): ByteArray? = getByteArray(name).orElse(null)

fun CompoundTag.optByte(name: String): Byte? = getByte(name).orElse(null)

fun CompoundTag.optString(name: String): String? = getString(name).orElse(null)

fun CompoundTag.keyNames(): Set<String> = keySet()

fun ListTag.compounds(): List<CompoundTag> = compoundStream().toList()

fun Tag.compoundOrNull(): CompoundTag? = asCompound().orElse(null)

fun Tag.stringOrNull(): String? = asString().orElse(null)
//?} else {
/*private const val BYTE_TYPE = 1
private const val BYTE_ARRAY_TYPE = 7
private const val STRING_TYPE = 8
private const val COMPOUND_TYPE = 10
private const val INT_ARRAY_TYPE = 11
private const val LONG_ARRAY_TYPE = 12
private const val NUMERIC_TYPE = 99

fun CompoundTag.optCompound(name: String): CompoundTag? = if (contains(name, COMPOUND_TYPE)) getCompound(name) else null

fun CompoundTag.optIntArray(name: String): IntArray? = if (contains(name, INT_ARRAY_TYPE)) getIntArray(name) else null

fun CompoundTag.optByteArray(name: String): ByteArray? = if (contains(name, BYTE_ARRAY_TYPE)) getByteArray(name) else null

fun CompoundTag.optByte(name: String): Byte? = if (contains(name, BYTE_TYPE)) getByte(name) else null

fun CompoundTag.optString(name: String): String? = if (contains(name, STRING_TYPE)) getString(name) else null

//? if > 1.8.9 {
/^fun CompoundTag.optLongArray(name: String): LongArray? = if (contains(name, LONG_ARRAY_TYPE)) getLongArray(name) else null

fun CompoundTag.keyNames(): Set<String> = allKeys

fun ListTag.compounds(): List<CompoundTag> = filterIsInstance<CompoundTag>()

fun Tag.stringOrNull(): String? = if (id.toInt() == STRING_TYPE) asString else null

fun ListTag.getStringOr(index: Int, fallback: String): String = getOrNull(index)?.stringOrNull() ?: fallback
^///?} else {
/^fun CompoundTag.keyNames(): Set<String> = keys

fun ListTag.compounds(): List<CompoundTag> = List(size()) { getCompound(it) }

val ListTag.size: Int get() = size()

fun ListTag.add(element: Tag) = addElement(element)
^///?}

fun Tag.compoundOrNull(): CompoundTag? = this as? CompoundTag

fun CompoundTag.getIntOr(name: String, fallback: Int): Int = if (contains(name, NUMERIC_TYPE)) getInt(name) else fallback

fun CompoundTag.getLongOr(name: String, fallback: Long): Long = if (contains(name, NUMERIC_TYPE)) getLong(name) else fallback

fun CompoundTag.getFloatOr(name: String, fallback: Float): Float = if (contains(name, NUMERIC_TYPE)) getFloat(name) else fallback

fun CompoundTag.getDoubleOr(name: String, fallback: Double): Double = if (contains(name, NUMERIC_TYPE)) getDouble(name) else fallback

fun CompoundTag.getStringOr(name: String, fallback: String): String = optString(name) ?: fallback

fun CompoundTag.getListOrEmpty(name: String): ListTag = get(name) as? ListTag ?: ListTag()

fun CompoundTag.getCompoundOrEmpty(name: String): CompoundTag = optCompound(name) ?: CompoundTag()
*///?}
