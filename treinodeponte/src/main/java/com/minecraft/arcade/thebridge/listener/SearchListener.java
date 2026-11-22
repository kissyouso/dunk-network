package com.minecraft.arcade.thebridge.listener;

import com.minecraft.arcade.thebridge.arcade.TheBridgeArcade;
import com.minecraft.arcade.thebridge.arcade.arena.TheBridgeArena;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.arcade.thebridge.user.factory.UserFactory;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.route.RouteContext;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.arcade.room.slot.Slot;
import com.minecraft.core.arcade.room.type.Type;
import com.minecraft.core.backend.data.list.server.RouteData;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.core.member.list.thebridge.TheBridgeMember;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import java.util.UUID;

public class SearchListener implements Listener {

    private final RouteData routeData;

    public SearchListener() {
        this.routeData = Core.getRouteData();
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!event.getLoginResult().equals(AsyncPlayerPreLoginEvent.Result.ALLOWED)) return;

        UUID id = event.getUniqueId();

        RouteContext route = routeData.read(id);

        if (route == null || !route.isValidArcade() || !isTheBridge(route.getArcade())) {
            /* Entrada direta por /server thebridge: sem rota o prelogin era
             * recusado. Assume o treino Solo para o jogador cair numa sala. */
            route = RouteContext.builder()
                    .senderId(id)
                    .serverType(ServerType.THE_BRIDGE)
                    .serverId(Core.getServerId())
                    .serverPort(Core.getServerPort())
                    .arcade(ArcadeRouteContext.builder()
                            .arcade(ArcadeCategory.THE_BRIDGE_PRACTICE)
                            .slot(Slot.PRACTICE)
                            .type(Type.CASUAL)
                            .join(Join.PLAYER)
                            .build())
                    .updatedAt(System.currentTimeMillis())
                    .build();

            Core.getLogger().info("[TheBridge] " + event.getName() + " entrou sem rota, usando o treino Solo.");
        } else {
            routeData.delete(id);
        }

        ArcadeRouteContext arcadeRoute = route.getArcade();

        TheBridgeArcade arcade = (TheBridgeArcade) BukkitCore.getManager().getArcade().read(arcadeRoute.getArcade());

        if (arcade == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Constant.ARCADE_NOT_FOUND_MESSAGE);
            return;
        }

        TheBridgeArena arena;

        if (arcadeRoute.getRoomId() > 0) {
            arena = awaitArena(arcade, arcadeRoute, null, 6000L);
        } else {
            arena = arcade.findBestArena(id, arcadeRoute);

            if (arena != null)
                arena = awaitArena(arcade, arcadeRoute, arena, 6000L);
        }

        if (arena == null || !arena.isReady() || arena.getWorld() == null) {
            Core.getLogger().warning("[TheBridge] Sala indisponivel para " + event.getName()
                    + " (rota " + arcadeRoute.getRoomId() + "). Entrada negada.");

            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§cA sala ainda está carregando. Tente novamente em alguns segundos.");
            return;
        }

        TheBridgeMember member = Core.getTheBridgeData().of(id, true);

        if (member == null)
            member = Core.getTheBridgeData().save(new TheBridgeMember(id, event.getName()));

        if (member == null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Constant.ACCOUNT_LOAD_FAILED_MESSAGE);
            return;
        }

        Core.getMemberController().save(member);
        Core.getTheBridgeData().cancelExpiration(id);

        UserFactory.create(member, arena, arcadeRoute.getJoin());
    }

    private boolean isTheBridge(ArcadeRouteContext route) {
        return route != null && route.getArcade() != null && route.getArcade().getServer().equals(ServerType.THE_BRIDGE);
    }

    private TheBridgeArena awaitArena(TheBridgeArcade arcade, ArcadeRouteContext route, TheBridgeArena arena, long timeout) {
        long deadline = System.currentTimeMillis() + timeout;

        while (true) {
            if (arena != null && !arena.isReady() && arcade.getPendingArena(arena.getId()) != arena)
                return null;

            if (arena == null && route.getRoomId() > 0) {
                arena = arcade.getPendingArena(route.getRoomId());

                if (arena == null)
                    arena = (TheBridgeArena) arcade.getRoom(route.getRoomId());
            }

            if (arena != null && arena.isReady() && arena.getWorld() != null)
                return arena;

            if (System.currentTimeMillis() >= deadline)
                return arena;

            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return arena;
            }
        }
    }

    @EventHandler
    public void login(PlayerLoginEvent event) {
        TheBridgeUser user = (TheBridgeUser) UserModel.of(event.getPlayer().getUniqueId());

        if (user == null || user.getArena() == null)
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Constant.ACCOUNT_LOAD_FAILED_MESSAGE + " (Invalid user/arena)");
    }

    @EventHandler
    public void spawn(PlayerSpawnLocationEvent event) {
        TheBridgeUser user = (TheBridgeUser) UserModel.of(event.getPlayer().getUniqueId());

        if (user == null || user.getArena() == null) return;

        if (!user.getArena().isReady()) return;

        event.setSpawnLocation(user.getArena().getSpawn());
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        TheBridgeUser user = (TheBridgeUser) UserModel.of(player.getUniqueId());

        if (user != null)
            user.handle();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        exit(event.getPlayer());
    }

    @EventHandler
    public void kick(PlayerKickEvent event) {
        exit(event.getPlayer());
    }

    private void exit(Player player) {
        TheBridgeUser user = (TheBridgeUser) UserModel.of(player.getUniqueId());

        if (user != null && user.getArena() != null)
            user.getArena().quit(player);

        UserModel.remove(player.getUniqueId());
        Core.getTheBridgeData().startExpiration(player.getUniqueId());
        Core.getMemberController().remove(player.getUniqueId(), TheBridgeMember.class);
    }
}
