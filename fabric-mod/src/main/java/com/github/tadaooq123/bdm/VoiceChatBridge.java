package com.github.tadaooq123.bdm;

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
 * Интеграция с Simple Voice Chat (подключается через entrypoint "voicechat",
 * поэтому без установленного мода этот класс даже не загружается).
 * Немой: его микрофон ни до кого не доходит. Глухой: ему не доходит ничей голос.
 */
public final class VoiceChatBridge implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return BdmMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        registration.registerEvent(EntitySoundPacketEvent.class, this::onSound);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onSound);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onSound);
        BdmMod.LOGGER.info("Simple Voice Chat подключён: немой молчит, глухой не слышит");
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        Game g = BdmMod.game();
        UUID id = uuid(event.getSenderConnection());
        if (g != null && id != null && g.role(id) == Role.MUTE) {
            event.cancel();
            g.abilities().micBlocked(id);
        }
    }

    private void onSound(PacketEvent<?> event) {
        Game g = BdmMod.game();
        UUID id = uuid(event.getReceiverConnection());
        if (g != null && id != null && g.role(id) == Role.DEAF) {
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
