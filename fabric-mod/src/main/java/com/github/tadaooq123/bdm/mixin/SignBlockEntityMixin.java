package com.github.tadaooq123.bdm.mixin;

import com.github.tadaooq123.bdm.BdmMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Слепой и немой не могут подписывать таблички (настраивается). */
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin {

    @Inject(method = "updateSignText", at = @At("HEAD"), cancellable = true)
    private void bdm$noWriting(Player player, boolean front, List<FilteredText> lines, CallbackInfo ci) {
        if (player instanceof ServerPlayer sp && !BdmMod.canWrite(sp)) {
            ci.cancel();
        }
    }
}
