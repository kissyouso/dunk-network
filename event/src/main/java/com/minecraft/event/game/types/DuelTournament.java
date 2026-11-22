package com.minecraft.event.game.types;

import com.minecraft.core.Core;
import com.minecraft.event.game.GameEvent;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Torneio de lutas 1v1: dois jogadores sorteados lutam por vez (pos1 x pos2),
 * quem perde é eliminado e o vencedor volta para a entrada. O último que sobrar vence.
 */
public abstract class DuelTournament extends GameEvent {

    private static final int ROUND_TIME = 90;

    protected UUID first, second;

    private int round, roundTime, freeze;

    @Override
    public void start() {
        Location entrance = point("entrada");

        for (Player player : getAlivePlayers())
            player.teleport(entrance);

        nextRound();
    }

    protected void nextRound() {
        first = null;
        second = null;

        List<Player> alive = new ArrayList<>(getAlivePlayers());

        if (alive.size() < 2) return;

        Collections.shuffle(alive, Core.RANDOM);

        Player a = alive.get(0), b = alive.get(1);

        first = a.getUniqueId();
        second = b.getUniqueId();

        round++;
        roundTime = 0;
        freeze = 3;

        reset(a);
        reset(b);

        prepare(a);
        prepare(b);

        a.teleport(point("pos1"));
        b.teleport(point("pos2"));

        getManager().broadcastLocal("§2§lEVENTO §8» §fRodada §a" + round + "§f: §e" + a.getName() + " §7vs §e" + b.getName());

        for (Player player : Arrays.asList(a, b))
            Core.getAccountController().of(player.getUniqueId()).title("§e§lRODADA " + round, "§f" + a.getName() + " §7vs §f" + b.getName());
    }

    /* Kit / efeitos de cada lutador */
    protected abstract void prepare(Player player);

    protected boolean isFighting(Player player) {
        return player != null && (player.getUniqueId().equals(first) || player.getUniqueId().equals(second));
    }

    protected boolean isFrozen() {
        return freeze > 0;
    }

    protected Player getOpponent(Player player) {
        UUID other = player.getUniqueId().equals(first) ? second : first;

        return other != null ? org.bukkit.Bukkit.getPlayer(other) : null;
    }

    /* O lutador perdeu: é eliminado e o outro volta para a entrada */
    protected void lose(Player loser, String reason) {
        Player winner = getOpponent(loser);

        first = null;
        second = null;

        if (winner != null) {
            reset(winner);
            winner.teleport(point("entrada"));
            winner.playSound(winner.getLocation(), Sound.LEVEL_UP, 1F, 1F);
        }

        eliminate(loser, reason);
    }

    @Override
    public void onRemove(Player player) {
        /* Se um dos lutadores saiu, o outro vence a rodada */
        if (isFighting(player)) {
            Player other = getOpponent(player);

            first = null;
            second = null;

            if (other != null) {
                reset(other);
                other.teleport(point("entrada"));
            }
        }

        if (first == null && getAlive().size() >= 2)
            getPlugin().getServer().getScheduler().runTaskLater(getPlugin(), () -> {
                if (first == null && getAlive().size() >= 2)
                    nextRound();
            }, 60L);
    }

    @Override
    public void tick(int tick) {
        if (tick % 20 != 0 || first == null) return;

        if (freeze > 0) {
            freeze--;

            if (freeze == 0) {
                for (UUID id : Arrays.asList(first, second)) {
                    Player player = org.bukkit.Bukkit.getPlayer(id);

                    if (player != null) {
                        Core.getAccountController().of(id).title("§a§lLUTEM!", "");
                        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1F, 2F);
                    }
                }
            }
            return;
        }

        roundTime++;

        if (roundTime >= ROUND_TIME) {
            Player a = org.bukkit.Bukkit.getPlayer(first), b = org.bukkit.Bukkit.getPlayer(second);

            if (a == null || b == null) return;

            /* Tempo esgotado: perde quem tiver menos vida (empate = sorteio) */
            Player loser = a.getHealth() < b.getHealth() ? a : b.getHealth() < a.getHealth() ? b : (Core.RANDOM.nextBoolean() ? a : b);

            getManager().broadcastLocal("§2§lEVENTO §8» §cTempo da rodada esgotado!");
            lose(loser, "tempo esgotado");
        }
    }

    /* Durante a contagem os lutadores não saem do lugar */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen() || !isFighting(event.getPlayer())) return;

        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ())
            event.setTo(event.getFrom().clone().setDirection(event.getTo().getDirection()));
    }

    @Override
    public List<String> getSidebar() {
        Player a = first != null ? org.bukkit.Bukkit.getPlayer(first) : null;
        Player b = second != null ? org.bukkit.Bukkit.getPlayer(second) : null;

        if (a == null || b == null)
            return Collections.singletonList("Rodada: §a" + round);

        return Arrays.asList("Rodada: §a" + round, "§e" + a.getName(), "§7vs", "§e" + b.getName());
    }

    @Override
    public Player timeoutWinner() {
        return null;
    }
}
