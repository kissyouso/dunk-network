package com.minecraft.lobby.listener;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.toggle.Toggle;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.server.HologramServer;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.event.type.account.AccountClanChangeEvent;
import com.minecraft.core.bukkit.event.type.update.type.UpdateType;
import com.minecraft.core.bukkit.event.type.update.type.list.AsyncUpdateEvent;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import com.minecraft.lobby.Lobby;
import com.minecraft.lobby.architect.Architect;
import com.minecraft.lobby.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.TimeUnit;

public class ServerListener implements Listener {

    private final Architect architect;

    public ServerListener() {
        this.architect = Lobby.getLoader().getArchitect();
    }

    @EventHandler
    public void onAsyncCounterUpdate(AsyncUpdateEvent event) {
        if (event.isType(UpdateType.SECOND)) {
            ServerType realServer = architect.getServer().getRealServerOfLobby();

            if (realServer != null) {
                for (ArcadeCategory arcade : ArcadeCategory.list(category -> category.getServer().equals(realServer))) {
                    HologramServer hologram = BukkitCore.getManager().getHologram().getServer(arcade.getId());

                    if (hologram != null && hologram.getRows().size() >= 2) {
                        int onlinePlayers = Core.getArcadeData().getOnlinePlayers(arcade);

                        hologram.setText(hologram.getRows().size() - 1, Architect.statusLine(realServer, onlinePlayers));
                    }
                }
            }

            // Atualizar players na scoreboard (o Duels usa "Jogadores", os demais "Players")
            String playersLabel = Lobby.getLoader().getArchitect().getServer() == ServerType.HUB_DUELS ? "Jogadores: §a" : "Players: §a";

            for (UserModel user : User.getList()) {
                if (user != null) {
                    Sidebar sidebar = user.getSidebar();

                    if (sidebar != null)
                        sidebar.updateRow("players", playersLabel + Util.formatNumber(Core.getServerData().getOnlinePlayers()));
                }
            }
        }
    }

    /* Void: nunca mata no lobby. Cancela o dano antes de qualquer outro listener e volta para o spawn */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onVoid(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getCause() != EntityDamageEvent.DamageCause.VOID) return;

        event.setCancelled(true);

        rescue((Player) event.getEntity());
    }

    private void rescue(Player player) {
        Location spawn = architect.getLocation("spawn");

        if (spawn == null)
            spawn = player.getWorld().getSpawnLocation().add(0.5, 0, 0.5);

        player.setFallDistance(0F);
        player.teleport(spawn);
    }

    @EventHandler
    public void onAccountClan(AccountClanChangeEvent event) {
        Account account = event.getAccount();

        User user = (User) User.of(account.getId());

        if (user != null)
            user.getSidebar().updateRow("clan", "Clan: §7" + (account.hasClan() ? account.getClanName() : "Nenhum"));
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        architect.chat(player, event.getMessage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlimeJump(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();

        /* Só olhar o mundo quando o jogador muda de bloco (o evento roda várias vezes por segundo por jogador) */
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;

        Player player = event.getPlayer();

        if (to.getY() < 0) {
            rescue(player);
            return;
        }

        Material down = to.getWorld().getBlockAt(to.getBlockX(), to.getBlockY() - 1, to.getBlockZ()).getType();

        if (down.equals(Material.SLIME_BLOCK)) {
            player.setVelocity(architect.getSlimeJump(player.getLocation().getDirection()));

            player.playSound(player.getLocation(), Sound.CLICK, 1.0f, 2.0f);
        }
    }

    @EventHandler
    public void onVisibilityItem(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null) return;

        ItemStack hand = event.getItem();

        if (event.hasBlock() && event.getClickedBlock().getType().name().contains("BUTTON"))
            event.setCancelled(true);

        if (event.hasItem() && hand != null) {
            if (hand.getType().equals(Material.INK_SACK)) {
                Toggle toggle = account.getToggle();

                if (account.hasCooldown(Constant.PLAYERS_VISIBILITY_COOLDOWN_KEY)) {
                    account.send("§cAguarde " + account.getFormattedCooldown(Constant.PLAYERS_VISIBILITY_COOLDOWN_KEY)
                            + " para " + (toggle.isVisiblePlayersHub() ? "esconder" : "mostrar") + " os jogadores novamente.");
                    return;
                }

                toggle.setVisiblePlayersHub(!toggle.isVisiblePlayersHub());
                account.setToggle(toggle);

                player.setItemInHand(Item.of(Material.INK_SACK, toggle.isVisiblePlayersHub() ? 10 : 8,
                        "§fJogadores: " + (toggle.isVisiblePlayersHub() ? "§aON" : "§cOFF")));

                player.updateInventory();

                Bukkit.getOnlinePlayers().forEach(online -> {
                    if (toggle.isVisiblePlayersHub())
                        player.showPlayer(online);
                    else
                        player.hidePlayer(online);
                });

                player.playSound(player.getLocation(), Sound.CLICK, 1.0f, 2.0f);

                account.setCooldown(Constant.PLAYERS_VISIBILITY_COOLDOWN_KEY, TimeUnit.SECONDS.toMillis(5));
            }
        }
    }
}
