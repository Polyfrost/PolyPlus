package org.polyfrost.polyplus.mixin.client.nameplate;

//? if = 26.2 {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(VulkanDevice.class)
public interface NameplateVulkanPipelinesAccessor {
    @Accessor("pipelineCache")
    Map<RenderPipeline, VulkanRenderPipeline> polyplus$pipelines();
}
*///?}
