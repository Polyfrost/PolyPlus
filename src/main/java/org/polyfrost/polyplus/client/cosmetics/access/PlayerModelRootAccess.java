package org.polyfrost.polyplus.client.cosmetics.access;

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart;

public interface PlayerModelRootAccess {
    ModelPart polyplus$root();
}
//?} else {
/*import net.minecraft.client.render.model.ModelPart;

public interface PlayerModelRootAccess {
    ModelPart polyplus$root();

    void polyplus$resetPart(ModelPart part);

    void polyplus$markPosed();
}
*///?}
