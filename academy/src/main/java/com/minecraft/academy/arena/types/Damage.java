package com.minecraft.academy.arena.types;

import com.minecraft.academy.arena.Arena;
import com.minecraft.academy.user.User;
import com.minecraft.academy.util.TimeFormat;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.academy.DamageSettings;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.menu.server.academy.AcademySkins;
import com.minecraft.core.bukkit.menu.server.academy.DamageMenu;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;

public class Damage extends Arena {

    private static final String OWNER_KEY = "academy_owner";

    public Damage() {
        super("Damage", Bukkit.getWorld("damage"), "CONFIGURAR", "VOLTAR");
    }

    public void openMenu(User user) {
        new DamageMenu(user.getPlayer(), user.getAccount(), null, user::isTraining, () -> {
            if (contains(user)) {
                giveItems(user);
                handleSidebar(user);
            }
        }).handle();
    }

    @Override
    protected void giveItems(User user) {
        Player player = user.getPlayer();
        DamageSettings settings = user.getDamageSettings();

        player.getInventory().clear();

        giveSoups(player, settings.isFullInventory(), settings.getRecraft() == DamageSettings.Recraft.COCOA);

        if (settings.getRecraft() == DamageSettings.Recraft.NONE) {
            player.getInventory().setItem(13, null);
            player.getInventory().setItem(14, null);
            player.getInventory().setItem(15, null);
        }

        if (settings.isArmor()) {
            player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.IRON_BOOTS), new ItemStack(Material.IRON_LEGGINGS),
                    new ItemStack(Material.IRON_CHESTPLATE), new ItemStack(Material.IRON_HELMET)});
        } else
            player.getInventory().setArmorContents(null);

        player.updateInventory();
    }

    @Override
    protected void spawnEntities(User user) {
        spawnNpc(user, "CONFIGURAR", AcademySkins.DAMAGE, player -> openMenu(User.fetch(player.getUniqueId())),
                "§a§lDAMAGE", "§eClique para configurar!");

        spawnBackNpc(user);
    }

    @Override
    protected void onQuit(User user) {
        user.setTraining(false);
        user.setTrainingSeconds(0);
    }

    private void startTraining(User user) {
        Player player = user.getPlayer();
        DamageSettings settings = user.getDamageSettings();

        user.setTraining(true);
        user.setTrainingSeconds(0);

        player.setHealth(player.getMaxHealth());
        player.setNoDamageTicks(15);

        if (settings.isWither())
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, Integer.MAX_VALUE, 1), true);

        player.sendMessage("§aTreino iniciado! Nível: " + settings.getLevel().getColor() + settings.getLevel().getName() + "§a.");

        handleSidebar(user);
    }

    private void endTraining(User user) {
        Player player = user.getPlayer();
        DamageSettings settings = user.getDamageSettings();

        int seconds = user.getTrainingSeconds();

        user.setTraining(false);
        user.setTrainingSeconds(0);

        player.sendMessage("§cVocê não aguentou! §7Tempo: §f" + TimeFormat.seconds(seconds));

        if (settings.isRecordValid()) {
            int reward = (seconds / 60) * getPlugin().getSettings().getDamageCoinsPerMinute();

            if (reward > 0) {
                user.addCoins(reward);
                player.sendMessage("§6+" + reward + " coins");
            }

            DamageSettings.Level level = settings.getLevel();

            if (seconds > user.getAcademy().getDamageRecord(level)) {
                user.getAcademy().setDamageRecord(level, seconds);
                Core.getPlatform().runAsync(() -> user.getAccount().saveAcademy());

                player.sendMessage("§aParabéns! Você bateu o seu recorde no nível " + level.getColor() + level.getName() + "§a.");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1F, 1F);
            }
        }

        reset(player);
        player.teleport(getSpawn());

        giveItems(user);
        handleSidebar(user);
    }

    @Override
    public void tick(int tick) {
        for (User user : new ArrayList<>(getUsers())) {
            if (!user.isTraining()) continue;

            Player player = user.getPlayer();

            if (player == null) continue;

            DamageSettings settings = user.getDamageSettings();

            if (tick % settings.getFrequency().getTicks() == 0) {
                double damage = settings.getLevel().getDamage() * (settings.isArmor() ? 1D - getPlugin().getSettings().getDamageArmorReduction() : 1D);

                if (player.getHealth() - damage <= 0) {
                    endTraining(user);
                    continue;
                }

                player.setHealth(player.getHealth() - damage);
                player.playEffect(EntityEffect.HURT);
                player.playSound(player.getLocation(), Sound.HURT_FLESH, 1F, 1F);
            }

            if (tick % 20 == 0)
                user.setTrainingSeconds(user.getTrainingSeconds() + 1);

            if (settings.isDrops() && tick % getPlugin().getSettings().getDamageDropsIntervalTicks() == 0) {
                for (int i = 0; i < 5; i++) {
                    Item item = getWorld().dropItemNaturally(player.getLocation().clone().add(0, 0.5, 0), new ItemStack(Material.WOOD_SPADE));
                    item.setMetadata(OWNER_KEY, new FixedMetadataValue(getPlugin(), player.getEntityId()));
                }
            }
        }
    }

    /* Pisar no bloco de início (padrão: argila amarela) começa o treino */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();

        if (to.getBlockX() == event.getFrom().getBlockX() && to.getBlockY() == event.getFrom().getBlockY() && to.getBlockZ() == event.getFrom().getBlockZ())
            return;

        Player player = event.getPlayer();
        User user = User.fetch(player.getUniqueId());

        if (!contains(user) || user.isTraining()) return;

        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Block block = to.getBlock().getRelative(BlockFace.DOWN);

        if (block.getType() == getPlugin().getSettings().getDamageStartBlock() && block.getData() == getPlugin().getSettings().getDamageStartBlockData())
            startTraining(user);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        User user = User.fetch(event.getEntity().getUniqueId());

        if (!contains(user)) return;

        /* Fora do treino não há dano; no treino só o Wither vem de evento (o resto é aplicado no tick) */
        if (!user.isTraining() || event.getCause() != EntityDamageEvent.DamageCause.WITHER) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getEntity();

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);

            Bukkit.getScheduler().runTask(getPlugin(), () -> {
                if (contains(user) && user.isTraining())
                    endTraining(user);
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
        Player player = event.getPlayer();
        User user = User.fetch(player.getUniqueId());

        if (!contains(user)) return;

        Item item = event.getItem();

        if (!user.isTraining() || !item.hasMetadata(OWNER_KEY) || item.getMetadata(OWNER_KEY).get(0).asInt() != player.getEntityId())
            event.setCancelled(true);
    }

    @Override
    protected void buildSidebar(User user, Sidebar sidebar) {
        DamageSettings settings = user.getDamageSettings();

        sidebar.addRow("level", "§fNível: " + settings.getLevel().getColor() + settings.getLevel().getName());
        sidebar.addRow("frequency", "§fFrequência: §a" + settings.getFrequency().getDisplay());

        if (user.isTraining())
            sidebar.addRow("time", "§fTempo: §e" + TimeFormat.seconds(user.getTrainingSeconds()));

        sidebar.addRow("record", "§fRecorde: §7" + TimeFormat.seconds(user.getAcademy().getDamageRecord(settings.getLevel())));
        sidebar.blankRow();
        sidebar.addRow("wither", "§fWither: " + (settings.isWither() ? "§a✔" : "§c✘"));
        sidebar.addRow("drops", "§fDrops: " + (settings.isDrops() ? "§a✔" : "§c✘"));
    }

    @Override
    public void updateSidebar(User user) {
        super.updateSidebar(user);

        if (user.isTraining() && user.getSidebar() != null && user.getSidebar().hasRow("time"))
            user.getSidebar().updateRow("time", "§fTempo: §e" + TimeFormat.seconds(user.getTrainingSeconds()));
    }
}
