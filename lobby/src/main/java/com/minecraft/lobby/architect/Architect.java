package com.minecraft.lobby.architect;

import com.minecraft.core.Core;
import com.minecraft.core.bukkit.api.vanish.Vanish;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.account.context.objects.tag.role.TagRole;
import com.minecraft.core.account.context.objects.toggle.Toggle;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.joinmessage.JoinMessage;
import com.minecraft.core.api.joinmessage.JoinMessageType;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.map.location.SignedLocation;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.server.HologramServer;
import com.minecraft.core.bukkit.api.npc.action.NpcAction;
import com.minecraft.core.bukkit.api.npc.type.client.NpcClient;
import com.minecraft.core.bukkit.api.npc.type.server.NpcServer;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.menu.account.AccountMenu;
import com.minecraft.core.bukkit.menu.account.stats.StatsInfoMenu;
import com.minecraft.core.bukkit.menu.server.academy.AcademyStatsMenu;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.stats.BedWarsStatsMenu;
import com.minecraft.core.bukkit.menu.server.collectible.CollectibleMenu;
import com.minecraft.core.event.IgnoreEvent;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import com.minecraft.core.util.list.StringUtil;
import com.minecraft.core.util.list.WorldUtil;
import com.minecraft.lobby.Lobby;
import com.minecraft.lobby.architect.list.main.MainArchitect;
import com.minecraft.lobby.menu.arcade.ArcadeSlotMenu;
import com.minecraft.lobby.menu.navigation.ServersMenu;
import com.minecraft.lobby.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.minecraft.core.server.Server;

@Getter
@RequiredArgsConstructor
@IgnoreEvent
public abstract class Architect implements Listener {

    private final String id = StringUtil.generateLetterCode(4) + Core.getServerId();
    private final ServerType server;

    private final List<SignedLocation> locations = new ArrayList<>();

    public void load() {
        handleEntities();

        WorldUtil.setup(getWorld());

        Bukkit.getPluginManager().registerEvents(this, Lobby.getInstance());
    }

    public void unload() {

    }

    public World getWorld() {
        return Bukkit.getWorlds().get(0);
    }

    public void join(Player player) {
        User user = (User) User.of(player.getUniqueId());

        Account account = user.getAccount();

        handleSidebar(user);
        handleHotbar(player);

        // Teleportar para o spawn
        Location spawn = getLocation("spawn");
        if (spawn != null) {
            player.teleport(spawn);
        } else {
            // Se não houver spawn configurado, usar o spawn padrão do mundo
            player.teleport(player.getWorld().getSpawnLocation());
        }

        // Garantir que o player não esteja voando
        player.setAllowFlight(false);
        player.setFlying(false);

        announceJoin(player, account);

        /* Título equipado volta a aparecer acima da cabeça */
        com.minecraft.core.api.collectible.operator.CollectibleOperator.load(player, com.minecraft.core.api.collectible.CollectibleCategory.TITLE);

        /* Esconder todos os jogadores, caso estejam invisíveis */
        if (!account.getToggle().isVisiblePlayersHub())
            Bukkit.getOnlinePlayers().forEach(player::hidePlayer);

        Location location = getLocation("npc_stats");

        /* Renderizar NPC de suas estatísticas */
        if (location != null) {
            CompletableFuture.runAsync(() -> {
                NpcClient stats = BukkitCore.getManager().getNpc().spawnClient(player, location,
                        account.getSkin().getValue(), account.getSkin().getSignature());

                stats.setContact(true);
                stats.setAction((target, action) -> {
                    Account other = Core.getAccountController().of(target.getUniqueId());

                    if (other != null) {
                        ServerType server = getServer().getRealServerOfLobby();

                        if (server == null) return;

                        if (server.equals(ServerType.BEDWARS))
                            new BedWarsStatsMenu(target, null).handle();
                        else if (server.equals(ServerType.ACADEMY))
                            new AcademyStatsMenu(target, other, null).handle();
                        else
                            new StatsInfoMenu(target, other, server, null).handle();
                    }
                });

                stats.display();
            });
        }
    }

    /**
     * Anuncia a entrada com a mensagem escolhida em Colecionáveis > Mensagens de entrada.
     * Mod+ sem escolha usa a "Entrada da equipe" (moldura); quem está no vanish não é anunciado.
     */
    private void announceJoin(Player player, Account account) {
        if (Vanish.has(player)) return;

        Tag tag = account.getTag();
        JoinMessage selected = account.getSelectedJoinMessageEnum();

        if (selected != null && !account.hasAccessToJoinMessage(selected))
            selected = null;

        boolean staff = account.hasRank(RankType.HELPER);

        if (staff && (selected == null || selected.getId().equalsIgnoreCase(JoinMessageType.STAFF.getId()))) {
            String line = JoinMessageType.STAFF_FRAME;

            Core.getAccountController().list().forEach(target -> target.send(line, "",
                    account.formatTag(tag, target.getTagPrefix()) + account.getNickname() + " §6" + JoinMessageType.STAFF.getMessage(), "", line));
            return;
        }

        if (!staff && (!account.isVIP() || tag.isRole(TagRole.STAFF))) return;

        String message;

        if (selected != null) {
            message = selected.getMessage();
        } else {
            /* Aleatória entre as liberadas (sem a da equipe, que tem moldura própria) */
            List<JoinMessage> available = new ArrayList<>(account.getAvailableJoinMessages());
            available.removeIf(join -> join.getId().equalsIgnoreCase(JoinMessageType.STAFF.getId()));

            message = available.isEmpty() ? JoinMessageType.DEFAULT.getMessage() : available.get(Core.RANDOM.nextInt(available.size())).getMessage();
        }

        Core.getAccountController().list().forEach(target -> target.send(account.formatTag(tag, target.getTagPrefix()) + account.getNickname() + " §6" + message));
    }

    public void quit(Player player) {
    }

    public abstract void handleSidebar(User user);

    public void chat(Player player, String message) {

    }

    public void handleEntities() {
    }

    public Vector getSlimeJump(Vector direction) {
        return new Vector(0, 0, 0);
    }

    public Location getLocation(String name) {
        return locations.stream()
                .filter(location -> location.getName().equalsIgnoreCase(name))
                .map(location -> location.getSynthetic().of(Bukkit.getWorlds().get(0)))
                .findFirst()
                .orElse(null);
    }

    public List<SignedLocation> getLocationsByName(String name) {
        return locations.stream()
                .filter(location -> location.getName().startsWith(name))
                .collect(Collectors.toList());
    }

    public List<Location> getLocationsByKey(String... names) {
        List<Location> list = new ArrayList<>();

        for (String name : names) {
            Location location = getLocation(name);

            if (location != null)
                list.add(location);
        }

        return list;
    }

    public void handleHotbar(Player player) {
        // Usar runTaskLater para garantir que o inventário esteja totalmente carregado
        Core.getPlatform().runSync(() -> {
            if (!player.isOnline()) return;
            
            User user = (User) User.of(player.getUniqueId());
            if (user == null) return;

            Account account = user.getAccount();
            if (account == null) return;
            
            Toggle toggle = account.getToggle();

            PlayerInventory inv = player.getInventory();

            inv.clear();
            inv.setArmorContents(null);

            inv.setItem(0, Item.of(Material.COMPASS, "§aModos de jogo")
                    .interact(event -> new ServersMenu(event.getPlayer(), ServersMenu.NavigationType.SERVER).handle()));

            inv.setItem(1, Heads.of(player).name("§aMeu perfil")
                    .interact(event -> new AccountMenu(event.getPlayer()).handle()));

            inv.setItem(4, Item.of(Material.CHEST, "§aColecionáveis")
                    .interact(event -> new CollectibleMenu(event.getPlayer()).handle()));

            inv.setItem(7, Item.of(Material.INK_SACK, toggle.isVisiblePlayersHub() ? 10 : 8,
                    "§fJogadores: " + (toggle.isVisiblePlayersHub() ? "§aON" : "§cOFF")));

            inv.setItem(8, Item.of(Material.NETHER_STAR, "§aLobbies")
                    .interact(event -> new ServersMenu(event.getPlayer(), ServersMenu.NavigationType.ROOM).handle()));

            handleExtraHotbar(player, inv);

            player.updateInventory();
        }, 2L);
    }

    /**
     * Itens extras da hotbar de cada lobby (aplicados junto com a hotbar padrão).
     */
    public void handleExtraHotbar(Player player, PlayerInventory inventory) {
    }

    /* ---------------- Status dos NPCs ---------------- */

    public static final String MAINTENANCE_LINE = "§4§lMANUTENÇÃO§c§l!";

    /* Tipos com servidor vivo no Redis. A varredura é uma vez por segundo e
     * dividida entre todos os hologramas, porque getServerList() relê a chave
     * de cada servidor e os NPCs atualizam um por um. */
    private static Set<ServerType> aliveTypes = Collections.emptySet();
    private static long aliveTypesAt;

    /**
     * Um hub também está em manutenção quando o servidor do modo dele caiu: o
     * lobby existe apenas para aquele modo, então entrar nele não levaria a nada.
     */
    public static boolean isMaintenance(ServerType type) {
        if (type == null) return false;

        if (!aliveTypes().contains(type)) return true;

        ServerType real = type.getRealServerOfLobby();

        return real != null && !aliveTypes().contains(real);
    }

    /**
     * Linha de status do holograma: a contagem normal, ou MANUTENÇÃO quando não
     * há nenhum servidor daquele hub/modo registrado (parou no painel).
     */
    public static String statusLine(ServerType type, int playing) {
        return isMaintenance(type) ? MAINTENANCE_LINE : "§e" + Util.formatNumber(playing) + " jogando!";
    }

    private static synchronized Set<ServerType> aliveTypes() {
        long now = System.currentTimeMillis();

        if (now - aliveTypesAt < 1000L) return aliveTypes;

        try {
            Set<ServerType> alive = new HashSet<>();

            for (Server server : Core.getServerData().getServerList(search -> !search.isDead()))
                alive.add(server.getType());

            aliveTypes = alive;
            aliveTypesAt = now;
        } catch (Exception e) {
            /* Redis lento não pode virar "todos os modos em manutenção": mantém o último resultado */
        }

        return aliveTypes;
    }

    public void handleStatsNpc(Location location) {
        HologramServer hologram = BukkitCore.getManager().getHologram().spawnServer("stats_" + getServer().name().toLowerCase(), location);

        hologram.setText(Arrays.asList(
                "§b§lSUAS ESTATÍSTICAS",
                "§eClique para ver!"
        ));
    }

    public NpcServer handleNpc(String name, Location location, String value, String signature) {
        NpcServer npc = BukkitCore.getManager().getNpc().spawnServer(location, value, signature);

        npc.display();

        HologramServer hologram = BukkitCore.getManager().getHologram().spawnServer(name.toLowerCase(), location);

        hologram.setText(Arrays.asList(
                "§b" + name,
                "§e" + Util.formatNumber(0) + " jogando!"
        ));

        return npc;
    }

    public void handleServerNpc(ServerType server, Location location, String value, String signature) {
        NpcServer npc = BukkitCore.getManager().getNpc().spawnServer(location, value, signature);

        npc.setAction((player, action) -> {
            User user = (User) User.of(player.getUniqueId());

            if (user != null)
                user.getAccount().redirect(server);
        });

        npc.setContact(true);

        npc.display();

        HologramServer hologram = BukkitCore.getManager().getHologram().spawnServer(server.name().toLowerCase(), location);

        if (server.getServerLobby() == ServerType.HUB_BEDWARS) {
            hologram.setText(Arrays.asList(
                    MainArchitect.generateColoredText(),
                    "§b" + server.getName(),
                    statusLine(server, Core.getServerData().getOnlinePlayers(server))
            ));
        } else {
            hologram.setText(Arrays.asList(
                    "§b" + server.getName(),
                    statusLine(server, Core.getServerData().getOnlinePlayers(server))
            ));
        }
    }

    public void handleArcadeNpc(ArcadeCategory arcade, Location location, NpcAction action, String value, String signature) {
        NpcServer npc = BukkitCore.getManager().getNpc().spawnServer(location, value, signature);

        npc.setContact(true);
        npc.setAction(action);

        npc.display();

        HologramServer hologram = BukkitCore.getManager().getHologram().spawnServer(arcade.getId(), location);

        hologram.setText(Arrays.asList(
                "§b" + (arcade.equals(ArcadeCategory.NONE) ? "§k?????" : getNpcName(arcade)),
                statusLine(arcade.getServer(), arcade.getPlayingNow())
        ));
    }

    public String getNpcName(ArcadeCategory arcade) {
        return arcade.getName();
    }

    public void handleArcadeNpc(ArcadeCategory arcade, Location location, String value, String signature) {
        handleArcadeNpc(arcade, location, (player, action) -> new ArcadeSlotMenu(player, null, arcade).handle(), value, signature);
    }
}