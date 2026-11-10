import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.detekt")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    if (providers.gradleProperty("nameplate.js").isPresent) {
        js {
            browser()
            binaries.library()
            generateTypeScriptDefinitions()
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":molang"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("deps.kotlinx_serialization")}")
        }
    }
}

tasks.named<Detekt>("detekt") {
    enabled = false
    dependsOn(tasks.withType<Detekt>().matching { it.name != "detekt" && !it.name.endsWith("SourceSet") })
}
