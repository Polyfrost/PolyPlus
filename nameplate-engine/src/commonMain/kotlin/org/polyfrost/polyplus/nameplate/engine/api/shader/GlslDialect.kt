package org.polyfrost.polyplus.nameplate.engine.api.shader

enum class GlslDialect(val defaultSamplers: GlslSamplers) {
    CORE_330(GlslSamplers.NAMED),
    ES_300(GlslSamplers.NAMED),
    CORE_330_UBO(GlslSamplers.NAMED),
    LEGACY_FLAT(GlslSamplers.NUMBERED),
    COMPAT_130(GlslSamplers.NAMED),
}

enum class GlslSamplers {
    NAMED,
    NUMBERED,
    ;

    fun name(unit: Int): String = when (this) {
        NAMED -> "$NAMED_PREFIX$unit"
        NUMBERED -> "Sampler${if (unit < LIGHTMAP_UNIT) unit else unit + 1}"
    }

    private companion object {
        internal const val NAMED_PREFIX = "NameplateSampler"
        const val LIGHTMAP_UNIT = 2
    }
}

enum class GlslGlobals {
    SCREEN_FIRST,
    CAMERA_FIRST,
}
