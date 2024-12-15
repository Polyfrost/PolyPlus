package org.polyfrost.polyplus

//#if FORGE
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import org.polyfrost.polyplus.social.SocialManager

//#else
//$$ import net.fabricmc.api.ClientModInitializer
//#endif

//#if FORGE
@Mod(modid = PolyPlusConstants.ID, version = PolyPlusConstants.VERSION, name = PolyPlusConstants.NAME, modLanguageAdapter = "org.polyfrost.oneconfig.utils.v1.forge.KotlinLanguageAdapter")
//#endif
object PolyPlus
    //#if FABRIC
    //$$ : ClientModInitializer
    //#endif
{

    fun initialize() {
        SocialManager.initialize()
    }

    //#if FORGE
    @Mod.EventHandler
    fun onInit(e: FMLInitializationEvent) {
        initialize()
    }
    //#else
    //$$ override fun onInitializeClient() {
    //$$     initialize()
    //$$ }
    //#endif

}
