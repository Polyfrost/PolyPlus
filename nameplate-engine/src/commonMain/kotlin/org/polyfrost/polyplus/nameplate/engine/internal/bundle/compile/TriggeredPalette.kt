package org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.PaletteView
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Triggers
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.track.BundleMolang
import org.polyfrost.polyplus.nameplate.engine.internal.track.PaletteJson

internal object TriggeredPalette {
    fun compile(bundle: NameplateBundle, triggers: Triggers): Array<PaletteView?> {
        val shots = triggers.oneShots.map { view(bundle, it.palette, it.length) }
        val whiles = triggers.whiles.map { view(bundle, it.palette, bundle.loop) }
        return (shots + whiles).toTypedArray()
    }

    private fun view(bundle: NameplateBundle, palette: PaletteJson, length: Int): PaletteView? {
        if (palette.isEmpty()) return null

        val manifest = ManifestJson(
            format = 1,
            id = bundle.id,
            fps = bundle.fps,
            loop = length,
            animations = mapOf(AnimationCompiler.LOOP to AnimationJson(tracks = palette)),
        )
        val tracks = PaletteTracks.load(manifest, bundle.palette, BundleMolang(emptyList()))
        if (tracks.isEmpty()) return null

        val view = NameplateBundle(
            id = bundle.id,
            requires = emptySet(),
            fps = bundle.fps,
            loop = length,
            ownsBackground = false,
            params = emptyMap(),
            layers = emptyList(),
            palette = bundle.palette,
            flags = emptyList(),
            variants = emptyMap(),
            paletteTracks = tracks,
        )
        return PaletteView(view, tracks.map { it.slot }.distinct().toIntArray())
    }
}
