package org.polyfrost.polyplus

import org.polyfrost.polyplus.client.PolyPlusClient

//? if fabric || ornithe {
import net.fabricmc.api.ClientModInitializer
//?}

//? if ornithe {
/*import net.minecraft.client.options.KeyBinding
import net.ornithemc.osl.keybinds.api.KeybindEvents
import net.ornithemc.osl.keybinds.api.KeybindRegistry
import org.lwjgl.input.Keyboard
*///?}

class PolyPlusEntrypoint
//? if fabric || ornithe {
    : ClientModInitializer
//?}
{

    //? if fabric || ornithe {
    override
    //?}
    fun onInitializeClient(
    ) {
        PolyPlusClient.initialize()

        //? if ornithe {
        /*KeybindEvents.REGISTER_KEYBINDS.register {
            dropModifier = KeybindRegistry.register("Drop Stack Modifier", Keyboard.KEY_NONE, "PolyPlus")
            hideScreen = KeybindRegistry.register("Hide Screen", Keyboard.KEY_F1, "PolyPlus")
            customDebug = KeybindRegistry.register("Custom F3", Keyboard.KEY_F3, "PolyPlus")
            clearShaders = KeybindRegistry.register("Clear Vanilla Shaders", Keyboard.KEY_F4, "PolyPlus")
        }
        *///?}
    }

    //? if ornithe {
    /*companion object {
        lateinit var dropModifier: KeyBinding
        lateinit var hideScreen: KeyBinding
        lateinit var customDebug: KeyBinding
        lateinit var clearShaders: KeyBinding

        @JvmStatic
        fun remap(binding: KeyBinding, vanillaKey: Int): Int =
            binding.keyCode.takeIf { it != Keyboard.KEY_NONE } ?: vanillaKey
    }
    *///?}

}
