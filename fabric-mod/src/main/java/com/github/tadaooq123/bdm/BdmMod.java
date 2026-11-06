package com.github.tadaooq123.bdm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;

public final class BdmMod implements ModInitializer {

    public static final String MOD_ID = "blinddeafmute";
    public static final Logger LOGGER = LoggerFactory.getLogger("BlindDeafMute");

    private static BdmConfig config;
    private static volatile Game game;

    public static BdmConfig config() {
        return config;
    }

    /** Текущая игра (null, пока сервер/мир не запущен). */
    public static Game game() {
        return game;
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
    }

    public static void reloadConfig() {
        config = BdmConfig.load(configPath());
        Game g = game;
        if (g != null) {
            g.reloadTasks();
        }
    }

    @Override
    public void onInitialize() {
        config = BdmConfig.load(configPath());
        PayloadTypeRegistry.playS2C().register(RolePayload.TYPE, RolePayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Game g = new Game(server);
            g.removeTeams(); // хвосты прошлой игры, если мир закрыли посреди партии
            game = g;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Game g = game;
            if (g != null) {
                g.shutdown();
            }
            game = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Game g = game;
            if (g != null) {
                g.tick();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Game g = game;
            if (g != null) {
                g.onJoin(handler.player);
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Game g = game;
            if (g == null || !g.isRunning()) {
                return;
            }
            if (entity instanceof ServerPlayer p) {
                g.onDeath(p);
            } else if (entity instanceof EnderDragon) {
                g.finish(true);
            } else if (source.getEntity() instanceof ServerPlayer killer && g.role(killer.getUUID()) != null) {
                g.onKill(entity.getType());
            }
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            Game g = game;
            if (g != null) {
                g.onWorldChange(player, destination);
            }
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> allowChat(sender));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BdmCommands.register(dispatcher));

        LOGGER.info("Слепой, глухой, немой — мод загружен");
    }

    // ---------------------------------------------------------------- проверки для миксинов

    /** Можно ли игроку писать в чат. Слепому чужие сообщения прячет клиентская часть мода. */
    public static boolean allowChat(ServerPlayer sender) {
        Game g = game;
        Role role = g == null ? null : g.role(sender.getUUID());
        if (role == null) {
            return true;
        }
        if (config.chatOff()) {
            sender.displayClientMessage(Txt.of("Чат отключён — общайтесь голосом и жестами.", ChatFormatting.RED), true);
            return false;
        }
        if (!role.canSpeak()) {
            sender.displayClientMessage(Txt.of("Ты НЕМОЙ — писать нельзя.", ChatFormatting.GOLD), true);
            return false;
        }
        return true;
    }

    /** true — команду нужно запретить (/msg, /me и т.п. во время игры). */
    public static boolean blockCommand(CommandSourceStack source, String command) {
        Game g = game;
        ServerPlayer p = source.getPlayer();
        if (g == null || p == null || g.role(p.getUUID()) == null) {
            return false;
        }
        String s = command.startsWith("/") ? command.substring(1) : command;
        int space = s.indexOf(' ');
        String label = (space < 0 ? s : s.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (config.blockedCommands.contains(label)) {
            p.sendSystemMessage(Txt.error("Во время игры эта команда запрещена."));
            return true;
        }
        return false;
    }

    /** Можно ли игроку подписывать таблички и книги. */
    public static boolean canWrite(ServerPlayer p) {
        Game g = game;
        Role role = g == null ? null : g.role(p.getUUID());
        if (role == null || config.canWrite(role)) {
            return true;
        }
        p.displayClientMessage(Txt.of("Ты " + role.displayName().toUpperCase(Locale.ROOT) + " — писать нельзя.",
                role.color()), true);
        return false;
    }

    /** Нажатие F. true — перехвачено способностью роли. */
    public static boolean onSwapHands(ServerPlayer p) {
        Game g = game;
        return g != null && g.abilities().onSwapHands(p);
    }
}
