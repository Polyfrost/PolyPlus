package org.polyfrost.polyplus.nameplate.engine.internal.material

internal object NodeCatalog {
    val kinds: Map<String, NodeKind> = (
        InputNodes.kinds() + MathNodes.kinds() + ColorNodes.kinds() + listOf(
            CombineKind,
            ComponentKind,
            SelectKind,
            NoiseKind("Noise", fractal = false),
            NoiseKind("Fbm", fractal = true),
            HashKind,
            BayerKind,
            CheckerKind,
            StripesKind,
            GradientKind,
            SampleAtKind,
            RepeatKind,
            EnvInputKind("RepeatIndex", SocketType.FLOAT, { c, env, o -> c.alias(o, env.index) }, "index"),
            SpriteKind,
            TextureKind("BundleTexture", TextureKind.Source.BUNDLE),
            TextureKind("VanillaTexture", TextureKind.Source.VANILLA),
            TextureKind("AtlasSprite", TextureKind.Source.ATLAS),
            OutputKind,
        )
        ).associateBy { it.type }
}
