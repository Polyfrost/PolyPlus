package org.polyfrost.polyplus.compat;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Set;

public class XaeroMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("PolyPlus");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(".compat.xaero.")) return true;
        boolean compatible;
        try {
            compatible = isCompatible(node(targetClassName));
        } catch (Exception e) {
            compatible = false;
        }
        if (!compatible) LOGGER.warn("Installed Xaero's Minimap doesn't match {}, skipping it", mixinClassName);
        return compatible;
    }

    private static boolean isCompatible(ClassNode target) throws Exception {
        //? if < 1.21.6 {
        /*MethodNode render = method(target, "renderChunksToFBO");
        return render != null && target.fields.stream().anyMatch(f -> f.name.equals("rotationFramebuffer"))
            && count(render, Type.INT_TYPE) >= 3 && count(render, Type.BOOLEAN_TYPE) >= 3;
        *///?} else {
        ClassNode state = node("xaero.hud.minimap.render.MinimapPipRenderState");
        for (String getter : List.of("getWidth", "getHeight", "getScale", "getMinimapScale", "getSize", "getBoxSize")) {
            if (method(state, getter) == null) return false;
        }
        if (method(node("xaero.common.minimap.MinimapProcessor"), "updateZoom") == null) return false;
        if (method(target, "textureIsReadyToBlit") != null) return false;
        //? if >= 26.3 {
        MethodNode prepare = method(target, "prepare");
        if (prepare == null || !invokes(prepare, "xaero_mm_prepareTexturesAndProjection") || !invokes(prepare, "renderToTexture")) return false;
        //?}
        return true;
        //?}
    }

    private static ClassNode node(String name) throws Exception {
        return MixinService.getService().getBytecodeProvider().getClassNode(name);
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElse(null);
    }

    private static int count(MethodNode method, Type type) {
        int n = 0;
        for (Type arg : Type.getArgumentTypes(method.desc)) if (arg.equals(type)) n++;
        return n;
    }

    private static boolean invokes(MethodNode method, String name) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals(name)) return true;
        }
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
