package com.minecraft.event.game.types;

import com.minecraft.core.Core;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

/**
 * Semáforo: mesmo percurso do Parkour (vence quem pisar na placa de ouro primeiro),
 * mas quem se mexer no VERMELHO é eliminado.
 */
public class RedLight extends Parkour {

    private enum Light {
        GREEN("§a§lVERDE", "§fCorra!"),
        YELLOW("§e§lAMARELO", "§fPrepare-se para parar!"),
        RED("§c§lVERMELHO", "§fNão se mexa!");

        private final String title, subtitle;

        Light(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private Light light = Light.RED;
    private int ticksLeft = 60, redTicks;

    /* Posição de cada jogador quando o vermelho acendeu */
    private final Map<UUID, Location> frozen = new HashMap<>();

    @Override
    public void start() {
        super.start();
        change(Light.RED, 60);
    }

    private void change(Light next, int ticks) {
        this.light = next;
        this.ticksLeft = ticks;
        this.redTicks = 0;

        frozen.clear();

        for (Player player : getAlivePlayers()) {
            Core.getAccountController().of(player.getUniqueId()).title(next.title, next.subtitle);
            player.playSound(player.getLocation(), next == Light.RED ? Sound.NOTE_BASS : Sound.NOTE_PLING, 1F, next == Light.GREEN ? 2F : 1F);

            if (next == Light.RED)
                frozen.put(player.getUniqueId(), player.getLocation().clone());
        }
    }

    @Override
    public void tick(int tick) {
        if (light == Light.RED)
            redTicks++;

        if (--ticksLeft > 0) return;

        switch (light) {
            case GREEN:
                change(Light.YELLOW, 30);
                break;
            case YELLOW:
                change(Light.RED, 40 + Core.RANDOM.nextInt(60));
                break;
            case RED:
                change(Light.GREEN, 60 + Core.RANDOM.nextInt(80));
                break;
        }
    }

    @Override
    protected void handleMove(Player player, PlayerMoveEvent event) {
        /* Meio segundo de tolerância depois que o vermelho acende */
        if (light == Light.RED && redTicks > 10) {
            Location origin = frozen.get(player.getUniqueId());

            if (origin == null) {
                frozen.put(player.getUniqueId(), event.getFrom().clone());
                return;
            }

            double dx = event.getTo().getX() - origin.getX(), dz = event.getTo().getZ() - origin.getZ();

            if (dx * dx + dz * dz > 0.09) {
                eliminate(player, "se mexeu no vermelho");
                return;
            }
        }

        super.handleMove(player, event);
    }

    @Override
    public List<String> getSidebar() {
        return Collections.singletonList("Sinal: " + light.title);
    }
}
