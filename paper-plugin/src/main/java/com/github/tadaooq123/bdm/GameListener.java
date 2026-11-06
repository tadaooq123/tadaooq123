package com.github.tadaooq123.bdm;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Locale;

import static net.kyori.adventure.text.Component.text;

public final class GameListener implements Listener {

    private final BdmPlugin plugin;

    public GameListener(BdmPlugin plugin) {
        this.plugin = plugin;
    }

    private Role role(Player p) {
        return plugin.game().role(p.getUniqueId());
    }

    // ---------------------------------------------------------------- общение

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.game().isRunning()) {
            return;
        }
        Player sender = event.getPlayer();
        Role role = role(sender);
        Settings s = plugin.settings();
        if (s.chatMode == Settings.ChatMode.OFF) {
            if (role != null) {
                event.setCancelled(true);
                sender.sendActionBar(text("Чат отключён — общайтесь голосом и жестами.", NamedTextColor.RED));
            }
            return;
        }
        if (role != null && !role.canSpeak()) {
            event.setCancelled(true);
            sender.sendActionBar(text("Ты НЕМОЙ — писать нельзя.", NamedTextColor.GOLD));
            return;
        }
        // Слепой не видит чат
        event.viewers().removeIf(a -> a instanceof Player v && role(v) == Role.BLIND && !v.equals(sender));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (role(p) == null) {
            return;
        }
        String msg = event.getMessage();
        int space = msg.indexOf(' ');
        String label = (space < 0 ? msg.substring(1) : msg.substring(1, space)).toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (plugin.settings().blockedCommands.contains(label)) {
            event.setCancelled(true);
            p.sendMessage(Msg.error("Во время игры эта команда запрещена."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        Role role = role(event.getPlayer());
        if (role != null && !plugin.settings().canWrite(role)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(text("Ты " + role.displayName().toUpperCase(Locale.ROOT)
                    + " — писать на табличках нельзя.", role.color()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        Role role = role(event.getPlayer());
        if (role != null && !plugin.settings().canWrite(role)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(text("Ты " + role.displayName().toUpperCase(Locale.ROOT)
                    + " — писать в книгах нельзя.", role.color()));
        }
    }

    // ---------------------------------------------------------------- способности

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Role role = role(event.getPlayer());
        if (role == null) {
            return;
        }
        event.setCancelled(true);
        plugin.abilities().use(event.getPlayer(), role);
    }

    // ---------------------------------------------------------------- игра

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.game().onDeath(event.getPlayer());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.game().isRunning()) {
            return;
        }
        if (event.getEntity() instanceof EnderDragon) {
            plugin.game().finish(true);
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer != null && role(killer) != null) {
            plugin.tasks().onKill(event.getEntityType(), plugin.game().onlineParticipants());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (role(event.getPlayer()) != null) {
            plugin.tasks().onWorld(event.getPlayer().getWorld().getEnvironment(), plugin.game().onlineParticipants());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.game().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.game().onQuit(event.getPlayer());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        plugin.pack().onStatus(event);
    }
}
