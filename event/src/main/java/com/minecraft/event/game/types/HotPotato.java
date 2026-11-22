package com.minecraft.event.game.types;

import com.minecraft.core.Core;
import com.minecraft.core.api.item.Item;
import com.minecraft.event.game.GameEvent;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Batata Quente: um jogador fica com a batata; batendo em outro, ela passa para ele.
 * Quando o tempo acaba, quem está com a batata explode e é eliminado.
 */
public class HotPotato extends GameEvent {

    private UUID holder;
    private int timer, round;

    @Override
    public void start() {
        Location entrance = point("entrada");

        for (Player player : getAlivePlayers())
            player.teleport(entrance);

        Bukkit.getScheduler().runTaskLater(getPlugin(), this::newRound, 60L);
    }

    private void newRound() {
        List<Player> alive = getAlivePlayers();

        if (alive.size() < 2) return;

        round++;

        /* As rodadas ficam mais rápidas: 20s, 18s, 16s... até 8s */
        timer = Math.max(8, 22 - round * 2);

        give(alive.get(Core.RANDOM.nextInt(alive.size())));
    }

    private void give(Player player) {
        Player old = holder != null ? Bukkit.getPlayer(holder) : null;

        if (old != null) {
            old.getInventory().clear();
            old.getInventory().setHelmet(null);
            old.updateInventory();
        }

        holder = player.getUniqueId();

        ItemStack potato = Item.of(Material.BAKED_POTATO, "§c§lBATATA QUENTE §7(bata em alguém!)");

        for (int slot = 0; slot < 9; slot++)
            player.getInventory().setItem(slot, potato);

        player.getInventory().setHelmet(new ItemStack(Material.TNT));
        player.updateInventory();

        Core.getAccountController().of(player.getUniqueId()).title("§c§lBATATA QUENTE!", "§fPasse para alguém, rápido!");
        player.playSound(player.getLocation(), Sound.FUSE, 1F, 1F);

        getManager().broadcastLocal("§2§lEVENTO §8» §c" + player.getName() + " §festá com a batata quente!");
    }

    @Override
    public void tick(int tick) {
        if (tick % 20 != 0 || holder == null) return;

        timer--;

        Player player = Bukkit.getPlayer(holder);

        if (player == null) return;

        if (timer <= 3 && timer > 0)
            player.playSound(player.getLocation(), Sound.NOTE_PLING, 1F, 0.5F);

        if (timer <= 0) {
            holder = null;

            player.getWorld().playEffect(player.getLocation(), Effect.EXPLOSION_HUGE, 0);
            player.getWorld().playSound(player.getLocation(), Sound.EXPLODE, 1F, 1F);

            eliminate(player, "a batata explodiu");

            if (getAlive().size() >= 2)
                Bukkit.getScheduler().runTaskLater(getPlugin(), this::newRound, 40L);
        }
    }

    @Override
    public void onRemove(Player player) {
        /* Quem estava com a batata saiu: começa outra rodada */
        if (player.getUniqueId().equals(holder)) {
            holder = null;

            if (getAlive().size() >= 2)
                Bukkit.getScheduler().runTaskLater(getPlugin(), this::newRound, 40L);
        }
    }

    @Override
    public boolean allowDamage(Player victim, EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent) || !isAlive(victim)) return false;

        if (!(((EntityDamageByEntityEvent) event).getDamager() instanceof Player)) return false;

        Player damager = (Player) ((EntityDamageByEntityEvent) event).getDamager();

        if (damager.getUniqueId().equals(holder) && isAlive(damager))
            give(victim);

        return false;
    }

    @Override
    public List<String> getSidebar() {
        Player player = holder != null ? Bukkit.getPlayer(holder) : null;

        return Arrays.asList("Com a batata:", player != null ? "§c" + player.getName() : "§7Ninguém", "Explode em: §c" + Math.max(0, timer) + "s");
    }
}
