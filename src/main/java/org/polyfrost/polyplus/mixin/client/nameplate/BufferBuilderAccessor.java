package org.polyfrost.polyplus.mixin.client.nameplate;

//? if = 1.8.9 {
/*import java.nio.IntBuffer;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("intBuffer")
    IntBuffer polyplus$intBuffer();

    @Accessor("vertexCount")
    int polyplus$vertexCount();

    @Accessor("vertexCount")
    void polyplus$setVertexCount(int count);

    @Invoker("grow")
    void polyplus$grow(int ints);
}
*///?}
