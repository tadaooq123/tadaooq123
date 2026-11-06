package com.github.tadaooq123.bdm;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static net.kyori.adventure.text.Component.text;

/** Задание на каждый раунд между сменами ролей. */
public final class TaskManager {

    public enum Type { ITEM, KILL, WORLD }

    public record TaskDef(Type type, String target, int amount, String name, int fromRound) {
    }

    private static final List<PotionEffectType> CURSES = List.of(
            PotionEffectType.SLOWNESS, PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS, PotionEffectType.MINING_FATIGUE);

    private final BdmPlugin plugin;
    private final List<TaskDef> pool = new ArrayList<>();
    private final Deque<TaskDef> recent = new ArrayDeque<>();
    private final List<ItemStack> rewards = new ArrayList<>();
    private boolean enabled;
    private boolean rewardLife;
    private boolean failCurse;
    private int curseSeconds;

    private TaskDef current;
    private int progress;
    private boolean done;
    private int completed;

    public TaskManager(BdmPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration c) {
        pool.clear();
        rewards.clear();
        enabled = c.getBoolean("tasks.enabled", true);
        rewardLife = c.getBoolean("tasks.reward-life", true);
        failCurse = c.getBoolean("tasks.fail-curse", true);
        curseSeconds = Math.max(1, c.getInt("tasks.curse-seconds", 90));

        for (String s : c.getStringList("tasks.reward-items")) {
            String[] parts = s.split(":");
            Material m = Material.matchMaterial(parts[0].trim());
            int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
            if (m == null || !m.isItem()) {
                plugin.getLogger().warning("Неизвестный предмет награды: " + s);
                continue;
            }
            rewards.add(new ItemStack(m, Math.max(1, amount)));
        }

        for (Map<?, ?> raw : c.getMapList("tasks.list")) {
            try {
                Type type = Type.valueOf(String.valueOf(raw.get("type")).toUpperCase(Locale.ROOT));
                String target = String.valueOf(raw.get("target")).toUpperCase(Locale.ROOT);
                int amount = Math.max(1, parseInt(String.valueOf(raw.get("amount")), 1));
                Object nameObj = raw.get("name");
                String name = nameObj == null ? type + " " + target : String.valueOf(nameObj);
                Object fromObj = raw.get("from-round");
                int from = fromObj == null ? 1 : parseInt(String.valueOf(fromObj), 1);
                if (!valid(type, target)) {
                    plugin.getLogger().warning("Пропускаю задание с неизвестной целью: " + raw);
                    continue;
                }
                pool.add(new TaskDef(type, target, amount, name, from));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Пропускаю неверное задание: " + raw);
            }
        }
        if (pool.isEmpty()) {
            enabled = false;
        }
    }

    public void reset() {
        current = null;
        progress = 0;
        done = false;
        completed = 0;
        recent.clear();
    }

    public int completed() {
        return completed;
    }

    public boolean hasTask() {
        return current != null;
    }

    /** Новое задание на раунд. */
    public void newRound(int round, Collection<Player> players) {
        current = null;
        progress = 0;
        done = false;
        if (!enabled) {
            return;
        }
        List<TaskDef> options = new ArrayList<>();
        for (TaskDef t : pool) {
            if (t.fromRound() <= round && !recent.contains(t)) {
                options.add(t);
            }
        }
        if (options.isEmpty()) {
            for (TaskDef t : pool) {
                if (t.fromRound() <= round) {
                    options.add(t);
                }
            }
        }
        if (options.isEmpty()) {
            return;
        }
        current = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        recent.addLast(current);
        while (recent.size() > Math.min(5, pool.size() / 2)) {
            recent.removeFirst();
        }
        for (Player p : players) {
            p.sendMessage(Msg.info(text("Новое задание: ", NamedTextColor.GRAY)
                    .append(text(current.name(), NamedTextColor.YELLOW))));
        }
    }

    /** Раунд закончился: если задание не выполнено — проклятие. */
    public void endRound(Collection<Player> players) {
        if (current == null || done) {
            return;
        }
        if (failCurse) {
            PotionEffectType curse = CURSES.get(ThreadLocalRandom.current().nextInt(CURSES.size()));
            for (Player p : players) {
                p.addPotionEffect(new PotionEffect(curse, curseSeconds * 20, 0, false, true, true));
                p.playSound(p.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.8f);
                p.sendMessage(Msg.error("Задание провалено — на команду наложено проклятие на "
                        + curseSeconds + " сек."));
            }
        } else {
            for (Player p : players) {
                p.sendMessage(Msg.error("Задание провалено."));
            }
        }
    }

    /** Раз в секунду: считаем предметы во всех инвентарях команды. */
    public void tickItems(Collection<Player> players) {
        if (current == null || done || current.type() != Type.ITEM) {
            return;
        }
        Material m = Material.matchMaterial(current.target());
        if (m == null) {
            return;
        }
        int count = 0;
        for (Player p : players) {
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() == m) {
                    count += item.getAmount();
                }
            }
        }
        progress = Math.min(count, current.amount());
        if (progress >= current.amount()) {
            complete(players);
        }
    }

    public void onKill(EntityType type, Collection<Player> players) {
        if (current == null || done || current.type() != Type.KILL || !type.name().equals(current.target())) {
            return;
        }
        progress++;
        if (progress >= current.amount()) {
            complete(players);
        }
    }

    public void onWorld(World.Environment env, Collection<Player> players) {
        if (current == null || done || current.type() != Type.WORLD || !env.name().equals(current.target())) {
            return;
        }
        progress = current.amount();
        complete(players);
    }

    private void complete(Collection<Player> players) {
        done = true;
        completed++;
        if (rewardLife) {
            plugin.game().addLife();
        }
        for (Player p : players) {
            Map<Integer, ItemStack> leftover = new HashMap<>();
            for (ItemStack reward : rewards) {
                leftover.putAll(p.getInventory().addItem(reward.clone()));
            }
            leftover.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
            p.sendMessage(Msg.ok("Задание выполнено: " + current.name()
                    + (rewardLife ? " (+1 жизнь команде)" : "")));
        }
    }

    public Component barText() {
        if (current == null) {
            return text("Заданий нет — просто выживайте", NamedTextColor.GRAY);
        }
        if (done) {
            return text("✔ " + current.name(), NamedTextColor.GREEN);
        }
        String suffix = current.type() == Type.WORLD ? "" : " (" + progress + "/" + current.amount() + ")";
        return text("Задание: ", NamedTextColor.YELLOW).append(text(current.name() + suffix, NamedTextColor.WHITE));
    }

    public float barProgress() {
        if (current == null) {
            return 0f;
        }
        if (done) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, progress / (float) current.amount()));
    }

    public BossBar.Color barColor() {
        return done ? BossBar.Color.GREEN : BossBar.Color.YELLOW;
    }

    private static boolean valid(Type type, String target) {
        return switch (type) {
            case ITEM -> Material.matchMaterial(target) != null;
            case KILL -> {
                try {
                    EntityType.valueOf(target);
                    yield true;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
            case WORLD -> {
                try {
                    World.Environment.valueOf(target);
                    yield true;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
        };
    }

    private static int parseInt(String s, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
