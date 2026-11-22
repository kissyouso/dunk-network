package com.minecraft.arcade.bedwars.arcade;

import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.BedCollectible;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.kill.FinalKillCollectible;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.type.BedCollectibleType;
import com.grinderwolf.swm.plugin.SWMPlugin;
import com.minecraft.arcade.bedwars.BedWars;
import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.event.Event;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.event.phase.EventPhase;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.top.enums.TopType;
import com.minecraft.arcade.bedwars.menu.custom.team.TeamSelectorMenu;
import com.minecraft.arcade.bedwars.menu.game.play.PlayAgainMenu;
import com.minecraft.arcade.bedwars.menu.game.teleport.TeleportMenu;
import com.minecraft.arcade.bedwars.structure.bot.BotController;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.arcade.bedwars.user.context.UserContext;
import com.minecraft.core.member.list.bedwars.objects.ability.enums.AbilityType;
import com.minecraft.arcade.bedwars.menu.management.ArenaManagementMenu;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.redirect.Redirect;
import com.minecraft.core.arcade.ArcadeHolder;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.Room;
import com.minecraft.core.arcade.room.custom.RoomCustom;
import com.minecraft.core.arcade.room.map.Map;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.arcade.room.slot.Slot;
import com.minecraft.core.arcade.room.type.Type;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.core.arcade.route.state.ArcadeState;
import com.minecraft.core.arcade.slime.SlimeWorldController;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.server.HologramServer;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.api.title.TitleAnimation;
import com.minecraft.core.bukkit.manager.Manager;
import com.minecraft.core.bukkit.manager.list.TagManager;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.BedWarsMenu;
import com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.cosmetic.BedCosmeticsMenu;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.enums.item.BedWarsItem;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.stats.BedStats;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import com.minecraft.core.util.list.TimeUtil;
import com.minecraft.core.util.list.bukkit.BukkitUtil;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.minecraft.core.util.list.bukkit.BukkitUtil.getItemAmount;

public abstract class Arcade extends ArcadeHolder {

    private final File mapsDirectory;

    @Getter
    private final Manager manager;

    public Arcade(String mapsDirectory, Integer minRooms, Integer maxRooms, ArcadeCategory category) {
        super(mapsDirectory, minRooms, maxRooms, category);

        this.mapsDirectory = new File(BedWars.getInstance().getDataFolder().getAbsolutePath() + "/maps");

        this.manager = BukkitCore.getManager();
    }

    @Override
    public boolean load() {
        Core.getLogger().info("[" + getName() + "] Iniciando jogo...");

        if (!loadMaps(mapsDirectory)) return false;

        List<Map> availableMaps = getMaps();

        if (availableMaps.isEmpty()) {
            Core.getLogger().warning("[" + getName() + "] Não há mapas disponíveis para este jogo.");
            return false;
        }

        Core.getLogger().info("[" + getName() + "] Jogo iniciado com sucesso. (Lazy loading ativado - arenas serão criadas sob demanda)");
        Core.getLogger().info("[" + getName() + "] Mapas carregados: " + availableMaps.size());

        availableMaps.forEach(this::isLoadable);

        return true;
    }

    @Override
    public void unload() {
        Core.getLogger().info("[" + getName() + "] Desligando dados...");

        getRooms().clear();

        Core.getLogger().info("[" + getName() + "] O jogo foi interrompido.");
    }

    @Override
    public Arena setupRoom(Map map, Slot slot, Type type) {
        Instant now = Instant.now();

        // Gerando ID da sala
        int id = (getId().getAndIncrement() + 1);

        Arena arena = new Arena(id, this, map, slot, type);

        Core.getLogger().info("[" + getName() + "/" + id + "] Iniciando arena...");

        String templateName = getTemplate(map),
                worldName = templateName + "-" + id;

        handleArenaData(templateName, worldName, arena, id, now);

        return arena;
    }

    /**
     * Nome do template no worlds.yml do SlimeWorldManager.
     *
     * <p>Um template que não existe lá faz o clone retornar sem chamar o
     * callback, e é nesse callback que a arena entra em {@code getRooms()}:
     * o jogador é sentado numa sala que {@code handlePulse()} nunca visita e o
     * lobby de espera não sai nunca.
     */
    public String getTemplate(Map map) {
        return getName().toLowerCase() + "-" + map.getName().toLowerCase();
    }

    private boolean isLoadable(Map map) {
        SWMPlugin plugin = SWMPlugin.getInstance();

        /* Sem o plugin é cedo demais para julgar; o aviso sai na abertura da sala. */
        if (plugin == null) return true;

        String template = getTemplate(map);

        if (SlimeWorldController.hasTemplate(plugin, template)) return true;

        Core.getLogger().warning("[" + getName() + "] Mapa \"" + map.getName() + "\" sem template \""
                + template + "\" no SlimeWorldManager: não abre partida.");

        return false;
    }

    public void removeArena(Arena root) {
        root.getGenerators().clear();
        root.getTeams().clear();

        getRooms().remove(root);
    }

    public Arena copyArena(Arena root) {
        Instant now = Instant.now();

        int id = root.getId();

        Map map = root.getMap();

        Arena arena = new Arena(id, this, map, root.getSlot(), root.getType());

        Core.getLogger().info("[" + getName() + "/" + id + "] Copiando arena...");

        String[] mapSplit = map.getName().split(" ");

        String mapName = mapSplit.length > 1 ? mapSplit[0] + "-" + mapSplit[1] : map.getName();

        String templateName = getName().toLowerCase() + "-" + mapName.toLowerCase(),
                worldName = templateName + "-" + id;

        handleArenaData(templateName, worldName, arena, id, now);

        return arena;
    }

    protected void handleArenaData(String template, String world, Arena arena, int id, Instant now) {
        ArcadeCategory arcade = arena.getArcade().getCategory();
        Slot slot = arena.getSlot();

        SlimeWorldController.cloneWorldFromTemplate(SWMPlugin.getInstance(),
                template,
                world, () -> {
                    List<Team> teamList = arcade.name().contains("VERSUS")
                            ? arena.buildTeams(ChatColor.RED, ChatColor.BLUE)
                            : (slot.equals(Slot.TRIO) || slot.equals(Slot.QUARTET)
                            ? arena.buildTeams(ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW)
                            : arena.buildTeams());

                    teamList.forEach(team -> team.setMaxPlayers(slot.ordinal()));

                    int minPlayers = !arcade.name().contains("VERSUS")
                            ? slot.equals(Slot.SOLO) ? 4 : slot.equals(Slot.DUO) ? 8 : slot.equals(Slot.TRIO) ? 6 : 12
                            : slot.getMaxPlayers();

                    int maxPlayers = !arcade.name().contains("VERSUS")
                            ? slot.equals(Slot.DUO) ? 16 : slot.equals(Slot.TRIO) ? 12 : slot.equals(Slot.QUARTET) ? 16 : 8
                            : slot.getMaxPlayers();

                    arena.getTeams().addAll(teamList);

                    arena.setMinPlayers(minPlayers);
                    arena.setMaxPlayers(maxPlayers);

                    arena.handleBorder();

                    handleEntities(arena);

                    getRooms().add(arena);

                    Core.getLogger().info("[" + getName() + "/" + id + "] Arena iniciada com sucesso. " +
                            "(Tempo médio: " + Util.formatInstant(now) + ")");
                });
    }

    // Método removido - arenas agora são criadas sob demanda (lazy loading)

    @Override
    public Arena findBestArena(UUID sender, ArcadeRouteContext route) {
        Account account = Core.getAccountData().of(sender);
        Redirect redirect = Core.getRedirectData().of(sender);

        if (redirect != null) {
            return findRedirectedArena(route);
        }

        Stream<Arena> stream = getFilteredArenas(route);

        Arena found = stream.filter(arena -> isArenaValidForPlayer(arena, route, account, sender))
                .findFirst()
                .orElse(null);
        
        // Se não encontrou arena disponível, criar uma nova sob demanda
        if (found == null) {
            Core.getLogger().info("[" + getName() + "] Nenhuma arena disponível. Criando nova arena sob demanda...");
            found = createArenaOnDemand(route);
        }
        
        return found;
    }
    
    private Arena createArenaOnDemand(ArcadeRouteContext route) {
        /* Só entram na escolha os mapas com template no SlimeWorldManager:
         * um mapa sem template cria uma sala que nunca é registrada e o
         * jogador fica preso no lobby de espera. */
        List<Map> loadable = getMaps().stream().filter(this::isLoadable).toList();

        Map map = route.hasMap()
                ? loadable.stream().filter(m -> m.getId() == route.getMapId()).findFirst().orElse(null)
                : loadable.isEmpty() ? null : loadable.get(Core.RANDOM.nextInt(loadable.size()));

        if (map == null) {
            Core.getLogger().warning("[" + getName() + "] Não foi possível criar arena: nenhum mapa disponível.");
            return null;
        }
        
        Slot slot = route.getSlot() != null ? route.getSlot() : getCategory().getSlots().get(0);
        Type type = route.getType() != null ? route.getType() : Type.CASUAL;
        
        Arena arena = setupRoom(map, slot, type);
        
        if (arena != null) {
            Core.getLogger().info("[" + getName() + "] Arena criada sob demanda: " + arena.getIdentifier());
        }
        
        return arena;
    }

    private Arena findRedirectedArena(ArcadeRouteContext route) {
        return getRooms().stream()
                .filter(room -> room.isCategory(route.getArcade())
                        && room.getId() == route.getRoomId()
                        && room.isFiltered(route.getMapId()))
                .map(room -> (Arena) room)
                .findFirst()
                .orElse(null);
    }

    // Filtra as arenas com base nos parâmetros da rota
    private Stream<Arena> getFilteredArenas(ArcadeRouteContext route) {
        Stream<Arena> stream = getRooms().stream()
                .map(room -> (Arena) room)
                .filter(arena -> arena.getSlot().equals(route.getSlot()));

        if (route.getType() != null) {
            stream = stream.filter(arena -> arena.getType().equals(route.getType()));
        }

        if (route.hasMap()) {
            stream = stream.filter(arena -> arena.isFiltered(route.getMapId()));
        }

        return stream;
    }

    // Verifica se uma arena é válida para o jogador com base nas reservas e condições de reconexão
    private boolean isArenaValidForPlayer(Arena arena, ArcadeRouteContext route, Account account, UUID sender) {
        System.out.println("[Search] Filtrando " + arena.getIdentifier() + " para " + sender);

        // Verifica reconexão
        if (account.hasReconnect() && arena.getId() == route.getRoomId() && arena.getMap().getId() == route.getMapId()) {
            return true;
        }

        // Verifica se a arena está disponível
        if (!arena.isAvailable()) {
            return false;
        }

        // Se for dono de uma party e a rota estiver vinculada, tenta adicionar reservas
        if (account.isPartyOwner() && route.isLinked() && !arena.hasReservations(route.getLink())) {
            arena.setReservations(route.getLink());
            System.out.println("[PT-Route] " + arena.getIdentifier() + " recebeu +" + route.getLink().size() + " reservas. " +
                    "Total de reservas: " + arena.getTotalReservations());
        }

        // Verifica se o jogador é dono da party e está reservado na arena
        if (account.isPartyOwner() && arena.isReserved() && arena.hasReservation(sender)) {
            Core.getLogger().info("[PT-Route] O dono " + account.getNickname() + " entrou em " + arena.getIdentifier() + ".");
            return true;
        }

        // Se a arena estiver reservada para o jogador e não estiver cheia, permite a entrada
        if (arena.isReserved() && arena.hasReservation(sender) && !arena.isFull()) {
            arena.removeReservation(sender);
            System.out.println("[PT-Route] " + account.getNickname() + " entrou em " + arena.getIdentifier() + ".");
            return true;
        }

        // Caso contrário, a arena deve estar disponível
        return arena.isAvailable();
    }

    /**
     * Unused method
     */
    @Override
    public Room setupRoom(Map map, Slot slot) {
        return null;
    }

    public void load(User user) {
        // Na sala de espera, garantir que o jogador use a tag padrão do account, não a do time
        if (user.getArena().isPreGame() && user.isPlayer()) {
            user.setTag(user.getAccount().getTag());
            user.setTeamPrefix(null);
        }
        
        handleSidebar(user);
        handleHotbar(user);
    }

    public void handleSidebar(User user) {
        Arena arena = user.getArena();

        Sidebar sidebar = user.getSidebar();

        sidebar.clear();
        sidebar.setTitle((arena.isType(Type.CASUAL) ? "§6" : arena.isCustom() ? "§b" : "§5") + "§lBED WARS");

        // Renderizar scoreboard quando o jogo iniciou (scoreboard.txt)
        if (arena.isPhase(RoomPhase.PLAYING)) {
            sidebar.addRow("room", "§8" + arena.getIdentifier());
            sidebar.blankRow();

            arena.renderSidebarTeams(user);

            Event event = arena.getEvent();

            if (event != null) {
                sidebar.blankRow();
                sidebar.addRow("event", Arena.getEventRow(event));
            }

            sidebar.blankRow();

        } else {
            // Renderizar scoreboard quando o jogo ainda não iniciou
            sidebar.blankRow();

            arena.getArcade().renderWaitingSidebar(user, sidebar);
            sidebar.blankRow();
        }

        sidebar.addWebsiteRow();

        sidebar.display();
        
        // Garantir que a tag não seja null antes de atualizar
        if (user.getTag() == null) {
            user.setTag(user.getAccount().getTag());
        }
        TagManager.updateTag(user.getAccount());
    }

    public void updateSidebar(User user) {

    }

    /* Scoreboard da sala de espera */
    public void renderWaitingSidebar(User user, Sidebar sidebar) {
        Arena arena = user.getArena();

        sidebar.addRow("map", "Mapa: §a" + arena.getMap().getName());
        sidebar.addRow("players", "Jogadores: §a(" + arena.getTotalPlayers() + "/" + arena.getMaxPlayers() + ")");
        sidebar.blankRow();

        arena.renderSidebarTime(sidebar);

        BedStats stats = user.getMember() != null ? user.getMember().getStats(getCategory(), arena.getType()) : null;

        sidebar.addRow("mode", "Modo: §a" + getCategory().getName());
        sidebar.addRow("winstreak", "Winstreak: §a" + (stats != null ? Util.formatNumber(stats.getWinStreak()) : "0"));
    }

    public void handleHotbar(User user) {
        Player player = user.getAccount().player();

        Arena arena = user.getArena();

        PlayerInventory inv = player.getInventory();

        if (arena.isPhase(RoomPhase.PLAYING)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);

                if (user.isPlayer()) {
                if (user.inState(ArcadeState.ALIVE)) {
                    Team team = user.getTeam();

                    // Equipar armadura de couro do time quando o jogo começa
                    BukkitUtil.sendLeatherArmor(player, team.getRgb());

                    // Espada permanente = upgrade do time ou habilidade comprada
                    UserContext context = user.getContext();
                    boolean keepSword = (team.getUpgrade() != null && team.getUpgrade().isPermanentSword())
                            || context.hasAbility(AbilityType.PERMANENT_SWORD);

                    ItemStack sword;

                    if (keepSword && context.hasPermanentSword())
                        sword = context.getPermanentSword().clone();
                    else if (keepSword && context.getSwordItem() != null)
                        sword = Item.fromStack(context.getSwordItem().getStack()).unbreakable();
                    else {
                        sword = new Item(Material.WOOD_SWORD).unbreakable();
                        context.setSwordItem(null);
                    }

                    UserContext.giveSword(player, sword);

                    if (team.hasSword())
                        team.getSword().applyEnchantment(player);

                    BedWarsItem armor = context.getArmorItem();

                    if (armor != null) {
                        String name = armor.name().split("_")[1].toUpperCase();

                        /* Reaplica as quatro peças no respawn/início de partida;
                         * trocar só pernas/botas mantinha o couro no corpo. */
                        inv.setHelmet(Item.of(Material.getMaterial(name + "_HELMET")).unbreakable());
                        inv.setChestplate(Item.of(Material.getMaterial(name + "_CHESTPLATE")).unbreakable());
                        inv.setLeggings(Item.of(Material.getMaterial(name + "_LEGGINGS")).unbreakable());
                        inv.setBoots(Item.of(Material.getMaterial(name + "_BOOTS")).unbreakable());
                    }

                    if (team.hasArmor())
                        team.getArmor().applyEnchantment(player);

                    if (context.getPickaxeItem() != null)
                        inv.addItem(Item.fromStack(context.getPickaxeItem().getStack()).unbreakable());

                    if (context.getAxeItem() != null)
                        inv.addItem(Item.fromStack(context.getAxeItem().getStack()).unbreakable());

                    // Haste
                    if (team.hasHaste())
                        Core.getPlatform().runSync(() -> team.getHaste().applyEnchantment(player), 5L);

                    // Shears
                    if (context.isPurchasedShears())
                        inv.addItem(new Item(Material.SHEARS).unbreakable());
                } else
                    sendDeathHotbar(player, arena);
            }

        } else {
            if (user.isPlayer() && arena.isPreGame()) {
                player.getInventory().clear();
                
                // Limpar armadura na sala de espera
                player.getInventory().setHelmet(null);
                player.getInventory().setChestplate(null);
                player.getInventory().setLeggings(null);
                player.getInventory().setBoots(null);

                inv.setItem(4, new Item(Material.EMERALD)
                        .name("§aCosméticos")
                        .interact(event -> new BedCosmeticsMenu(event.getPlayer(), null).handle()));

                // Item de gerenciamento para admins/desenvolvedores
                Account account = user.getAccount();
                if (account.hasRank(RankType.ADMIN) || account.isStaffer()) {
                    inv.setItem(6, Item.of(Material.BREWING_STAND_ITEM, "§6Gerenciar Sala",
                                    "§7Gerencie a sala de espera.",
                                    "",
                                    "§eClique para abrir o menu!")
                            .interact(event -> {
                                // Buscar arena atual do jogador em vez de usar a capturada
                                User clickUser = (User) User.of(event.getPlayer().getUniqueId());
                                if (clickUser != null && clickUser.getArena() != null) {
                                    new ArenaManagementMenu(event.getPlayer(), clickUser.getArena()).handle();
                                } else {
                                    event.getPlayer().sendMessage("§cNão foi possível encontrar sua arena!");
                                }
                            }));
                }

                // Item para selecionar time (disponível para todas as arenas)
                if (arena.isCustom() || getCategory().getSlots().get(0).ordinal() > com.minecraft.core.arcade.room.slot.Slot.SOLO.ordinal())
                    inv.setItem(1, Item.of(Material.NAME_TAG, "§aSelecionar time")
                            .interact(event -> new TeamSelectorMenu(event.getPlayer()).handle()));

                // Itens da sala customizada
                if (arena.isCustom()) {
                    RoomCustom custom = arena.getCustom();

                    if (custom.isAuthor(player.getUniqueId()))
                        inv.setItem(5, Item.of(Material.BREWING_STAND_ITEM, "§aGerenciar sala §7(/menu)")
                                .interact(event -> event.getPlayer().performCommand("menu")));
                }

                inv.setItem(8, Item.of(Material.BED, "§cVoltar ao lobby")
                        .interact(event -> event.getPlayer().performCommand("lobby")));

            } else if (arena.isPhase(RoomPhase.ENDING))
                sendDeathHotbar(player, arena);
        }
    }

    protected void sendDeathHotbar(Player player, Arena arena) {
        PlayerInventory inv = player.getInventory();

        if (arena.isPhase(RoomPhase.PLAYING))
            inv.setItem(0, Item.of(Material.COMPASS, "§aTeleportador")
                    .interact(event -> new TeleportMenu(event.getPlayer()).handle()));

        inv.setItem(7, Item.of(Material.PAPER, "§aJogar novamente")
                .interact(event -> new PlayAgainMenu(event.getPlayer()).handle()));

        inv.setItem(8, Item.of(Material.ACACIA_DOOR_ITEM, "§cVoltar ao lobby")
                .interact(event -> event.getPlayer().performCommand("lobby")));
    }

    /**
     * Time ainda na disputa: cama de pé, humano vivo ou bot vivo. Sem contar o
     * bot, uma sala cheia de bots era dada como eliminada e a partida acabava
     * sozinha.
     */
    private boolean isCompeting(Arena arena, Team team) {
        return team.hasBed()
                || (!team.getMembers().isEmpty() && !arena.getAliveUsers(team).isEmpty())
                || BotController.alive(arena, team) > 0;
    }

    public void handleDeath(Arena arena, User user, User killer, DeathCause cause) {
        Player player = user.getAccount().player();

        UserContext context = user.getContext();

        boolean hasKiller = killer != null;

        // Salvar espada antes de limpar inventário (upgrade do time ou habilidade permanente)
        Team team = user.getTeam();
        boolean keepSword = (team != null && team.getUpgrade() != null && team.getUpgrade().isPermanentSword())
                || context.hasAbility(AbilityType.PERMANENT_SWORD);

        if (keepSword) {
            ItemStack sword = player.getInventory().getItem(0);
            if (sword != null && sword.getType().name().contains("SWORD"))
                context.setPermanentSword(sword.clone());
        } else {
            /* Sem espada permanente, o respawn volta para a madeira: limpa o estado da compra. */
            context.setSwordItem(null);
        }

        /* Restaura vida/fome/fogo/queda/efeitos antes de animar a morte: o jogador
         * não pode ficar com a vida baixa (ou "preso" com armadura) até renascer. */
        BukkitUtil.resetPlayerState(player);

        /* Restaurar Cooldown */
        BukkitCore.getManager().getCooldown().resetCooldown(player);

        /* Rebaixar as Picaretas / Machados */
        context.downGradePickaxe(player);
        context.downGradeAxe(player);

        user.setCombat(null);
        user.setDeaths();

        /* Caso for o abate final */
        final boolean finalKill = arena.isFinalKill(user);

        if (finalKill)
            user.setFinalDeath();

        if (hasKiller) {
            Account account = killer.getAccount();

            killer.setCombat(null);
            killer.setKills();

            /* Pegar minérios e enviar para o Killer */
            List<ItemStack> ores = new ArrayList<>();

            for (ItemStack content : player.getInventory().getContents()) {
                if (content != null && isOre(content.getType()))
                    ores.add(content);
            }

            Player target = account.player();

            if (!ores.isEmpty()) {
                ores.forEach(ore -> target.getInventory().addItem(ore));

                int iron = getItemAmount(player, Material.IRON_INGOT),
                        gold = getItemAmount(player, Material.GOLD_INGOT),
                        diamond = getItemAmount(player, Material.DIAMOND),
                        emerald = getItemAmount(player, Material.EMERALD);

                if (iron > 0)
                    target.sendMessage("§f+" + iron + " Ferros!");

                if (gold > 0)
                    target.sendMessage("§6+" + gold + " Ouros!");

                if (diamond > 0)
                    target.sendMessage("§b+" + diamond + " Diamantes!");

                if (emerald > 0)
                    target.sendMessage("§a+" + emerald + " Esmeraldas!");
            }

            if (finalKill) {
                BedMember member = killer.getMember();

                /* Efeito de abate final escolhido pelo assassino (Cosméticos > Abate Final) */
                playFinalKillEffect(member, player);

                int xp = Core.RANDOM.ints(7, 15).findFirst().orElse(7) * 2;
                member.addLevelXp(xp);
                killer.setXpGain(xp);
                killer.setFinalKill();

                arena.verifyTop(TopType.FINAL_KILL, killer.getAccount().getId(), context.getFinalKill());

                account.send("§b+" + xp + " XP §7(Kill Final)");
                
                // Esconder jogador morto do killer quando for final kill
                Player killerPlayer = account.player();
                if (killerPlayer != null && killerPlayer.isOnline()) {
                    killerPlayer.hidePlayer(player);
                }
            }
            
            // Raio visual para VIPs+ quando matarem alguém
            if (account.isVIP()) {
                Location killLocation = player.getLocation();
                if (killLocation != null && killLocation.getWorld() != null) {
                    killLocation.getWorld().strikeLightningEffect(killLocation);
                }
            }

            updateSidebar(killer);
        }

        /* Morte comum aguarda 5s e renasce na base; só o abate final elimina */
        if (finalKill)
            user.setState(ArcadeState.DEAD);

        if (finalKill) {
            if (team == null) {
                Core.getLogger().warning("[BedWars] Jogador " + player.getName() + " foi eliminado mas não tem time!");
            } else {
                // Remover jogador do time quando for eliminado
                team.getMembers().remove(player.getUniqueId());
                Core.getLogger().info("[BedWars] Jogador " + player.getName() + " removido do time " + team.getTypeName() + 
                    ". Membros restantes: " + team.getMembers().size());
            }

            TitleAnimation.sendDefeatFrame(player, "", TimeUnit.SECONDS.toMillis(5));

            if (team != null && arena.getTeamUsers(team).isEmpty()) {
                arena.send("",
                        "§f§lTIME ELIMINADO §f» §cO " + team.getColor() + "Time " + team.getName() + "§c foi eliminado.",
                        "");

                arena.getUsers().forEach(target -> CompletableFuture.runAsync(() -> arena.updateSidebarTeams(target)));
            }

            // Só verificar vitória por eliminação se as estatísticas devem ser contadas
            if (arena.getContext().isShouldCountStatistics()) {
                /* Sem nenhum humano vivo, a partida fecha: deixar os bots
                 * brigando entre si travaria o ciclo da sala e o jogador
                 * nunca voltaria para o hub. */
                if (BotController.hasBots(arena) && arena.getAliveUsers().stream().noneMatch(User::isPlayer)) {
                    Team winner = arena.getTeamList().stream()
                            .filter(target -> isCompeting(arena, target))
                            .max(Comparator.comparingInt(target -> BotController.alive(arena, target)))
                            .orElse(null);

                    arena.setWinner(winner);
                    arena.setPhase(RoomPhase.ENDING);
                    return;
                }

                // Contar times que ainda estão competindo (tem cama OU tem jogadores vivos)
                long teamsCompeting = arena.getTeamList().stream()
                    .filter(target -> isCompeting(arena, target))
                    .count();
                
                if (teamsCompeting <= 1) {
                    // Encontrar o time vencedor (time com cama OU jogadores vivos)
                    Team winner = arena.getTeamList().stream()
                        .filter(target -> isCompeting(arena, target))
                        .findFirst()
                        .orElse(null);
                    
                    arena.setWinner(winner);
                    
                    if (winner != null) {
                        Core.getLogger().info("[BedWars] Time vencedor: " + winner.getTypeName() + 
                            " (Cama: " + winner.hasBed() + ", Membros: " + winner.getMembers().size() + ")");
                    } else {
                        Core.getLogger().warning("[BedWars] Nenhum time vencedor encontrado!");
                    }

                    arena.setPhase(RoomPhase.ENDING);
                    return;
                }
            }

            user.setTag(Tag.SPECTATOR);
            user.setTeamPrefix(null);
            TagManager.updateTag(user.getAccount());

            handleHotbar(user);
            
            // Em final kill, garantir que o jogador morto fique invisível para todos (especialmente o killer)
            if (hasKiller && finalKill) {
                Player killerPlayer = killer.getAccount().player();
                if (killerPlayer != null && killerPlayer.isOnline()) {
                    // Já foi escondido acima, mas garantir que permaneça invisível
                    killerPlayer.hidePlayer(player);
                }
            }
        }

        player.playSound(player.getLocation(), Sound.BLAZE_DEATH, 1.0f, 1.0f);

        sendDeathMessage(arena, user, killer, cause);
        arena.hideAndShow(user);

        if (!finalKill)
            arena.startRespawn(user);
    }

    private void playFinalKillEffect(BedMember member, Player victim) {
        try {
            BedCollectible effect = member.getActiveCollectible(BedCollectibleType.FINAL_KILL);

            if (effect instanceof com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.kill.Random)
                effect = member.getRandomCollectible(BedCollectibleType.FINAL_KILL);

            if (effect instanceof FinalKillCollectible)
                ((FinalKillCollectible) effect).execute(victim.getLocation(), victim.getName());
        } catch (Exception exception) {
            Core.getLogger().warning("[BedWars] Falha no efeito de abate final: " + exception.getMessage());
        }
    }

    /* Death Messages */
    private final List<String> voidByOtherMessages = Arrays.asList(
            "foi jogado no void por", "foi arremessado direto pro void por"
    ), voidMessages = Arrays.asList(
            "caiu no void", "sucumbiu no vazio", "tropeçou e caiu no void", "tentou nadar no limbo"
    ), normalMessages = Arrays.asList(
            "teve sua cabeça arrancada por", "foi humilhado por", "não aguentou", "foi morto por"
    );

    public void sendDeathMessage(Arena arena, User user, User killer, DeathCause cause) {
        if (cause.equals(DeathCause.NONE)) return;

        boolean hasKiller = killer != null;

        if (hasKiller) {
            Account account = killer.getAccount();

            account.sound(Sound.SUCCESSFUL_HIT);
        }

        Account account = user.getAccount();

        boolean finalKill = arena.isFinalKill(user);

        String normalMessage = normalMessages.get(Core.RANDOM.nextInt(normalMessages.size())),
                voidMessage = voidMessages.get(Core.RANDOM.nextInt(voidMessages.size())),
                voidByOtherMessage = voidByOtherMessages.get(Core.RANDOM.nextInt(voidByOtherMessages.size()));

        // Validar se o jogador tem time antes de acessar
        Team userTeam = user.getTeam();
        if (userTeam == null) {
            Core.getLogger().warning("[BedWars] sendDeathMessage: Jogador " + account.getNickname() + " não tem time!");
            arena.send("§7" + account.getNickname() + " §7morreu.");
            return;
        }
        
        // Validar se o killer tem time (se houver killer)
        String killerName = "";
        if (hasKiller) {
            Team killerTeam = killer.getTeam();
            if (killerTeam == null) {
                Core.getLogger().warning("[BedWars] sendDeathMessage: Killer " + killer.getAccount().getNickname() + " não tem time!");
                killerName = "§f" + killer.getAccount().getNickname();
            } else {
                killerName = killerTeam.getColor() + killer.getAccount().getNickname();
            }
        }

        /* Cosmético "Mensagem de kill" do assassino */
        if (hasKiller && killer.getMember() != null) {
            com.minecraft.core.member.list.bedwars.objects.metadata.objects.cosmetic.BedCosmetic message = killer.getMember()
                    .getCosmetic(com.minecraft.core.member.list.bedwars.objects.metadata.objects.cosmetic.BedCosmetic.Category.KILL_MESSAGE);

            if (message != null)
                normalMessage = message.getValue();
        }

        /* Abate de bot: a entidade não é User, então o nome vem da memória de
         * ataques do controller em vez do killer. */
        if (!hasKiller) {
            Player victim = account.player();

            String attacker = victim != null ? BotController.lastAttacker(victim) : null;

            if (attacker != null) {
                hasKiller = true;
                killerName = attacker;

                BotController.forget(victim);
            }
        }

        arena.send(userTeam.getColor() + account.getNickname() + " §7" +
                (!cause.equals(DeathCause.VOID) ? (hasKiller ? normalMessage + " " + killerName : "morreu")
                        : (hasKiller ? voidByOtherMessage + " " + killerName : voidMessage))
                + "§7." + (hasKiller && finalKill ? " §f§lKILL FINAL!" : "")
        );
    }

    public enum DeathCause {
        PLAYER, VOID, NONE
    }

    public void updateStatisticalData(Team winner, List<Team> losers) {
        // Não contar estatísticas se a flag estiver desativada (ex: partida com 1 jogador)
        Arena arena = null;
        if (winner != null && !winner.getMembers().isEmpty()) {
            User firstUser = (User) User.of(winner.getMembers().iterator().next());
            if (firstUser != null) {
                arena = firstUser.getArena();
                if (arena != null && !arena.getContext().isShouldCountStatistics()) {
                    return;
                }
            }
        }
        
        if (winner != null) {
            for (UUID id : winner.getMembers()) {
                User user = (User) User.of(id);

                if (user == null) continue;

                BedMember member = user.getMember();

                Arena userArena = user.getArena();

                BedStats stats = member.getStats(userArena.getArcade().getCategory(), userArena.getType());

                int gainXp = Core.RANDOM.ints(40, 140).findFirst().orElse(35);

                stats.setWins();
                stats.setWinStreak();

                member.addLevelXp(gainXp);

                member.updateStats(stats);

                user.setXpGain(user.getXpGain() + gainXp);
                user.getAccount().send("§b+" + gainXp + " XP");
            }
        }

        if (losers != null && !losers.isEmpty()) {
            for (Team loser : losers) {
                for (UUID id : loser.getMembers()) {
                    User user = (User) User.of(id);

                    if (user == null) continue;

                    BedMember member = user.getMember();
                    int lostXp = Core.RANDOM.ints(30, 45).findFirst().orElse(25);

                    handleDefeatStats(user);
                    member.removeLevelXp(lostXp);
                }
            }
        }
    }

    public void handleDefeatStats(User user) {
        BedMember member = user.getMember();

        Arena arena = user.getArena();

        BedStats stats = member.getStats(arena.getArcade().getCategory(), arena.getType());

//        int lostXp = SkyMinigames.RANDOM.ints(30, 45).findFirst().orElse(25);

        stats.setDefeats();
        stats.setWinStreak(0);

        member.updateStats(stats);
    }

    public void handleEntities(Arena arena) {
        for (Team team : arena.getTeamList()) {
            Location shopNpcLocation = arena.getLocation("npc_shop_" + team.getTypeName().toLowerCase());

            if (shopNpcLocation != null) {
                HologramServer hologram = manager.getHologram().spawnServer("npc_shop_" + team.getTypeName().toLowerCase(), shopNpcLocation);

                hologram.setText(Arrays.asList(
                        team.getColor() + "§lLOJA",
                        "§7Clique para comprar!"
                ));
            }

            Location upgradeNpcLocation = arena.getLocation("npc_upgrade_" + team.getTypeName().toLowerCase());

            if (upgradeNpcLocation != null) {
                HologramServer hologram = manager.getHologram().spawnServer("npc_upgrade_" + team.getTypeName().toLowerCase(), upgradeNpcLocation);

                hologram.setText(Arrays.asList(
                        team.getColor() + "§lMELHORIAS",
                        "§7Clique para ver!"
                ));
            }
        }
    }

    private static boolean isOre(Material material) {
        return material == Material.IRON_INGOT || material == Material.GOLD_INGOT 
                || material == Material.EMERALD || material == Material.DIAMOND;
    }
}
