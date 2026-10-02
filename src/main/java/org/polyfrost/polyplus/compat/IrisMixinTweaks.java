package org.polyfrost.polyplus.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public final class IrisMixinTweaks implements MixinCanceller, MixinAnnotationAdjuster {
    private static final String COMPAT_PACKAGE = "org.polyfrost.polyplus.mixin.compat.iris.";
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return !IRIS && mixinClassName.startsWith(COMPAT_PACKAGE);
    }

    @Override
    public AdjustableAnnotationNode adjust(List<String> targetClassNames, String mixinClassName, MethodNode handlerNode, AdjustableAnnotationNode annotationNode) {
        //? if >= 26.2 {
        if (mixinClassName.equals("net.irisshaders.iris.mixin.vertices.immediate.MixinBufferSource")
                && handlerNode.name.equals("iris$redirectBegin")
                && annotationNode.is(com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod.class)) {
            return null;
        }
        //?}
        return annotationNode;
    }
}
