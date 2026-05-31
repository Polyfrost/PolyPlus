plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1-fabric"

stonecutter parameters {
    constants {
        match(current.project.substringAfterLast("-"), "fabric")
    }
    replacements {
        string(eval(current.version, ">= 1.21.11"), "identifier") {
            replace("ResourceLocation", "Identifier")
        }
        regex(eval(current.version, "< 1.21.11")) {
            replace(
                "import net.minecraft.resources.Identifier(?!;)",
                "import net.minecraft.resources.ResourceLocation as Identifier",
                "import net.minecraft.resources.ResourceLocation as Identifier",
                "import net.minecraft.resources.Identifier",
            )
        }
    }
}
