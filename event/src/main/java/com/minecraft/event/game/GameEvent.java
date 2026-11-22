package com.minecraft.event.game;

import com.minecraft.event.Events;
import com.minecraft.event.config.EventLocations;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base de um evento presencial. O {@link EventManager} cuida das chamadas, entrada/saída,
 * eliminação, vencedor e recompensas; cada evento só implementa as próprias regras.
 */
@Getter
@Setter
public abstract class GameEvent implements Listener {

    private EventType type;

    /* Participantes ainda vivos no evento */
    private final Set<UUID> alive = new LinkedHashSet<>();

    public void setup(EventType type) {
        this.type = type;
    }

    public Events getPlugin() {
        return Events.getEvents();
    }

    public EventManager getManager() {
        return getPlugin().getEventManager();
    }

    public EventLocations getLocations() {
        return getPlugin().getLocations();
    }

    public Location point(String name) {
        return getLocations().get(type.name(), name);
    }

    public List<Player> getAlivePlayers() {
        return alive.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public boolean isAlive(Player player) {
        return player != null && alive.contains(player.getUniqueId());
    }

    /* Elimina o jogador (vai para o camarote) e verifica se já há vencedor */
    protected void eliminate(Player player, String reason) {
        getManager().eliminate(player, reason);
    }

    protected void win(Player player) {
        getManager().finish(player);
    }

    public static void reset(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setItemOnCursor(null);

        for (PotionEffect effect : player.getActivePotionEffects())
            player.removePotionEffect(effect.getType());

        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    /* Chamado quando o evento começa (após as chamadas) */
    public abstract void start();

    /* A cada tick enquanto o evento roda */
    public void tick(int tick) {
    }

    /* Ao terminar ou ser cancelado (restaurar arenas, limpar itens) */
    public void stop() {
    }

    /* Um participante saiu/foi eliminado (ex: passar a batata, próxima luta) */
    public void onRemove(Player player) {
    }

    /* Linhas extras da scoreboard durante o evento */
    public List<String> getSidebar() {
        return Collections.emptyList();
    }

    /* Se o dano deve acontecer (o padrão é nenhum dano) */
    public boolean allowDamage(Player victim, EntityDamageEvent event) {
        return false;
    }

    /* Tempo esgotado: vencedor (ou null para terminar sem vencedor) */
    public Player timeoutWinner() {
        return null;
    }
}
