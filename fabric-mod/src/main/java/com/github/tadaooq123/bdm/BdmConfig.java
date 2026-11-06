package com.github.tadaooq123.bdm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Настройки: config/blinddeafmute.json. Файл создаётся при первом запуске,
 * новые поля дописываются автоматически.
 */
public final class BdmConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String _readme = "Слепой, глухой, немой. swapMode: ROTATE (по кругу) или RANDOM (случайно, но роль всегда новая). "
            + "chatMode: OFF (чат запрещён) или ROLES (немой не пишет, слепой не видит чат). teamLives: 0 = бесконечно. "
            + "Задания: type ITEM (предметы у всей команды), KILL (убить мобов), WORLD (target nether или the_end).";

    /** Как часто меняются роли, минуты. */
    public double swapIntervalMinutes = 10;
    public String swapMode = "ROTATE";
    public int swapWarningSeconds = 10;
    public int teamLives = 5;
    /** Зашедшие во время игры получают самую редкую роль. */
    public boolean lateJoin = true;

    public int startCountdownSeconds = 5;
    public boolean startHeal = true;
    public boolean startClearInventory = false;

    public String chatMode = "OFF";
    public List<String> blockedCommands = new ArrayList<>(List.of(
            "msg", "tell", "w", "me", "say", "teammsg", "tm", "whisper", "r", "reply", "m"));

    public Blind blind = new Blind();
    public Deaf deaf = new Deaf();
    public Mute mute = new Mute();
    public Tasks tasks = new Tasks();

    public static final class Blind {
        /** Дополнительно эффект «Тьма» — экран пульсирует. */
        public boolean darknessPulse = true;
        public boolean echolocation = true;
        public double echolocationRadius = 16;
        public boolean lavaWarning = true;
        public boolean canWrite = false;
        public double abilityCooldownSeconds = 5;
    }

    public static final class Deaf {
        /** Глушить звуки игры (если у глухого не стоит мод — сервер обрывает звуки каждый тик). */
        public boolean muteGameSounds = true;
        public boolean dangerCompass = true;
        public double dangerRadius = 14;
        public double abilityCooldownSeconds = 20;
        public int glowSeconds = 5;
    }

    public static final class Mute {
        public boolean canWrite = false;
        public double gestureCooldownSeconds = 1.5;
        public double gestureRadius = 32;
    }

    public static final class Tasks {
        public boolean enabled = true;
        public boolean rewardLife = true;
        public List<String> rewardItems = new ArrayList<>(List.of("golden_apple:1", "cooked_beef:4"));
        public boolean failCurse = true;
        public int curseSeconds = 90;
        public List<TaskDef> list = defaultTasks();
    }

    public static final class TaskDef {
        public String type;
        public String target;
        public int amount = 1;
        public String name;
        public int fromRound = 1;

        public TaskDef() {
        }

        TaskDef(String type, String target, int amount, String name, int fromRound) {
            this.type = type;
            this.target = target;
            this.amount = amount;
            this.name = name;
            this.fromRound = fromRound;
        }
    }

    private static List<TaskDef> defaultTasks() {
        List<TaskDef> l = new ArrayList<>();
        l.add(new TaskDef("ITEM", "oak_log", 32, "Нарубить 32 дубовых бревна", 1));
        l.add(new TaskDef("ITEM", "cobblestone", 64, "Накопать стак булыжника", 1));
        l.add(new TaskDef("ITEM", "coal", 16, "Добыть 16 угля", 1));
        l.add(new TaskDef("ITEM", "bread", 8, "Испечь 8 хлеба", 1));
        l.add(new TaskDef("ITEM", "cooked_beef", 10, "Приготовить 10 стейков", 1));
        l.add(new TaskDef("ITEM", "white_bed", 1, "Сделать белую кровать", 1));
        l.add(new TaskDef("ITEM", "iron_ingot", 12, "Выплавить 12 железных слитков", 2));
        l.add(new TaskDef("ITEM", "shield", 3, "Сделать 3 щита", 2));
        l.add(new TaskDef("ITEM", "bucket", 2, "Сделать 2 ведра", 2));
        l.add(new TaskDef("ITEM", "gold_ingot", 6, "Добыть 6 золотых слитков", 2));
        l.add(new TaskDef("ITEM", "diamond", 3, "Найти 3 алмаза", 3));
        l.add(new TaskDef("ITEM", "obsidian", 10, "Добыть 10 обсидиана", 4));
        l.add(new TaskDef("ITEM", "ender_pearl", 8, "Собрать 8 жемчугов Края", 4));
        l.add(new TaskDef("ITEM", "blaze_rod", 5, "Добыть 5 стержней ифрита", 5));
        l.add(new TaskDef("ITEM", "ender_eye", 10, "Скрафтить 10 очей Края", 6));
        l.add(new TaskDef("KILL", "zombie", 6, "Убить 6 зомби", 1));
        l.add(new TaskDef("KILL", "skeleton", 4, "Убить 4 скелета", 1));
        l.add(new TaskDef("KILL", "spider", 4, "Убить 4 пауков", 1));
        l.add(new TaskDef("KILL", "creeper", 2, "Убить 2 криперов", 1));
        l.add(new TaskDef("KILL", "cow", 5, "Добыть мясо: убить 5 коров", 1));
        l.add(new TaskDef("KILL", "enderman", 2, "Убить 2 эндерменов", 3));
        l.add(new TaskDef("WORLD", "nether", 1, "Попасть в Незер", 3));
        l.add(new TaskDef("WORLD", "the_end", 1, "Попасть в Край", 7));
        return l;
    }

    // ---------------------------------------------------------------- удобные геттеры

    public int swapIntervalSeconds() {
        return (int) Math.max(30, Math.round(swapIntervalMinutes * 60));
    }

    public boolean randomSwap() {
        return "RANDOM".equalsIgnoreCase(swapMode);
    }

    public boolean chatOff() {
        return !"ROLES".equalsIgnoreCase(chatMode);
    }

    public boolean canWrite(Role role) {
        return switch (role) {
            case BLIND -> blind.canWrite;
            case MUTE -> mute.canWrite;
            case DEAF -> true;
        };
    }

    public long cooldownMs(Role role) {
        double s = switch (role) {
            case BLIND -> blind.abilityCooldownSeconds;
            case DEAF -> deaf.abilityCooldownSeconds;
            case MUTE -> mute.gestureCooldownSeconds;
        };
        return Math.max(0, Math.round(s * 1000));
    }

    // ---------------------------------------------------------------- файл

    public static BdmConfig load(Path file) {
        BdmConfig config = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(r, BdmConfig.class);
            } catch (IOException | RuntimeException e) {
                BdmMod.LOGGER.error("Не удалось прочитать {} — используются настройки по умолчанию", file, e);
                return new BdmConfig();
            }
        }
        if (config == null) {
            config = new BdmConfig();
        }
        config.fillMissing();
        config.save(file);
        return config;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            BdmMod.LOGGER.error("Не удалось сохранить {}", file, e);
        }
    }

    private void fillMissing() {
        if (blind == null) {
            blind = new Blind();
        }
        if (deaf == null) {
            deaf = new Deaf();
        }
        if (mute == null) {
            mute = new Mute();
        }
        if (tasks == null) {
            tasks = new Tasks();
        }
        if (tasks.list == null) {
            tasks.list = defaultTasks();
        }
        if (tasks.rewardItems == null) {
            tasks.rewardItems = new ArrayList<>();
        }
        if (blockedCommands == null) {
            blockedCommands = new ArrayList<>();
        }
    }
}
