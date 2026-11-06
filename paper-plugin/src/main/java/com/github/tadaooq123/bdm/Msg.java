package com.github.tadaooq123.bdm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import static net.kyori.adventure.text.Component.text;

/** Короткие помощники для цветных сообщений. */
public final class Msg {

    private static final Component PREFIX = text("[", NamedTextColor.DARK_GRAY)
            .append(text("СГН", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(text("] ", NamedTextColor.DARK_GRAY));

    private Msg() {
    }

    public static Component info(String s) {
        return PREFIX.append(text(s, NamedTextColor.GRAY));
    }

    public static Component info(Component c) {
        return PREFIX.append(c.colorIfAbsent(NamedTextColor.GRAY));
    }

    public static Component ok(String s) {
        return PREFIX.append(text(s, NamedTextColor.GREEN));
    }

    public static Component error(String s) {
        return PREFIX.append(text(s, NamedTextColor.RED));
    }

    public static Component role(Role r) {
        return text(r.displayName(), r.color(), TextDecoration.BOLD);
    }

    public static String time(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Стрелка в сторону цели относительно взгляда игрока. */
    public static String arrow(double playerYaw, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = targetYaw - playerYaw;
        rel = ((rel % 360) + 540) % 360 - 180; // в диапазон [-180, 180)
        int index = (int) Math.round(rel / 45.0);
        index = ((index % 8) + 8) % 8;
        return switch (index) {
            case 0 -> "↑";
            case 1 -> "↗";
            case 2 -> "→";
            case 3 -> "↘";
            case 4 -> "↓";
            case 5 -> "↙";
            case 6 -> "←";
            default -> "↖";
        };
    }
}
