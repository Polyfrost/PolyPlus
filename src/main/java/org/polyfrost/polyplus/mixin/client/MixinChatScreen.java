package org.polyfrost.polyplus.mixin.client;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >= 1.21.10 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

import org.polyfrost.polyplus.client.emoji.EmojiChatPicker;
import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen {
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;

    @Unique private static final Pattern POLYPLUS_TOKEN = Pattern.compile("(?<![A-Za-z0-9_:/.+\\-]):([a-z0-9_+\\-]{2,})$");
    @Unique private static final int POLYPLUS_MAX = 10;
    @Unique private static final int POLYPLUS_LINE_H = 12;
    @Unique private static final int POLYPLUS_CHAT_BACKGROUND = 0x80000000;

    @Unique private final EmojiChatPicker polyplus$picker = new EmojiChatPicker();

    @Unique private List<String> polyplus$suggestions = Collections.emptyList();
    @Unique private int polyplus$selected = 0;
    @Unique private int polyplus$tokenStart = -1;
    @Unique private String polyplus$token = null;

    @Inject(method = "init", at = @At("TAIL"))
    private void polyplus$installEmojiFormatter(CallbackInfo ci) {
        if (input == null) return;
        //? if >= 1.21.10 {
        input.addFormatter((str, offset) -> polyplus$formatInput(str));
        //?} else {
        /*input.setFormatter((str, offset) -> polyplus$formatInput(str));
        *///?}
    }

    @Unique
    private FormattedCharSequence polyplus$formatInput(String str) {
        if (EmojiRegistry.enabled()) {
            FormattedCharSequence styled = EmojiRegistry.styleInput(str, Style.EMPTY);
            if (styled != null) return styled;
        }
        return FormattedCharSequence.forward(str, Style.EMPTY);
    }

    @Unique
    private void polyplus$refresh() {
        polyplus$suggestions = Collections.emptyList();
        polyplus$tokenStart = -1;
        if (input == null || !EmojiRegistry.enabled()) {
            polyplus$token = null;
            return;
        }
        if (commandSuggestions != null && commandSuggestions.isVisible()) {
            polyplus$token = null;
            return;
        }
        String value = input.getValue();
        int cursor = Math.min(input.getCursorPosition(), value.length());
        Matcher m = POLYPLUS_TOKEN.matcher(value.substring(0, cursor));
        if (!m.find()) {
            polyplus$token = null;
            return;
        }
        String prefix = m.group(1);
        List<String> found = EmojiRegistry.completions(prefix, POLYPLUS_MAX);
        if (found.isEmpty()) {
            polyplus$token = null;
            return;
        }
        if (!prefix.equals(polyplus$token)) {
            polyplus$selected = 0;
            polyplus$token = prefix;
        }
        polyplus$suggestions = found;
        polyplus$tokenStart = m.start();
        if (polyplus$selected >= found.size()) polyplus$selected = 0;
    }

    @Unique
    private boolean polyplus$accept() {
        if (polyplus$suggestions.isEmpty() || polyplus$tokenStart < 0) return false;
        String alias = polyplus$suggestions.get(polyplus$selected);
        String value = input.getValue();
        int cursor = Math.min(input.getCursorPosition(), value.length());
        String before = value.substring(0, polyplus$tokenStart);
        String after = value.substring(cursor);
        String insert = ":" + alias + ":";
        input.setValue(before + insert + after);
        input.setCursorPosition((before + insert).length());
        polyplus$suggestions = Collections.emptyList();
        polyplus$tokenStart = -1;
        polyplus$token = null;
        return true;
    }

    @Unique
    private boolean polyplus$handleKey(int key, boolean shiftDown) {
        if (polyplus$picker.handleKey(key, shiftDown, this::polyplus$insertEmoji)) return true;
        if (polyplus$suggestions.isEmpty()) return false;
        int n = polyplus$suggestions.size();
        switch (key) {
            case 265:
                polyplus$selected = (polyplus$selected - 1 + n) % n;
                return true;
            case 264:
                polyplus$selected = (polyplus$selected + 1) % n;
                return true;
            case 258:
            case 257:
            case 335:
                return polyplus$accept();
            case 256:
                polyplus$suggestions = Collections.emptyList();
                polyplus$tokenStart = -1;
                polyplus$token = null;
                return true;
            default:
                return false;
        }
    }

    @Inject(
        //? if >= 26.1 {
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        //?} else {
        /*method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
        *///?}
        at = @At("TAIL")
    )
    private void polyplus$renderEmoji(
        //? if >= 26.1 {
        GuiGraphicsExtractor graphics,
        //?} else {
        /*GuiGraphics graphics,
        *///?}
        int mouseX, int mouseY, float delta, CallbackInfo ci
    ) {
        Font font = Minecraft.getInstance().font;
        if (input != null && EmojiRegistry.enabled()) {
            polyplus$picker.layout(input.getX(), input.getY(), input.getWidth());
            polyplus$picker.render(graphics, font, mouseX, mouseY);
        }
        polyplus$refresh();
        if (polyplus$suggestions.isEmpty() || polyplus$picker.isOpen()) return;
        int x = input.getX();
        int bottom = input.getY() - 2;
        int top = bottom - polyplus$suggestions.size() * POLYPLUS_LINE_H;
        int width = 0;
        for (String a : polyplus$suggestions) width = Math.max(width, font.width(EmojiRegistry.suggestionRow(a)));
        width += 6;
        graphics.fill(x, top, x + width, bottom, Minecraft.getInstance().options.getBackgroundColor(POLYPLUS_CHAT_BACKGROUND));
        for (int i = 0; i < polyplus$suggestions.size(); i++) {
            int rowY = top + i * POLYPLUS_LINE_H;
            if (i == polyplus$selected) graphics.fill(x, rowY, x + width, rowY + POLYPLUS_LINE_H, 0x40FFFFFF);
            int color = i == polyplus$selected ? 0xFFFFFF00 : 0xFFAAAAAA;
            //? if >= 26.1 {
            graphics.text(font, EmojiRegistry.suggestionRow(polyplus$suggestions.get(i)), x + 3, rowY + 2, color);
            //?} else {
            /*graphics.drawString(font, EmojiRegistry.suggestionRow(polyplus$suggestions.get(i)), x + 3, rowY + 2, color);
            *///?}
        }
    }

    //? if >= 1.21.10 {
    public boolean charTyped(CharacterEvent event) {
        if (polyplus$picker.charTyped(event.codepoint())) return true;
        GuiEventListener focused = ((Screen) (Object) this).getFocused();
        return focused != null && focused.charTyped(event);
    }
    //?} else {
    /*public boolean charTyped(char codepoint, int modifiers) {
        if (polyplus$picker.charTyped(codepoint)) return true;
        GuiEventListener focused = ((Screen) (Object) this).getFocused();
        return focused != null && focused.charTyped(codepoint, modifiers);
    }
    *///?}

    @WrapMethod(
        //? if >= 1.21.10 {
        method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"
        //?} else {
        /*method = "keyPressed(III)Z"
        *///?}
    )
    //? if >= 1.21.10 {
    private boolean polyplus$keyPressed(KeyEvent event, Operation<Boolean> original) {
        return polyplus$handleKey(event.key(), event.hasShiftDown()) || original.call(event);
    }
    //?} else {
    /*private boolean polyplus$keyPressed(int key, int scan, int mods, Operation<Boolean> original) {
        return polyplus$handleKey(key, Screen.hasShiftDown()) || original.call(key, scan, mods);
    }
    *///?}

    @Unique
    private boolean polyplus$emojiClicked(double mouseX, double mouseY, int button, boolean shiftDown) {
        if (input == null || !EmojiRegistry.enabled()) return false;
        polyplus$picker.layout(input.getX(), input.getY(), input.getWidth());
        return polyplus$picker.mouseClicked(mouseX, mouseY, button, shiftDown, this::polyplus$insertEmoji);
    }

    @Unique
    private boolean polyplus$emojiScrolled(double mouseX, double mouseY, double deltaY) {
        if (input == null || !EmojiRegistry.enabled()) return false;
        polyplus$picker.layout(input.getX(), input.getY(), input.getWidth());
        return polyplus$picker.mouseScrolled(mouseX, mouseY, deltaY);
    }

    @Unique
    private void polyplus$insertEmoji(String alias) {
        String value = input.getValue();
        int cursor = Math.min(input.getCursorPosition(), value.length());
        String insert = ":" + alias + ":";
        input.setValue(value.substring(0, cursor) + insert + value.substring(cursor));
        input.setCursorPosition(cursor + insert.length());
    }

    @WrapMethod(
        //? if >= 1.21.10 {
        method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"
        //?} else {
        /*method = "mouseClicked(DDI)Z"
        *///?}
    )
    //? if >= 1.21.10 {
    private boolean polyplus$mouseClicked(MouseButtonEvent event, boolean doubleClick, Operation<Boolean> original) {
        if (polyplus$emojiClicked(event.x(), event.y(), event.button(), event.hasShiftDown())) return true;
        return original.call(event, doubleClick);
    }
    //?} else {
    /*private boolean polyplus$mouseClicked(double mouseX, double mouseY, int button, Operation<Boolean> original) {
        if (polyplus$emojiClicked(mouseX, mouseY, button, Screen.hasShiftDown())) return true;
        return original.call(mouseX, mouseY, button);
    }
    *///?}

    @WrapMethod(method = "mouseScrolled(DDDD)Z")
    private boolean polyplus$mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY, Operation<Boolean> original) {
        if (polyplus$emojiScrolled(mouseX, mouseY, deltaY)) return true;
        return original.call(mouseX, mouseY, deltaX, deltaY);
    }
}
