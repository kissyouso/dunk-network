package com.minecraft.academy.arena.types;

import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.user.User;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

public class Texture extends Arena {

    public Texture() {
        super("Texture", Bukkit.getWorld("texture"), "VOLTAR");
    }

    @Override
    protected void giveItems(User user) {
        Player player = user.getPlayer();

        player.getInventory().setItem(7, getVisibilityItem(user));
        player.getInventory().setItem(8, getBackItem());
        player.setAllowFlight(getPlugin().getSettings().isTextureFlight());
        player.updateInventory();
    }

    @Override
    public void applySettings() {
        super.applySettings();

        boolean flight = getPlugin().getSettings().isTextureFlight();

        for (User user : getUsers()) {
            if (!flight)
                user.getPlayer().setFlying(false);

            user.getPlayer().setAllowFlight(flight);
        }
    }

    @Override
    protected void onQuit(User user) {
        Player player = user.getPlayer();

        if (player != null) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @Override
    protected void spawnEntities(User user) {
        spawnBackNpc(user);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        User user = User.fetch(event.getEntity().getUniqueId());

        if (!contains(user)) return;

        event.setCancelled(true);

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID)
            event.getEntity().teleport(getSpawn());
    }

    @Override
    protected void buildSidebar(User user, Sidebar sidebar) {
        sidebar.addRow("text_one", "§fExplore sua textura em");
        sidebar.addRow("text_two", "§fnosso mapa exclusivo!");
    }
}
