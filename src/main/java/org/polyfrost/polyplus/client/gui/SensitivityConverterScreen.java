package org.polyfrost.polyplus.client.gui;

//? if = 1.8.9 {
/*import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.input.Keyboard;
import org.polyfrost.polyplus.client.features.SensitivityConverter;

import java.util.concurrent.CompletableFuture;

public class SensitivityConverterScreen extends Screen {
    private static final int APPLY_BUTTON = 0;
    private static final int CANCEL_BUTTON = 1;
    private static final int FIELD_WIDTH = 200;

    private static float appliedSensitivity = Float.NaN;
    private static String appliedFrom;

    private final Screen parent;
    private final GameOptions options;
    private CompletableFuture<SensitivityConverter.PointerSettings> pending;
    private boolean detected;
    private boolean enhancePrecision;

    private String sensitivityText;
    private String pointerSpeedText;

    private TextFieldWidget sensitivity;
    private TextFieldWidget pointerSpeed;
    private ButtonWidget apply;
    private Double converted;

    public SensitivityConverterScreen(Screen parent, GameOptions options) {
        this.parent = parent;
        this.options = options;
        if (options.mouseSensitivity == appliedSensitivity) {
            this.sensitivityText = appliedFrom;
        } else {
            int percent = Math.round(options.mouseSensitivity * 200.0F);
            this.sensitivityText = percent >= 0 && percent <= 200 ? String.valueOf(percent) : "";
        }
        this.pointerSpeedText = String.valueOf(SensitivityConverter.DEFAULT_POINTER_SPEED);
        this.pending = CompletableFuture.supplyAsync(SensitivityConverter.INSTANCE::windowsPointerSettings);
    }

    @Override
    public void init() {
        Keyboard.enableRepeatEvents(true);
        int left = this.width / 2 - FIELD_WIDTH / 2;
        int top = this.top();

        this.sensitivity = this.field(0, left, top + 12, 6, "[0-9.]*", this.sensitivityText);
        this.pointerSpeed = this.field(1, left, top + 54, 2, "[0-9]*", this.pointerSpeedText);
        this.sensitivity.setFocused(true);

        this.buttons.clear();
        this.apply = new ButtonWidget(APPLY_BUTTON, left, top + 110, 98, 20, "Apply");
        this.buttons.add(this.apply);
        this.buttons.add(new ButtonWidget(CANCEL_BUTTON, left + 102, top + 110, 98, 20, "Cancel"));
        this.update();
    }

    private TextFieldWidget field(int id, int x, int y, int maxLength, String allowed, String text) {
        TextFieldWidget field = new TextFieldWidget(id, this.textRenderer, x, y, FIELD_WIDTH, 20);
        field.setMaxLength(maxLength);
        field.setFilter(input -> input.matches(allowed));
        field.setText(text);
        return field;
    }

    private int top() {
        return this.height / 2 - 62;
    }

    private void update() {
        this.sensitivityText = this.sensitivity.getText();
        this.pointerSpeedText = this.pointerSpeed.getText();
        this.converted = null;
        try {
            double old = Double.parseDouble(this.sensitivityText);
            int speed = Integer.parseInt(this.pointerSpeedText);
            if (old >= 0 && old <= 200 && speed >= 1 && speed <= SensitivityConverter.MAX_POINTER_SPEED) {
                this.converted = SensitivityConverter.INSTANCE.convert(old, speed);
            }
        } catch (NumberFormatException ignored) {
        }
        this.apply.active = this.converted != null;
    }

    private void apply() {
        if (this.converted == null) return;
        this.options.mouseSensitivity = (float) (this.converted / 200.0);
        this.options.save();
        appliedSensitivity = this.options.mouseSensitivity;
        appliedFrom = this.sensitivityText;
        this.minecraft.openScreen(this.parent);
    }

    @Override
    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void tick() {
        if (this.pending != null && this.pending.isDone()) {
            SensitivityConverter.PointerSettings settings = this.pending.getNow(null);
            this.pending = null;
            if (settings != null) {
                this.detected = true;
                this.enhancePrecision = settings.getEnhancePrecision();
                this.pointerSpeed.setText(String.valueOf(settings.getSpeed()));
                this.update();
            }
        }
        this.sensitivity.tick();
        this.pointerSpeed.tick();
    }

    @Override
    protected void keyPressed(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            this.minecraft.openScreen(this.parent);
        } else if (key == Keyboard.KEY_TAB) {
            boolean first = this.sensitivity.isFocused();
            this.sensitivity.setFocused(!first);
            this.pointerSpeed.setFocused(first);
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.apply();
        } else {
            this.sensitivity.keyPressed(character, key);
            this.pointerSpeed.keyPressed(character, key);
            this.update();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        this.sensitivity.mouseClicked(mouseX, mouseY, button);
        this.pointerSpeed.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == APPLY_BUTTON) {
            this.apply();
        } else if (button.id == CANCEL_BUTTON) {
            this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        int left = this.width / 2 - FIELD_WIDTH / 2;
        int top = this.top();

        this.drawCenteredString(this.textRenderer, "Convert Sensitivity", this.width / 2, top - 34, 0xFFFFFF);
        this.drawCenteredString(this.textRenderer, "Get the raw input sensitivity that feels like your old one", this.width / 2, top - 20, 0xA0A0A0);
        this.drawString(this.textRenderer, "Old sensitivity (%)", left, top, 0xA0A0A0);
        this.drawString(this.textRenderer, "Windows mouse pointer speed (1-20" + (this.detected ? ", detected)" : ", default 10)"), left, top + 42, 0xA0A0A0);
        if (this.enhancePrecision) {
            this.drawCenteredString(this.textRenderer, "Enhance pointer precision is on, so the result is approximate", this.width / 2, top + 136, 0xFFAA00);
        }
        this.sensitivity.render();
        this.pointerSpeed.render();

        if (this.converted == null) {
            this.drawCenteredString(this.textRenderer, "Enter a sensitivity from 0 to 200 and a speed from 1 to 20", this.width / 2, top + 84, 0xFF5555);
        } else {
            this.drawCenteredString(this.textRenderer, "Raw input sensitivity: " + Math.round(this.converted) + "%", this.width / 2, top + 84, 0xFFFFFF);
            if (this.converted < 0 || this.converted > 200) {
                this.drawCenteredString(this.textRenderer, "Outside the slider's range. Applies fine, until you drag the slider.", this.width / 2, top + 95, 0xA0A0A0);
            }
        }

        super.render(mouseX, mouseY, tickDelta);
    }
}
*///?}
