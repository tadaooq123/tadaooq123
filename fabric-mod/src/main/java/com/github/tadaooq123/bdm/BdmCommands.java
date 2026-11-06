package com.github.tadaooq123.bdm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /bdm — управление игрой. */
public final class BdmCommands {

    private BdmCommands() {
    }

    /** Ведущий: оператор, либо хозяин мира, открытого по LAN (даже без читов). */
    private static boolean isAdmin(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        ServerPlayer p = source.getPlayer();
        return p != null && source.getServer().isSingleplayerOwner(p.getGameProfile());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("bdm")
                .executes(BdmCommands::help)
                .then(literal("help").executes(BdmCommands::help))
                .then(literal("info").executes(BdmCommands::info))
                .then(literal("roles").executes(ctx -> {
                    Game g = game(ctx);
                    if (g != null) {
                        g.rolesText().forEach(line -> ctx.getSource().sendSystemMessage(line));
                    }
                    return 1;
                }))
                .then(literal("start").requires(BdmCommands::isAdmin).executes(ctx -> {
                    Game g = game(ctx);
                    if (g != null) {
                        g.start(ctx.getSource());
                    }
                    return 1;
                }))
                .then(literal("stop").requires(BdmCommands::isAdmin).executes(ctx -> {
                    Game g = game(ctx);
                    if (g != null) {
                        g.stop(ctx.getSource());
                    }
                    return 1;
                }))
                .then(literal("swap").requires(BdmCommands::isAdmin).executes(ctx -> {
                    Game g = game(ctx);
                    if (g != null) {
                        g.swap(ctx.getSource());
                    }
                    return 1;
                }))
                .then(literal("setrole").requires(BdmCommands::isAdmin)
                        .then(argument("player", EntityArgument.player())
                                .then(argument("role", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                List.of("blind", "deaf", "mute"), b))
                                        .executes(BdmCommands::setRole))))
                .then(literal("interval").requires(BdmCommands::isAdmin)
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Txt.info("Роли меняются каждые "
                                    + Txt.time(BdmMod.config().swapIntervalSeconds())));
                            return 1;
                        })
                        .then(argument("minutes", DoubleArgumentType.doubleArg(0.5, 600))
                                .executes(BdmCommands::interval)))
                .then(literal("lives").requires(BdmCommands::isAdmin)
                        .then(argument("count", IntegerArgumentType.integer(1, 1000))
                                .executes(BdmCommands::lives)))
                .then(literal("reload").requires(BdmCommands::isAdmin).executes(ctx -> {
                    BdmMod.reloadConfig();
                    ctx.getSource().sendSystemMessage(Txt.ok("Настройки перечитаны из config/blinddeafmute.json"));
                    return 1;
                })));
    }

    private static Game game(CommandContext<CommandSourceStack> ctx) {
        Game g = BdmMod.game();
        if (g == null) {
            ctx.getSource().sendFailure(Txt.error("Мир ещё загружается."));
        }
        return g;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        s.sendSystemMessage(Txt.info(Txt.of("Слепой, глухой, немой — команды:", ChatFormatting.YELLOW)));
        line(s, "/bdm info — правила и роли");
        line(s, "/bdm roles — у кого какая роль");
        if (isAdmin(s)) {
            line(s, "/bdm start | stop — начать / закончить игру");
            line(s, "/bdm swap — сменить роли прямо сейчас");
            line(s, "/bdm setrole <игрок> <blind|deaf|mute>");
            line(s, "/bdm interval <минуты> — период смены ролей");
            line(s, "/bdm lives <число> — жизни команды");
            line(s, "/bdm reload — перечитать настройки");
        }
        return 1;
    }

    private static void line(CommandSourceStack s, String text) {
        s.sendSystemMessage(Txt.of("  " + text, ChatFormatting.GRAY));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack s = ctx.getSource();
        s.sendSystemMessage(Txt.info(Txt.of("Цель — убить Эндер-дракона. Жизни общие, роли меняются по таймеру.",
                ChatFormatting.YELLOW)));
        for (Role r : Role.values()) {
            s.sendSystemMessage(Txt.of("• ").append(Txt.role(r)).append(Txt.of(" — " + r.description(), ChatFormatting.GRAY)));
            s.sendSystemMessage(Txt.of("    " + r.ability(), ChatFormatting.DARK_AQUA));
        }
        s.sendSystemMessage(Txt.of("Каждый раунд — задание. Выполнили: награда и +1 жизнь. Провалили: проклятие.",
                ChatFormatting.GRAY));
        Game g = BdmMod.game();
        ServerPlayer p = s.getPlayer();
        if (g != null && p != null && g.role(p.getUUID()) != null) {
            s.sendSystemMessage(Txt.info(Txt.of("Сейчас ты: ", ChatFormatting.GRAY).append(Txt.role(g.role(p.getUUID())))));
        }
        return 1;
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Game g = game(ctx);
        if (g == null) {
            return 0;
        }
        if (!g.isRunning()) {
            ctx.getSource().sendFailure(Txt.error("Игра не запущена."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Role role = Role.parse(StringArgumentType.getString(ctx, "role"));
        if (role == null) {
            ctx.getSource().sendFailure(Txt.error("Роль: blind, deaf или mute."));
            return 0;
        }
        g.setRole(target, role);
        Component msg = Txt.ok(target.getGameProfile().getName() + " теперь " + role.displayName());
        ctx.getSource().sendSystemMessage(msg);
        return 1;
    }

    private static int interval(CommandContext<CommandSourceStack> ctx) {
        double minutes = DoubleArgumentType.getDouble(ctx, "minutes");
        BdmConfig c = BdmMod.config();
        c.swapIntervalMinutes = minutes;
        c.save(BdmMod.configPath());
        Game g = BdmMod.game();
        if (g != null) {
            g.capSwapTimer(c.swapIntervalSeconds());
        }
        ctx.getSource().sendSystemMessage(Txt.ok("Роли будут меняться каждые " + Txt.time(c.swapIntervalSeconds())));
        return 1;
    }

    private static int lives(CommandContext<CommandSourceStack> ctx) {
        Game g = game(ctx);
        if (g == null) {
            return 0;
        }
        if (!g.isRunning() || BdmMod.config().teamLives <= 0) {
            ctx.getSource().sendFailure(Txt.error("Игра не запущена или жизни выключены (teamLives: 0)."));
            return 0;
        }
        g.setLives(IntegerArgumentType.getInteger(ctx, "count"));
        ctx.getSource().sendSystemMessage(Txt.ok("Жизней у команды: " + g.lives()));
        return 1;
    }
}
