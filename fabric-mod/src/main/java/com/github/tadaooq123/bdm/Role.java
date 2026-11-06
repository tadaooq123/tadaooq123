package com.github.tadaooq123.bdm;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/** Три роли челленджа. Каждая что-то теряет и получает маленькую способность взамен. */
public enum Role {

    BLIND("Слепой", ChatFormatting.DARK_GRAY, false, true, true,
            "Ты ничего не видишь, но всё слышишь и можешь говорить.",
            "F — «Эхо»: услышать, где союзники. Мобов и лаву слышно издалека."),

    DEAF("Глухой", ChatFormatting.AQUA, true, false, true,
            "Ты не слышишь ни звуков игры, ни голосового чата. Говорить можешь.",
            "F — «Взгляд»: союзники подсвечиваются. Внизу экрана — компас опасности."),

    MUTE("Немой", ChatFormatting.GOLD, true, true, false,
            "Ты всё видишь и слышишь, но не можешь говорить и писать.",
            "F — «Хлопок», Shift+F — «Тревога»: звук и частицы, чтобы подать знак.");

    private final String displayName;
    private final ChatFormatting color;
    private final boolean canSee;
    private final boolean canHear;
    private final boolean canSpeak;
    private final String description;
    private final String ability;

    Role(String displayName, ChatFormatting color, boolean canSee, boolean canHear, boolean canSpeak,
         String description, String ability) {
        this.displayName = displayName;
        this.color = color;
        this.canSee = canSee;
        this.canHear = canHear;
        this.canSpeak = canSpeak;
        this.description = description;
        this.ability = ability;
    }

    public String displayName() {
        return displayName;
    }

    public ChatFormatting color() {
        return color;
    }

    public boolean canSee() {
        return canSee;
    }

    public boolean canHear() {
        return canHear;
    }

    public boolean canSpeak() {
        return canSpeak;
    }

    public String description() {
        return description;
    }

    public String ability() {
        return ability;
    }

    /** Следующая роль по кругу: слепой → глухой → немой → слепой. */
    public Role next() {
        Role[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Role byId(int id) {
        Role[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }

    public static Role parse(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "blind", "слепой", "b" -> BLIND;
            case "deaf", "глухой", "d" -> DEAF;
            case "mute", "немой", "m" -> MUTE;
            default -> null;
        };
    }
}
