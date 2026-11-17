package org.polyfrost.polyplus.client.gui.screens.packs

import java.nio.file.Path
//? if > 1.8.9 {
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import org.polyfrost.polyplus.mixin.client.access.PackFileSupplierAccessor
import org.polyfrost.polyplus.mixin.client.access.PackPathSupplierAccessor
import org.polyfrost.polyplus.mixin.client.access.PackResourcesAccessor

internal object PackSources {
    fun pathOf(pack: Pack): Path? = runCatching {
        when (val supplier = (pack as Any as PackResourcesAccessor).`polyplus$resources`()) {
            is PathPackResources.PathResourcesSupplier -> (supplier as Any as PackPathSupplierAccessor).`polyplus$content`()
            is FilePackResources.FileResourcesSupplier -> (supplier as Any as PackFileSupplierAccessor).`polyplus$content`()?.toPath()
            else -> null
        }
    }.getOrNull()
}
//?}
