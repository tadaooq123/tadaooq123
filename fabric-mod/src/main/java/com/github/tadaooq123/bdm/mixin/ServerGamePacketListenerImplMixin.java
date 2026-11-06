package com.github.tadaooq123.bdm.mixin;

import com.github.tadaooq123.bdm.BdmMod;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    /** Клавиша F (смена рук) — способность роли. Срабатывает уже в главном потоке сервера. */
    @Inject(method = "handlePlayerAction",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void bdm$ability(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND
                && BdmMod.onSwapHands(player)) {
            ci.cancel();
        }
    }

    /** Слепой и немой не могут писать в книгах (настраивается). */
    @Inject(method = "updateBookContents", at = @At("HEAD"), cancellable = true)
    private void bdm$noBookEdit(List<FilteredText> pages, int slot, CallbackInfo ci) {
        if (!BdmMod.canWrite(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "signBook", at = @At("HEAD"), cancellable = true)
    private void bdm$noBookSign(FilteredText title, List<FilteredText> pages, int slot, CallbackInfo ci) {
        if (!BdmMod.canWrite(player)) {
            ci.cancel();
        }
    }
}
