package org.polyfrost.polyplus.mixin.client;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.gui.screen.menu.options.ControlsListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.Objects;

@Mixin(ControlsListWidget.class)
public abstract class MixinControlsListWidget {
    @Shadow @Mutable
    private EntryListWidget.Entry[] entries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void polyplus$dropEmptyEntries(CallbackInfo ci) {
        entries = Arrays.stream(entries).filter(Objects::nonNull).toArray(EntryListWidget.Entry[]::new);
    }
}
*///?}
