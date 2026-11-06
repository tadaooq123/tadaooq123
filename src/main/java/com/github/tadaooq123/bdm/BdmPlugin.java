package com.github.tadaooq123.bdm;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BdmPlugin extends JavaPlugin {

    private Settings settings;
    private TaskManager tasks;
    private DeafPack pack;
    private Abilities abilities;
    private GameManager game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        tasks = new TaskManager(this);
        tasks.load(getConfig());
        pack = new DeafPack(this);
        pack.start(settings);
        abilities = new Abilities(this);
        game = new GameManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        PluginCommand command = getCommand("bdm");
        if (command != null) {
            BdmCommand executor = new BdmCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (settings.voiceChat && getServer().getPluginManager().getPlugin("voicechat") != null) {
            try {
                if (VoiceChatBridge.register(this)) {
                    getLogger().info("Simple Voice Chat подключён: немой молчит, глухой не слышит.");
                } else {
                    getLogger().warning("Simple Voice Chat найден, но его API недоступно.");
                }
            } catch (Throwable t) {
                getLogger().warning("Не удалось подключиться к Simple Voice Chat: " + t);
            }
        } else {
            getLogger().info("Simple Voice Chat не найден — голосовые ограничения работать не будут.");
        }

        game.startTicker();
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (pack != null) {
            pack.stop();
        }
    }

    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
        tasks.load(getConfig());
    }

    public Settings settings() {
        return settings;
    }

    public TaskManager tasks() {
        return tasks;
    }

    public DeafPack pack() {
        return pack;
    }

    public Abilities abilities() {
        return abilities;
    }

    public GameManager game() {
        return game;
    }
}
