package org.polyfrost.polyplus.mixin.client.access;

//? if > 1.8.9 {
import com.mojang.realmsclient.dto.RealmsNotification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if >= 26.3 {
import java.net.URI;
//?}

@Mixin(RealmsNotification.VisitUrl.class)
public interface RealmsVisitUrlAccessor {
    //? if >= 26.3 {
    @Accessor("url")
    URI polyplus$url();
    //?} else {
    /*@Accessor("url")
    String polyplus$url();
    *///?}
}
//?}
