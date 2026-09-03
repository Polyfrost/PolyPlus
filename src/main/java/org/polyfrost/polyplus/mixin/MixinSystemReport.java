package org.polyfrost.polyplus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.SystemReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.io.PrintWriter;
import java.io.StringWriter;

//? if >= 26.2 {
import net.minecraft.CrashReportDetail;
//?} else {
/*import java.util.function.Supplier;
*///?}

@Mixin(SystemReport.class)
public class MixinSystemReport {
    @Unique
    private static final Logger POLYPLUS_LOGGER = LoggerFactory.getLogger("polyplus/crash-details");

    //? if >= 26.2 {
    @WrapMethod(method = "setDetail(Ljava/lang/String;Lnet/minecraft/CrashReportDetail;)V")
    private void polyplus$detailFailuresWithStackTrace(String key, CrashReportDetail<?> detail, Operation<Void> original) {
    //?} else {
    /*@WrapMethod(method = "setDetail(Ljava/lang/String;Ljava/util/function/Supplier;)V")
    private void polyplus$detailFailuresWithStackTrace(String key, Supplier<String> detail, Operation<Void> original) {
    *///?}
        String value;
        try {
            //? if >= 26.2 {
            value = String.valueOf(detail.call());
            //?} else {
            /*value = detail.get();
            *///?}
        } catch (Throwable t) {
            POLYPLUS_LOGGER.warn("Failed to get system info for {}", key, t);
            value = "ERR " + polyplus$stackTrace(t);
        }
        ((SystemReport) (Object) this).setDetail(key, value);
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
