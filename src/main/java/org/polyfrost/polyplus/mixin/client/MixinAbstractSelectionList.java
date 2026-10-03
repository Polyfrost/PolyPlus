package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Mixin;

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?}

//? if < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

@Mixin(AbstractSelectionList.class)
public class MixinAbstractSelectionList {
    @WrapMethod(
        //? if >= 26.1 {
        method = "extractListBackground"
        //?} else {
        /*method = "renderListBackground"
        *///?}
    )
    private void polyplus$keepPanoramaListBackground(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        Operation<Void> original
    ) {
        if (MenuPanorama.backdropDrawn()) {
            AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
            graphics.fill(list.getX(), list.getY(), list.getRight(), list.getBottom(), MenuPanorama.LIST_TINT);
            return;
        }
        original.call(graphics);
    }
}
//?} else {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.widget.ListWidget;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.vertex.Tesselator;
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ListWidget.class)
public abstract class MixinAbstractSelectionList {
    @Shadow protected int minY;
    @Shadow protected int maxY;
    @Shadow protected int minX;
    @Shadow protected int maxX;
    @Shadow @Final protected Minecraft minecraft;

    @Unique
    private boolean polyplus$modern() {
        return MenuPanorama.backdropDrawn() || (minecraft.world != null && PolyPlusMainMenuConfig.getModernInGameMenus());
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 0)
    )
    private void polyplus$modernListBackground(Tesselator tesselator, Operation<Void> original) {
        if (!polyplus$modern()) {
            original.call(tesselator);
            return;
        }
        polyplus$discard(tesselator);
        GuiElement.fill(minX, minY, maxX, maxY, MenuPanorama.backdropDrawn() ? MenuPanorama.LIST_TINT : 0x70000000);
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 1)
    )
    private void polyplus$dropTopShadow(Tesselator tesselator, Operation<Void> original) {
        if (polyplus$modern()) polyplus$discard(tesselator);
        else original.call(tesselator);
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()V", ordinal = 2)
    )
    private void polyplus$dropBottomShadow(Tesselator tesselator, Operation<Void> original) {
        if (polyplus$modern()) polyplus$discard(tesselator);
        else original.call(tesselator);
    }

    @WrapWithCondition(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ListWidget;renderHoleBackground(IIII)V")
    )
    private boolean polyplus$modernSeparators(ListWidget list, int top, int bottom, int topAlpha, int bottomAlpha) {
        if (!polyplus$modern()) return true;
        if (top == 0) {
            GuiElement.fill(minX, minY - 2, maxX, minY - 1, 0x33FFFFFF);
            GuiElement.fill(minX, minY - 1, maxX, minY, 0xBF000000);
        } else {
            GuiElement.fill(minX, maxY, maxX, maxY + 1, 0xBF000000);
            GuiElement.fill(minX, maxY + 1, maxX, maxY + 2, 0x33FFFFFF);
        }
        return false;
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ListWidget;renderList(IIII)V")
    )
    private void polyplus$clipRows(ListWidget list, int x, int y, int mouseX, int mouseY, Operation<Void> original) {
        if (!polyplus$modern()) {
            original.call(list, x, y, mouseX, mouseY);
            return;
        }
        polyplus$clipped(() -> original.call(list, x, y, mouseX, mouseY));
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/ListWidget;renderHeader(IILnet/minecraft/client/render/vertex/Tesselator;)V")
    )
    private void polyplus$clipHeader(ListWidget list, int x, int y, Tesselator tesselator, Operation<Void> original) {
        if (!polyplus$modern()) {
            original.call(list, x, y, tesselator);
            return;
        }
        polyplus$clipped(() -> original.call(list, x, y, tesselator));
    }

    @Unique
    private void polyplus$clipped(Runnable draw) {
        int scale = new Window(minecraft).getScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, minecraft.height - maxY * scale, minecraft.width, (maxY - minY) * scale);
        try {
            draw.run();
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Unique
    private static void polyplus$discard(Tesselator tesselator) {
        tesselator.getBuffer().end();
        tesselator.getBuffer().clear();
    }
}
*///?}
