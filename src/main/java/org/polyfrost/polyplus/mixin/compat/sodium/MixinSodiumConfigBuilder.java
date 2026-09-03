package org.polyfrost.polyplus.mixin.compat.sodium;

//? if >= 1.21.11 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.injection.At;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.SodiumConfigBuilder", remap = false)
public class MixinSodiumConfigBuilder {
    //? if >= 1.21.11 {
    @ModifyExpressionValue(
            method = "buildGeneralPage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/api/config/structure/ConfigBuilder;createOptionGroup()Lnet/caffeinemc/mods/sodium/api/config/structure/OptionGroupBuilder;",
                    ordinal = 2
            ),
            remap = false,
            require = 0,
            expect = 0
    )
    private OptionGroupBuilder polyplus$restoreViewBobbing(OptionGroupBuilder group, ConfigBuilder builder) {
        Options options = Minecraft.getInstance().options;
        if (options == null) {
            return group;
        }

        StorageEventHandler storage = options::save;

        return group.addOption(
                builder.createBooleanOption(Identifier.parse("sodium:general.view_bobbing"))
                        .setStorageHandler(storage)
                        .setName(Component.translatable("options.viewBobbing"))
                        .setTooltip(Component.translatable("polyplus.sodium.options.view_bobbing.tooltip"))
                        .setDefaultValue(true)
                        .setBinding(options.bobView()::set, options.bobView()::get)
        );
    }
    //?}
}
