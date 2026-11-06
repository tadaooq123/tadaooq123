package com.github.tadaooq123.bdm;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Состояние игры на одном сервере (или в одном открытом мире). */
public final class Game {

    public enum State { IDLE, STARTING, RUNNING }

    private final MinecraftServer server;
    private final Map<UUID, Role> roles = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final ServerBossEvent timerBar = new ServerBossEvent(Component.empty(),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final ServerBossEvent taskBar = new ServerBossEvent(Component.empty(),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    private final Tasks tasks;
    private final Abilities abilities;

    private volatile State state = State.IDLE;
    private long tick;
    private int countdown;
    private int secondsUntilSwap;
    private int lives;
    private int round;
    private int deaths;
    private long startedAt;
    private boolean dragonKilledAtStart;

    public Game(MinecraftServer server) {
        this.server = server;
        this.tasks = new Tasks(this);
        this.abilities = new Abilities(this);
        tasks.load(config());
    }

    // ---------------------------------------------------------------- доступ

    public BdmConfig config() {
        return BdmMod.config();
    }

    public MinecraftServer server() {
        return server;
    }

    public Tasks tasks() {
        return tasks;
    }

    public Abilities abilities() {
        return abilities;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** Роль участника во время игры, иначе null. Потокобезопасно. */
    public Role role(UUID id) {
        return state == State.RUNNING ? roles.get(id) : null;
    }

    public List<ServerPlayer> onlineParticipants() {
        List<ServerPlayer> list = new ArrayList<>(roles.size());
        for (UUID id : roles.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public int lives() {
        return lives;
    }

    public void reloadTasks() {
        tasks.load(config());
    }

    // ---------------------------------------------------------------- старт / стоп

    public void start(CommandSourceStack source) {
        if (state != State.IDLE) {
            source.sendFailure(Txt.error("Игра уже идёт. Сначала /bdm stop"));
            return;
        }
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) {
            source.sendFailure(Txt.error("Нет игроков."));
            return;
        }
        BdmConfig c = config();
        roles.clear();
        names.clear();
        Collections.shuffle(players);
        List<Role> order = shuffledRoles();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer p = players.get(i);
            roles.put(p.getUUID(), order.get(i % order.size()));
            names.put(p.getUUID(), p.getGameProfile().getName());
        }
        lives = c.teamLives;
        round = 1;
        deaths = 0;
        tasks.load(c);
        tasks.reset();
        abilities.reset();
        for (ServerPlayer p : players) {
            p.setGameMode(GameType.SURVIVAL);
            if (c.startClearInventory) {
                p.getInventory().clearContent();
            }
            if (c.startHeal) {
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFoodLevel(20);
                p.getFoodData().setSaturation(10f);
                p.clearFire();
            }
        }
        if (players.size() < 3) {
            source.sendSystemMessage(Txt.info("Игроков меньше трёх — не все роли будут заняты, но играть можно."));
        }
        countdown = Math.max(0, c.startCountdownSeconds);
        state = State.STARTING;
        broadcast(Txt.info(Txt.of("Игра «Слепой, глухой, немой» начинается! Роли меняются каждые "
                + Txt.time(c.swapIntervalSeconds()) + ".", ChatFormatting.YELLOW)));
    }

    public void stop(CommandSourceStack source) {
        if (state == State.IDLE) {
            source.sendFailure(Txt.error("Игра не запущена."));
            return;
        }
        broadcast(Txt.info("Игра остановлена."));
        cleanup();
    }

    public void shutdown() {
        if (state != State.IDLE) {
            cleanup();
        }
    }

    private void begin() {
        state = State.RUNNING;
        startedAt = System.currentTimeMillis();
        secondsUntilSwap = config().swapIntervalSeconds();
        EndDragonFight fight = dragonFight();
        dragonKilledAtStart = fight != null && fight.hasPreviouslyKilledDragon();
        List<ServerPlayer> online = onlineParticipants();
        for (ServerPlayer p : online) {
            applyRole(p, roles.get(p.getUUID()), true);
            timerBar.addPlayer(p);
            taskBar.addPlayer(p);
        }
        broadcastRoles();
        tasks.newRound(round, online);
        updateBars();
    }

    private void cleanup() {
        state = State.IDLE;
        timerBar.removeAllPlayers();
        taskBar.removeAllPlayers();
        for (ServerPlayer p : onlineParticipants()) {
            clearRole(p);
        }
        removeTeams();
        abilities.reset();
        roles.clear();
    }

    // ---------------------------------------------------------------- тик

    public void tick() {
        tick++;
        if (state == State.IDLE) {
            return;
        }
        if (tick % 20 == 0) {
            secondTick();
        }
        if (state == State.RUNNING) {
            abilities.tick(tick, onlineParticipants());
        }
    }

    private void secondTick() {
        if (state == State.STARTING) {
            if (countdown <= 0) {
                begin();
                return;
            }
            for (ServerPlayer p : onlineParticipants()) {
                title(p, Txt.of(String.valueOf(countdown), ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Txt.of("Приготовьтесь…", ChatFormatting.GRAY), 0, 18, 2);
                sound(p, SoundEvents.NOTE_BLOCK_HAT, p.getX(), p.getY(), p.getZ(), 1f, 1f);
            }
            countdown--;
            return;
        }

        EndDragonFight fight = dragonFight();
        if (fight != null && !dragonKilledAtStart && fight.hasPreviouslyKilledDragon()) {
            finish(true);
            return;
        }

        BdmConfig c = config();
        List<ServerPlayer> online = onlineParticipants();
        secondsUntilSwap--;
        if (secondsUntilSwap <= 0) {
            swap(null);
            return;
        }
        if (secondsUntilSwap <= c.swapWarningSeconds) {
            for (ServerPlayer p : online) {
                title(p, Txt.of(String.valueOf(secondsUntilSwap), ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                        Txt.of("Смена ролей!", ChatFormatting.GRAY), 0, 18, 2);
                sound(p, secondsUntilSwap <= 3 ? SoundEvents.NOTE_BLOCK_PLING : SoundEvents.NOTE_BLOCK_HAT,
                        p.getX(), p.getY(), p.getZ(), 1f, 1.2f);
            }
        }
        tasks.tickItems(online);
        for (ServerPlayer p : online) {
            enforce(p, roles.get(p.getUUID()));
        }
        updateBars();
    }

    private EndDragonFight dragonFight() {
        ServerLevel end = server.getLevel(Level.END);
        return end == null ? null : end.getDragonFight();
    }

    // ---------------------------------------------------------------- роли

    /** Сменить роли. source == null — по таймеру. */
    public void swap(CommandSourceStack source) {
        if (state != State.RUNNING) {
            if (source != null) {
                source.sendFailure(Txt.error("Игра не запущена."));
            }
            return;
        }
        List<ServerPlayer> online = onlineParticipants();
        tasks.endRound(online);
        roles.putAll(config().randomSwap() ? randomSwap() : rotateSwap());
        round++;
        secondsUntilSwap = config().swapIntervalSeconds();
        for (ServerPlayer p : online) {
            applyRole(p, roles.get(p.getUUID()), true);
        }
        broadcast(Txt.info(Txt.of("Роли поменялись! Раунд " + round + ".", ChatFormatting.LIGHT_PURPLE)));
        broadcastRoles();
        tasks.newRound(round, online);
        updateBars();
    }

    public void setRole(ServerPlayer p, Role role) {
        roles.put(p.getUUID(), role);
        names.put(p.getUUID(), p.getGameProfile().getName());
        if (state == State.RUNNING) {
            applyRole(p, role, true);
            timerBar.addPlayer(p);
            taskBar.addPlayer(p);
        }
    }

    private Map<UUID, Role> rotateSwap() {
        Map<UUID, Role> next = new HashMap<>();
        roles.forEach((id, r) -> next.put(id, r.next()));
        return next;
    }

    /** Случайная раздача, в которой каждый получает новую роль (если возможно). */
    private Map<UUID, Role> randomSwap() {
        List<UUID> ids = new ArrayList<>(roles.keySet());
        for (int attempt = 0; attempt < 50; attempt++) {
            Collections.shuffle(ids);
            List<Role> order = shuffledRoles();
            Map<UUID, Role> next = new HashMap<>();
            boolean allChanged = true;
            for (int i = 0; i < ids.size(); i++) {
                Role r = order.get(i % order.size());
                next.put(ids.get(i), r);
                if (r == roles.get(ids.get(i))) {
                    allChanged = false;
                }
            }
            if (allChanged) {
                return next;
            }
        }
        return rotateSwap();
    }

    private static List<Role> shuffledRoles() {
        List<Role> order = new ArrayList<>(List.of(Role.values()));
        Collections.shuffle(order);
        return order;
    }

    private Role rarestRole() {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (Role r : Role.values()) {
            counts.put(r, 0);
        }
        roles.values().forEach(r -> counts.merge(r, 1, Integer::sum));
        List<Role> order = shuffledRoles();
        Role best = order.get(0);
        for (Role r : order) {
            if (counts.get(r) < counts.get(best)) {
                best = r;
            }
        }
        return best;
    }

    private void applyRole(ServerPlayer p, Role role, boolean announce) {
        if (role == null) {
            return;
        }
        removeRoleEffects(p);
        joinTeam(p, role);
        if (ServerPlayNetworking.canSend(p, RolePayload.TYPE)) {
            ServerPlayNetworking.send(p, RolePayload.of(role));
        }
        enforce(p, role);
        if (announce) {
            title(p, Txt.of("Ты — ", ChatFormatting.WHITE).append(Txt.role(role)),
                    Txt.of(role.description(), ChatFormatting.GRAY), 6, 80, 16);
            sound(p, holder(SoundEvents.ILLUSIONER_MIRROR_MOVE), p.getX(), p.getY(), p.getZ(), 1f, 1f);
            p.sendSystemMessage(Txt.info(Txt.of("Твоя роль: ", ChatFormatting.GRAY).append(Txt.role(role))));
            p.sendSystemMessage(Txt.of("  " + role.description(), ChatFormatting.GRAY));
            p.sendSystemMessage(Txt.of("  " + role.ability(), ChatFormatting.YELLOW));
        }
    }

    /** Держит эффекты роли (на случай молока, смерти и т.п.). */
    private void enforce(ServerPlayer p, Role role) {
        if (role == Role.BLIND) {
            if (!hasInfinite(p, MobEffects.BLINDNESS)) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
            if (config().blind.darknessPulse && !hasInfinite(p, MobEffects.DARKNESS)) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
        } else if (hasInfinite(p, MobEffects.BLINDNESS) || hasInfinite(p, MobEffects.DARKNESS)) {
            removeRoleEffects(p);
        }
    }

    private void clearRole(ServerPlayer p) {
        removeRoleEffects(p);
        p.setGlowingTag(false);
        if (ServerPlayNetworking.canSend(p, RolePayload.TYPE)) {
            ServerPlayNetworking.send(p, RolePayload.of(null));
        }
    }

    private static void removeRoleEffects(ServerPlayer p) {
        for (Holder<MobEffect> type : List.of(MobEffects.BLINDNESS, MobEffects.DARKNESS)) {
            if (hasInfinite(p, type)) {
                p.removeEffect(type);
            }
        }
    }

    private static boolean hasInfinite(ServerPlayer p, Holder<MobEffect> type) {
        MobEffectInstance e = p.getEffect(type);
        return e != null && e.isInfiniteDuration();
    }

    // ---------------------------------------------------------------- команды в табе (префикс и цвет подсветки)

    private static String teamName(Role role) {
        return "bdm_" + role.name().toLowerCase(java.util.Locale.ROOT);
    }

    private void joinTeam(ServerPlayer p, Role role) {
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(teamName(role));
        if (team == null) {
            team = sb.addPlayerTeam(teamName(role));
        }
        team.setPlayerPrefix(Txt.of("[" + role.displayName() + "] ", role.color()));
        team.setColor(role.color());
        team.setAllowFriendlyFire(true);
        sb.addPlayerToTeam(p.getScoreboardName(), team);
    }

    public void removeTeams() {
        ServerScoreboard sb = server.getScoreboard();
        for (Role r : Role.values()) {
            PlayerTeam team = sb.getPlayerTeam(teamName(r));
            if (team != null) {
                sb.removePlayerTeam(team);
            }
        }
    }

    // ---------------------------------------------------------------- события

    public void onJoin(ServerPlayer p) {
        if (state == State.RUNNING) {
            Role role = roles.get(p.getUUID());
            if (role == null && config().lateJoin) {
                role = rarestRole();
                roles.put(p.getUUID(), role);
                names.put(p.getUUID(), p.getGameProfile().getName());
                p.setGameMode(GameType.SURVIVAL);
                broadcast(Txt.info(p.getGameProfile().getName() + " присоединился к игре."));
            }
            if (role != null) {
                Role r = role;
                server.execute(() -> {
                    if (state == State.RUNNING && roles.get(p.getUUID()) == r && !p.hasDisconnected()) {
                        applyRole(p, r, true);
                        timerBar.addPlayer(p);
                        taskBar.addPlayer(p);
                    }
                });
                return;
            }
        }
        // Игра закончилась, пока игрока не было — снимаем эффекты роли.
        removeRoleEffects(p);
        p.setGlowingTag(false);
    }

    public void onDeath(ServerPlayer p) {
        if (role(p.getUUID()) == null) {
            return;
        }
        deaths++;
        if (config().teamLives <= 0) {
            return;
        }
        lives--;
        if (lives <= 0) {
            lives = 0;
            broadcast(Txt.error(p.getGameProfile().getName() + " погиб. Жизни команды закончились!"));
            server.execute(() -> finish(false));
        } else {
            broadcast(Txt.error(p.getGameProfile().getName() + " погиб. Осталось жизней: " + lives));
        }
        updateBars();
    }

    public void onKill(EntityType<?> type) {
        if (isRunning()) {
            tasks.onKill(type, onlineParticipants());
        }
    }

    public void onWorldChange(ServerPlayer p, ServerLevel destination) {
        if (role(p.getUUID()) != null) {
            tasks.onWorld(destination.dimension(), onlineParticipants());
        }
    }

    public void addLife() {
        if (config().teamLives > 0) {
            lives++;
        }
    }

    public void setLives(int value) {
        lives = Math.max(1, value);
        updateBars();
    }

    public void capSwapTimer(int seconds) {
        if (state == State.RUNNING && secondsUntilSwap > seconds) {
            secondsUntilSwap = seconds;
            updateBars();
        }
    }

    /** Конец игры: победа (дракон убит) или поражение (кончились жизни). */
    public void finish(boolean win) {
        if (state != State.RUNNING) {
            return;
        }
        List<ServerPlayer> online = onlineParticipants();
        long minutes = (System.currentTimeMillis() - startedAt) / 60000;
        MutableComponent stats = Txt.of("Время: " + minutes + " мин · Раундов: " + round + " · Смертей: " + deaths
                + " · Заданий: " + tasks.completed(), ChatFormatting.GRAY);
        cleanup();
        for (ServerPlayer p : online) {
            if (win) {
                title(p, Txt.of("ПОБЕДА!", ChatFormatting.GOLD, ChatFormatting.BOLD), stats, 10, 120, 20);
                sound(p, holder(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE), p.getX(), p.getY(), p.getZ(), 1f, 1f);
            } else {
                title(p, Txt.of("ПОРАЖЕНИЕ", ChatFormatting.DARK_RED, ChatFormatting.BOLD), stats, 10, 120, 20);
                sound(p, holder(SoundEvents.WITHER_DEATH), p.getX(), p.getY(), p.getZ(), 0.7f, 1f);
                p.setGameMode(GameType.SPECTATOR);
            }
        }
        broadcast(Txt.info(Txt.of(win ? "Дракон повержен! Команда победила. " : "Команда проиграла. ",
                win ? ChatFormatting.GOLD : ChatFormatting.RED).append(stats)));
    }

    // ---------------------------------------------------------------- вывод

    private void updateBars() {
        if (state != State.RUNNING) {
            return;
        }
        BdmConfig c = config();
        int interval = c.swapIntervalSeconds();
        String livesText = c.teamLives <= 0 ? "∞" : String.valueOf(lives);
        timerBar.setName(Txt.of("Смена ролей через ", ChatFormatting.LIGHT_PURPLE)
                .append(Txt.of(Txt.time(secondsUntilSwap), ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Txt.of("  ·  Раунд " + round + "  ·  ", ChatFormatting.GRAY))
                .append(Txt.of("❤ " + livesText, ChatFormatting.RED)));
        timerBar.setProgress(Math.max(0f, Math.min(1f, secondsUntilSwap / (float) interval)));
        timerBar.setColor(secondsUntilSwap <= c.swapWarningSeconds ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        taskBar.setName(tasks.barText());
        taskBar.setProgress(tasks.barProgress());
        taskBar.setColor(tasks.barColor());
    }

    public List<Component> rolesText() {
        List<Component> lines = new ArrayList<>();
        if (roles.isEmpty()) {
            lines.add(Txt.info("Игра не запущена."));
            return lines;
        }
        lines.add(Txt.info(Txt.of("Роли (раунд " + round + "):", ChatFormatting.YELLOW)));
        for (Role r : Role.values()) {
            List<String> list = new ArrayList<>();
            roles.forEach((id, role) -> {
                if (role == r) {
                    list.add(names.getOrDefault(id, id.toString().substring(0, 8)));
                }
            });
            lines.add(Txt.of("  ").append(Txt.role(r))
                    .append(Txt.of(": " + (list.isEmpty() ? "—" : String.join(", ", list)), ChatFormatting.WHITE)));
        }
        return lines;
    }

    private void broadcastRoles() {
        List<Component> lines = rolesText();
        for (ServerPlayer p : onlineParticipants()) {
            lines.forEach(p::sendSystemMessage);
        }
    }

    private void broadcast(Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    // ---------------------------------------------------------------- общие помощники

    public static Holder<SoundEvent> holder(SoundEvent e) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(e);
    }

    /** Звук в точке, который слышит только этот игрок. */
    public static void sound(ServerPlayer p, Holder<SoundEvent> s, double x, double y, double z, float volume, float pitch) {
        p.connection.send(new ClientboundSoundPacket(s, SoundSource.MASTER, x, y, z, volume, pitch, p.getRandom().nextLong()));
    }

    public static void title(ServerPlayer p, Component title, Component subtitle, int in, int stay, int out) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
