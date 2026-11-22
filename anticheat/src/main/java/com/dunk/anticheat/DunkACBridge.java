package com.dunk.anticheat;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Recebe os alertas do Anticheat por um comando de console (configurado no punishments.yml)
 * e publica no Redis da rede. Nao faz deteccao: apenas transporta a flag.
 */
public final class DunkACBridge extends org.bukkit.plugin.java.JavaPlugin implements CommandExecutor {

    /* Mesma flag do mesmo jogador dentro da janela nao e republicada. */
    private static final long PLAYER_CHECK_COOLDOWN_MS = 3_000L;
    private static final int MAX_PUBLISH_PER_CYCLE = 20;

    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Queue<Flag> queue = new ConcurrentLinkedQueue<>();

    private RedisAlertPublisher publisher;
    private BukkitTask flushTask;

    @Override
    public void onEnable() {
        publisher = new RedisAlertPublisher(this);

        getCommand("dunkacbridge").setExecutor(this);

        flushTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this, this::flush, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (flushTask != null) flushTask.cancel();
        if (publisher != null) publisher.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 5 || !args[0].equalsIgnoreCase("flag")) {
            sender.sendMessage("[ANTICHEAT] Uso: /" + label + " flag <uuid> <vl> <ping> <check>");
            return true;
        }

        UUID uuid;

        try {
            uuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            return true;
        }

        Player player = Bukkit.getPlayer(uuid);

        if (player == null) return true;

        int violations;
        int ping;

        try {
            violations = Integer.parseInt(args[2]);
            ping = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            return true;
        }

        StringBuilder check = new StringBuilder(args[4]);
        for (int i = 5; i < args.length; i++) check.append(' ').append(args[i]);

        queueFlag(uuid, player.getName(), check.toString(), violations, ping);
        return true;
    }

    private void queueFlag(UUID uuid, String name, String check, int violations, int ping) {
        String key = uuid + "|" + check.toLowerCase();
        long now = System.currentTimeMillis();

        synchronized (cooldowns) {
            Long last = cooldowns.get(key);

            if (last != null && now - last < PLAYER_CHECK_COOLDOWN_MS) return;

            cooldowns.put(key, now);

            if (cooldowns.size() > 512) {
                Iterator<Map.Entry<String, Long>> iterator = cooldowns.entrySet().iterator();
                while (iterator.hasNext()) {
                    if (now - iterator.next().getValue() > 60_000L) iterator.remove();
                }
            }
        }

        if (queue.size() >= 256) return;

        queue.add(new Flag(uuid.toString(), name, System.getProperty("server_name", ""), check, violations, ping));
    }

    private void flush() {
        int sent = 0;
        Flag flag;

        while (sent < MAX_PUBLISH_PER_CYCLE && (flag = queue.poll()) != null) {
            if (!publisher.publish(flag.uuid, flag.name, flag.server, flag.check, flag.violations, flag.ping)) {
                return;
            }

            sent++;
        }
    }

    private static final class Flag {
        private final String uuid;
        private final String name;
        private final String server;
        private final String check;
        private final int violations;
        private final int ping;

        private Flag(String uuid, String name, String server, String check, int violations, int ping) {
            this.uuid = uuid;
            this.name = name;
            this.server = server;
            this.check = check;
            this.violations = violations;
            this.ping = ping;
        }
    }
}
