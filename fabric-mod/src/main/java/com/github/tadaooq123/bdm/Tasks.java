package com.github.tadaooq123.bdm;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Задание на каждый раунд между сменами ролей. */
public final class Tasks {

    private enum Type { ITEM, KILL, WORLD }

    private record Task(Type type, Item item, EntityType<?> entity, ResourceKey<Level> world,
                        int amount, String name, int fromRound) {
    }

    private static final List<Holder<MobEffect>> CURSES = List.of(
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN);

    private final Game game;
    private final List<Task> pool = new ArrayList<>();
    private final List<ItemStack> rewards = new ArrayList<>();
    private final Deque<Task> recent = new ArrayDeque<>();

    private Task current;
    private int progress;
    private boolean done;
    private int completed;

    public Tasks(Game game) {
        this.game = game;
    }

    public void load(BdmConfig config) {
        pool.clear();
        rewards.clear();
        for (String s : config.tasks.rewardItems) {
            int colon = s.lastIndexOf(':');
            String id = s;
            int count = 1;
            if (colon > 0 && s.substring(colon + 1).matches("\\d+")) {
                id = s.substring(0, colon);
                count = Integer.parseInt(s.substring(colon + 1));
            }
            Item item = item(id);
            if (item == null) {
                BdmMod.LOGGER.warn("Неизвестный предмет награды: {}", s);
                continue;
            }
            rewards.add(new ItemStack(item, Math.max(1, count)));
        }
        for (BdmConfig.TaskDef d : config.tasks.list) {
            if (d == null || d.type == null || d.target == null) {
                continue;
            }
            Type type;
            try {
                type = Type.valueOf(d.type.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                BdmMod.LOGGER.warn("Неизвестный тип задания: {}", d.type);
                continue;
            }
            Item item = null;
            EntityType<?> entity = null;
            ResourceKey<Level> world = null;
            switch (type) {
                case ITEM -> item = item(d.target);
                case KILL -> {
                    ResourceLocation id = ResourceLocation.tryParse(d.target.toLowerCase(Locale.ROOT));
                    entity = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                }
                case WORLD -> {
                    String t = d.target.toLowerCase(Locale.ROOT);
                    world = t.contains("nether") ? Level.NETHER : t.contains("end") ? Level.END : null;
                }
            }
            if (item == null && entity == null && world == null) {
                BdmMod.LOGGER.warn("Пропускаю задание с неизвестной целью: {} {}", d.type, d.target);
                continue;
            }
            String name = d.name == null ? d.type + " " + d.target : d.name;
            pool.add(new Task(type, item, entity, world, Math.max(1, d.amount), name, Math.max(1, d.fromRound)));
        }
    }

    private static Item item(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
        if (loc == null || !BuiltInRegistries.ITEM.containsKey(loc)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(loc);
    }

    private boolean enabled() {
        return game.config().tasks.enabled && !pool.isEmpty();
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

    public void newRound(int round, Collection<ServerPlayer> players) {
        current = null;
        progress = 0;
        done = false;
        if (!enabled()) {
            return;
        }
        List<Task> options = new ArrayList<>();
        for (Task t : pool) {
            if (t.fromRound() <= round && !recent.contains(t)) {
                options.add(t);
            }
        }
        if (options.isEmpty()) {
            for (Task t : pool) {
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
        for (ServerPlayer p : players) {
            p.sendSystemMessage(Txt.info(Txt.of("Новое задание: ", ChatFormatting.GRAY)
                    .append(Txt.of(current.name(), ChatFormatting.YELLOW))));
        }
    }

    /** Раунд закончился: если задание не выполнено — проклятие. */
    public void endRound(Collection<ServerPlayer> players) {
        if (current == null || done) {
            return;
        }
        BdmConfig.Tasks c = game.config().tasks;
        if (c.failCurse) {
            Holder<MobEffect> curse = CURSES.get(ThreadLocalRandom.current().nextInt(CURSES.size()));
            int seconds = Math.max(1, c.curseSeconds);
            for (ServerPlayer p : players) {
                p.addEffect(new MobEffectInstance(curse, seconds * 20, 0, false, true, true));
                Game.sound(p, Game.holder(SoundEvents.WITCH_CELEBRATE), p.getX(), p.getY(), p.getZ(), 1f, 0.8f);
                p.sendSystemMessage(Txt.error("Задание провалено — на команду наложено проклятие на " + seconds + " сек."));
            }
        } else {
            for (ServerPlayer p : players) {
                p.sendSystemMessage(Txt.error("Задание провалено."));
            }
        }
    }

    /** Раз в секунду: считаем предметы во всех инвентарях команды. */
    public void tickItems(Collection<ServerPlayer> players) {
        if (current == null || done || current.type() != Type.ITEM) {
            return;
        }
        int count = 0;
        for (ServerPlayer p : players) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(current.item())) {
                    count += stack.getCount();
                }
            }
        }
        progress = Math.min(count, current.amount());
        if (progress >= current.amount()) {
            complete(players);
        }
    }

    public void onKill(EntityType<?> type, Collection<ServerPlayer> players) {
        if (current == null || done || current.type() != Type.KILL || current.entity() != type) {
            return;
        }
        progress++;
        if (progress >= current.amount()) {
            complete(players);
        }
    }

    public void onWorld(ResourceKey<Level> world, Collection<ServerPlayer> players) {
        if (current == null || done || current.type() != Type.WORLD || current.world() != world) {
            return;
        }
        progress = current.amount();
        complete(players);
    }

    private void complete(Collection<ServerPlayer> players) {
        done = true;
        completed++;
        boolean life = game.config().tasks.rewardLife && game.config().teamLives > 0;
        if (life) {
            game.addLife();
        }
        for (ServerPlayer p : players) {
            for (ItemStack reward : rewards) {
                ItemStack copy = reward.copy();
                if (!p.getInventory().add(copy) && !copy.isEmpty()) {
                    p.drop(copy, false);
                }
            }
            Game.sound(p, Game.holder(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), p.getX(), p.getY(), p.getZ(), 0.8f, 1f);
            p.sendSystemMessage(Txt.ok("Задание выполнено: " + current.name() + (life ? " (+1 жизнь команде)" : "")));
        }
    }

    public Component barText() {
        if (current == null) {
            return Txt.of("Заданий нет — просто выживайте", ChatFormatting.GRAY);
        }
        if (done) {
            return Txt.of("✔ " + current.name(), ChatFormatting.GREEN);
        }
        String suffix = current.type() == Type.WORLD ? "" : " (" + progress + "/" + current.amount() + ")";
        return Txt.of("Задание: ", ChatFormatting.YELLOW).append(Txt.of(current.name() + suffix, ChatFormatting.WHITE));
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

    public BossEvent.BossBarColor barColor() {
        return done ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW;
    }
}
