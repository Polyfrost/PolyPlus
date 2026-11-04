package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
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
    @Shadow @Final private ServerData serverData;
    @Shadow private EditBox ipEdit;
    // Survives the re-init on resize
    @Unique private String polyplus$folder;

    protected MixinManageServerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void polyplus$addFolderBox(CallbackInfo ci) {
        int top = this.height / 4 + 72;
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.getY() >= top) widget.setY(widget.getY() + 24);
        }

        if (polyplus$folder == null) polyplus$folder = Objects.requireNonNullElse(ServerFolders.folderOf(serverData.ip), "");
        var box = new EditBox(this.font, this.width / 2 - 100, top, 176, 20, Component.translatable("polyplus.serverFolder"));
        box.setMaxLength(32);
        box.setValue(polyplus$folder);
        box.setHint(Component.translatable("polyplus.serverFolder.hint"));
        box.setResponder(value -> polyplus$folder = value);
        addRenderableWidget(box);

        var pick = Button.builder(Component.literal("▼"), button -> box.setValue(polyplus$nextFolder(box.getValue())))
            .bounds(this.width / 2 + 80, top, 20, 20)
            .build();
        pick.setTooltip(Tooltip.create(Component.translatable("polyplus.serverFolder.pick")));
        pick.active = !ServerFolders.names().isEmpty();
        addRenderableWidget(pick);
    }

    // Steps through the existing folders, then back to none
    @Unique
    private static String polyplus$nextFolder(String current) {
        var names = ServerFolders.names();
        int index = names.indexOf(current.trim());
        return index + 1 < names.size() ? names.get(index + 1) : "";
    }

    @Inject(method = "onAdd", at = @At("HEAD"))
    private void polyplus$saveFolder(CallbackInfo ci) {
        if (polyplus$folder != null) ServerFolders.setFolder(ipEdit.getValue(), polyplus$folder);
    }
}
//?}
