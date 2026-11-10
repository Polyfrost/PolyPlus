package org.polyfrost.polyplus.nameplate.engine.api.issue

import org.polyfrost.polyplus.molang.MolangProblem
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles

enum class IssueLevel {
    ERROR,
    WARNING,
    INFO,
}

enum class IssueCode(val id: String, val level: IssueLevel = IssueLevel.ERROR) {
    MISSING_FILE("missing_file"),
    JSON_SIZE("json_size"),
    JSON_DEPTH("json_depth"),
    JSON_SYNTAX("json_syntax"),
    FORMAT("format"),
    MANIFEST("manifest"),
    LAYERS("layers"),
    LAYER("layer"),
    PALETTE("palette"),
    COLORWAY("colorway"),
    VARIANT("variant"),
    TEXT("text"),
    LETTER_PROGRAM("letter_program"),
    LETTER_OWNED("letter_owned"),
    LETTER_BUDGET("letter_budget"),
    LETTER_READABLE("letter_readable"),
    BADGE("badge"),
    CORNERS("corners"),
    CORNERS_BOTH("corners_both"),
    FLAGS("flags"),
    SPRITE("sprite"),
    SPRITE_FRAMES("sprite_frames"),
    PATH("path"),
    REPEAT("repeat"),
    WIDTHS("widths"),
    EMITTER("emitter"),
    ANIMATION("animation"),
    KEY_START("key_start"),
    KEY_ORDER("key_order"),
    KEY_RANGE("key_range"),
    SPEED_RANGE("speed_range"),
    SPEED_TRIGGER("speed_trigger"),
    SPEED_PATH("speed_path"),
    BURST_COUNT("burst_count"),
    BADGE_SCHEDULE("badge_schedule"),
    BADGE_CHANNEL("badge_channel"),
    BADGE_MOLANG("badge_molang"),
    GROUP("group"),
    REACTION("reaction"),
    REDUCED_WHILE("reduced_while"),
    REDUCED_CHOICE("reduced_choice"),
    PALETTE_ANIMATION("palette_animation"),
    PALETTE_CYCLE("palette_cycle"),
    PALETTE_MOLANG("palette_molang"),
    MOLANG("molang"),
    MOLANG_EMPTY("molang_empty"),
    MOLANG_UNEXPECTED("molang_unexpected"),
    MOLANG_TOO_LONG("molang_too_long"),
    MOLANG_TOO_DEEP("molang_too_deep"),
    MOLANG_TOO_BIG("molang_too_big"),
    MOLANG_UNTERMINATED("molang_unterminated"),
    MOLANG_CHARACTER("molang_character"),
    MOLANG_STRING("molang_string"),
    MOLANG_VALUE("molang_value"),
    MOLANG_UNCALLED("molang_uncalled"),
    MOLANG_UNKNOWN_NAME("molang_unknown_name"),
    MOLANG_NOT_QUERY("molang_not_query"),
    MOLANG_UNKNOWN_QUERY("molang_unknown_query"),
    MOLANG_QUERY_PLACE("molang_query_place"),
    MOLANG_ASSIGN("molang_assign"),
    MOLANG_VARIABLE_NAME("molang_variable_name"),
    MOLANG_VARIABLES("molang_variables"),
    MOLANG_UNKNOWN_FUNCTION("molang_unknown_function"),
    MOLANG_UNKNOWN_MATH("molang_unknown_math"),
    MOLANG_PARAM_CALL("molang_param_call"),
    MOLANG_UNKNOWN_PARAM("molang_unknown_param"),
    MOLANG_ARGUMENTS("molang_arguments"),
    MOLANG_COST("molang_cost"),
    MOLANG_CONSTANT("molang_constant"),
    MATERIAL("material"),
    MATERIAL_LOOP("material_loop"),
    MATERIAL_NODES("material_nodes"),
    MATERIAL_COST("material_cost"),
    MATERIAL_SAMPLES("material_samples"),
    MATERIAL_INPUTS("material_inputs"),
    MATERIAL_OUTPUTS("material_outputs"),
    MATERIAL_CYCLE("material_cycle"),
    MATERIAL_MARGINS("material_margins"),
    NODE_ID("node_id"),
    NODE_DUPLICATE("node_duplicate"),
    NODE_TYPE("node_type"),
    NODE_INPUT("node_input"),
    NODE_LINK("node_link"),
    NODE_OUTPUT_LINK("node_output_link"),
    NODE_LITERAL("node_literal"),
    NODE_REQUIRED("node_required"),
    NODE_SETTING("node_setting"),
    NODE_PARAM("node_param"),
    NODE_TEXTURE("node_texture"),
    NODE_SPRITE("node_sprite"),
    NODE_TYPING("node_typing"),
    NODE_REPEAT("node_repeat"),
    TEXTURE("texture"),
    TEXTURE_FORMAT("texture_format"),
    TEXTURE_SIZE("texture_size"),
    LAYOUT("layout"),
    MASK("mask"),
    BODY_ART("body_art"),
    ANCHOR("anchor"),
    FOLLOW("follow"),
    PATH_TIMING("path_timing"),
    NAME_TARGET("name_target"),
    PLATE_CHANNEL("plate_channel"),
    OVER_CPU("over_cpu"),
    OVER_QUADS("over_quads"),
    OVER_CACHE("over_cache"),
    OVER_MARGIN("over_margin"),
    OVER_STATES("over_states"),
    FLASH("flash"),
    READABILITY("readability", IssueLevel.WARNING),
    BADGE_HIDDEN("badge_hidden", IssueLevel.WARNING),
    BADGE_FADED("badge_faded", IssueLevel.WARNING),
    BADGE_COVERED("badge_covered", IssueLevel.WARNING),
    ART_COVERAGE("art_coverage", IssueLevel.WARNING),
    FIXED_HEIGHT("fixed_height", IssueLevel.WARNING),
    TRANSLUCENT_OVERLAP("translucent_overlap", IssueLevel.WARNING),
    POSITION_CLAMPED("position_clamped", IssueLevel.WARNING),
    PLATE_BACKGROUND("plate_background", IssueLevel.WARNING),
    FALLBACK_CACHE("fallback_cache", IssueLevel.INFO),
    FALLBACK_STILL("fallback_still", IssueLevel.INFO),
    FALLBACK_REBUILD("fallback_rebuild", IssueLevel.INFO),
    FALLBACK_SHOTS("fallback_shots", IssueLevel.INFO),
    ;

    companion object {
        private val BY_ID: Map<String, IssueCode> = entries.associateBy { it.id }

        fun byId(id: String): IssueCode? = BY_ID[id]

        fun of(problem: MolangProblem): IssueCode = BY_ID["${MOLANG.id}_${problem.id}"] ?: MOLANG

        fun of(path: String): IssueCode {
            val file = path.substringBefore('#')
            val field = path.substringAfter('#', "")
            return when {
                file.startsWith("textures/") -> TEXTURE
                file.startsWith("materials") -> MATERIAL
                file.startsWith("sprites/") -> SPRITE
                file.startsWith(BundleFiles.FLAGS) -> FLAGS
                file.startsWith("layers") -> layerField(field)
                file == BundleFiles.PALETTES -> if (field.startsWith("colorways")) COLORWAY else PALETTE
                file == BundleFiles.MANIFEST -> manifestField(field)
                else -> FORMAT
            }
        }

        private fun layerField(field: String): IssueCode = when (head(field)) {
            "path", "walkers" -> PATH
            "repeat" -> REPEAT
            "breakpoints" -> WIDTHS
            "corners" -> CORNERS
            "timeline", "mirrorDelay" -> ANIMATION
            "art" -> BODY_ART
            "anchor", "at" -> ANCHOR
            "follow" -> FOLLOW
            else -> LAYER
        }

        private fun manifestField(field: String): IssueCode = when (head(field)) {
            "format" -> FORMAT
            "layers" -> if (field.endsWith(".mask")) MASK else LAYERS
            "text" -> TEXT
            "badge" -> BADGE
            "animations" -> animationField(field)
            "groups" -> if (".breakpoints" in field) WIDTHS else GROUP
            "variants" -> when (field.split('.').getOrNull(2)?.let(::head)) {
                "text" -> TEXT
                "badge" -> BADGE
                else -> VARIANT
            }
            else -> MANIFEST
        }

        private fun animationField(field: String): IssueCode {
            val rest = field.split('.').drop(2)
            return when {
                rest.firstOrNull()?.let(::head) in REACTION_FIELDS -> REACTION
                rest.getOrNull(0) == "tracks" && rest.getOrNull(1) == "palette" -> PALETTE_ANIMATION
                else -> ANIMATION
            }
        }

        private val REACTION_FIELDS = setOf("trigger", "reduced", "restart")

        private fun head(field: String): String = field.takeWhile { it != '.' && it != '[' && it != ' ' }
    }
}
