package com.github.tadaooq123.bdm;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * «Тихий» ресурспак для глухого: каждый звук игры заменён пустым.
 * Пак собирается при запуске и раздаётся встроенным HTTP-сервером.
 * Если пак не загрузился, глухота держится на принудительной остановке звуков каждый тик.
 */
public final class DeafPack {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("blind-deaf-mute:deaf-pack".getBytes(StandardCharsets.UTF_8));

    private final BdmPlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> sent = ConcurrentHashMap.newKeySet();
    private HttpServer server;
    private byte[] zip;
    private byte[] sha1;
    private String path;
    private int port;

    public DeafPack(BdmPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(Settings settings) {
        if (!settings.deafMuteSounds || !settings.deafPackEnabled) {
            return;
        }
        try {
            zip = buildZip();
            sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
            path = "/deaf-" + HexFormat.of().formatHex(sha1).substring(0, 10) + ".zip";
            port = settings.deafPackPort;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (!exchange.getRequestURI().getPath().equals(path)) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, zip.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(zip);
                    }
                }
            });
            server.start();
            plugin.getLogger().info("Тихий ресурспак для глухого раздаётся на порту " + port
                    + " (" + zip.length / 1024 + " КБ)");
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            server = null;
            plugin.getLogger().log(Level.WARNING, "Не удалось запустить раздачу ресурспака для глухого "
                    + "(будет использоваться принудительная остановка звуков): " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        loaded.clear();
        sent.clear();
    }

    public boolean isRunning() {
        return server != null;
    }

    /** Звуки у игрока уже заглушены ресурспаком. */
    public boolean isLoaded(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    public void send(Player p, Settings settings) {
        if (server == null || sent.contains(p.getUniqueId())) {
            return;
        }
        String url = "http://" + host(p, settings) + ":" + port + path;
        sent.add(p.getUniqueId());
        p.addResourcePack(PACK_ID, url, sha1,
                "Ты ГЛУХОЙ: этот пак выключает все звуки игры. Если откажешься — звуки будут обрываться принудительно.",
                false);
    }

    public void remove(Player p) {
        boolean had = sent.remove(p.getUniqueId()) | loaded.remove(p.getUniqueId());
        if (had) {
            p.removeResourcePack(PACK_ID);
        }
    }

    public void forget(UUID id) {
        sent.remove(id);
        loaded.remove(id);
    }

    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(id);
            case ACCEPTED, DOWNLOADED -> {
            }
            default -> {
                loaded.remove(id);
                if (event.getStatus() != PlayerResourcePackStatusEvent.Status.DISCARDED) {
                    plugin.getLogger().info("Ресурспак глухого не загрузился у " + event.getPlayer().getName()
                            + ": " + event.getStatus());
                }
            }
        }
    }

    private static String host(Player p, Settings settings) {
        if (!settings.deafPackHost.isEmpty()) {
            return settings.deafPackHost;
        }
        InetSocketAddress virtual = p.getVirtualHost();
        if (virtual != null) {
            String h = virtual.getHostString();
            int zero = h.indexOf('\0'); // клиенты Forge дописывают метки после \0
            if (zero >= 0) {
                h = h.substring(0, zero);
            }
            if (h.endsWith(".")) {
                h = h.substring(0, h.length() - 1);
            }
            if (!h.isBlank()) {
                return h.contains(":") ? "[" + h + "]" : h;
            }
        }
        String ip = Bukkit.getIp();
        return ip == null || ip.isBlank() ? "localhost" : ip;
    }

    private static byte[] buildZip() throws IOException {
        Map<String, Set<String>> byNamespace = new TreeMap<>();
        for (Sound sound : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key == null) {
                continue;
            }
            byNamespace.computeIfAbsent(key.getNamespace(), k -> new TreeSet<>()).add(key.getKey());
        }
        if (byNamespace.isEmpty()) {
            throw new IOException("реестр звуков пуст");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            put(zos, "pack.mcmeta", """
                    {
                      "pack": {
                        "description": "Слепой, глухой, немой — тишина для глухого",
                        "pack_format": 46,
                        "supported_formats": {"min_inclusive": 1, "max_inclusive": 9999},
                        "min_format": 1,
                        "max_format": 9999
                      }
                    }
                    """);
            for (Map.Entry<String, Set<String>> e : byNamespace.entrySet()) {
                StringBuilder json = new StringBuilder("{\n");
                boolean first = true;
                for (String name : e.getValue()) {
                    if (!first) {
                        json.append(",\n");
                    }
                    first = false;
                    json.append("  \"").append(name).append("\": {\"replace\": true, \"sounds\": []}");
                }
                json.append("\n}\n");
                put(zos, "assets/" + e.getKey() + "/sounds.json", json.toString());
            }
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0); // одинаковый хеш при каждом запуске
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
