package com.github.tadaooq123.bdm.client;

import com.github.tadaooq123.bdm.Role;
import com.github.tadaooq123.bdm.RolePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class BdmClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RolePayload.TYPE, (payload, context) -> {
            Role role = Role.byId(payload.role());
            ClientRole.set(role);
            if (role == Role.DEAF) {
                context.client().getSoundManager().stop(); // мгновенная тишина
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRole.set(null));

        // Слепой не видит сообщения других игроков.
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            if (!ClientRole.isBlind()) {
                return true;
            }
            Minecraft mc = Minecraft.getInstance();
            return sender != null && mc.player != null && sender.getId().equals(mc.player.getUUID());
        });

        // Небольшая плашка с ролью в углу экрана.
        HudRenderCallback.EVENT.register((graphics, delta) -> {
            Role role = ClientRole.get();
            Minecraft mc = Minecraft.getInstance();
            if (role == null || mc.options.hideGui) {
                return;
            }
            Component text = Component.literal("Ты: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(role.displayName()).withStyle(role.color(), ChatFormatting.BOLD));
            graphics.drawString(mc.font, text, 4, 4, 0xFFFFFF, true);
        });
    }
}
