package com.github.tadaooq123.bdm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;

/**
 * Пассивные и активные (клавиша F) способности ролей.
 *
 * Слепой: эхолокация мобов, сердцебиение рядом с врагом, шипение лавы, F — услышать союзников.
 * Глухой: компас опасности в action bar, F — подсветить союзников.
 * Немой: F — хлопок, Shift+F — тревога (звук + частицы + подсказка глухим).
 */
public final class Abilities {

    private final BdmPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> glowUntil = new HashMap<>();
    private final Map<UUID, Long> actionBarLock = new HashMap<>();
    private final Map<UUID, Long> micHints = new ConcurrentHashMap<>();

    public Abilities(BdmPlugin plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        for (UUID id : glowUntil.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setGlowing(false);
            }
        }
        glowUntil.clear();
        cooldowns.clear();
        actionBarLock.clear();
        micHints.clear();
    }

    /** Вызывается каждый тик, пока идёт игра. */
    public void tick(long tick, List<Player> participants) {
        Settings s = plugin.settings();
        GameManager game = plugin.game();
        for (Player p : participants) {
            Role r = game.role(p.getUniqueId());
            if (r == null) {
                continue;
            }
            switch (r) {
                case DEAF -> {
                    if (s.deafMuteSounds && !plugin.pack().isLoaded(p)) {
                        p.stopAllSounds();
                    }
                    if (s.deafCompass && tick % 10 == 0) {
                        dangerCompass(p, s);
                    }
                }
                case BLIND -> {
                    if (tick % 20 == 0 && s.blindEcholocation) {
                        echolocation(p, s, tick);
                    }
                    if (tick % 30 == 0 && s.blindLavaWarning) {
                        lavaWarning(p);
                    }
                }
                case MUTE -> {
                }
            }
        }
        if (tick % 20 == 0) {
            expireGlows();
        }
    }

    // ---------------------------------------------------------------- клавиша F

    public void use(Player p, Role role) {
        Settings s = plugin.settings();
        long now = System.currentTimeMillis();
        long cd = switch (role) {
            case BLIND -> s.blindCooldownMs;
            case DEAF -> s.deafCooldownMs;
            case MUTE -> s.muteCooldownMs;
        };
        Long next = cooldowns.get(p.getUniqueId());
        if (next != null && now < next) {
            long left = (next - now + 999) / 1000;
            actionBar(p, text("Перезарядка: " + left + " сек", NamedTextColor.GRAY), 0);
            return;
        }
        cooldowns.put(p.getUniqueId(), now + cd);
        switch (role) {
            case BLIND -> echoAllies(p);
            case DEAF -> glowAllies(p, s);
            case MUTE -> gesture(p, p.isSneaking(), s);
        }
    }

    private void echoAllies(Player blind) {
        int heard = 0;
        for (Player other : plugin.game().onlineParticipants()) {
            if (other.equals(blind) || other.getWorld() != blind.getWorld()) {
                continue;
            }
            double d = other.getLocation().distance(blind.getLocation());
            if (d > 96) {
                continue;
            }
            Role r = plugin.game().role(other.getUniqueId());
            float pitch = r == Role.BLIND ? 0.7f : r == Role.DEAF ? 1.0f : 1.4f;
            float volume = (float) Math.max(1.0, Math.min(6.0, d / 14.0));
            blind.playSound(other.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.MASTER, volume, pitch);
            heard++;
        }
        if (heard == 0) {
            blind.playSound(blind.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 1f, 0.5f);
            actionBar(blind, text("Ты прислушался… никого поблизости.", NamedTextColor.GRAY), 1500);
        } else {
            actionBar(blind, text("Ты прислушался: колокольчики — это союзники.", NamedTextColor.GRAY), 1500);
        }
    }

    private void glowAllies(Player deaf, Settings s) {
        long until = System.currentTimeMillis() + s.deafGlowSeconds * 1000L;
        int count = 0;
        for (Player other : plugin.game().onlineParticipants()) {
            if (other.equals(deaf)) {
                continue;
            }
            other.setGlowing(true);
            glowUntil.put(other.getUniqueId(), until);
            count++;
        }
        actionBar(deaf, text(count == 0 ? "Союзников нет в сети."
                : "Союзники подсвечены на " + s.deafGlowSeconds + " сек.", NamedTextColor.AQUA), 1500);
    }

    private void gesture(Player mute, boolean alarm, Settings s) {
        Location loc = mute.getLocation();
        World w = mute.getWorld();
        Location head = loc.clone().add(0, 2.3, 0);
        mute.swingMainHand();
        if (alarm) {
            w.playSound(loc, Sound.BLOCK_BELL_USE, SoundCategory.PLAYERS, 2.5f, 1.3f);
            w.spawnParticle(Particle.ANGRY_VILLAGER, head, 6, 0.4, 0.3, 0.4, 0);
        } else {
            w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_SNARE, SoundCategory.PLAYERS, 2.5f, 1.7f);
            w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 2.0f, 1.2f);
            w.spawnParticle(Particle.HAPPY_VILLAGER, head, 8, 0.4, 0.3, 0.4, 0);
        }
        double r2 = s.muteGestureRadius * s.muteGestureRadius;
        for (Player other : plugin.game().onlineParticipants()) {
            if (other.equals(mute) || other.getWorld() != w
                    || plugin.game().role(other.getUniqueId()) != Role.DEAF
                    || other.getLocation().distanceSquared(loc) > r2) {
                continue;
            }
            String arrow = Msg.arrow(other.getLocation().getYaw(),
                    loc.getX() - other.getLocation().getX(), loc.getZ() - other.getLocation().getZ());
            Component msg = alarm
                    ? text("‼ " + mute.getName() + " бьёт тревогу " + arrow, NamedTextColor.RED, TextDecoration.BOLD)
                    : text("✋ " + mute.getName() + " машет тебе " + arrow, NamedTextColor.GOLD);
            actionBar(other, msg, 2500);
        }
    }

    // ---------------------------------------------------------------- пассивки

    private void echolocation(Player p, Settings s, long tick) {
        LivingEntity enemy = nearestEnemy(p, s.blindEchoRadius);
        if (enemy == null) {
            return;
        }
        double d = enemy.getLocation().distance(p.getLocation());
        boolean close = d < 6;
        if (!close && tick % 40 != 0) {
            return; // далеко — пищит реже
        }
        float pitch = (float) Math.max(0.5, Math.min(2.0, 0.6 + 1.4 * (1 - d / s.blindEchoRadius)));
        float volume = (float) Math.max(1.0, s.blindEchoRadius / 16.0);
        p.playSound(enemy.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.MASTER, volume, pitch);
        if (close) {
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.2f, 1.0f);
        }
    }

    private void lavaWarning(Player p) {
        Block base = p.getLocation().getBlock();
        Block nearest = null;
        double best = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block b = base.getRelative(dx, dy, dz);
                    if (b.getType() == Material.LAVA) {
                        double d = dx * dx + dy * dy + dz * dz;
                        if (d < best) {
                            best = d;
                            nearest = b;
                        }
                    }
                }
            }
        }
        if (nearest != null) {
            Location at = nearest.getLocation().add(0.5, 0.5, 0.5);
            p.playSound(at, Sound.BLOCK_LAVA_POP, SoundCategory.MASTER, 1.5f, 0.8f);
            p.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.MASTER, 0.4f, 1.6f);
        }
    }

    private void dangerCompass(Player p, Settings s) {
        Long lock = actionBarLock.get(p.getUniqueId());
        if (lock != null && System.currentTimeMillis() < lock) {
            return;
        }
        LivingEntity enemy = nearestEnemy(p, s.deafDangerRadius);
        if (enemy == null) {
            return;
        }
        Location me = p.getLocation();
        Location it = enemy.getLocation();
        String arrow = Msg.arrow(me.getYaw(), it.getX() - me.getX(), it.getZ() - me.getZ());
        int d = (int) Math.round(it.distance(me));
        Component msg;
        if (enemy instanceof Creeper creeper && creeper.getFuseTicks() > 0) {
            msg = text("☠ КРИПЕР ШИПИТ " + arrow + " " + d + "м", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        } else {
            NamedTextColor color = d <= 5 ? NamedTextColor.RED : NamedTextColor.YELLOW;
            msg = text("⚠ ", color).append(enemy.name().color(color))
                    .append(text(" " + arrow + " " + d + "м", color));
        }
        p.sendActionBar(msg);
    }

    private static LivingEntity nearestEnemy(Player p, double radius) {
        LivingEntity best = null;
        double bestSq = radius * radius;
        Location me = p.getLocation();
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Enemy) || !(e instanceof LivingEntity le) || le.isDead()) {
                continue;
            }
            double sq = e.getLocation().distanceSquared(me);
            if (sq < bestSq) {
                bestSq = sq;
                best = le;
            }
        }
        return best;
    }

    private void expireGlows() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = glowUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (now >= e.getValue()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    p.setGlowing(false);
                }
                it.remove();
            }
        }
    }

    private void actionBar(Player p, Component msg, long lockMs) {
        p.sendActionBar(msg);
        if (lockMs > 0) {
            actionBarLock.put(p.getUniqueId(), System.currentTimeMillis() + lockMs);
        }
    }

    /** Немой пытается говорить в голосовой чат (вызывается из потока голосового чата). */
    public void micBlocked(UUID id) {
        long now = System.currentTimeMillis();
        Long last = micHints.get(id);
        if (!plugin.isEnabled() || last != null && now - last < 4000) {
            return;
        }
        micHints.put(id, now);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                actionBar(p, text("Ты НЕМОЙ — тебя никто не слышит. Используй жесты (F).",
                        NamedTextColor.GOLD), 2000);
            }
        });
    }
}
