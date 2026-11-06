package com.github.tadaooq123.bdm;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Пассивные и активные (клавиша F) способности ролей.
 *
 * Слепой: эхолокация мобов, сердцебиение рядом с врагом, шипение лавы, F — услышать союзников.
 * Глухой: компас опасности над хотбаром, F — подсветить союзников.
 * Немой: F — хлопок, Shift+F — тревога (звук + частицы + подсказка глухим).
 */
public final class Abilities {

    private final Game game;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> glowUntil = new HashMap<>();
    private final Map<UUID, Long> actionBarLock = new HashMap<>();
    private final Map<UUID, Long> micHints = new ConcurrentHashMap<>();

    public Abilities(Game game) {
        this.game = game;
    }

    public void reset() {
        for (UUID id : glowUntil.keySet()) {
            ServerPlayer p = game.server().getPlayerList().getPlayer(id);
            if (p != null) {
                p.setGlowingTag(false);
            }
        }
        glowUntil.clear();
        cooldowns.clear();
        actionBarLock.clear();
        micHints.clear();
    }

    /** Каждый тик, пока идёт игра. */
    public void tick(long tick, List<ServerPlayer> participants) {
        BdmConfig c = game.config();
        for (ServerPlayer p : participants) {
            Role r = game.role(p.getUUID());
            if (r == null || p.isSpectator()) {
                continue;
            }
            switch (r) {
                case DEAF -> {
                    // У кого стоит мод — звук глушит клиент. Иначе сервер обрывает звуки каждый тик.
                    if (c.deaf.muteGameSounds && !ServerPlayNetworking.canSend(p, RolePayload.TYPE)) {
                        p.connection.send(new ClientboundStopSoundPacket(null, null));
                    }
                    if (c.deaf.dangerCompass && tick % 10 == 0) {
                        dangerCompass(p, c);
                    }
                }
                case BLIND -> {
                    if (c.blind.echolocation && tick % 20 == 0) {
                        echolocation(p, c, tick);
                    }
                    if (c.blind.lavaWarning && tick % 30 == 0) {
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

    /** @return true — нажатие F перехвачено способностью (обмен рук отменяется). */
    public boolean onSwapHands(ServerPlayer p) {
        Role role = game.role(p.getUUID());
        if (role == null || p.isSpectator()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long next = cooldowns.get(p.getUUID());
        if (next != null && now < next) {
            long left = (next - now + 999) / 1000;
            actionBar(p, Txt.of("Перезарядка: " + left + " сек", ChatFormatting.GRAY), 0);
            return true;
        }
        cooldowns.put(p.getUUID(), now + game.config().cooldownMs(role));
        switch (role) {
            case BLIND -> echoAllies(p);
            case DEAF -> glowAllies(p);
            case MUTE -> gesture(p, p.isShiftKeyDown());
        }
        return true;
    }

    private void echoAllies(ServerPlayer blind) {
        int heard = 0;
        for (ServerPlayer other : game.onlineParticipants()) {
            if (other == blind || other.level() != blind.level() || other.isSpectator()) {
                continue;
            }
            double d = other.distanceTo(blind);
            if (d > 96) {
                continue;
            }
            Role r = game.role(other.getUUID());
            float pitch = r == Role.BLIND ? 0.7f : r == Role.DEAF ? 1.0f : 1.4f;
            float volume = (float) Math.max(1.0, Math.min(6.0, d / 14.0));
            Game.sound(blind, SoundEvents.NOTE_BLOCK_BELL, other.getX(), other.getY(), other.getZ(), volume, pitch);
            heard++;
        }
        if (heard == 0) {
            Game.sound(blind, SoundEvents.NOTE_BLOCK_BASS, blind.getX(), blind.getY(), blind.getZ(), 1f, 0.5f);
            actionBar(blind, Txt.of("Ты прислушался… никого поблизости.", ChatFormatting.GRAY), 1500);
        } else {
            actionBar(blind, Txt.of("Ты прислушался: колокольчики — это союзники.", ChatFormatting.GRAY), 1500);
        }
    }

    private void glowAllies(ServerPlayer deaf) {
        int seconds = Math.max(1, game.config().deaf.glowSeconds);
        long until = System.currentTimeMillis() + seconds * 1000L;
        int count = 0;
        for (ServerPlayer other : game.onlineParticipants()) {
            if (other == deaf) {
                continue;
            }
            other.setGlowingTag(true);
            glowUntil.put(other.getUUID(), until);
            count++;
        }
        actionBar(deaf, Txt.of(count == 0 ? "Союзников нет в сети."
                : "Союзники подсвечены на " + seconds + " сек.", ChatFormatting.AQUA), 1500);
    }

    private void gesture(ServerPlayer mute, boolean alarm) {
        ServerLevel level = mute.serverLevel();
        double x = mute.getX();
        double y = mute.getY();
        double z = mute.getZ();
        mute.swing(InteractionHand.MAIN_HAND, true);
        if (alarm) {
            level.playSound(null, x, y, z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.5f, 1.3f);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, x, y + 2.3, z, 6, 0.4, 0.3, 0.4, 0);
        } else {
            level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_SNARE, SoundSource.PLAYERS, 2.5f, 1.7f);
            level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, 2.0f, 1.2f);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 2.3, z, 8, 0.4, 0.3, 0.4, 0);
        }
        double r = game.config().mute.gestureRadius;
        for (ServerPlayer other : game.onlineParticipants()) {
            if (other == mute || other.level() != level || game.role(other.getUUID()) != Role.DEAF
                    || other.distanceToSqr(mute) > r * r) {
                continue;
            }
            String arrow = Txt.arrow(other.getYRot(), x - other.getX(), z - other.getZ());
            String name = mute.getGameProfile().getName();
            MutableComponent msg = alarm
                    ? Txt.of("‼ " + name + " бьёт тревогу " + arrow, ChatFormatting.RED, ChatFormatting.BOLD)
                    : Txt.of("✋ " + name + " машет тебе " + arrow, ChatFormatting.GOLD);
            actionBar(other, msg, 2500);
        }
    }

    // ---------------------------------------------------------------- пассивки

    private void echolocation(ServerPlayer p, BdmConfig c, long tick) {
        double radius = c.blind.echolocationRadius;
        LivingEntity enemy = nearestEnemy(p, radius);
        if (enemy == null) {
            return;
        }
        double d = enemy.distanceTo(p);
        boolean close = d < 6;
        if (!close && tick % 40 != 0) {
            return; // далеко — пищит реже
        }
        float pitch = (float) Math.max(0.5, Math.min(2.0, 0.6 + 1.4 * (1 - d / radius)));
        float volume = (float) Math.max(1.0, radius / 16.0);
        Game.sound(p, SoundEvents.NOTE_BLOCK_BASEDRUM, enemy.getX(), enemy.getY(), enemy.getZ(), volume, pitch);
        if (close) {
            Game.sound(p, Game.holder(SoundEvents.WARDEN_HEARTBEAT), p.getX(), p.getY(), p.getZ(), 1.2f, 1f);
        }
    }

    private void lavaWarning(ServerPlayer p) {
        BlockPos base = p.blockPosition();
        BlockPos nearest = null;
        int best = Integer.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    if (p.level().getFluidState(pos).is(FluidTags.LAVA)) {
                        int d = dx * dx + dy * dy + dz * dz;
                        if (d < best) {
                            best = d;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        if (nearest != null) {
            double x = nearest.getX() + 0.5;
            double y = nearest.getY() + 0.5;
            double z = nearest.getZ() + 0.5;
            Game.sound(p, Game.holder(SoundEvents.LAVA_POP), x, y, z, 1.5f, 0.8f);
            Game.sound(p, Game.holder(SoundEvents.FIRE_EXTINGUISH), x, y, z, 0.4f, 1.6f);
        }
    }

    private void dangerCompass(ServerPlayer p, BdmConfig c) {
        Long lock = actionBarLock.get(p.getUUID());
        if (lock != null && System.currentTimeMillis() < lock) {
            return;
        }
        LivingEntity enemy = nearestEnemy(p, c.deaf.dangerRadius);
        if (enemy == null) {
            return;
        }
        String arrow = Txt.arrow(p.getYRot(), enemy.getX() - p.getX(), enemy.getZ() - p.getZ());
        int d = (int) Math.round(enemy.distanceTo(p));
        Component msg;
        if (enemy instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            msg = Txt.of("☠ КРИПЕР ШИПИТ " + arrow + " " + d + "м", ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        } else {
            ChatFormatting color = d <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW;
            msg = Txt.of("⚠ ", color).append(enemy.getName().copy().withStyle(color))
                    .append(Txt.of(" " + arrow + " " + d + "м", color));
        }
        p.displayClientMessage(msg, true);
    }

    private static LivingEntity nearestEnemy(ServerPlayer p, double radius) {
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive())) {
            double sq = e.distanceToSqr(p);
            if (sq < bestSq) {
                bestSq = sq;
                best = e;
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
                ServerPlayer p = game.server().getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    p.setGlowingTag(false);
                }
                it.remove();
            }
        }
    }

    private void actionBar(ServerPlayer p, Component msg, long lockMs) {
        p.displayClientMessage(msg, true);
        if (lockMs > 0) {
            actionBarLock.put(p.getUUID(), System.currentTimeMillis() + lockMs);
        }
    }

    /** Немой пытается говорить в голосовой чат (вызывается из потока голосового чата). */
    public void micBlocked(UUID id) {
        long now = System.currentTimeMillis();
        Long last = micHints.get(id);
        if (last != null && now - last < 4000) {
            return;
        }
        micHints.put(id, now);
        game.server().execute(() -> {
            ServerPlayer p = game.server().getPlayerList().getPlayer(id);
            if (p != null) {
                actionBar(p, Txt.of("Ты НЕМОЙ — тебя никто не слышит. Используй жесты (F).", ChatFormatting.GOLD), 2000);
            }
        });
    }
}
