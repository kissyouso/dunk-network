package com.minecraft.event.game.types;

import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.event.game.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Killer: todos contra todos com kit. Morreu = eliminado, o último vivo vence.
 */
public class Killer extends GameEvent {

    private int grace;

    @Override
    public void start() {
        Location entrance = point("entrada");

        for (Player player : getAlivePlayers()) {
            /* Espalha os jogadores um pouco em volta da entrada */
            Location spot = entrance.clone().add(Core.RANDOM.nextInt(9) - 4, 0, Core.RANDOM.nextInt(9) - 4);
            player.teleport(spot.getBlock().getType() == Material.AIR ? spot : entrance);

            player.getInventory().setItem(0, Item.of(Material.IRON_SWORD).unbreakable());
            player.getInventory().setItem(1, new ItemStack(Material.GOLDEN_APPLE, 2));

            for (int slot = 2; slot < 9; slot++)
                player.getInventory().setItem(slot, new ItemStack(Material.MUSHROOM_SOUP));

            player.getInventory().setArmorContents(new ItemStack[]{
                    Item.of(Material.IRON_BOOTS).unbreakable(), Item.of(Material.IRON_LEGGINGS).unbreakable(),
                    Item.of(Material.IRON_CHESTPLATE).unbreakable(), Item.of(Material.IRON_HELMET).unbreakable()});

            player.updateInventory();
        }

        grace = 5;
        getManager().broadcastLocal("§2§lEVENTO §8» §fO PvP será liberado em §a5 segundos§f!");
    }

    @Override
    public void tick(int tick) {
        if (tick % 20 == 0 && grace > 0) {
            grace--;

            if (grace == 0)
                getAlivePlayers().forEach(player -> Core.getAccountController().of(player.getUniqueId()).title("§c§lPVP LIBERADO!", "§fO último vivo vence!"));
        }
    }

    @Override
    public boolean allowDamage(Player victim, EntityDamageEvent event) {
        if (!isAlive(victim) || grace > 0) return false;

        if (event instanceof EntityDamageByEntityEvent) {
            if (!(((EntityDamageByEntityEvent) event).getDamager() instanceof Player)
                    || !isAlive((Player) ((EntityDamageByEntityEvent) event).getDamager()))
                return false;
        }

        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);

            Player killer = victim.getKiller();
            eliminate(victim, killer != null ? "morto por " + killer.getName() : "morreu");
            return false;
        }

        return true;
    }

    @Override
    public Player timeoutWinner() {
        return getAlivePlayers().stream().max(Comparator.comparingDouble(Player::getHealth)).orElse(null);
    }

    @Override
    public List<String> getSidebar() {
        return Collections.singletonList(grace > 0 ? "PvP em: §e" + grace + "s" : "§cPvP liberado!");
    }
}
