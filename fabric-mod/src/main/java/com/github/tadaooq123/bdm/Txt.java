package com.github.tadaooq123.bdm;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Короткие помощники для цветного текста. */
public final class Txt {

    private Txt() {
    }

    public static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("СГН").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent of(String s, ChatFormatting... style) {
        return Component.literal(s).withStyle(style);
    }

    public static MutableComponent info(String s) {
        return prefix().append(of(s, ChatFormatting.GRAY));
    }

    public static MutableComponent info(Component c) {
        return prefix().append(c);
    }

    public static MutableComponent ok(String s) {
        return prefix().append(of(s, ChatFormatting.GREEN));
    }

    public static MutableComponent error(String s) {
        return prefix().append(of(s, ChatFormatting.RED));
    }

    public static MutableComponent role(Role r) {
        return of(r.displayName(), r.color(), ChatFormatting.BOLD);
    }

    public static String time(int seconds) {
        int s = Math.max(0, seconds);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    /** Стрелка в сторону цели относительно взгляда игрока. */
    public static String arrow(double playerYaw, double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = targetYaw - playerYaw;
        rel = ((rel % 360) + 540) % 360 - 180;
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
