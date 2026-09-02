package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.LanguageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.io.PrintWriter;
import java.io.StringWriter;

@Mixin(Minecraft.class)
public class MixinMinecraftSystemReport {
    @Unique
    private static final Logger POLYPLUS_LOGGER = LoggerFactory.getLogger("polyplus/crash-details");

    @WrapMethod(
        method = "fillReport(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/resources/language/LanguageManager;Ljava/lang/String;Lnet/minecraft/client/Options;Lnet/minecraft/CrashReport;)V"
    )
    private static void polyplus$guardSystemDetails(
        Minecraft minecraft,
        LanguageManager languageManager,
        String launchedVersion,
        Options options,
        CrashReport report,
        Operation<Void> original
    ) {
        try {
            original.call(minecraft, languageManager, launchedVersion, options, report);
        } catch (Throwable t) {
            POLYPLUS_LOGGER.error("Failed to collect client system details for the crash report", t);
            report.addCategory("System Details Failure").setDetail("Error", polyplus$stackTrace(t));
        }
    }

    @Unique
    private static String polyplus$stackTrace(Throwable t) {
        StringWriter writer = new StringWriter();
        try (PrintWriter printer = new PrintWriter(writer)) {
            t.printStackTrace(printer);
        }
        return writer.toString().replace("\r\n", "\n").strip().replace("\n", "\n\t\t");
    }
}
