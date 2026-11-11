package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
//? if > 1.8.9 {
import net.minecraft.core.HolderGetter
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.StringTag
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.util.Mth
import net.minecraft.util.SimpleBitStorage
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.material.FluidState
//?} else {
/*import net.minecraft.nbt.NbtReadLimiter
import net.minecraft.util.math.Direction
import net.minecraft.world.biome.Biome
import net.minecraft.world.gen.WorldGeneratorType
import java.nio.ByteBuffer
*///?}
import net.minecraft.core.SectionPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.Tag
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.DataLayer
import net.minecraft.world.level.chunk.LevelChunkSection
import org.polyfrost.polyplus.client.party.PartyScene
import java.io.ByteArrayOutputStream
//? if = 1.8.9
/*import java.io.DataInputStream*/
import java.io.DataOutputStream
//? if = 1.8.9
/*import java.io.FilterInputStream*/
//? if = 1.8.9
/*import java.io.IOException*/
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
//? if = 1.8.9
/*import java.util.zip.GZIPInputStream*/
import java.util.zip.GZIPOutputStream

//? if > 1.8.9 {
enum class LobbyPrivacy(val kept: Set<String>) {
    OWN_WORLD(setOf("id", "x", "y", "z", "front_text", "back_text", "is_waxed", "patterns", "sherds", "blockState", "facing", "progress", "extending", "source")),
    SERVER(setOf("id", "x", "y", "z", "patterns", "sherds", "blockState", "facing", "progress", "extending", "source")),
}
//?} else {
/*enum class LobbyPrivacy(val kept: Set<String>) {
    OWN_WORLD(setOf("id", "x", "y", "z", "Text1", "Text2", "Text3", "Text4", "Base", "Patterns", "Item", "Data", "SkullType", "Rot", "blockId", "blockData", "facing", "progress", "extending")),
    SERVER(setOf("id", "x", "y", "z", "Base", "Patterns", "Item", "Data", "SkullType", "Rot", "blockId", "blockData", "facing", "progress", "extending")),
}
*///?}

class LobbySectionCopy(val y: Int, val section: LevelChunkSection, val blockLight: DataLayer?, val skyLight: DataLayer?)

class LobbyChunkCopy(val x: Int, val z: Int) {
    val sections = ArrayList<LobbySectionCopy>()
    val blockEntities = ListTag()
    //? if = 1.8.9
    /*var biomes = ByteArray(0)*/
}

class LobbyCopyView(chunks: Collection<LobbyChunkCopy>, private val minY: Int, private val height: Int) : BlockGetter {
    private val sections = HashMap<Long, Array<LevelChunkSection?>>()

    init {
        val minSection = SectionPos.blockToSectionCoord(minY)
        val count = height shr SectionPos.SECTION_BITS
        for (chunk in chunks) {
            val column = arrayOfNulls<LevelChunkSection>(count)
            for (section in chunk.sections) column[section.y - minSection] = section.section
            sections[LobbyCompat.chunkKey(chunk.x, chunk.z)] = column
        }
    }

    override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

    //? if = 1.8.9 {
    /*override fun getLightColor(pos: BlockPos, blockLight: Int): Int = 0

    override fun isAir(pos: BlockPos): Boolean = getBlockState(pos).isAir

    override fun getBiome(pos: BlockPos): Biome = Biome.PLAINS

    override fun isEmpty(): Boolean = false

    override fun getDirectSignal(pos: BlockPos, dir: Direction): Int = 0

    override fun getGeneratorType(): WorldGeneratorType = WorldGeneratorType.DEFAULT

    *///?}
    override fun getBlockState(pos: BlockPos): BlockState {
        val index = (pos.y - minY) shr SectionPos.SECTION_BITS
        val column = sections[LobbyCompat.chunkKey(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))]
        val section = column?.getOrNull(index) ?: return Blocks.AIR.defaultBlockState()
        return section.getBlockState(pos.x and SECTION_MASK, pos.y and SECTION_MASK, pos.z and SECTION_MASK)
    }

    //? if > 1.8.9 {
    override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

    override fun getHeight(): Int = height
    //?}

    //? if >= 1.21.4 {
    override fun getMinY(): Int = minY
    //?} elif > 1.8.9 {
    /*override fun getMinBuildHeight(): Int = minY
    *///?}

    private companion object {
        const val SECTION_MASK = 15
    }
}

object LobbySnapshot {
    //? if > 1.8.9 {
    const val FORMAT = 4
    private val READABLE = intArrayOf(2, FORMAT)
    //?} else {
    /*const val FORMAT = 3
    private val READABLE = intArrayOf(FORMAT)
    *///?}
    const val DEFAULT_RADIUS = 6
    const val MIN_RADIUS = 4
    const val BELOW_ANCHOR = 16
    const val ABOVE_ANCHOR = 48
    //? if > 1.8.9 {
    private const val FALLBACK_BIOME = "minecraft:plains"
    private const val SECTION_CELLS = 4096
    private const val MIN_PALETTE_BITS = 4
    private const val LONG_BITS = 64
    private const val SECTION_AXIS = 15
    private const val SECTION_SHIFT = 4
    //?}

    val dataVersion: Int
        get() = LobbyCompat.dataVersion()

    class Meta(
        val anchor: BlockPos,
        val clock: Long,
        val radius: Int,
        val minSection: Int,
        val maxSection: Int,
        val biome: String,
        val privacy: LobbyPrivacy,
        val stage: CompoundTag?,
        val packs: List<LobbyScenePack> = emptyList(),
        val entities: ListTag? = null,
        val dimension: String = "",
    )

    //? if = 1.8.9 {
    /*fun copySection(level: ClientLevel, pos: ChunkPos, sectionY: Int, section: LevelChunkSection): LobbySectionCopy {
        val copy = LevelChunkSection(sectionY shl SectionPos.SECTION_BITS, true)
        copy.blockStates = section.blockStates.clone()
        copy.validateBlockCounters()
        return LobbySectionCopy(sectionY, copy, DataLayer(section.blockLightStorage.data.clone()), section.skyLightStorage?.let { DataLayer(it.data.clone()) })
    }

    fun encode(chunks: List<LobbyChunkCopy>, meta: Meta): CompoundTag {
        val list = ListTag()
        chunks.forEach { list.add(encodeChunk(it)) }
        return header(list, meta)
    }

    private fun encodeChunk(chunk: LobbyChunkCopy): CompoundTag {
        val sections = ListTag()
        chunk.sections.forEach { copy ->
            sections.add(
                CompoundTag().apply {
                    putInt("y", copy.y)
                    if (!copy.section.isEmpty) putByteArray("blocks", packBlocks(copy.section.blockStates))
                    putLight("block_light", copy.blockLight)
                    putLight("sky_light", copy.skyLight)
                },
            )
        }
        return CompoundTag().apply {
            putInt("x", chunk.x)
            putInt("z", chunk.z)
            putByteArray("biomes", chunk.biomes)
            put("sections", sections)
            put("block_entities", chunk.blockEntities)
        }
    }

    private fun packBlocks(states: CharArray): ByteArray {
        val bytes = ByteBuffer.allocate(states.size * Char.SIZE_BYTES)
        bytes.asCharBuffer().put(states)
        return bytes.array()
    }

    fun unpackBlocks(bytes: ByteArray): CharArray {
        val states = CharArray(bytes.size / Char.SIZE_BYTES)
        ByteBuffer.wrap(bytes).asCharBuffer().get(states)
        return states
    }
    *///?} else {
    fun copySection(level: ClientLevel, pos: ChunkPos, sectionY: Int, section: LevelChunkSection): LobbySectionCopy {
        val lightPos = SectionPos.of(pos, sectionY)
        val engine = level.lightEngine
        return LobbySectionCopy(
            sectionY,
            LobbyCompat.copySection(section),
            engine.getLayerListener(LightLayer.BLOCK).getDataLayerData(lightPos)?.copy(),
            engine.getLayerListener(LightLayer.SKY).getDataLayerData(lightPos)?.copy(),
        )
    }
    //?}

    fun clean(tag: CompoundTag, privacy: LobbyPrivacy): CompoundTag {
        val keys = tag.keyNames().filterNot { it in privacy.kept }
        keys.forEach(tag::remove)
        return tag
    }

    //? if > 1.8.9 {
    fun encode(chunks: List<LobbyChunkCopy>, meta: Meta, factory: LobbyContainers): CompoundTag {
        val list = ListTag()
        chunks.forEach { list.add(encodeChunk(it, factory)) }
        return header(list, meta)
    }
    //?}

    private fun header(list: ListTag, meta: Meta): CompoundTag =
        CompoundTag().apply {
            putInt("format", FORMAT)
            putInt("data_version", dataVersion)
            putIntArray("anchor", intArrayOf(meta.anchor.x, meta.anchor.y, meta.anchor.z))
            putLong("time", meta.clock)
            putInt("radius", meta.radius)
            putInt("min_section", meta.minSection)
            putInt("max_section", meta.maxSection)
            putString("biome", meta.biome)
            putString("privacy", meta.privacy.name)
            if (meta.dimension.isNotEmpty()) putString("dimension", meta.dimension)
            meta.stage?.let { put("stage", it) }
            meta.entities?.let { if (it.size > 0) put("entities", it) }
            if (meta.packs.isNotEmpty()) {
                put("packs", LobbyPacks.write(meta.packs))
                putString("game_version", PartyScene.mcVersion)
            }
            put("chunks", list)
        }

    //? if > 1.8.9 {
    private fun encodeChunk(chunk: LobbyChunkCopy, factory: LobbyContainers): CompoundTag {
        val sections = ListTag()
        chunk.sections.forEach { copy ->
            sections.add(
                CompoundTag().apply {
                    putInt("y", copy.y)
                    if (!copy.section.hasOnlyAir()) {
                        put("blocks", factory.blockCodec.encodeStart(NbtOps.INSTANCE, copy.section.states).getOrThrow())
                    }
                    put("biomes", factory.biomeCodec.encodeStart(NbtOps.INSTANCE, copy.section.biomes).getOrThrow())
                    putLight("block_light", copy.blockLight)
                    putLight("sky_light", copy.skyLight)
                },
            )
        }
        return CompoundTag().apply {
            putInt("x", chunk.x)
            putInt("z", chunk.z)
            put("sections", sections)
            put("block_entities", chunk.blockEntities)
        }
    }
    //?}

    private fun CompoundTag.putLight(name: String, layer: DataLayer?) {
        if (layer == null) return
        val bytes = layer.data
        val first = bytes[0].toInt()
        if ((first and 0xF) == ((first shr 4) and 0xF) && bytes.all { it == bytes[0] }) {
            putByte("${name}_fill", (bytes[0].toInt() and 0xF).toByte())
        } else {
            putByteArray(name, bytes)
        }
    }

    fun readLight(section: CompoundTag, name: String): DataLayer? =
        section.optByteArray(name)?.let { DataLayer(it.clone()) } ?: section.optByte("${name}_fill")?.let(::filledLight)

    //? if > 1.8.9 {
    private fun filledLight(value: Byte) = DataLayer(value.toInt())

    fun knownBiomes(biomes: CompoundTag, lookup: HolderLookup<Biome>): CompoundTag {
        val palette = biomes.getListOrEmpty("palette")
        val fixed = ListTag()
        var changed = false
        for (index in 0 until palette.size) {
            val name = palette.getStringOr(index, FALLBACK_BIOME)
            val known = Identifier.tryParse(name)?.let { lookup.get(ResourceKey.create(Registries.BIOME, it)).isPresent } == true
            fixed.add(StringTag.valueOf(if (known) name else FALLBACK_BIOME))
            changed = changed || !known
        }
        if (!changed) return biomes
        return biomes.copy().apply { put("palette", fixed) }
    }

    fun foreignBlocks(blocks: CompoundTag, target: PalettedContainer<BlockState>, lookup: HolderGetter<Block>): PalettedContainer<BlockState> {
        val entries = blocks.getListOrEmpty("palette")
        val palette = List(entries.size) { paletteState(entries.get(it), lookup) }
        val first = palette.firstOrNull() ?: return target
        val data = blocks.optLongArray("data")
        val storage = data?.let { SimpleBitStorage(paletteBits(palette.size, it.size), SECTION_CELLS, it) }
        for (index in 0 until SECTION_CELLS) {
            val state = if (storage != null) palette[storage.get(index)] else first
            target.set(index and SECTION_AXIS, (index shr (SECTION_SHIFT * 2)) and SECTION_AXIS, (index shr SECTION_SHIFT) and SECTION_AXIS, state)
        }
        return target
    }

    //? if < 1.21.5 {
    /*private val STAND_INS = mapOf(
        "minecraft:bush" to Blocks.FERN,
        "minecraft:leaf_litter" to Blocks.AIR,
        "minecraft:cactus_flower" to Blocks.AIR,
        "minecraft:short_dry_grass" to Blocks.DEAD_BUSH,
        "minecraft:tall_dry_grass" to Blocks.DEAD_BUSH,
    )

    *///?}
    private fun paletteBits(size: Int, longs: Int): Int {
        val least = maxOf(MIN_PALETTE_BITS, Mth.ceillog2(size))
        return (least..LONG_BITS).first { bits -> (SECTION_CELLS + LONG_BITS / bits - 1) / (LONG_BITS / bits) == longs }
    }

    private fun paletteState(tag: Tag, lookup: HolderGetter<Block>): BlockState {
        val entry = tag.compoundOrNull()
        val name = tag.stringOrNull() ?: entry?.optString("") ?: entry?.optString("id") ?: entry?.optString("Name")
        //? if < 1.21.5
        /*STAND_INS[name]?.let { return it.defaultBlockState() }*/
        val holder = name?.let(Identifier::tryParse)?.let { lookup.get(ResourceKey.create(Registries.BLOCK, it)).orElse(null) }
        val block = holder?.value() ?: return Blocks.STONE.defaultBlockState()
        val properties = entry?.optCompound("properties") ?: entry?.optCompound("Properties")
        var state = block.defaultBlockState()
        properties?.keyNames()?.forEach { key ->
            val property = block.stateDefinition.getProperty(key) ?: return@forEach
            properties.optString(key)?.let { state = withValue(state, property, it) }
        }
        return state
    }

    private fun <T : Comparable<T>> withValue(state: BlockState, property: Property<T>, value: String): BlockState =
        property.getValue(value).map { state.setValue(property, it) }.orElse(state)
    //?} else {
    /*private fun filledLight(value: Byte): DataLayer {
        val packed = (value.toInt() and 0xF) * 0x11
        return DataLayer(ByteArray(LIGHT_BYTES) { packed.toByte() })
    }

    private const val LIGHT_BYTES = 2048
    *///?}

    fun compress(root: CompoundTag): ByteArray {
        val bytes = ByteArrayOutputStream()
        val gzip = object : GZIPOutputStream(bytes) {
            init {
                def.setLevel(Deflater.BEST_COMPRESSION)
            }
        }
        DataOutputStream(gzip).use { NbtIo.write(root, it) }
        return bytes.toByteArray()
    }

    fun read(path: Path): CompoundTag = Files.newInputStream(path).use(::read)

    //? if > 1.8.9 {
    fun read(stream: InputStream): CompoundTag = NbtIo.readCompressed(stream, NbtAccounter.create(MAX_NBT_BYTES))
    //?} else {
    /*fun read(stream: InputStream): CompoundTag =
        DataInputStream(CappedStream(GZIPInputStream(stream))).use { NbtIo.read(it) }

    private class CappedStream(source: InputStream) : FilterInputStream(source) {
        private var total = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) count(it) }

        private fun count(n: Int) {
            total += n
            if (total > MAX_NBT_BYTES) throw IOException("scene data is too large")
        }
    }
    *///?}

    private const val MAX_NBT_BYTES = 64L * 1024L * 1024L

    fun withDimension(entry: LobbySceneEntry, root: CompoundTag): LobbySceneEntry {
        if (entry.dimension.isNotEmpty()) return entry
        val dimension = root.getStringOr("dimension", "")
        return if (dimension.isEmpty()) entry else entry.copy(dimension = dimension)
    }

    fun native(root: CompoundTag): Boolean = root.getIntOr("data_version", 0) == dataVersion

    fun compatible(root: CompoundTag, bundled: Boolean = false): Boolean =
        root.getIntOr("format", 0) in READABLE && (bundled || native(root)) && packProblem(root) == null

    private fun packProblem(root: CompoundTag): String? =
        LobbyPacks.problem(LobbyPacks.read(root.getListOrEmpty("packs")), root.getStringOr("game_version", ""))
}
