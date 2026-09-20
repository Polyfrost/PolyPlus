plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3-fabric"

stonecutter handlers {
    inherit("json5", "json")
}

stonecutter parameters {
    constants {
        match(current.project.substringAfterLast("-"), "fabric", "ornithe")
    }
    replacements {
        string(eval(current.version, ">= 1.21.11"), "identifier") {
            replace("ResourceLocation", "Identifier")
        }
        regex(eval(current.version, "< 1.21.11") && current.version != "1.8.9") {
            replace(
                "import net.minecraft.resources.Identifier(?!;)",
                "import net.minecraft.resources.ResourceLocation as Identifier",
                "import net.minecraft.resources.ResourceLocation as Identifier",
                "import net.minecraft.resources.Identifier",
            )
        }
        regex(eval(current.version, "= 1.8.9")) {
            replace("net\\.minecraft\\.resources\\.Identifier", "net.minecraft.resource.Identifier", "net\\.minecraft\\.resource\\.Identifier", "net.minecraft.resources.Identifier")
            replace("\\bIdentifier\\.fromNamespaceAndPath\\(", "Identifier(", "\\bIdentifier\\(", "Identifier.fromNamespaceAndPath(")
        }
        string(eval(current.version, "= 1.8.9")) {
            replace("com.mojang.blaze3d.platform.InputConstants", "org.polyfrost.oneconfig.internal.legacy.InputConstants")
            replace("net.minecraft.network.chat.contents.TranslatableContents", "org.polyfrost.oneconfig.internal.legacy.chat.TranslatableContents")
            replace("net.minecraft.network.chat.contents.PlainTextContents", "org.polyfrost.oneconfig.internal.legacy.chat.PlainTextContents")
            replace("net.minecraft.network.chat.", "org.polyfrost.oneconfig.internal.legacy.chat.")
            replace("net.minecraft.util.FormattedCharSequence", "org.polyfrost.oneconfig.internal.legacy.chat.FormattedCharSequence")
            replace("net.minecraft.ChatFormatting", "org.polyfrost.oneconfig.internal.legacy.chat.ChatFormatting")
            replace("net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback", "org.polyfrost.oneconfig.internal.legacy.command.ClientCommandRegistrationCallback")
            replace("net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource", "org.polyfrost.oneconfig.internal.legacy.command.FabricClientCommandSource")
            replace("net.minecraft.client.User", "org.polyfrost.oneconfig.internal.legacy.User")
            replace("net.minecraft.client.resources.sounds.SimpleSoundInstance", "org.polyfrost.oneconfig.internal.legacy.SimpleSoundInstance")
            replace("net.minecraft.sounds.SoundEvents", "org.polyfrost.oneconfig.internal.legacy.SoundEvents")
            replace("net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.screen.Screen")
            replace("net.minecraft.client.gui.screens.TitleScreen", "net.minecraft.client.gui.screen.TitleScreen")
            replace("net.minecraft.client.gui.screens.ConnectScreen", "net.minecraft.client.gui.screen.ConnectScreen")
            replace("net.minecraft.client.gui.screens.DisconnectedScreen", "net.minecraft.client.gui.screen.DisconnectedScreen")
            replace("net.minecraft.CrashReport", "net.minecraft.util.crash.CrashReport")
            replace("net.minecraft.server.Bootstrap", "net.minecraft.Bootstrap")
            replace("import net.minecraft.util.Mth\n", "import net.minecraft.util.math.MathHelper as Mth\n")
        }
    }
}

stonecutter tasks {
    order("runClient")
}
