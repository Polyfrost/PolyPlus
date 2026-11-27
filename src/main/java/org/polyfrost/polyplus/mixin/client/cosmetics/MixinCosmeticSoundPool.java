package org.polyfrost.polyplus.mixin.client.cosmetics;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.resources.Identifier;
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticSoundLibrary;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoundManager.class)
public class MixinCosmeticSoundPool {
    @WrapMethod(method = "get")
    private SoundPool polyplus$cosmeticSound(Identifier id, Operation<SoundPool> original) {
        SoundPool own = CosmeticSoundLibrary.pool(id);
        return own != null ? own : original.call(id);
    }
}
*///?}
