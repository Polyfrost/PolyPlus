package org.polyfrost.polyplus.mixin.client.nameplate;

//? if >= 26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.ScissorState;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
//?}

//? if >= 26.3 {
@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
//?}
public class MixinRenderTypeFeatureRendererGroup {
    //? if >= 26.3 {
    @Unique
    private static final boolean POLYPLUS$SCISSOR_EQUALS = polyplus$scissorEquals();

    @Unique
    private static boolean polyplus$scissorEquals() {
        try {
            return ScissorState.class.getMethod("equals", Object.class).getDeclaringClass() != Object.class;
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    @WrapOperation(
        method = "getOrAddDraw",
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I")
    )
    private int polyplus$findDraw(List<?> renderTypes, Object renderType, Operation<Integer> original) {
        if (POLYPLUS$SCISSOR_EQUALS) {
            return original.call(renderTypes, renderType);
        }
        for (int i = 0, size = renderTypes.size(); i < size; i++) {
            if (renderTypes.get(i) == renderType) {
                return i;
            }
        }
        return -1;
    }
    //?}
}
