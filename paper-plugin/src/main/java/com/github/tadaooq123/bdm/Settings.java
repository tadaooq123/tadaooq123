package com.github.tadaooq123.bdm;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Разобранный config.yml. Пересоздаётся при /bdm reload. */
public final class Settings {

    public enum ChatMode { OFF, ROLES }

    public enum SwapMode { ROTATE, RANDOM }

    public final int swapIntervalSeconds;
    public final SwapMode swapMode;
    public final int swapWarningSeconds;
    public final int teamLives;
    public final boolean lateJoin;

    public final int startCountdown;
    public final boolean startHeal;
    public final boolean startClearInventory;

    public final ChatMode chatMode;
    public final Set<String> blockedCommands;

    public final boolean voiceChat;

    public final boolean blindDarkness;
    public final boolean blindEcholocation;
    public final double blindEchoRadius;
    public final boolean blindLavaWarning;
    public final boolean blindCanWrite;
    public final long blindCooldownMs;

    public final boolean deafMuteSounds;
    public final boolean deafPackEnabled;
    public final int deafPackPort;
    public final String deafPackHost;
    public final boolean deafCompass;
    public final double deafDangerRadius;
    public final long deafCooldownMs;
    public final int deafGlowSeconds;

    public final boolean muteCanWrite;
    public final long muteCooldownMs;
    public final double muteGestureRadius;

    public Settings(FileConfiguration c) {
        swapIntervalSeconds = (int) Math.max(30, Math.round(c.getDouble("swap-interval-minutes", 10) * 60));
        swapMode = parseEnum(SwapMode.class, c.getString("swap-mode"), SwapMode.ROTATE);
        swapWarningSeconds = Math.max(0, c.getInt("swap-warning-seconds", 10));
        teamLives = Math.max(0, c.getInt("team-lives", 5));
        lateJoin = c.getBoolean("late-join", true);

        startCountdown = Math.max(0, c.getInt("start.countdown-seconds", 5));
        startHeal = c.getBoolean("start.heal", true);
        startClearInventory = c.getBoolean("start.clear-inventory", false);

        chatMode = parseEnum(ChatMode.class, c.getString("chat.mode"), ChatMode.OFF);
        Set<String> cmds = new HashSet<>();
        for (String s : c.getStringList("chat.blocked-commands")) {
            cmds.add(s.toLowerCase(Locale.ROOT));
        }
        blockedCommands = Set.copyOf(cmds);

        voiceChat = c.getBoolean("voice-chat.enabled", true);

        blindDarkness = c.getBoolean("blind.darkness-pulse", true);
        blindEcholocation = c.getBoolean("blind.echolocation", true);
        blindEchoRadius = c.getDouble("blind.echolocation-radius", 16);
        blindLavaWarning = c.getBoolean("blind.lava-warning", true);
        blindCanWrite = c.getBoolean("blind.can-write", false);
        blindCooldownMs = seconds(c.getDouble("blind.ability-cooldown-seconds", 5));

        deafMuteSounds = c.getBoolean("deaf.mute-game-sounds", true);
        deafPackEnabled = c.getBoolean("deaf.resource-pack.enabled", true);
        deafPackPort = c.getInt("deaf.resource-pack.port", 8765);
        deafPackHost = c.getString("deaf.resource-pack.public-host", "").trim();
        deafCompass = c.getBoolean("deaf.danger-compass", true);
        deafDangerRadius = c.getDouble("deaf.danger-radius", 14);
        deafCooldownMs = seconds(c.getDouble("deaf.ability-cooldown-seconds", 20));
        deafGlowSeconds = Math.max(1, c.getInt("deaf.glow-seconds", 5));

        muteCanWrite = c.getBoolean("mute.can-write", false);
        muteCooldownMs = seconds(c.getDouble("mute.gesture-cooldown-seconds", 1.5));
        muteGestureRadius = c.getDouble("mute.gesture-radius", 32);
    }

    public boolean canWrite(Role role) {
        return switch (role) {
            case BLIND -> blindCanWrite;
            case MUTE -> muteCanWrite;
            case DEAF -> true;
        };
    }

    private static long seconds(double s) {
        return Math.max(0, Math.round(s * 1000));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
