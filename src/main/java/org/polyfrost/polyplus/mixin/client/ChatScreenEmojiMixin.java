package org.polyfrost.polyplus.mixin.client;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import org.polyfrost.polyplus.client.emoji.EmojiRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenEmojiMixin {
    @Shadow protected EditBox input;

    @Unique private static final Pattern POLYPLUS_TOKEN = Pattern.compile(":([a-z0-9_+\\-]{2,})$");
    @Unique private static final int POLYPLUS_MAX = 10;
    @Unique private static final int POLYPLUS_LINE_H = 12;

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
            polyplus$selected = 0; // reset selection when the prefix changes
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
    private boolean polyplus$handleKey(int key) {
        if (polyplus$suggestions.isEmpty()) return false;
        int n = polyplus$suggestions.size();
        switch (key) {
            case 265: // GLFW_KEY_UP
                polyplus$selected = (polyplus$selected - 1 + n) % n;
                return true;
            case 264: // GLFW_KEY_DOWN
                polyplus$selected = (polyplus$selected + 1) % n;
                return true;
            case 258: // TAB - complete highlighted emoji
            case 257: // ENTER
            case 335: // KP_ENTER
                return polyplus$accept();
            case 256: // ESC
                polyplus$suggestions = Collections.emptyList();
                polyplus$tokenStart = -1;
                polyplus$token = null;
                return true;
            default:
                return false;
        }
    }

    //? if < 26.1 {
    /*@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void polyplus$renderEmoji(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        polyplus$refresh();
        if (polyplus$suggestions.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int x = input.getX();
        int bottom = input.getY() - 2;
        int top = bottom - polyplus$suggestions.size() * POLYPLUS_LINE_H;
        int width = 0;
        for (String a : polyplus$suggestions) width = Math.max(width, font.width(EmojiRegistry.suggestionRow(a)));
        width += 6;
        graphics.fill(x, top, x + width, bottom, 0xE0000000);
        for (int i = 0; i < polyplus$suggestions.size(); i++) {
            int rowY = top + i * POLYPLUS_LINE_H;
            if (i == polyplus$selected) graphics.fill(x, rowY, x + width, rowY + POLYPLUS_LINE_H, 0x40FFFFFF);
            int color = i == polyplus$selected ? 0xFFFFFF00 : 0xFFAAAAAA;
            graphics.drawString(font, EmojiRegistry.suggestionRow(polyplus$suggestions.get(i)), x + 3, rowY + 2, color);
        }
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void polyplus$keyPressed(int key, int scan, int mods, CallbackInfoReturnable<Boolean> cir) {
        if (polyplus$handleKey(key)) cir.setReturnValue(true);
    }
    *///?} else {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void polyplus$renderEmoji(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        polyplus$refresh();
        if (polyplus$suggestions.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int x = input.getX();
        int bottom = input.getY() - 2;
        int top = bottom - polyplus$suggestions.size() * POLYPLUS_LINE_H;
        int width = 0;
        for (String a : polyplus$suggestions) width = Math.max(width, font.width(EmojiRegistry.suggestionRow(a)));
        width += 6;
        graphics.fill(x, top, x + width, bottom, 0xE0000000);
        for (int i = 0; i < polyplus$suggestions.size(); i++) {
            int rowY = top + i * POLYPLUS_LINE_H;
            if (i == polyplus$selected) graphics.fill(x, rowY, x + width, rowY + POLYPLUS_LINE_H, 0x40FFFFFF);
            int color = i == polyplus$selected ? 0xFFFFFF00 : 0xFFAAAAAA;
            graphics.text(font, EmojiRegistry.suggestionRow(polyplus$suggestions.get(i)), x + 3, rowY + 2, color);
        }
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void polyplus$keyPressed(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (polyplus$handleKey(event.key())) cir.setReturnValue(true);
    }
    //?}
}
