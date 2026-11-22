package com.minecraft.academy.arena.types;

import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.user.User;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.menu.server.academy.AcademySkins;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

public class Lava extends Arena {

    public Lava() {
        super("Lava", Bukkit.getWorld("lava"), "FACIL", "MEDIO", "DIFICIL", "EXTREMO", "VOLTAR");
    }

    @Override
    protected void giveItems(User user) {
        Player player = user.getPlayer();

        giveSoups(player, true, false);

        Bukkit.getScheduler().runTask(getPlugin(), () -> {
            player.setFireTicks(0);
            player.setVelocity(new Vector());
        });
    }

    @Override
    protected void spawnEntities(User user) {
        for (Level level : Level.values()) {
            spawnNpc(user, level.name(), AcademySkins.FLAME,
                    player -> complete(User.fetch(player.getUniqueId()), level),
                    level.getColor() + "§l" + level.getName().toUpperCase(), "§eClique para finalizar!");
        }

        spawnBackNpc(user);
    }

    /* O NPC de cada nível fica no final do percurso */
    private void complete(User user, Level level) {
        if (!contains(user)) return;

        Player player = user.getPlayer();

        if (System.currentTimeMillis() - user.getArenaJoinedAt() < getPlugin().getSettings().getLavaMinimumSeconds(level.name()) * 1000L) {
            player.sendMessage("§cVocê precisa completar o percurso antes de finalizar.");
            return;
        }

        AcademyMetadata academy = user.getAcademy();
        level.getSetter().accept(academy, level.getGetter().applyAsInt(academy) + 1);

        Core.getPlatform().runAsync(() -> user.getAccount().saveAcademy());

        int coins = getPlugin().getSettings().getLavaCoins(level.name());
        user.addCoins(coins);

        player.sendMessage("§aVocê completou o desafio de lava " + level.getColor() + level.getName() + "§a! §6+" + coins + " coins");
        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);

        restart(user);
    }

    private void restart(User user) {
        Player player = user.getPlayer();

        user.setArenaJoinedAt(System.currentTimeMillis());

        reset(player);
        player.teleport(getSpawn());

        giveItems(user);
        handleSidebar(user);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        User user = User.fetch(event.getEntity().getUniqueId());

        if (!contains(user)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        boolean valid = cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.VOID;

        if (!valid) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getEntity();

        /* Morrer reinicia o percurso */
        if (cause == EntityDamageEvent.DamageCause.VOID || player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            player.sendMessage("§cVocê morreu! Tente novamente.");

            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (contains(user))
                    restart(user);
            });
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (contains(User.fetch(event.getPlayer().getUniqueId())))
            event.getItemDrop().remove();
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (contains(User.fetch(event.getPlayer().getUniqueId())))
            event.setCancelled(true);
    }

    @Override
    protected void buildSidebar(User user, Sidebar sidebar) {
        AcademyMetadata academy = user.getAcademy();

        for (Level level : Level.values())
            sidebar.addRow(level.name().toLowerCase(), level.getColor() + level.getName() + ": §f" + level.getGetter().applyAsInt(academy));
    }

    @Getter
    @AllArgsConstructor
    public enum Level {

        FACIL("Fácil", "§a", AcademyMetadata::getLavaEasy, AcademyMetadata::setLavaEasy),
        MEDIO("Médio", "§e", AcademyMetadata::getLavaMedium, AcademyMetadata::setLavaMedium),
        DIFICIL("Difícil", "§c", AcademyMetadata::getLavaHard, AcademyMetadata::setLavaHard),
        EXTREMO("Extremo", "§4", AcademyMetadata::getLavaExtreme, AcademyMetadata::setLavaExtreme);

        private final String name, color;

        private final ToIntFunction<AcademyMetadata> getter;
        private final BiConsumer<AcademyMetadata, Integer> setter;
    }
}
