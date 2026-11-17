package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import java.util.function.Function;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionInstance.class)
public interface SettingsOptionInstanceAccessor {
    @Accessor("toString")
    Function<Object, Component> polyplus$valueLabel();

    @Accessor("tooltip")
    OptionInstance.TooltipSupplier<Object> polyplus$tooltip();
}
//?}
