package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Objects;

//? if >= 1.21.10 {
import net.minecraft.client.gui.screens.ManageServerScreen;
//?} else {
/*import net.minecraft.client.gui.screens.EditServerScreen;
*///?}

//? if >= 1.21.10 {
@Mixin(ManageServerScreen.class)
//?} else {
/*@Mixin(EditServerScreen.class)
*///?}
public abstract class MixinManageServerScreen extends Screen {
    @Unique private static final int ROW_HEIGHT = 24;
    @Unique private static final int BOX_WIDTH = 176;
    @Unique private static final int PICK_SIZE = 20;
    @Unique private static final int MAX_FOLDER_LENGTH = 32;

    @Shadow @Final private ServerData serverData;
    @Shadow private EditBox ipEdit;
    @Shadow private EditBox nameEdit;
    @Unique private String polyplus$folder;

    protected MixinManageServerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void polyplus$addFolderBox(CallbackInfo ci) {
        int top = this.height / 4 + 72;
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget != nameEdit && widget != ipEdit && widget.getY() >= top) {
                widget.setY(widget.getY() + ROW_HEIGHT);
            }
        }

        if (polyplus$folder == null) polyplus$folder = Objects.requireNonNullElse(ServerFolders.folderOf(serverData.ip), "");
        var box = new EditBox(this.font, this.width / 2 - 100, top, BOX_WIDTH, 20, Component.translatable("polyplus.serverFolder"));
        box.setMaxLength(MAX_FOLDER_LENGTH);
        box.setValue(polyplus$folder);
        box.setHint(Component.translatable("polyplus.serverFolder.hint"));
        box.setTooltip(Tooltip.create(Component.translatable("polyplus.serverFolder")));
        box.setResponder(value -> polyplus$folder = value);
        addRenderableWidget(box);

        var pick = Button.builder(Component.literal("\u25BC"), button -> box.setValue(polyplus$nextFolder(box.getValue())))
            .bounds(this.width / 2 + 100 - PICK_SIZE, top, PICK_SIZE, PICK_SIZE)
            .build();
        pick.setTooltip(Tooltip.create(Component.translatable("polyplus.serverFolder.pick")));
        pick.active = !ServerFolders.names().isEmpty();
        addRenderableWidget(pick);
    }

    @Unique
    private static String polyplus$nextFolder(String current) {
        var names = ServerFolders.names();
        var trimmed = current.trim();
        int index = -1;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(trimmed)) {
                index = i;
                break;
            }
        }
        return index + 1 < names.size() ? names.get(index + 1) : "";
    }

    @Inject(method = "onAdd", at = @At("HEAD"))
    private void polyplus$saveFolder(CallbackInfo ci) {
        if (polyplus$folder == null) return;
        var previous = serverData.ip;
        var address = ipEdit.getValue();
        if (previous != null && !previous.isBlank()
            && !FeaturedServerModelsKt.normalizeServerAddress(previous).equals(FeaturedServerModelsKt.normalizeServerAddress(address))) {
            ServerFolders.forget(previous);
        }
        ServerFolders.setFolder(address, polyplus$folder);
    }
}
//?} else {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.multiplayer.AddServerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.resource.language.I18n;
import org.polyfrost.polyplus.client.featured.FeaturedServerModelsKt;
import org.polyfrost.polyplus.client.featured.FeaturedServerVanillaRenderer;
import org.polyfrost.polyplus.client.featured.ServerFolders;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.input.Keyboard;
import java.util.Objects;

@Mixin(AddServerScreen.class)
public abstract class MixinManageServerScreen extends Screen {
    @Unique private static final int ROW_HEIGHT = 24;
    @Unique private static final int BOX_WIDTH = 176;
    @Unique private static final int PICK_SIZE = 20;
    @Unique private static final int MAX_FOLDER_LENGTH = 32;
    @Unique private static final int PICK_BUTTON_ID = 0x50505302;
    @Unique private static final int HINT_COLOR = 0x707070;

    @Shadow @Final private ServerListEntry server;
    @Shadow private TextFieldWidget addressField;
    @Shadow private TextFieldWidget serverNameField;

    @Unique private TextFieldWidget polyplus$folderField;
    @Unique private ButtonWidget polyplus$pickButton;
    @Unique private String polyplus$folder;

    @Inject(method = "init", at = @At("RETURN"))
    private void polyplus$addFolderBox(CallbackInfo ci) {
        int top = this.height / 4 + 72;
        for (var button : buttons) {
            if (button.y >= top) button.y += ROW_HEIGHT;
        }

        if (polyplus$folderField != null) polyplus$folder = polyplus$folderField.getText();
        if (polyplus$folder == null) polyplus$folder = Objects.requireNonNullElse(ServerFolders.folderOf(server.ip), "");
        polyplus$folderField = new TextFieldWidget(2, textRenderer, this.width / 2 - 100, top, BOX_WIDTH, 20);
        polyplus$folderField.setMaxLength(MAX_FOLDER_LENGTH);
        polyplus$folderField.setText(polyplus$folder);

        polyplus$pickButton = new ButtonWidget(PICK_BUTTON_ID, this.width / 2 + 100 - PICK_SIZE, top, PICK_SIZE, PICK_SIZE, "");
        polyplus$pickButton.active = !ServerFolders.names().isEmpty();
        buttons.add(polyplus$pickButton);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void polyplus$tickFolderBox(CallbackInfo ci) {
        if (polyplus$folderField != null) polyplus$folderField.tick();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void polyplus$typeFolder(char chr, int key, CallbackInfo ci) {
        if (polyplus$folderField == null) return;
        if (key == Keyboard.KEY_TAB) {
            boolean name = serverNameField.isFocused();
            boolean address = addressField.isFocused();
            boolean folder = polyplus$folderField.isFocused();
            serverNameField.setFocused(folder || (!name && !address));
            addressField.setFocused(name);
            polyplus$folderField.setFocused(address);
            ci.cancel();
            return;
        }
        polyplus$folderField.keyPressed(chr, key);
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void polyplus$clickFolderBox(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (polyplus$folderField != null) polyplus$folderField.mouseClicked(mouseX, mouseY, button);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void polyplus$renderFolderBox(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (polyplus$folderField == null) return;
        polyplus$folderField.render();
        if (polyplus$folderField.getText().isEmpty() && !polyplus$folderField.isFocused()) {
            textRenderer.drawWithShadow(
                I18n.translate("polyplus.serverFolder.hint"),
                polyplus$folderField.x + 4,
                polyplus$folderField.y + 6,
                HINT_COLOR
            );
        }
        var pick = polyplus$pickButton;
        if (pick == null) return;
        FeaturedServerVanillaRenderer.triangleDown(pick.x + 7, pick.y + 8, pick.active ? 0xFFFFFFFF : 0xFFA0A0A0);
        if (pick.isHovered()) renderTooltip(I18n.translate("polyplus.serverFolder.pick"), mouseX, mouseY);
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void polyplus$handleFolderButtons(ButtonWidget button, CallbackInfo ci) {
        if (button == null || !button.active || polyplus$folderField == null) return;
        if (button == polyplus$pickButton) {
            polyplus$folderField.setText(polyplus$nextFolder(polyplus$folderField.getText()));
            ci.cancel();
            return;
        }
        if (button.id != 0) return;
        var previous = server.ip;
        var address = addressField.getText();
        if (previous != null && !previous.isBlank()
            && !FeaturedServerModelsKt.normalizeServerAddress(previous).equals(FeaturedServerModelsKt.normalizeServerAddress(address))) {
            ServerFolders.forget(previous);
        }
        ServerFolders.setFolder(address, polyplus$folderField.getText());
    }

    @Unique
    private static String polyplus$nextFolder(String current) {
        var names = ServerFolders.names();
        var trimmed = current.trim();
        int index = -1;
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(trimmed)) {
                index = i;
                break;
            }
        }
        return index + 1 < names.size() ? names.get(index + 1) : "";
    }
}
*///?}
