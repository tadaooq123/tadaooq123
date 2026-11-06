package com.github.tadaooq123.bdm;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.kyori.adventure.text.Component.text;

/** Состояние игры: участники, роли, таймер смены, жизни, боссбары. */
public final class GameManager {

    public enum State { IDLE, STARTING, RUNNING }

    private final BdmPlugin plugin;
    private final NamespacedKey marker;
    private final Map<UUID, Role> roles = new ConcurrentHashMap<>();
    private final BossBar timerBar = BossBar.bossBar(text(""), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
    private final BossBar taskBar = BossBar.bossBar(text(""), 0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    private volatile State state = State.IDLE;
    private BukkitTask ticker;
    private long tick;
    private int countdown;
    private int secondsUntilSwap;
    private int lives;
    private int round;
    private int deaths;
    private long startedAt;

    public GameManager(BdmPlugin plugin) {
        this.plugin = plugin;
        this.marker = new NamespacedKey(plugin, "affected");
    }

    // ---------------------------------------------------------------- запросы

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** Роль участника во время игры (null — не участник или игра не идёт). Потокобезопасно. */
    public Role role(UUID id) {
        return state == State.RUNNING ? roles.get(id) : null;
    }

    public Map<UUID, Role> roles() {
        return Collections.unmodifiableMap(roles);
    }

    public List<Player> onlineParticipants() {
        List<Player> list = new ArrayList<>(roles.size());
        for (UUID id : roles.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public int lives() {
        return lives;
    }

    public int secondsUntilSwap() {
        return secondsUntilSwap;
    }

    public int round() {
        return round;
    }

    // ---------------------------------------------------------------- жизненный цикл

    public void startTicker() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 1L, 1L);
    }

    public void shutdown() {
        if (state != State.IDLE) {
            cleanup();
        }
        if (ticker != null) {
            ticker.cancel();
        }
    }

    public boolean start(CommandSender sender) {
        if (state != State.IDLE) {
            sender.sendMessage(Msg.error("Игра уже идёт. Сначала /bdm stop"));
            return false;
        }
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("bdm.bypass")) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            sender.sendMessage(Msg.error("Нет игроков для начала игры."));
            return false;
        }
        Settings s = plugin.settings();
        roles.clear();
        Collections.shuffle(players);
        List<Role> order = shuffledRoles();
        for (int i = 0; i < players.size(); i++) {
            roles.put(players.get(i).getUniqueId(), order.get(i % order.size()));
        }
        lives = s.teamLives;
        round = 1;
        deaths = 0;
        plugin.tasks().reset();
        plugin.abilities().reset();

        for (Player p : players) {
            p.setGameMode(GameMode.SURVIVAL);
            if (s.startClearInventory) {
                p.getInventory().clear();
            }
            if (s.startHeal) {
                heal(p);
            }
        }
        if (players.size() < 3) {
            sender.sendMessage(Msg.info("Игроков меньше трёх — не все роли будут заняты, но играть можно."));
        }
        countdown = s.startCountdown;
        state = State.STARTING;
        broadcast(Msg.info(text("Игра «Слепой, глухой, немой» начинается! Роли меняются каждые "
                + Msg.time(s.swapIntervalSeconds) + ".", NamedTextColor.YELLOW)));
        return true;
    }

    public void stop(CommandSender sender) {
        if (state == State.IDLE) {
            sender.sendMessage(Msg.error("Игра не запущена."));
            return;
        }
        broadcast(Msg.info("Игра остановлена."));
        cleanup();
    }

    private void begin() {
        Settings s = plugin.settings();
        state = State.RUNNING;
        startedAt = System.currentTimeMillis();
        secondsUntilSwap = s.swapIntervalSeconds;
        List<Player> online = onlineParticipants();
        for (Player p : online) {
            applyRole(p, roles.get(p.getUniqueId()), true);
            p.showBossBar(timerBar);
            p.showBossBar(taskBar);
        }
        broadcastRoles();
        plugin.tasks().newRound(round, online);
        updateBars();
    }

    private void cleanup() {
        state = State.IDLE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(timerBar);
            p.hideBossBar(taskBar);
            if (roles.containsKey(p.getUniqueId())) {
                clearRole(p);
            }
        }
        plugin.abilities().reset();
        roles.clear();
    }

    // ---------------------------------------------------------------- тик

    private void onTick() {
        tick++;
        if (state == State.IDLE) {
            return;
        }
        if (tick % 20 == 0) {
            secondTick();
        }
        if (state == State.RUNNING) {
            plugin.abilities().tick(tick, onlineParticipants());
        }
    }

    private void secondTick() {
        if (state == State.STARTING) {
            if (countdown <= 0) {
                begin();
                return;
            }
            for (Player p : onlineParticipants()) {
                p.showTitle(Title.title(text(countdown, NamedTextColor.GOLD, TextDecoration.BOLD),
                        text("Приготовьтесь…", NamedTextColor.GRAY), times(0, 900, 100)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            countdown--;
            return;
        }

        Settings s = plugin.settings();
        List<Player> online = onlineParticipants();
        secondsUntilSwap--;
        if (secondsUntilSwap <= 0) {
            swap(null);
            return;
        }
        if (secondsUntilSwap <= s.swapWarningSeconds) {
            for (Player p : online) {
                p.showTitle(Title.title(text(secondsUntilSwap, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        text("Смена ролей!", NamedTextColor.GRAY), times(0, 900, 100)));
                p.playSound(p.getLocation(), secondsUntilSwap <= 3
                        ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
            }
        }
        plugin.tasks().tickItems(online);
        for (Player p : online) {
            enforce(p, roles.get(p.getUniqueId()));
        }
        updateBars();
    }

    // ---------------------------------------------------------------- роли

    /** Сменить роли. sender == null — по таймеру. */
    public void swap(CommandSender sender) {
        if (state != State.RUNNING) {
            if (sender != null) {
                sender.sendMessage(Msg.error("Игра не запущена."));
            }
            return;
        }
        List<Player> online = onlineParticipants();
        plugin.tasks().endRound(online);

        Map<UUID, Role> next = plugin.settings().swapMode == Settings.SwapMode.RANDOM
                ? randomSwap() : rotateSwap();
        roles.putAll(next);
        round++;
        secondsUntilSwap = plugin.settings().swapIntervalSeconds;

        for (Player p : online) {
            applyRole(p, roles.get(p.getUniqueId()), true);
        }
        broadcast(Msg.info(text("Роли поменялись! Раунд " + round + ".", NamedTextColor.LIGHT_PURPLE)));
        broadcastRoles();
        plugin.tasks().newRound(round, online);
        updateBars();
    }

    public void setRole(Player p, Role role) {
        roles.put(p.getUniqueId(), role);
        if (state == State.RUNNING) {
            applyRole(p, role, true);
            p.showBossBar(timerBar);
            p.showBossBar(taskBar);
        }
    }

    private Map<UUID, Role> rotateSwap() {
        Map<UUID, Role> next = new HashMap<>();
        roles.forEach((id, r) -> next.put(id, r.next()));
        return next;
    }

    /** Случайная раздача, где каждый получает новую роль (если это возможно). */
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

    /** Роль, которой сейчас меньше всего (для зашедших позже). */
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

    private void applyRole(Player p, Role role, boolean announce) {
        if (role == null) {
            return;
        }
        Settings s = plugin.settings();
        removeRoleEffects(p);
        p.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        p.playerListName(text("[" + role.displayName() + "] ", role.color()).append(text(p.getName(), NamedTextColor.WHITE)));
        if (role == Role.DEAF && s.deafMuteSounds) {
            plugin.pack().send(p, s);
        } else {
            plugin.pack().remove(p);
        }
        enforce(p, role);
        if (announce) {
            p.showTitle(Title.title(text("Ты — ", NamedTextColor.WHITE).append(Msg.role(role)),
                    text(role.description(), NamedTextColor.GRAY), times(300, 4000, 800)));
            p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
            p.sendMessage(Msg.info(text("Твоя роль: ").append(Msg.role(role))));
            p.sendMessage(text("  " + role.description(), NamedTextColor.GRAY));
            p.sendMessage(text("  " + role.ability(), NamedTextColor.YELLOW));
        }
    }

    /** Держит эффекты роли (на случай молока, смерти и т.п.). */
    private void enforce(Player p, Role role) {
        if (role == Role.BLIND) {
            if (!hasInfinite(p, PotionEffectType.BLINDNESS)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            }
            if (plugin.settings().blindDarkness && !hasInfinite(p, PotionEffectType.DARKNESS)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            }
        } else if (hasInfinite(p, PotionEffectType.BLINDNESS) || hasInfinite(p, PotionEffectType.DARKNESS)) {
            removeRoleEffects(p);
        }
    }

    private void clearRole(Player p) {
        removeRoleEffects(p);
        p.playerListName(null);
        p.setGlowing(false);
        plugin.pack().remove(p);
        p.getPersistentDataContainer().remove(marker);
    }

    private static void removeRoleEffects(Player p) {
        for (PotionEffectType type : List.of(PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS)) {
            if (hasInfinite(p, type)) {
                p.removePotionEffect(type);
            }
        }
    }

    private static boolean hasInfinite(Player p, PotionEffectType type) {
        PotionEffect e = p.getPotionEffect(type);
        return e != null && e.isInfinite();
    }

    // ---------------------------------------------------------------- события

    public void onJoin(Player p) {
        if (state == State.RUNNING) {
            Role role = roles.get(p.getUniqueId());
            if (role == null && plugin.settings().lateJoin && !p.hasPermission("bdm.bypass")) {
                role = rarestRole();
                roles.put(p.getUniqueId(), role);
                p.setGameMode(GameMode.SURVIVAL);
                broadcast(Msg.info(p.getName() + " присоединился к игре."));
            }
            if (role != null) {
                Role r = role;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && state == State.RUNNING && roles.get(p.getUniqueId()) == r) {
                        applyRole(p, r, true);
                        p.showBossBar(timerBar);
                        p.showBossBar(taskBar);
                    }
                }, 20L);
                return;
            }
        }
        if (p.getPersistentDataContainer().has(marker) && !roles.containsKey(p.getUniqueId())) {
            clearRole(p); // игра закончилась, пока игрока не было
        }
    }

    public void onQuit(Player p) {
        plugin.pack().forget(p.getUniqueId());
    }

    public void onDeath(Player p) {
        if (role(p.getUniqueId()) == null) {
            return;
        }
        deaths++;
        if (plugin.settings().teamLives <= 0) {
            return;
        }
        lives--;
        if (lives <= 0) {
            lives = 0;
            broadcast(Msg.error(p.getName() + " погиб. Жизни команды закончились!"));
            Bukkit.getScheduler().runTask(plugin, () -> finish(false));
        } else {
            broadcast(Msg.error(p.getName() + " погиб. Осталось жизней: " + lives));
        }
        updateBars();
    }

    public void addLife() {
        if (plugin.settings().teamLives > 0) {
            lives++;
        }
    }

    public void setLives(int value) {
        lives = Math.max(1, value);
        updateBars();
    }

    public void setSecondsUntilSwap(int seconds) {
        secondsUntilSwap = Math.max(1, seconds);
        updateBars();
    }

    /** Конец игры: победа (дракон убит) или поражение (кончились жизни). */
    public void finish(boolean win) {
        if (state != State.RUNNING) {
            return;
        }
        List<Player> online = onlineParticipants();
        long minutes = (System.currentTimeMillis() - startedAt) / 60000;
        Component stats = text("Время: " + minutes + " мин · Раундов: " + round + " · Смертей: " + deaths
                + " · Заданий: " + plugin.tasks().completed(), NamedTextColor.GRAY);
        cleanup();
        for (Player p : online) {
            if (win) {
                p.showTitle(Title.title(text("ПОБЕДА!", NamedTextColor.GOLD, TextDecoration.BOLD), stats, times(500, 6000, 1000)));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else {
                p.showTitle(Title.title(text("ПОРАЖЕНИЕ", NamedTextColor.DARK_RED, TextDecoration.BOLD), stats, times(500, 6000, 1000)));
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
                p.setGameMode(GameMode.SPECTATOR);
            }
        }
        broadcast(Msg.info(text(win ? "Дракон повержен! Команда победила. " : "Команда проиграла. ",
                win ? NamedTextColor.GOLD : NamedTextColor.RED).append(stats)));
    }

    // ---------------------------------------------------------------- вывод

    private void updateBars() {
        if (state != State.RUNNING) {
            return;
        }
        Settings s = plugin.settings();
        String livesText = s.teamLives <= 0 ? "∞" : String.valueOf(lives);
        timerBar.name(text("Смена ролей через ", NamedTextColor.LIGHT_PURPLE)
                .append(text(Msg.time(secondsUntilSwap), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(text("  ·  Раунд " + round + "  ·  ", NamedTextColor.GRAY))
                .append(text("❤ " + livesText, NamedTextColor.RED)));
        timerBar.progress(Math.max(0f, Math.min(1f, secondsUntilSwap / (float) s.swapIntervalSeconds)));
        timerBar.color(secondsUntilSwap <= s.swapWarningSeconds ? BossBar.Color.RED : BossBar.Color.PURPLE);

        taskBar.name(plugin.tasks().barText());
        taskBar.progress(plugin.tasks().barProgress());
        taskBar.color(plugin.tasks().barColor());
    }

    public void sendRoles(CommandSender to) {
        if (roles.isEmpty()) {
            to.sendMessage(Msg.info("Игра не запущена."));
            return;
        }
        to.sendMessage(Msg.info(text("Роли (раунд " + round + "):", NamedTextColor.YELLOW)));
        for (Role r : Role.values()) {
            List<String> names = new ArrayList<>();
            roles.forEach((id, role) -> {
                if (role == r) {
                    String name = Bukkit.getOfflinePlayer(id).getName();
                    names.add(name == null ? id.toString().substring(0, 8) : name);
                }
            });
            to.sendMessage(text("  ").append(Msg.role(r)).append(text(": "
                    + (names.isEmpty() ? "—" : String.join(", ", names)), NamedTextColor.WHITE)));
        }
    }

    private void broadcastRoles() {
        for (Player p : onlineParticipants()) {
            sendRoles(p);
        }
    }

    private void broadcast(Component msg) {
        Bukkit.getServer().sendMessage(msg);
    }

    @SuppressWarnings("deprecation")
    private static void heal(Player p) {
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(10f);
        p.setFireTicks(0);
    }

    private static Title.Times times(long inMs, long stayMs, long outMs) {
        return Title.Times.times(Duration.ofMillis(inMs), Duration.ofMillis(stayMs), Duration.ofMillis(outMs));
    }
}
