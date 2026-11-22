package com.minecraft.core.bungee.listener;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.backend.database.redis.message.types.network.AntiCheatAlertMessage;
import com.minecraft.core.bungee.BungeeCore;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Recebe as flags publicadas pela ponte do Anticheat e entrega para a staff do proxy.
 * Os alertas de cada jogador/check são agrupados numa janela para não floodar o chat.
 */
public final class AntiCheatListener {

    private static final long AGGREGATE_WINDOW_MS = 5_000L;
    private static final long MAX_ALERTS_PER_CYCLE = 3L;
    private static final int HISTORY_SIZE = 100;
    private static final int MAX_ALTS = 4;
    private static final long ALT_CACHE_MS = 120_000L;

    private static final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private static final Map<String, AltCache> altCache = new ConcurrentHashMap<>();
    private static final Set<String> altLoading = ConcurrentHashMap.newKeySet();
    private static final Queue<Alert> queue = new ConcurrentLinkedQueue<>();
    private static final Deque<String> history = new ArrayDeque<>();

    private static BungeeCore plugin;

    private AntiCheatListener() {
    }

    public static void start(BungeeCore instance) {
        plugin = instance;

        ProxyServer.getInstance().getScheduler()
                .schedule(plugin, AntiCheatListener::drain, 1L, 1L, TimeUnit.SECONDS);
    }

    public static void accept(AntiCheatAlertMessage message) {
        if (message == null || message.getName() == null || message.getHack() == null) return;
        if (message.getName().trim().isEmpty()) return;

        String key = message.getName().toLowerCase(Locale.ROOT) + "|" + message.getHack().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        Pending entry = pending.computeIfAbsent(key, ignored -> new Pending());

        synchronized (entry) {
            entry.name = message.getName();
            entry.uuid = message.getUuid();
            entry.server = message.getServer();
            entry.ping = message.getPing();
            entry.violations = Math.max(entry.violations, message.getViolations());
            entry.count += Math.max(1, message.getCount());
            entry.lastSeen = now;

            if (now - entry.lastSent >= AGGREGATE_WINDOW_MS) {
                emit(key, entry, now);
            }
        }
    }

    private static void emit(String key, Pending entry, long now) {
        if (entry.count <= 0) return;

        Alert alert = new Alert(entry.uuid, entry.name, entry.server, entry.hack, entry.violations, entry.count, entry.ping);

        entry.count = 0;
        entry.violations = 0;
        entry.lastSent = now;

        queue.add(alert);
    }

    private static void drain() {
        long now = System.currentTimeMillis();

        for (Iterator<Map.Entry<String, Pending>> iterator = pending.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Pending> mapEntry = iterator.next();
            Pending entry = mapEntry.getValue();

            synchronized (entry) {
                if (now - entry.lastSent >= AGGREGATE_WINDOW_MS) {
                    emit(mapEntry.getKey(), entry, now);
                }

                if (entry.count <= 0 && now - entry.lastSeen > 60_000L) {
                    iterator.remove();
                }
            }
        }

        long sent = 0;
        Alert alert;
        while (sent < MAX_ALERTS_PER_CYCLE && (alert = queue.poll()) != null) {
            send(alert);
            sent++;
        }
    }

    private static void send(Alert alert) {
        Account flagged = resolve(alert);

        if (flagged != null && flagged.hasRank(RankType.TRIAL))
            return;

        List<String> alts = altsOf(flagged, alert.name);

        String plain = plain(alert, flagged, alts);
        addHistory(plain);

        for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
            Account staff = Core.getAccountController().of(player.getUniqueId());

            if (staff == null || !staff.hasRank(RankType.HELPER)) continue;
            if (staff.getToggle() == null || !staff.getToggle().isAnticheatAlerts()) continue;

            player.sendMessage(build(alert, flagged, alts));
        }
    }

    private static Account resolve(Alert alert) {
        if (alert.uuid == null) return null;

        try {
            return Core.getAccountController().of(UUID.fromString(alert.uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String serverName(Alert alert, Account flagged) {
        if (alert.server != null && !alert.server.trim().isEmpty())
            return alert.server;

        if (flagged != null && flagged.getServerType() != null)
            return flagged.getServerType().getName();

        return "Rede";
    }

    private static List<String> altsOf(Account flagged, String flaggedName) {
        List<String> empty = new ArrayList<>();

        if (flagged == null) return empty;

        String ip = flagged.getIpAddress();

        if (ip == null || ip.isEmpty()) return empty;

        long now = System.currentTimeMillis();
        AltCache cache = altCache.get(ip);

        if (cache != null && cache.expiresAt >= now) return cache.names;

        if (plugin != null && altLoading.add(ip)) {
            ProxyServer.getInstance().getScheduler().runAsync(plugin, () -> {
                try {
                    List<String> names = new ArrayList<>();

                    for (Account alt : Core.getAccountData().getAlts(ip)) {
                        if (alt.getName() == null) continue;
                        if (alt.getName().equalsIgnoreCase(flaggedName)) continue;
                        names.add(alt.getName());
                    }

                    altCache.put(ip, new AltCache(names, System.currentTimeMillis() + ALT_CACHE_MS));
                } catch (Exception e) {
                    Core.getLogger().log(Level.WARNING, "Falha ao carregar contas compartilhadas de " + flaggedName, e);
                } finally {
                    altLoading.remove(ip);
                }
            });
        }

        return empty;
    }

    private static TextComponent build(Alert alert, Account flagged, List<String> alts) {
        TextComponent line = new TextComponent(TextComponent.fromLegacyText("§c§lAC §8» "));

        TextComponent name = new TextComponent(TextComponent.fromLegacyText("§f" + alert.name));
        name.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/go " + alert.name));
        name.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                "§7Clique para ir até §f" + alert.name
                        + "\n§7Ping: §f" + alert.ping + "ms"
                        + (flagged != null ? "\n§7IP: §f" + flagged.getIpAddress() : ""))));
        line.addExtra(name);

        line.addExtra(new TextComponent(TextComponent.fromLegacyText(
                " §7falhou §f" + alert.hack
                        + " §8(§cx" + alert.violations + "§8)"
                        + (alert.count > 1 ? " §8[" + alert.count + "x]" : "")
                        + " §7em §f" + serverName(alert, flagged))));

        if (!alts.isEmpty()) {
            TextComponent altLine = new TextComponent(TextComponent.fromLegacyText("\n§8  ↳ §7Contas compartilhadas: "));

            int shown = Math.min(alts.size(), MAX_ALTS);

            for (int i = 0; i < shown; i++) {
                String alt = alts.get(i);

                TextComponent altComponent = new TextComponent(TextComponent.fromLegacyText("§f" + alt));
                altComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/go " + alt));
                altComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(
                        "§7Clique para ir até §f" + alt)));

                altLine.addExtra(altComponent);

                if (i + 1 < shown) altLine.addExtra(new TextComponent(TextComponent.fromLegacyText("§7, ")));
            }

            if (alts.size() > shown)
                altLine.addExtra(new TextComponent(TextComponent.fromLegacyText(" §8(+" + (alts.size() - shown) + ")")));

            line.addExtra(altLine);
        }

        return line;
    }

    private static String plain(Alert alert, Account flagged, List<String> alts) {
        StringBuilder builder = new StringBuilder("§c[AC] §f")
                .append(alert.name)
                .append(" §7falhou §f")
                .append(alert.hack)
                .append(" §7(x")
                .append(alert.violations)
                .append(")")
                .append(alert.count > 1 ? " [" + alert.count + "x]" : "")
                .append(" §7em §f")
                .append(serverName(alert, flagged));

        if (!alts.isEmpty()) {
            builder.append(" §8| §7contas: §f").append(String.join("§7, §f", alts.subList(0, Math.min(alts.size(), MAX_ALTS))));

            if (alts.size() > MAX_ALTS) builder.append(" §8(+" + (alts.size() - MAX_ALTS) + ")");
        }

        return builder.toString();
    }

    private static void addHistory(String line) {
        synchronized (history) {
            history.addFirst(line);

            while (history.size() > HISTORY_SIZE)
                history.removeLast();
        }
    }

    public static List<String> recentAlerts() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public static List<String> sharedAccounts(String name) {
        List<String> names = new ArrayList<>();

        Account account = Core.getAccountData().of(name);

        if (account == null) return names;

        for (Account alt : Core.getAccountData().getAlts(account.getIpAddress())) {
            if (alt.getName() == null || alt.getName().equalsIgnoreCase(account.getName())) continue;
            names.add(alt.getName());
        }

        return names;
    }

    private static final class Pending {
        private String uuid;
        private String name;
        private String server;
        private String hack;
        private int violations;
        private int count;
        private int ping;
        private long lastSeen = System.currentTimeMillis();
        private long lastSent;
    }

    private static final class Alert {
        private final String uuid;
        private final String name;
        private final String server;
        private final String hack;
        private final int violations;
        private final int count;
        private final int ping;

        private Alert(String uuid, String name, String server, String hack, int violations, int count, int ping) {
            this.uuid = uuid;
            this.name = name;
            this.server = server;
            this.hack = hack;
            this.violations = violations;
            this.count = count;
            this.ping = ping;
        }
    }

    private static final class AltCache {
        private final List<String> names;
        private final long expiresAt;

        private AltCache(List<String> names, long expiresAt) {
            this.names = names;
            this.expiresAt = expiresAt;
        }
    }
}
