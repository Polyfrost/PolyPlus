package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Consumer;

@Mixin(TranslatableContents.class)
public interface TranslatableContentsInvoker {
    @Invoker("decomposeTemplate")
    void polyplus$decomposeTemplate(String template, Consumer<FormattedText> consumer);
}
//?}
