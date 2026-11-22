package com.minecraft.event.game;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.event.EventState;
import com.minecraft.core.backend.database.redis.message.types.network.NetworkBroadcastMessage;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.util.Util;
import com.minecraft.event.Events;
import com.minecraft.event.user.User;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.text.SimpleDateFormat;
import java.util.*;

import static com.minecraft.core.bukkit.command.single.EventCommand.PREFIX;

@Getter
public class EventManager {

    public enum Phase {
        NONE, CALLING, STARTING, RUNNING, ENDING
    }

    private final Events plugin;

    private GameEvent event;
    private Phase phase = Phase.NONE;

    private boolean vipOnly;

    /* Chamadas restantes e segundos até a próxima */
    private int callsLeft, nextCall, countdown, seconds;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> spectators = new HashSet<>();

    private String lastScheduled = "";

    public EventManager(Events plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return phase != Phase.NONE;
    }

    /* ---------------- Início / chamadas ---------------- */

    public String start(EventType type, boolean vipOnly) {
        if (isRunning())
            return "Já existe um evento acontecendo (" + event.getType().getName() + ").";

        List<String> missing = new ArrayList<>();

        for (String point : type.getPoints()) {
            if (plugin.getLocations().get(type.name(), point) == null)
                missing.add(point);
        }

        if (!missing.isEmpty())
            return "Defina os locais de " + type.getName() + " antes: " + String.join(", ", missing) + " (/evento set " + type.name().toLowerCase() + " <ponto>).";

        GameEvent created = type.getFactory().get();
        created.setup(type);

        this.event = created;
        this.vipOnly = vipOnly;
        this.phase = Phase.CALLING;

        this.callsLeft = plugin.getSettings().getCalls();
        this.nextCall = 0;
        this.seconds = 0;

        participants.clear();
        spectators.clear();

        Bukkit.getPluginManager().registerEvents(created, plugin);

        publishState();
        return null;
    }

    private void call() {
        callsLeft--;

        String name = event.getType().getName();

        new NetworkBroadcastMessage("/evento entrar", "§aClique para participar do evento " + name + "!", new String[]{
                "",
                PREFIX + "O evento §a§l" + name.toUpperCase() + " §festá começando!" + (vipOnly ? " §7(exclusivo para §aVIPs§7)" : ""),
                PREFIX + "§7" + event.getType().getDescription(),
                PREFIX + "Jogadores: §a" + participants.size() + " §7| §fChamadas restantes: §a" + callsLeft,
                PREFIX + "§a§lCLIQUE AQUI §fou use §a/evento entrar §fpara participar!",
                ""}).send();

        nextCall = plugin.getSettings().getCallInterval();
    }

    public void forceStart() {
        if (phase == Phase.CALLING) {
            callsLeft = 0;
            nextCall = 0;
        }
    }

    private void begin() {
        if (participants.size() < plugin.getSettings().getMinimumPlayers()) {
            cancel("Evento cancelado por falta de jogadores (mínimo: " + plugin.getSettings().getMinimumPlayers() + ").");
            return;
        }

        phase = Phase.STARTING;
        countdown = 5;
    }

    private void run() {
        phase = Phase.RUNNING;
        seconds = 0;

        event.getAlive().clear();
        event.getAlive().addAll(participants);

        for (Player player : event.getAlivePlayers()) {
            GameEvent.reset(player);

            Account account = Core.getAccountController().of(player.getUniqueId());

            if (account != null) {
                account.getContext().setEventParticipations(account.getContext().getEventParticipations() + 1);
                Core.getPlatform().runAsync(() -> account.saveContext(account.getContext()));
            }
        }

        broadcastLocal(PREFIX + "O evento §a" + event.getType().getName() + " §fcomeçou! Boa sorte!");

        new NetworkBroadcastMessage(plugin.getSettings().isSpectators() ? "/evento assistir" : null, "§aClique para assistir!", new String[]{
                PREFIX + "O evento §a" + event.getType().getName() + " §fcomeçou com §a" + participants.size() + " §fjogadores!"
                        + (plugin.getSettings().isSpectators() ? " §7(/evento assistir)" : "")}).send();

        event.start();
        checkWinner();
    }

    /* ---------------- Loop ---------------- */

    public void tick(int tick) {
        if (phase == Phase.RUNNING && event != null)
            event.tick(tick);

        if (tick % 20 != 0) return;

        if (tick % 1200 == 0)
            checkSchedule();

        switch (phase) {
            case CALLING: {
                if (nextCall <= 0) {
                    if (callsLeft <= 0)
                        begin();
                    else
                        call();
                } else
                    nextCall--;
                break;
            }
            case STARTING: {
                if (countdown <= 0) {
                    run();
                    break;
                }

                for (Player player : onlineParticipants()) {
                    Core.getAccountController().of(player.getUniqueId()).title("§a§l" + countdown, "§fO evento começa em breve!");
                    player.playSound(player.getLocation(), Sound.NOTE_PLING, 1F, 1F);
                }

                countdown--;
                break;
            }
            case RUNNING: {
                seconds++;

                if (seconds >= plugin.getSettings().getMaxTime(event.getType())) {
                    broadcastLocal(PREFIX + "§cO tempo do evento acabou!");
                    finish(event.timeoutWinner());
                }
                break;
            }
            default:
                break;
        }

        publishState();
        updateSidebars();
    }

    private void checkSchedule() {
        if (!plugin.getSettings().isScheduleEnabled() || isRunning()) return;

        String now = new SimpleDateFormat("HH:mm").format(new Date());

        if (now.equals(lastScheduled)) return;

        for (String entry : plugin.getSettings().getSchedule()) {
            String[] parts = entry.trim().split(" ");

            if (parts.length < 2 || !parts[0].equals(now)) continue;

            EventType type = parts[1].equalsIgnoreCase("ALEATORIO")
                    ? EventType.values()[Core.RANDOM.nextInt(EventType.values().length)]
                    : EventType.of(parts[1]);

            if (type == null) continue;

            lastScheduled = now;

            String error = start(type, false);

            if (error != null)
                plugin.getLogger().warning("[Eventos] Evento agendado não iniciou: " + error);
            return;
        }
    }

    /* ---------------- Jogadores ---------------- */

    public void join(Player player) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (phase != Phase.CALLING) {
            player.sendMessage(PREFIX + (phase == Phase.NONE ? "§cNão há nenhum evento aberto agora." : "§cO evento já começou. Use §f/evento assistir§c."));
            return;
        }

        if (vipOnly && account != null && !account.isVIP()) {
            player.sendMessage(PREFIX + "§cEste evento é exclusivo para §aVIPs§c.");
            return;
        }

        if (participants.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cVocê já está no evento.");
            return;
        }

        spectators.remove(player.getUniqueId());
        participants.add(player.getUniqueId());

        GameEvent.reset(player);

        Location waiting = event.point("espera");
        player.teleport(waiting != null ? waiting : plugin.getLocations().getSpawn());

        broadcastLocal(PREFIX + "§a" + player.getName() + " §fentrou no evento! §7(" + participants.size() + " jogadores)");
    }

    public void spectate(Player player) {
        if (!isRunning()) {
            player.sendMessage(PREFIX + "§cNão há nenhum evento acontecendo agora.");
            return;
        }

        if (!plugin.getSettings().isSpectators()) {
            player.sendMessage(PREFIX + "§cEste evento não permite espectadores.");
            return;
        }

        if (event.isAlive(player) || participants.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§cVocê está participando do evento. Use §f/evento sair §cpara sair.");
            return;
        }

        spectators.add(player.getUniqueId());

        GameEvent.reset(player);
        player.teleport(plugin.getLocations().getSpectator());

        player.sendMessage(PREFIX + "Você está assistindo ao evento §a" + event.getType().getName() + "§f. Use §a/evento sair §fpara sair.");
    }

    public void leave(Player player, boolean quit) {
        UUID id = player.getUniqueId();

        boolean wasAlive = event != null && phase == Phase.RUNNING && event.isAlive(player);

        participants.remove(id);
        spectators.remove(id);

        if (event != null)
            event.getAlive().remove(id);

        if (!quit) {
            GameEvent.reset(player);
            player.teleport(plugin.getLocations().getSpawn());
            player.sendMessage(PREFIX + "Você saiu do evento.");
        }

        if (wasAlive) {
            broadcastLocal(PREFIX + "§c" + player.getName() + " §fsaiu do evento. §7(Restam " + event.getAlive().size() + ")");
            event.onRemove(player);
            checkWinner();
        }
    }

    public void eliminate(Player player, String reason) {
        if (event == null || phase != Phase.RUNNING || !event.isAlive(player)) return;

        event.getAlive().remove(player.getUniqueId());
        participants.remove(player.getUniqueId());

        broadcastLocal(PREFIX + "§c" + player.getName() + " §ffoi eliminado" + (reason != null ? " §7(" + reason + ")" : "") + "§f! §7Restam " + event.getAlive().size() + ".");

        player.playSound(player.getLocation(), Sound.VILLAGER_DEATH, 1F, 1F);
        Core.getAccountController().of(player.getUniqueId()).title("§c§lELIMINADO", "§fBoa sorte no próximo evento!");

        GameEvent.reset(player);

        if (plugin.getSettings().isSpectators()) {
            spectators.add(player.getUniqueId());
            player.teleport(plugin.getLocations().getSpectator());
            player.sendMessage(PREFIX + "Você está no camarote. Use §a/evento sair §fpara sair.");
        } else
            player.teleport(plugin.getLocations().getSpawn());

        event.onRemove(player);
        checkWinner();
    }

    private void checkWinner() {
        if (phase != Phase.RUNNING || event == null) return;

        List<Player> alive = event.getAlivePlayers();

        if (alive.size() == 1)
            finish(alive.get(0));
        else if (alive.isEmpty())
            finish(null);
    }

    /* ---------------- Fim ---------------- */

    public void finish(Player winner) {
        if (phase == Phase.ENDING || phase == Phase.NONE) return;

        phase = Phase.ENDING;

        String name = event.getType().getName();

        if (winner != null) {
            Account account = Core.getAccountController().of(winner.getUniqueId());

            NetworkBroadcastMessage.of(
                    "",
                    PREFIX + "§a§l" + winner.getName() + " §fvenceu o evento §a§l" + name.toUpperCase() + "§f!",
                    "").send();

            if (account != null) {
                account.getContext().setEventWins(account.getContext().getEventWins() + 1);
                Core.getPlatform().runAsync(() -> account.saveContext(account.getContext()));

                account.title("§a§lVITÓRIA!", "§fVocê venceu o evento " + name + "!");
            }

            winner.playSound(winner.getLocation(), Sound.LEVEL_UP, 1F, 1F);

            for (String command : plugin.getSettings().getRewards(event.getType()))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", winner.getName()));
        } else
            NetworkBroadcastMessage.of(PREFIX + "O evento §a" + name + " §fterminou sem vencedor.").send();

        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, 100L);
    }

    public void cancel(String reason) {
        if (!isRunning()) return;

        NetworkBroadcastMessage.of(PREFIX + "§cO evento " + event.getType().getName() + " foi cancelado." + (reason != null ? " §7(" + reason + ")" : "")).send();

        cleanup();
    }

    private void cleanup() {
        if (event != null) {
            event.stop();
            HandlerList.unregisterAll(event);
        }

        Set<UUID> everyone = new HashSet<>(participants);
        everyone.addAll(spectators);

        if (event != null)
            everyone.addAll(event.getAlive());

        for (UUID id : everyone) {
            Player player = Bukkit.getPlayer(id);

            if (player == null) continue;

            GameEvent.reset(player);
            player.teleport(plugin.getLocations().getSpawn());
        }

        participants.clear();
        spectators.clear();

        event = null;
        phase = Phase.NONE;

        publishState();
        updateSidebars();
    }

    public void kick(Player player) {
        if (event != null && phase == Phase.RUNNING && event.isAlive(player))
            eliminate(player, "expulso");
        else
            leave(player, false);
    }

    /* ---------------- Utilitários ---------------- */

    public List<Player> onlineParticipants() {
        List<Player> list = new ArrayList<>();

        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);

            if (player != null)
                list.add(player);
        }

        return list;
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public void broadcastLocal(String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    private void publishState() {
        Core.getPlatform().runAsync(() -> {
            if (event == null || phase == Phase.NONE || phase == Phase.ENDING) {
                EventState.publish(null);
                return;
            }

            int players = phase == Phase.RUNNING ? event.getAlive().size() : participants.size();

            EventState.publish(new EventState(event.getType().name(), event.getType().getName(),
                    phase == Phase.CALLING ? "CALLING" : "RUNNING", players, callsLeft, vipOnly, plugin.getSettings().isSpectators()));
        });
    }

    private static String time(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public void updateSidebars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            User user = User.fetch(player.getUniqueId());

            if (user == null) continue;

            Sidebar sidebar = user.getSidebar();

            if (sidebar == null) {
                sidebar = new Sidebar(player, "EVENTOS");
                user.setSidebar(sidebar);
            }

            sidebar.clear();
            sidebar.setTitle("EVENTOS");
            sidebar.blankRow();

            if (phase == Phase.NONE || event == null) {
                sidebar.addRow("none_one", "Nenhum evento");
                sidebar.addRow("none_two", "acontecendo agora.");
                sidebar.blankRow();
                sidebar.addRow("none_three", "Aguarde a próxima");
                sidebar.addRow("none_four", "chamada!");
                sidebar.blankRow();
                sidebar.addRow("online", "Jogadores: §a" + Bukkit.getOnlinePlayers().size());
            } else if (phase == Phase.CALLING || phase == Phase.STARTING) {
                int remaining = phase == Phase.STARTING ? countdown
                        : nextCall + Math.max(0, callsLeft) * plugin.getSettings().getCallInterval();

                sidebar.addRow("event", "Evento: §a" + event.getType().getName());
                sidebar.addRow("players", "Jogadores: §a" + participants.size());
                sidebar.addRow("calls", "Chamadas: §a" + Math.max(0, callsLeft));
                sidebar.blankRow();
                sidebar.addRow("starting", "Iniciando em: §a" + time(remaining));

                if (vipOnly) {
                    sidebar.blankRow();
                    sidebar.addRow("vip", "§aExclusivo para VIPs");
                }
            } else {
                sidebar.addRow("event", "Evento: §a" + event.getType().getName());
                sidebar.addRow("alive", "Restantes: §a" + event.getAlive().size());
                sidebar.addRow("time", "Tempo: §a" + time(seconds));

                List<String> extra = event.getSidebar();

                if (!extra.isEmpty()) {
                    sidebar.blankRow();

                    int index = 0;
                    for (String line : extra)
                        sidebar.addRow("extra_" + index++, line);
                }

                if (isSpectator(player)) {
                    sidebar.blankRow();
                    sidebar.addRow("spectator", "§7Você está assistindo.");
                }
            }

            sidebar.blankRow();
            sidebar.addWebsiteRow();
            sidebar.display();
        }
    }
}
