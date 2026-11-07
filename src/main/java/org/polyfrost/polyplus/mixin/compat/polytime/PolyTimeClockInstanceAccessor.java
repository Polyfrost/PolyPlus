package org.polyfrost.polyplus.mixin.compat.polytime;

//? if >= 26.1 && < 26.3 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.ClientClockManager$ClockInstance")
public interface PolyTimeClockInstanceAccessor {
    @Accessor("totalTicks")
    long polyplus$totalTicks();
}
*///?}
