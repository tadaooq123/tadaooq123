package com.github.tadaooq123.bdm.client.mixin;

import com.github.tadaooq123.bdm.client.ClientRole;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Глухой не слышит ни одного звука игры: ни мобов, ни шагов, ни музыки. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void bdm$deaf(SoundInstance sound, CallbackInfo ci) {
        if (ClientRole.isDeaf()) {
            ci.cancel();
        }
    }
}
