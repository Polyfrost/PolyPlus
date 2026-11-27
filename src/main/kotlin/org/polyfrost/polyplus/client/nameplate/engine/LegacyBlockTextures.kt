package org.polyfrost.polyplus.client.nameplate.engine

object LegacyBlockTextures {
    private val woods = mapOf(
        "oak" to "oak",
        "spruce" to "spruce",
        "birch" to "birch",
        "jungle" to "jungle",
        "acacia" to "acacia",
        "dark_oak" to "big_oak",
    )

    private val colors = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
    )

    private val renamed = mapOf(
        "prismarine" to "prismarine_rough",
        "dark_prismarine" to "prismarine_dark",
        "stone_bricks" to "stonebrick",
        "mossy_stone_bricks" to "stonebrick_mossy",
        "cracked_stone_bricks" to "stonebrick_cracked",
        "chiseled_stone_bricks" to "stonebrick_carved",
        "bricks" to "brick",
        "nether_bricks" to "nether_brick",
        "grass_block_top" to "grass_top",
        "grass_block_side" to "grass_side",
        "smooth_stone" to "stone_slab_top",
        "granite" to "stone_granite",
        "polished_granite" to "stone_granite_smooth",
        "diorite" to "stone_diorite",
        "polished_diorite" to "stone_diorite_smooth",
        "andesite" to "stone_andesite",
        "polished_andesite" to "stone_andesite_smooth",
        "mossy_cobblestone" to "cobblestone_mossy",
        "sandstone" to "sandstone_normal",
        "chiseled_sandstone" to "sandstone_carved",
        "cut_sandstone" to "sandstone_smooth",
        "red_sandstone" to "red_sandstone_normal",
        "chiseled_red_sandstone" to "red_sandstone_carved",
        "cut_red_sandstone" to "red_sandstone_smooth",
        "terracotta" to "hardened_clay",
        "packed_ice" to "ice_packed",
        "spawner" to "mob_spawner",
        "redstone_lamp" to "redstone_lamp_off",
        "jack_o_lantern" to "pumpkin_face_on",
        "carved_pumpkin" to "pumpkin_face_off",
        "slime_block" to "slime",
        "snow_block" to "snow",
        "torch" to "torch_on",
        "dark_oak_sapling" to "sapling_roofed_oak",
        "chiseled_quartz_block" to "quartz_block_chiseled",
        "quartz_pillar" to "quartz_block_lines",
        "nether_quartz_ore" to "quartz_ore",
    )

    fun name(modern: String): String {
        renamed[modern]?.let { return it }
        for ((wood, legacy) in woods) {
            when (modern) {
                "${wood}_planks" -> return "planks_$legacy"
                "${wood}_log" -> return "log_$legacy"
                "${wood}_log_top" -> return "log_${legacy}_top"
                "${wood}_leaves" -> return "leaves_$legacy"
                "${wood}_sapling" -> return "sapling_$legacy"
            }
        }
        for (color in colors) {
            val legacy = if (color == "light_gray") "silver" else color
            when (modern) {
                "${color}_wool" -> return "wool_colored_$legacy"
                "${color}_stained_glass" -> return "glass_$legacy"
                "${color}_terracotta" -> return "hardened_clay_stained_$legacy"
            }
        }
        return modern.replace("light_gray", "silver")
    }
}
