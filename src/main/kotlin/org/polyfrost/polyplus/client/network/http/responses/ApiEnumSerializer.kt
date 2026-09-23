package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes an API enum by its wire name. Anything the backend adds later is decoded as [unknown].
 */
internal class ApiEnumSerializer<T : Enum<T>>(
    name: String,
    entries: List<T>,
    private val unknown: T,
    private val wireName: (T) -> String,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    private val byName = entries.filterNot { it == unknown }.associateBy(wireName)

    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(wireName(value))

    override fun deserialize(decoder: Decoder): T = byName[decoder.decodeString()] ?: unknown
}
