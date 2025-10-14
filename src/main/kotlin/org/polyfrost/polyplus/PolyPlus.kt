package org.polyfrost.polyplus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.commands.v1.CommandManager
import org.polyfrost.polyplus.client.ExampleCommand
import org.polyfrost.polyplus.client.Config

//#if FABRIC
//$$ import net.fabricmc.api.ModInitializer
//$$ import net.fabricmc.api.ClientModInitializer
//$$ import net.fabricmc.api.DedicatedServerModInitializer
//#elseif FORGE
//#if MC >= 1.16.5
//$$ import net.minecraftforge.eventbus.api.IEventBus
//$$ import net.minecraftforge.fml.common.Mod
//$$ import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
//$$ import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
//$$ import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
//$$ import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
//#else
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import org.polyfrost.polyplus.discordrpc.RPC
import java.time.Instant

//#endif
//#elseif NEOFORGE
//$$ import net.neoforged.bus.api.IEventBus
//$$ import net.neoforged.fml.common.Mod
//$$ import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
//$$ import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
//$$ import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
//#endif


//#if FORGE-LIKE
//$$ import org.polyfrost.example.ExampleConstants
//#if MC >= 1.16.5
//$$ @Mod(PolyPlus.ID)
//#else
@Mod(modid = PolyPlus.ID, version = PolyPlus.VERSION)
//#endif
//#endif
class PolyPlus
//#if FABRIC
//$$     : ModInitializer, ClientModInitializer
//#endif
{

    //#if FORGE && MC >= 1.16.5
    //$$ init {
    //$$     setupForgeEvents(FMLJavaModLoadingContext.get().modEventBus)
    //$$ }
    //#elseif NEOFORGE
    //$$ constructor(modEventBus: IEventBus) {
    //$$     setupForgeEvents(modEventBus)
    //$$ }
    //#endif

    //#if FABRIC
    //$$ override
    //#elseif FORGE && MC <= 1.12.2
    @Mod.EventHandler
    //#endif
    fun onInitialize(
        //#if FORGE-LIKE
        //#if MC >= 1.16.5
        //$$ event: FMLCommonSetupEvent
        //#else
        event: FMLInitializationEvent
        //#endif
        //#endif
    ) {
        Example.initialize()
    }

    //#if FABRIC
    //$$ override
    //#elseif FORGE && MC <= 1.12.2
    @Mod.EventHandler
    //#endif
    fun onInitializeClient(
        //#if FORGE-LIKE
        //#if MC >= 1.16.5
        //$$ event: FMLClientSetupEvent
        //#else
        event: FMLInitializationEvent
        //#endif
        //#endif
    ) {
        //#if MC <= 1.12.2
        if (!event.side.isClient) return
        //#endif

        launch = Instant.now()
        RPC.start()
        Config.preload()
        CommandManager.register(ExampleCommand)
    }

    //#if FORGE-LIKE && MC >= 1.16.5
    //$$ fun setupForgeEvents(modEventBus: IEventBus) {
    //$$     modEventBus.addListener(this::onInitialize)
    //$$     modEventBus.addListener(this::onInitializeClient)
    //$$ }
    //#endif

    companion object {
        var launch: Instant? = null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        const val ID = "@MOD_ID@"
        const val NAME = "@MOD_NAME@"
        const val VERSION = "@MOD_VERSION@"
    }
}
