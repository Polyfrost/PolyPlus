package org.polyfrost.polyplus.mixin.client.access;

//? if >= 26.3 {
import com.mojang.realmsclient.dto.RealmsNotification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.net.URI;

@Mixin(RealmsNotification.VisitUrl.class)
public interface RealmsVisitUrlAccessor {
    @Accessor("url")
    URI polyplus$url();
}
//?}
