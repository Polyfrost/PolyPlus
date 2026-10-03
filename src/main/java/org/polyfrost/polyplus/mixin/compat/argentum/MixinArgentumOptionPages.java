package org.polyfrost.polyplus.mixin.compat.argentum;

//? if = 1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyReceiver;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.api.options.OptionIdentifier;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionStorage;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.gui.ArgentumOptionPages", remap = false)
public class MixinArgentumOptionPages {
    @ModifyReceiver(
            method = "general",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/taumc/celeritas/api/options/structure/OptionGroup$Builder;build()Lorg/taumc/celeritas/api/options/structure/OptionGroup;",
                    ordinal = 2
            ),
            remap = false,
            require = 0
    )
    private static OptionGroup.Builder polyplus$addDynamicFov(OptionGroup.Builder group) {
        OptionStorage<PolyPlusConfig> storage = new OptionStorage<>() {
            @Override
            public PolyPlusConfig getData() {
                return PolyPlusConfig.INSTANCE;
            }

            @Override
            public void save() {
                PolyPlusConfig.INSTANCE.save();
            }
        };

        return group.add(OptionImpl.createBuilder(Boolean.class, storage)
                .setId(OptionIdentifier.create("polyplus", "dynamic_fov", Boolean.class))
                .setName(TextComponent.literal("Dynamic FOV"))
                .setTooltip(TextComponent.literal("Controls how much the field of view can change with gameplay effects."))
                .setControl(TickBoxControl::new)
                .setBinding((config, value) -> PolyPlusConfig.setDynamicFov(value), config -> PolyPlusConfig.getDynamicFov())
                .build());
    }
}
*///?}
