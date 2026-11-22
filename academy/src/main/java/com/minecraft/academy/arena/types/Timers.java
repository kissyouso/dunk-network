package com.minecraft.academy.arena.types;

import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.user.User;
import com.minecraft.academy.util.TimeFormat;
import com.minecraft.academy.util.TimerMode;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import com.minecraft.core.bukkit.api.npc.type.client.NpcClient;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.menu.server.academy.AcademySkins;
import org.bukkit.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;

public class Timers extends Arena {

    public Timers() {
        super("Timers", Bukkit.getWorld("timers"), "REFILL", "COGUMELO", "COCOA", "VOLTAR");
    }

    @Override
    protected void giveItems(User user) {
        Player player = user.getPlayer();

        player.getInventory().setItem(7, getVisibilityItem(user));
        player.getInventory().setItem(8, getBackItem());
        player.updateInventory();
    }

    @Override
    protected void spawnEntities(User user) {
        for (TimerMode mode : TimerMode.values()) {
            NpcClient npc = spawnNpc(user, mode.getIdentifier(), AcademySkins.EDITOR,
                    player -> prepare(User.fetch(player.getUniqueId()), mode),
                    "§a§l" + mode.getIdentifier(), "§eClique para iniciar");

            if (npc != null) {
                npc.setHelmet(new ItemStack(Material.WATCH));
                npc.updateEquipment();
            }
        }

        spawnBackNpc(user);
    }

    @Override
    protected void onQuit(User user) {
        user.resetTimer();
    }

    public void prepare(User user, TimerMode mode) {
        if (!contains(user)) return;

        Player player = user.getPlayer();

        reset(player);

        PlayerInventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Material.STONE_SWORD));

        switch (mode) {
            case REFILL:
                for (int i = 9; i < 36; i++)
                    inventory.setItem(i, new ItemStack(Material.MUSHROOM_SOUP));
                break;
            case MUSHROOM:
                inventory.setItem(9, new ItemStack(Material.BOWL, 64));
                inventory.setItem(10, new ItemStack(Material.RED_MUSHROOM, 64));
                inventory.setItem(11, new ItemStack(Material.BROWN_MUSHROOM, 64));
                break;
            case COCOA:
                inventory.setItem(9, new ItemStack(Material.BOWL, 64));
                inventory.setItem(10, new ItemStack(Material.INK_SACK, 64, (short) 3));
                break;
        }

        player.updateInventory();

        user.setTimerMode(mode);
        user.setTimerStart(0L);

        /* O tempo começa quando o jogador abre o inventário (conquista "abrir inventário") */
        player.removeAchievement(Achievement.OPEN_INVENTORY);

        player.sendMessage("§aAbra seu inventário para começar!");
        player.playSound(player.getLocation(), Sound.CLICK, 1F, 1F);
    }

    private void start(User user) {
        if (user.getTimerMode() == null || user.getTimerStart() != 0L) return;

        user.setTimerStart(System.currentTimeMillis());
        user.getPlayer().sendMessage("§aTimer iniciado!");

        handleSidebar(user);
    }

    private void finish(User user) {
        Player player = user.getPlayer();
        TimerMode mode = user.getTimerMode();

        long elapsed = System.currentTimeMillis() - user.getTimerStart();

        user.resetTimer();

        AcademyMetadata academy = user.getAcademy();
        long record = mode.getRecord(academy);

        player.sendMessage("§eSeu tempo de " + mode.getKind() + " foi: §f" + TimeFormat.millis(elapsed));

        if (record == 0L || elapsed < record) {
            if (record != 0L)
                player.sendMessage("§a-" + TimeFormat.difference(record - elapsed));

            player.sendMessage("§aParabéns! Você bateu o seu recorde no " + mode.getDescription() + ".");
            player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);

            mode.setRecord(academy, elapsed);
            Core.getPlatform().runAsync(() -> user.getAccount().saveAcademy());

            int reward = getPlugin().getSettings().getTimersRecordCoins();

            if (reward > 0) {
                user.addCoins(reward);
                player.sendMessage("§6+" + reward + " coins");
            }
        } else {
            player.sendMessage("§c+" + TimeFormat.difference(elapsed - record));
            player.playSound(player.getLocation(), Sound.NOTE_BASS, 1F, 1F);
        }

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (!contains(user)) return;

            player.closeInventory();
            reset(player);
            giveItems(user);
            handleSidebar(user);
        }, 10L);
    }

    /* Termina quando os slots 1-8 da hotbar estão com sopa */
    private void check(User user) {
        if (!user.isTimerRunning()) return;

        PlayerInventory inventory = user.getPlayer().getInventory();

        for (int slot = 1; slot < 9; slot++) {
            ItemStack item = inventory.getItem(slot);

            if (item == null || item.getType() != Material.MUSHROOM_SOUP)
                return;
        }

        finish(user);
    }

    @Override
    public void tick(int tick) {
        if (tick % 20 != 0) return;

        long timeout = getPlugin().getSettings().getTimersTimeoutSeconds() * 1000L;

        for (User user : new ArrayList<>(getUsers())) {
            if (user.isTimerRunning() && System.currentTimeMillis() - user.getTimerStart() > timeout) {
                user.resetTimer();

                Player player = user.getPlayer();

                player.closeInventory();
                reset(player);
                giveItems(user);
                handleSidebar(user);

                player.sendMessage("§cTempo esgotado! Tente novamente.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAchievement(PlayerAchievementAwardedEvent event) {
        if (event.getAchievement() != Achievement.OPEN_INVENTORY) return;

        User user = User.fetch(event.getPlayer().getUniqueId());

        if (!contains(user)) return;

        event.setCancelled(true);
        start(user);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        User user = User.fetch(event.getWhoClicked().getUniqueId());

        if (!contains(user) || user.getTimerMode() == null) return;

        start(user);
        Bukkit.getScheduler().runTask(getPlugin(), () -> check(user));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDrag(InventoryDragEvent event) {
        User user = User.fetch(event.getWhoClicked().getUniqueId());

        if (!contains(user) || user.getTimerMode() == null) return;

        Bukkit.getScheduler().runTask(getPlugin(), () -> check(user));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        User user = User.fetch(event.getPlayer().getUniqueId());

        if (!contains(user)) return;

        if (user.getTimerMode() == null) {
            event.setCancelled(true);
            return;
        }

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

        sidebar.addRow("records", "§fRecordes:");
        sidebar.addRow("cocoa", " §fCocoa: §a" + TimeFormat.millis(academy.getTimerCocoa()));
        sidebar.addRow("mushroom", " §fCogu: §a" + TimeFormat.millis(academy.getTimerMushroom()));
        sidebar.addRow("refill", " §fRefill: §a" + TimeFormat.millis(academy.getTimerRefill()));

        if (user.isTimerRunning()) {
            sidebar.blankRow();
            sidebar.addRow("time", "§fTempo: §e" + TimeFormat.millis(System.currentTimeMillis() - user.getTimerStart()));
        }
    }

    @Override
    public void updateSidebar(User user) {
        super.updateSidebar(user);

        if (user.isTimerRunning() && user.getSidebar() != null && user.getSidebar().hasRow("time"))
            user.getSidebar().updateRow("time", "§fTempo: §e" + TimeFormat.millis(System.currentTimeMillis() - user.getTimerStart()));
    }
}
