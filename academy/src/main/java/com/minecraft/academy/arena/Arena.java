package com.minecraft.academy.arena;

import com.minecraft.academy.Academy;
import com.minecraft.academy.user.User;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.bukkit.api.npc.type.client.NpcClient;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.server.type.ServerType;
import com.mojang.authlib.properties.Property;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.function.Consumer;

@Getter
@Setter
public abstract class Arena implements Listener {

    private final String name = getClass().getSimpleName();
    private final String displayName;

    private final World world;

    private final Set<User> users = new HashSet<>();
    private final List<String> points;

    private Location spawn;

    public Arena(String displayName, World world, String... points) {
        this.displayName = displayName;
        this.world = world;
        this.points = Arrays.asList(points);

        Location saved = getPlugin().getLocationConfig().getSpawn(name);
        this.spawn = saved != null ? saved : new Location(world, 0.5, 71, 0.5, 0, 0);
    }

    public void join(User user) {
        Player player = user.getPlayer();

        clearEntities(player);

        users.add(user);

        user.setArena(this);
        user.setArenaJoinedAt(System.currentTimeMillis());

        reset(player);

        if (!player.getWorld().getUID().equals(world.getUID()) || player.getLocation().distanceSquared(spawn) > 4)
            player.teleport(spawn);

        giveItems(user);
        applyVisibility(user);

        user.ensureSidebar(getTitle());
        handleSidebar(user);

        Bukkit.getScheduler().runTaskLater(getPlugin(), () -> {
            if (user.getArena() == this && player.isOnline())
                spawnEntities(user);
        }, 5L);
    }

    public void quit(User user) {
        users.remove(user);
        onQuit(user);

        Player player = user.getPlayer();

        if (player != null)
            clearEntities(player);
    }

    public void reset(Player player) {
        if (player.getPassenger() != null)
            player.getPassenger().leaveVehicle();

        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setItemOnCursor(null);
        player.getOpenInventory().getTopInventory().clear();

        player.setLevel(0);
        player.setExp(0);

        for (PotionEffect effect : player.getActivePotionEffects())
            player.removePotionEffect(effect.getType());

        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR)
            player.setGameMode(GameMode.SURVIVAL);
    }

    /* Recria os NPCs/hologramas de todos (após alterar pontos ou skins) */
    public void refresh() {
        for (User user : new ArrayList<>(users)) {
            Player player = user.getPlayer();

            if (player == null) continue;

            clearEntities(player);
            spawnEntities(user);
        }
    }

    public void applyVisibility(User user) {
        Player player = user.getPlayer();

        for (User other : users) {
            if (other == user || other.getPlayer() == null) continue;

            if (other.isPlayersHidden())
                other.getPlayer().hidePlayer(player);

            if (user.isPlayersHidden())
                player.hidePlayer(other.getPlayer());
        }
    }

    public void togglePlayers(User user) {
        Player player = user.getPlayer();

        user.setPlayersHidden(!user.isPlayersHidden());

        for (User other : users) {
            if (other == user || other.getPlayer() == null) continue;

            if (user.isPlayersHidden())
                player.hidePlayer(other.getPlayer());
            else
                player.showPlayer(other.getPlayer());
        }

        player.sendMessage(user.isPlayersHidden() ? "§eVocê escondeu todos os jogadores." : "§aVocê agora está vendo todos os jogadores.");
        player.playSound(player.getLocation(), Sound.CLICK, 1F, 1F);
    }

    public ItemStack getVisibilityItem(User user) {
        boolean hidden = user.isPlayersHidden();

        return Item.of(Material.INK_SACK, hidden ? 8 : 10, "§fJogadores: " + (hidden ? "§cOFF" : "§aON"),
                        "§7Alterne a visibilidade de players.")
                .interact(event -> {
                    event.setCancelled(true);

                    togglePlayers(user);

                    Player player = event.getPlayer();
                    player.getInventory().setItem(player.getInventory().getHeldItemSlot(), getVisibilityItem(user));
                });
    }

    public ItemStack getBackItem() {
        return Item.of(Material.BED, "§aVoltar ao lobby", "§7Volte para o lobby da academia.")
                .interact(event -> {
                    event.setCancelled(true);
                    backToLobby(event.getPlayer());
                });
    }

    public static void backToLobby(Player player) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account != null)
            account.redirect(ServerType.HUB_PVP);
    }

    public void giveSoups(Player player, boolean full, boolean cocoa) {
        player.getInventory().setItem(0, Item.of(Material.STONE_SWORD).unbreakable().flags(ItemFlag.values()));
        player.getInventory().setItem(13, new ItemStack(Material.BOWL, 64));

        if (cocoa) {
            player.getInventory().setItem(14, new ItemStack(Material.INK_SACK, 64, (short) 3));
        } else {
            player.getInventory().setItem(14, new ItemStack(Material.RED_MUSHROOM, 64));
            player.getInventory().setItem(15, new ItemStack(Material.BROWN_MUSHROOM, 64));
        }

        int limit = full ? 36 : 9;

        for (int i = 1; i < limit; i++) {
            if (player.getInventory().getItem(i) == null)
                player.getInventory().setItem(i, new ItemStack(Material.MUSHROOM_SOUP));
        }

        player.updateInventory();
    }

    protected NpcClient spawnNpc(User user, String key, Property defaultSkin, Consumer<Player> interact, String... lines) {
        Location location = getPlugin().getLocationConfig().getPoint(name, key);

        if (location == null) return null;

        Player player = user.getPlayer();
        Property skin = getPlugin().getLocationConfig().getSkin(name, key);

        NpcClient npc = BukkitCore.getManager().getNpc().spawnClient(player, location, skin != null ? skin : defaultSkin);

        npc.setContact(true);
        npc.setAction((target, action) -> interact.accept(target));
        npc.display();

        if (lines.length > 0) {
            HologramClient hologram = BukkitCore.getManager().getHologram()
                    .spawnClient(player, name.toLowerCase() + "_" + key.toLowerCase(), location.clone().add(0, 2.1, 0));

            hologram.setText(Arrays.asList(lines));
            hologram.setTouch((target, touch) -> interact.accept(target));
            hologram.spawnTo(player);
        }

        return npc;
    }

    protected NpcClient spawnBackNpc(User user) {
        return spawnNpc(user, "VOLTAR", null, Arena::backToLobby, "§a§lVOLTAR AO LOBBY", "§eClique aqui.");
    }

    public void clearEntities(Player player) {
        BukkitCore.getManager().getNpc().removeClients(player);
        BukkitCore.getManager().getHologram().removeClients(player);
    }

    protected String website() {
        return "§6" + Constant.SERVER_DOMAIN;
    }

    public boolean isEnabled() {
        return getPlugin().getSettings().isEnabled(name);
    }

    public void applySettings() {
        if (isEnabled()) return;

        Arena target = getPlugin().getArenaStorage().getDefaultArena();

        if (target == this) return;

        for (User user : new ArrayList<>(users)) {
            user.getPlayer().sendMessage("§cEsta arena foi desativada. Você foi enviado para " + target.getDisplayName() + ".");
            user.sendTo(target);
        }
    }

    public boolean contains(User user) {
        return user != null && user.getArena() == this;
    }

    public Academy getPlugin() {
        return Academy.getAcademy();
    }

    public void tick(int tick) {
    }

    protected void onQuit(User user) {
    }

    protected void spawnEntities(User user) {
    }

    /* Título da scoreboard (ex.: §a§lTIMERS) */
    public String getTitle() {
        return "§a§l" + displayName.toUpperCase();
    }

    /**
     * Monta a scoreboard do zero (entrada na arena ou mudança de estado).
     */
    public void handleSidebar(User user) {
        Sidebar sidebar = user.getSidebar();

        if (sidebar == null) return;

        sidebar.clear();
        sidebar.setTitle(getTitle());
        sidebar.blankRow();

        buildSidebar(user, sidebar);

        sidebar.blankRow();
        sidebar.addRow("coins", "§fCoins: §6" + user.getCoins());
        sidebar.blankRow();
        sidebar.addRow("website", website());

        sidebar.display();
    }

    /**
     * Atualiza a cada segundo apenas as linhas que mudam.
     */
    public void updateSidebar(User user) {
        Sidebar sidebar = user.getSidebar();

        if (sidebar == null) return;

        if (sidebar.hasRow("coins"))
            sidebar.updateRow("coins", "§fCoins: §6" + user.getCoins());
    }

    protected abstract void buildSidebar(User user, Sidebar sidebar);

    protected abstract void giveItems(User user);
}
