package com.github.tadaooq123.bdm;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;

import java.util.UUID;

/**
 * Интеграция с Simple Voice Chat.
 * Немой: его микрофон не доходит ни до кого. Глухой: ему не доходит ничей голос.
 * Класс загружается только если плагин voicechat установлен.
 */
public final class VoiceChatBridge implements VoicechatPlugin {

    private final BdmPlugin plugin;

    private VoiceChatBridge(BdmPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean register(BdmPlugin plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            return false;
        }
        service.registerPlugin(new VoiceChatBridge(plugin));
        return true;
    }

    @Override
    public String getPluginId() {
        return "blind_deaf_mute";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(EntitySoundPacketEvent.class, this::onSound);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onSound);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onSound);
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        UUID id = uuid(event.getSenderConnection());
        if (id != null && plugin.game().role(id) == Role.MUTE) {
            event.cancel();
            plugin.abilities().micBlocked(id);
        }
    }

    private void onSound(PacketEvent<?> event) {
        UUID receiver = uuid(event.getReceiverConnection());
        if (receiver != null && plugin.game().role(receiver) == Role.DEAF) {
            event.cancel();
        }
    }

    private static UUID uuid(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) {
            return null;
        }
        return connection.getPlayer().getUuid();
    }
}
