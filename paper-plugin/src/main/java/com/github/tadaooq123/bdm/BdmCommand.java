package com.github.tadaooq123.bdm;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.text;

public final class BdmCommand implements TabExecutor {

    private static final List<String> ADMIN = List.of("start", "stop", "swap", "setrole", "interval", "lives", "reload");
    private static final List<String> PUBLIC = List.of("help", "info", "roles");

    private final BdmPlugin plugin;

    public BdmCommand(BdmPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN.contains(sub) && !sender.hasPermission("bdm.admin")) {
            sender.sendMessage(Msg.error("Нет прав."));
            return true;
        }
        GameManager game = plugin.game();
        switch (sub) {
            case "start" -> game.start(sender);
            case "stop" -> game.stop(sender);
            case "swap" -> game.swap(sender);
            case "roles" -> game.sendRoles(sender);
            case "info" -> info(sender);
            case "setrole" -> setRole(sender, args);
            case "interval" -> interval(sender, args);
            case "lives" -> lives(sender, args);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Msg.ok("Конфиг перезагружен. (Порт ресурспака меняется только после перезапуска.)"));
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.info(text("Слепой, глухой, немой — команды:", NamedTextColor.YELLOW)));
        s.sendMessage(text("  /bdm info — правила и роли", NamedTextColor.GRAY));
        s.sendMessage(text("  /bdm roles — у кого какая роль", NamedTextColor.GRAY));
        if (s.hasPermission("bdm.admin")) {
            s.sendMessage(text("  /bdm start | stop — начать / закончить игру", NamedTextColor.GRAY));
            s.sendMessage(text("  /bdm swap — сменить роли прямо сейчас", NamedTextColor.GRAY));
            s.sendMessage(text("  /bdm setrole <игрок> <blind|deaf|mute>", NamedTextColor.GRAY));
            s.sendMessage(text("  /bdm interval <минуты> — период смены ролей", NamedTextColor.GRAY));
            s.sendMessage(text("  /bdm lives <число> — жизни команды", NamedTextColor.GRAY));
            s.sendMessage(text("  /bdm reload — перечитать config.yml", NamedTextColor.GRAY));
        }
    }

    private void info(CommandSender s) {
        s.sendMessage(Msg.info(text("Цель — убить Эндер-дракона. Жизни общие, роли меняются по таймеру.",
                NamedTextColor.YELLOW)));
        for (Role r : Role.values()) {
            s.sendMessage(text("• ").append(Msg.role(r)).append(text(" — " + r.description(), NamedTextColor.GRAY)));
            s.sendMessage(text("    " + r.ability(), NamedTextColor.DARK_AQUA));
        }
        s.sendMessage(text("Каждый раунд — задание. Выполнили: награда и +1 жизнь. Провалили: проклятие.",
                NamedTextColor.GRAY));
        if (s instanceof Player p) {
            Role mine = plugin.game().role(p.getUniqueId());
            if (mine != null) {
                s.sendMessage(Msg.info(text("Сейчас ты: ").append(Msg.role(mine))));
            }
        }
    }

    private void setRole(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage(Msg.error("/bdm setrole <игрок> <blind|deaf|mute>"));
            return;
        }
        if (!plugin.game().isRunning()) {
            s.sendMessage(Msg.error("Игра не запущена."));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Role role = Role.parse(args[2]);
        if (target == null || role == null) {
            s.sendMessage(Msg.error("Игрок не найден или неизвестная роль."));
            return;
        }
        plugin.game().setRole(target, role);
        s.sendMessage(Msg.ok(target.getName() + " теперь " + role.displayName()));
    }

    private void interval(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Msg.info("Сейчас роли меняются каждые " + Msg.time(plugin.settings().swapIntervalSeconds)));
            return;
        }
        double minutes;
        try {
            minutes = Double.parseDouble(args[1].replace(',', '.'));
        } catch (NumberFormatException e) {
            s.sendMessage(Msg.error("Нужно число минут, например 7.5"));
            return;
        }
        if (minutes < 0.5) {
            s.sendMessage(Msg.error("Минимум — 0.5 минуты."));
            return;
        }
        plugin.getConfig().set("swap-interval-minutes", minutes);
        plugin.saveConfig();
        plugin.reload();
        int seconds = plugin.settings().swapIntervalSeconds;
        if (plugin.game().isRunning() && plugin.game().secondsUntilSwap() > seconds) {
            plugin.game().setSecondsUntilSwap(seconds);
        }
        s.sendMessage(Msg.ok("Роли будут меняться каждые " + Msg.time(seconds)));
    }

    private void lives(CommandSender s, String[] args) {
        if (!plugin.game().isRunning()) {
            s.sendMessage(Msg.error("Игра не запущена."));
            return;
        }
        if (plugin.settings().teamLives <= 0) {
            s.sendMessage(Msg.error("Жизни выключены в конфиге (team-lives: 0)."));
            return;
        }
        if (args.length < 2) {
            s.sendMessage(Msg.info("Жизней у команды: " + plugin.game().lives()));
            return;
        }
        try {
            plugin.game().setLives(Integer.parseInt(args[1]));
            s.sendMessage(Msg.ok("Жизней у команды: " + plugin.game().lives()));
        } catch (NumberFormatException e) {
            s.sendMessage(Msg.error("Нужно целое число."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PUBLIC);
            if (sender.hasPermission("bdm.admin")) {
                options.addAll(ADMIN);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setrole")) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setrole")) {
            options.addAll(List.of("blind", "deaf", "mute"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
