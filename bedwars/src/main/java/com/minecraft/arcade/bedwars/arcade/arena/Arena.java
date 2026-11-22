package com.minecraft.arcade.bedwars.arcade.arena;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.arcade.bedwars.arcade.Arcade;
import com.minecraft.arcade.bedwars.arcade.arena.context.ArenaContext;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.event.Event;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.event.phase.EventPhase;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.top.Top;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.top.enums.TopCategory;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.top.enums.TopType;
import com.minecraft.arcade.bedwars.arcade.arena.context.objects.top.user.TopUser;
import com.minecraft.arcade.bedwars.arcade.arena.context.phase.ArenaPhaseExecutor;
import com.minecraft.arcade.bedwars.menu.shop.ShopMenu;
import com.minecraft.arcade.bedwars.menu.upgrade.UpgradeMenu;
import com.minecraft.arcade.bedwars.structure.generator.objects.level.GeneratorLevel;
import com.minecraft.arcade.bedwars.structure.generator.objects.type.forge.ForgeGenerator;
import com.minecraft.arcade.bedwars.structure.generator.objects.type.ore.list.DiamondGenerator;
import com.minecraft.arcade.bedwars.structure.generator.objects.type.ore.list.EmeraldGenerator;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.arcade.bedwars.structure.bot.BedWarsBot;
import com.minecraft.arcade.bedwars.structure.bot.BotController;
import com.minecraft.arcade.bedwars.structure.generator.objects.type.ore.OreGenerator;
import com.minecraft.arcade.bedwars.structure.team.objects.forge.Forge;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.arcade.bedwars.user.context.UserContext;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.medal.Medal;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.api.punishment.Punishment;
import com.minecraft.core.api.punishment.objects.enums.PunishmentCategory;
import com.minecraft.core.api.reconnect.Reconnect;
import com.minecraft.core.arcade.room.Room;
import com.minecraft.core.arcade.room.map.Map;
import com.minecraft.core.arcade.room.map.area.Cuboid;
import com.minecraft.core.arcade.room.map.location.SyntheticLocation;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import com.minecraft.core.arcade.room.slot.Slot;
import com.minecraft.core.arcade.room.team.preset.TeamPresetType;
import com.minecraft.core.arcade.room.type.Type;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.arcade.route.state.ArcadeState;
import com.minecraft.core.arcade.slime.SlimeWorldController;
import com.minecraft.core.bukkit.api.firework.FireworkApi;
import com.minecraft.core.bukkit.api.hologram.type.server.HologramServer;
import com.minecraft.core.bukkit.api.npc.type.client.NpcClient;
import com.minecraft.core.bukkit.api.protocol.ProtocolHandler;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.api.vanish.Vanish;
import com.minecraft.core.bukkit.manager.list.HologramManager;
import com.minecraft.core.bukkit.manager.list.TagManager;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.member.list.bedwars.objects.enums.BedOre;
import com.minecraft.core.member.list.bedwars.objects.enums.BedShop;
import com.minecraft.core.member.list.bedwars.objects.enums.BedSkin;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.seller.SellerCollectible;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.seller.list.Default;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.seller.list.Mirror;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.seller.list.Random;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.type.BedCollectibleType;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.arcade.bedwars.arcade.arena.leaderboard.BedLevelLeaderboard;
import com.minecraft.arcade.bedwars.arcade.arena.leaderboard.BedWinsLeaderboard;
import com.minecraft.arcade.bedwars.arcade.arena.leaderboard.BedFinalKillLeaderboard;
import com.minecraft.arcade.bedwars.arcade.arena.leaderboard.BedWinstreakLeaderboard;
import com.minecraft.core.util.list.TimeUtil;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
public class Arena extends Room {

    private final ArenaContext context;

    public Arena(int id, Arcade arcade, Map map, Slot slot, Type type) {
        super(id, arcade, map, slot, type);

        this.context = new ArenaContext();
    }

    public void unload() {
        getUsers().forEach(user -> user.getAccount().redirectToHub());

        if (getPlayers().isEmpty()) {
            Arcade arcade = getArcade();

            SlimeWorldController.unloadWorld(getWorld(), () -> arcade.removeArena(this));
        }
    }

    public void reload(Player author) {
        Arcade arcade = getArcade();

        Arena arena = arcade.setupRoom(getMap(), getSlot(), getType());

        arena.setCustom(getCustom());

        Core.getPlatform().runSync(() -> {
            if (arena.getWorld() != null) {
                send("§b" + author.getName() + "§e reiniciou a sala.");

                getUsers().forEach(user -> {
                    user.setArena(arena);

                    arena.join(user.getPlayer());
                });

                if (getPlayers().isEmpty())
                    SlimeWorldController.unloadWorld(getWorld(), () -> arcade.removeArena(this));
            }
        }, 10L);
    }

    @Override
    public void join(Player player) {
        User user = (User) User.of(player.getUniqueId());

        /* O join pode vir da rota gravada no servidor anterior. Se ele diz
         * VANISH mas o jogador não está mais em vanish e não veio de um /go,
         * é estado obsoleto: volta para PLAYER para não entrar como espectador
         * (GM1), ser marcado como morto e "renascer" sozinho na partida. */
        if (user.isVanish() && !Vanish.has(player) && Core.getRedirectData().of(player.getUniqueId()) == null)
            user.setJoin(Join.PLAYER);

        UserContext context = user.getContext();

        Arcade arcade = user.getArcade();

        Account account = user.getAccount();

        getPlayers().add(player);

        player.getEnderChest().clear();
        
        // Limpar armadura ao entrar na arena (sala de espera)
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);

        Location spawn = getLocation("spawn");

        if (user.isPlayer()) {
            if (context.hasReconnect()) {
                Reconnect reconnect = context.getReconnect();
                Team team = getTeamList().stream()
                        .filter(search -> search.getCodeId().equalsIgnoreCase(reconnect.getTeamId()))
                        .findFirst()
                        .orElse(null);

                if (team != null) {
                    team.setBedExpiresAt(-1L);
                    user.setTeam(team);
                    
                    if (!team.getMembers().contains(player.getUniqueId()))
                        team.getMembers().add(player.getUniqueId());

                    if (team.hasBed()) {
                        // Garantir que o jogador seja definido como PLAYER ao reconectar
                        user.setJoin(Join.PLAYER);

                        Tag teamTag = com.minecraft.arcade.bedwars.arcade.arena.context.phase.ArenaPhaseExecutor.getTeamTag(team.getTypeName());
                        user.setTag(teamTag != null ? teamTag : account.getTag());

                        user.setTeamPrefix(team.getBrackets() + " ");

                        /* Reconectar é um renascimento: cai direto no respawn(), sem espera */
                        respawn(user);

                        player.sendMessage("§aVocê reconectou!");
                        send(team.getColor() + account.getNickname() + "§7 reconectou.");
                    } else {
                        user.setState(ArcadeState.DEAD);
                        user.setTag(Tag.SPECTATOR);
                        user.setTeamPrefix(null);
                        
                        Location spawnLoc = team.getBase();
                        if (spawnLoc != null)
                            player.teleport(spawnLoc.clone().add(0, 20, 0));
                        
                        player.sendMessage("§cO seu time foi eliminado enquanto você estava fora.");
                        account.title("§c§lELIMINADO", "§eO seu time foi eliminado!");
                        send("§7" + account.getNickname() + "§e reconectou (time eliminado).");
                    }
                    
                    TagManager.updateTag(account);
                    Core.getPlatform().runSync(() -> handleEntities(user), 5L);
                    Core.getPlatform().runSync(() -> getUsers().forEach(this::updateSidebarTeams), 3L);
                    account.setRoute(this, team.getCodeId(), user.getState(), user.getJoin());
                } else {
                    user.setTeam(null);
                    user.setTag(account.getTag());
                    user.setTeamPrefix(null);
                    user.setState(ArcadeState.ALIVE);
                    player.teleport(spawn);
                    handleLeaderboards(user);
                }

                Core.getReconnectData().delete(player.getUniqueId());
            } else {
                user.setTeam(null);
                user.setTag(account.getTag());
                user.setTeamPrefix(null);
                user.setState(ArcadeState.ALIVE);
                player.teleport(spawn);
                handleLeaderboards(user);

                Tag tag = user.getTag();
                send(tag.getColor() + (tag.ordinal() >= Tag.FUNDADOR.ordinal() ? "§o" : "") + account.getNickname()
                        + "§e entrou na sala. (§b" + getTotalPlayers() + "§e/§b" + getMaxPlayers() + "§e)");
            }
        } else {
            user.setTag(Tag.SPECTATOR);
            user.setTeamPrefix(null);
            handleVanish(player);
            player.teleport(spawn);
        }

        hideAndShow(user);

        arcade.load(user);

        account.setRoute(this, user.getTeam() != null ? user.getTeam().getCodeId() : "", user.getState(), user.getJoin());
    }

    @Override
    public void quit(Player player) {
        User user = (User) User.of(player.getUniqueId());

        if (user.isPlayer() && !user.inState(ArcadeState.DEAD)) {
            Team team = user.getTeam();

            if (team != null) {
                if (isPhase(RoomPhase.PLAYING)) {
                    boolean hasBed = team.hasBed();
                    
                    if (hasBed) {
                        ArcadeRouteContext route = ArcadeRouteContext.builder()
                                .arcade(getArcade().getCategory())
                                .roomId(getId())
                                .mapId(getMap().getId())
                                .slot(getSlot())
                                .serverId(Core.getServerId())
                                .arenaIdentifier(getIdentifier())
                                .state(ArcadeState.ALIVE)
                                .maxPlayers(getMaxPlayers())
                                .teamId(team.getCodeId())
                                .build();
                        
                        Reconnect reconnect = new Reconnect(player.getUniqueId(), route);
                        Core.getReconnectData().save(reconnect);
                        
                        Core.getLogger().info("[BedWars] Reconexão criada para " + player.getName() + " (expira em 3 minutos)");
                    } else {
                        team.getMembers().remove(player.getUniqueId());
                        Core.getLogger().info("[BedWars] " + player.getName() + " removido do time (sem cama)");
                    }
                    
                    Core.getPlatform().runSync(() -> {
                        send(team.getColor() + player.getName() + "§7 desconectou.");

                        if (team.getPlayers().isEmpty() && team.hasBed()) {
                            team.setBedExpiresAt(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3));
                            Core.getLogger().info("[BedWars] Time " + team.getTypeName() + " ficou vazio. Cama expira em 3 minutos.");
                        }

                        if (!hasBed) {
                            user.setState(ArcadeState.DEAD);
                            getArcade().handleDefeatStats(user);
                        }
                        
                        long alivePlayers = getUsers().stream()
                                .filter(u -> u.isPlayer() && !u.inState(ArcadeState.DEAD))
                                .count();
                        
                        if (alivePlayers == 0) {
                            Core.getLogger().info("[BedWars] Todos os jogadores desconectaram. Fechando partida...");
                            send("§cTodos os jogadores desconectaram. A partida será encerrada.");
                            Core.getPlatform().runSync(this::unload, 60L);
                            return;
                        }
                        
                        getUsers().forEach(this::updateSidebarTeams);
                    }, 3L);
                } else if (isPhase(RoomPhase.WAITING) || isPhase(RoomPhase.STARTING)) {
                    team.getMembers().remove(player.getUniqueId());
                    
                    Account account = user.getAccount();
                    Tag tag = user.getTag();
                    send(tag.getColor() + (tag.ordinal() >= Tag.FUNDADOR.ordinal() ? "§o" : "") + account.getNickname()
                            + "§e saiu da sala. §7(§b" + getTotalPlayers() + "§7/§b" + getMaxPlayers() + "§7)");
                }
            }
        }

        getPlayers().remove(player);
    }

    public static final int RESPAWN_COOLDOWN_SECONDS = 5;

    /**
     * Espera de 5 segundos antes de renascer: o jogador fica invisível e no ar
     * (estado RESPAWN) enquanto o título conta o tempo. Abate final não passa
     * por aqui — vai direto para DEAD.
     */
    public void startRespawn(User user) {
        Player player = user.getPlayer();

        if (player == null || !player.isOnline() || user.getTeam() == null) return;

        user.setState(ArcadeState.RESPAWN);
        hideAndShow(user);

        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("bedwars");

        if (plugin == null) {
            respawn(user);
            return;
        }

        new BukkitRunnable() {
            int remaining = RESPAWN_COOLDOWN_SECONDS;

            @Override
            public void run() {
                Player current = user.getPlayer();

                if (current == null || !current.isOnline() || user.getArena() != Arena.this
                        || !user.inState(ArcadeState.RESPAWN) || !isPhase(RoomPhase.PLAYING)) {
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    cancel();
                    respawn(user);
                    return;
                }

                Account account = user.getAccount();
                account.title("", "§c" + remaining + "...");
                account.sound(Sound.NOTE_PLING);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Renasce na base do time com o kit completo e sem proteção de spawn (pode
     * quebrar blocos, pegar itens e bater imediatamente).
     */
    public void respawn(User user) {
        Player player = user.getPlayer();

        if (player == null || !player.isOnline() || user.getTeam() == null) return;

        user.setState(ArcadeState.ALIVE);

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFallDistance(0F);
        player.setFireTicks(0);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        player.setFoodLevel(20);
        player.setSaturation(5F);
        player.setExhaustion(0F);
        player.setHealth(player.getMaxHealth());

        for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects())
            player.removePotionEffect(effect.getType());

        Location spawn = user.getTeam().getBase();

        if (spawn != null)
            player.teleport(spawn);

        getArcade().handleHotbar(user);

        /* O inventário do cliente só é reconstruído no tick seguinte: reaplicar o
         * kit garante que a espada, a armadura e as ferramentas não se percam. */
        Core.getPlatform().runSync(() -> {
            getArcade().handleHotbar(user);
            player.updateInventory();
        }, 3L);

        hideAndShow(user);

        TagManager.updateTag(user.getAccount());

        Account account = user.getAccount();

        account.title("", "§aVocê renasceu!");
        account.sound(Sound.LEVEL_UP);
    }

    public void handlePulse() {
        if (isPhase(RoomPhase.WAITING) && canBeCount())
            setPhase(RoomPhase.STARTING);

        if (isPhase(RoomPhase.STARTING)) {
            if (context.isCounterEnabled()) {
                if (canBeCount()) {
                    if (isFull() && getTime() > 10)
                        setTime(10);

                    setTime(getTime() - 1);

                    final int time = getTime();

                    if (time == 10) {
                        title("§c§l" + getTime(), "§csegundos!");

                        sound(Sound.CLICK);
                    }

                    if (time <= 5 && time >= 1) {
                        send("§eA partida inicia em §c" + time + "§e segundo" + (time > 1 ? "s" : "") + "!");
                        title((time <= 3 ? "§a" : "§c") + time, "§ePrepare-se!");

                        sound(Sound.CLICK);
                    }

                    if (time <= 0)
                        setPhase(RoomPhase.PLAYING);
                } else if (!isCustom() && !context.isIgnoreMinPlayers()) {
                    setPhase(RoomPhase.WAITING);
                }
            }
        }

        if (isPhase(RoomPhase.PLAYING)) {
            setFullTime(getFullTime() + 1);

            checkTeamBeds();
            updateGenerators();
            
            Event event = getEvent();

            if (context.isCounterEnabled()) {
                if (event != null) {
                    int eventTime = event.getTime();

                    if (eventTime > 0)
                        event.setTime(eventTime - 1);

                    if (eventTime == 0) {
                        event.handle(this);
                        updateEventSidebar(event);
                    } else
                        updateEventSidebar(event);
                }
            }

            /* A contagem de respawn é controlada por startRespawn(). */

            long teamsCompeting = getTeamList().stream()
                .filter(team -> team.hasBed() || !team.getPlayers().isEmpty())
                .count();
            
            if (teamsCompeting <= 1) {
                if (context.isShouldCountStatistics()) {
                    send("§4§lAVISO: §cApenas um time restante!");

                    Team winner = getTeamList().stream()
                        .filter(team -> team.hasBed() || !team.getPlayers().isEmpty())
                        .findFirst()
                        .orElse(null);

                    setWinner(winner);
                    setPhase(RoomPhase.ENDING);
                }
            }
        }

        if (isPhase(RoomPhase.ENDING)) {
            setTime(getTime() - 1);

            if (getTime() <= 0 || getPlayers().isEmpty())
                setPhase(RoomPhase.RESETTING);

            if (hasWinner())
                getWinner().getPlayers().forEach(player -> Core.getPlatform().runSync(() -> FireworkApi.random(player.getLocation())));
        }

        getAllUsers().forEach(user -> updateSidebarTime(user.getSidebar()));
    }

    protected void updateEventSidebar(Event event) {
        getAllUsers().forEach(user -> {
            Sidebar sidebar = user.getSidebar();

            if (sidebar != null && sidebar.hasRow("event")) {
                sidebar.updateRow("event", getEventRow(event));
            }
        });
    }

    public void chat(ChatType type, User user, String message) {
        Account account = user.getAccount();

        // Verificar se o jogador está mutado
        Punishment mute = account.getActivePunishment(PunishmentCategory.MUTE);
        if (mute != null) {
            String timeMessage = mute.isTemporary() 
                ? " §7(Expira em: §f" + com.minecraft.core.util.list.TimeUtil.formatTime(mute.getExpiresAt(), com.minecraft.core.util.list.TimeUtil.TimeFormat.SHORT) + "§7)"
                : "";
            account.send("§cVocê está mutado! Motivo: §f" + mute.getCause() + timeMessage);
            return;
        }

        BedMember member = user.getMember();

        String rank = member.getLevelId() + " ";

        Team team = user.getTeam();

        String prefix = !user.inState(ArcadeState.ALIVE) ? "§7[MORTO] " : "";

        if (Objects.requireNonNull(type) == ChatType.TEAM) {
            List<User> usersToSend = !user.inState(ArcadeState.DEAD)
                    ? getTeamUsers(team)
                    : getAllUsers().stream().filter(search -> !search.inState(ArcadeState.ALIVE)).collect(Collectors.toList());

            usersToSend.forEach(teamMember -> {
                Player player = teamMember.getPlayer();

                if (player != null) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(!user.inState(ArcadeState.DEAD) ? team.getBrackets() + " " : "")
                            .append(prefix).append(rank);
                    
                    // Adicionar medalha se não for NONE
                    if (account.getMedal() != null && !account.getMedal().equals(Medal.NONE)) {
                        builder.append(account.getMedal().getColoredSymbol()).append(" ");
                    }
                    
                    builder.append(account.formatTag(teamMember.getAccount().getTagPrefix()))
                            .append(account.getNickname())
                            .append(": §f")
                            .append(message);
                    
                    player.sendMessage(builder.toString());
                }
            });

        } else {
            List<User> usersToSend = !user.inState(ArcadeState.DEAD)
                    ? getUsers()
                    : getAllUsers().stream().filter(search -> !search.inState(ArcadeState.ALIVE)).collect(Collectors.toList());

            usersToSend.forEach(target -> {
                Player player = target.getPlayer();

                if (player != null) {
                    StringBuilder builder = new StringBuilder();
                    builder.append(!isSlot(Slot.SOLO) ? "§6[G] " : "")
                            .append(team != null && isPhase(RoomPhase.PLAYING) ?
                                    (!user.inState(ArcadeState.DEAD) ? team.getBrackets() + " " : "") : "")
                            .append(prefix).append(rank);
                    
                    // Adicionar medalha se não for NONE
                    if (account.getMedal() != null && !account.getMedal().equals(Medal.NONE)) {
                        builder.append(account.getMedal().getColoredSymbol()).append(" ");
                    }
                    
                    builder.append(account.formatTag(target.getAccount().getTagPrefix()))
                            .append(account.getNickname())
                            .append(": §f")
                            .append(message);
                    
                    player.sendMessage(builder.toString());
                }
            });
        }
    }

    public enum ChatType {
        TEAM, GLOBAL
    }

    public Arcade getArcade() {
        return (Arcade) super.getArcade();
    }

    @Override
    public void setPhase(RoomPhase phase) {
        if (isPhase(phase)) return;

        super.setPhase(phase);
        ArenaPhaseExecutor.execute(this, phase);
    }

    @Override
    public boolean canBeCount() {
        if (!context.isCounterEnabled()) return false;

        /* Sala customizada ou "ignorar mínimo" ligada pelo menu de gestão:
         * quem abre a partida é o próprio autor, então um jogador já conta.
         * O piso de 2 jogadores ficava na frente desse teste e travava o
         * lobby de espera mesmo com a contagem forçada. */
        if (isCustom() || context.isIgnoreMinPlayers())
            return getTotalPlayers() >= 1;

        /* Com preenchimento por bot ligado, sozinho já monta partida: senão o
         * jogador ficaria preso no lobby esperando gente que não vem. */
        if (BotController.willFill(this))
            return getTotalPlayers() >= 1;

        return getTotalPlayers() >= getMinPlayers();
    }

    public boolean isProtectedArea(Location center) {
        for (OreGenerator generator : getGenerators()) {
            Location location = generator.getLocation();

            if (location != null && center.getWorld().equals(center.getWorld()) && center.distance(location) <= 3)
                return true;
        }

        /* Mapa sem "area_protection" (MarioWorld) não tem base protegida; o
         * get() devolve null e arrebentava a checagem inteira. */
        JsonElement protection = getMap().getConfig().get("area_protection");

        if (protection == null || !protection.isJsonObject()) return false;

        JsonObject area = protection.getAsJsonObject();

        int frontAndBack = getInt(area, "frontAndBack"),
                up = getInt(area, "up"),
                side = getInt(area, "side");

        if (frontAndBack <= 0 || up <= 0 || side <= 0) return false;

        for (Team team : getTeamList()) {
            Location base = team.getBase();
            
            if (base == null) continue;

            SyntheticLocation synthetic = getSynthetic(team.getTypeName().toLowerCase() + "_spawn");

            boolean axisX = synthetic != null && synthetic.isAxisX();

            if (Cuboid.isProtectedArea(base, center, frontAndBack, up, side, axisX))
                return true;
        }

        return false;
    }

    private static int getInt(JsonObject json, String key) {
        JsonElement element = json.get(key);

        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    public void handleEntities(User user) {
        Player player = user.getPlayer();

        BedMember member = user.getMember();

        Account account = user.getAccount();

        Team userTeam = user.getTeam();

        for (Team team : getTeamList()) {
            if (team.equals(userTeam)) continue;

            BedSkin skin = BedSkin.of(team.getName());
            if (skin == null) {
                TeamPresetType preset = TeamPresetType.of(team.getColor());
                if (preset != null) {
                    String code = preset.name();
                    skin = BedSkin.of(code);
                    if (skin == null) {
                        skin = switch (code) {
                            case "PINK", "PURPLE" -> BedSkin.PURP;
                            case "GREEN", "ORAN", "ORANGE" -> BedSkin.ORAN;
                            case "WHITE" -> BedSkin.WHIT;
                            case "YEL", "YELLOW" -> BedSkin.YEL;
                            default -> skin;
                        };
                    }
                }
            }

            if (skin == null) {
                skin = BedSkin.BLUE;
            }

            handleTeamNpc(player, team, skin.getValue(), skin.getSignature());
        }

        String value, signature;

        SellerCollectible seller = (SellerCollectible) member.getActiveCollectible(BedCollectibleType.SELLER_SKIN);

        if (seller != null && seller.isNotEqual(Default.class)) {
            if (!seller.isNotEqual(Random.class)) {
                seller = (SellerCollectible) member.getRandomCollectible(BedCollectibleType.SELLER_SKIN);

                if (seller == null)
                    seller = (SellerCollectible) member.getCollectible(BedCollectibleType.SELLER_SKIN, "Default");
            }

            value = seller.isNotEqual(Mirror.class) ? seller.getValue() : account.getSkin().getValue();
            signature = seller.isNotEqual(Mirror.class) ? seller.getSignature() : account.getSkin().getSignature();
        } else {
            BedSkin bedSkin = BedSkin.of(userTeam.getName());
            if (bedSkin == null) {
                TeamPresetType preset = TeamPresetType.of(userTeam.getColor());
                if (preset != null) {
                    String code = preset.name();
                    bedSkin = BedSkin.of(code);
                    if (bedSkin == null) {
                        bedSkin = switch (code) {
                            case "PINK", "PURPLE" -> BedSkin.PURP;
                            case "GREEN", "ORAN", "ORANGE" -> BedSkin.ORAN;
                            case "WHITE" -> BedSkin.WHIT;
                            case "YEL", "YELLOW" -> BedSkin.YEL;
                            default -> bedSkin;
                        };
                    }
                }
            }

            if (bedSkin == null) bedSkin = BedSkin.BLUE;

            value = bedSkin.getValue();
            signature = bedSkin.getSignature();
        }

        handleTeamNpc(player, userTeam, value, signature);
    }

    protected void handleTeamNpc(Player player, Team team, String value, String signature) {
        Location shopLocation = getLocation("npc_shop_" + team.getTypeName().toLowerCase());

        if (shopLocation != null) {
            NpcClient shop = getArcade().getManager().getNpc().spawnClient(player, shopLocation, value, signature);

            shop.setAction((target, action) -> {
                if (ableToInteract(target))
                    new ShopMenu(target, BedShop.FAVORITE).handle();
            });

            shop.display();
        }

        Location upgradeLocation = getLocation("npc_upgrade_" + team.getTypeName().toLowerCase());

        if (upgradeLocation != null) {
            NpcClient upgrade = getArcade().getManager().getNpc().spawnClient(player, upgradeLocation, value, signature);

            upgrade.setAction((target, action) -> {
                if (ableToInteract(target))
                    new UpgradeMenu(target).handle();
            });

            upgrade.display();
        }

        handleChestHolograms(player);
    }

    /* Hologramas acima dos baús das ilhas (preferência "Holograma dos baús") */
    protected void handleChestHolograms(Player player) {
        User user = (User) User.of(player.getUniqueId());

        if (user == null || user.getMember() == null || !user.getMember().getMetadata().isChestHologram())
            return;

        for (Team team : getTeamList()) {
            String name = team.getTypeName().toLowerCase();

            Location chest = getLocation("chest_" + name);

            if (chest != null) {
                com.minecraft.core.bukkit.api.hologram.type.client.HologramClient hologram = com.minecraft.core.bukkit.BukkitCore.getManager().getHologram()
                        .spawnClient(player, "chest_" + name, chest.clone().add(0.5, 1.6, 0.5));

                hologram.setText(java.util.Arrays.asList(team.getColor() + "§lBaú do time", "§7Itens compartilhados"));
                hologram.spawnTo(player);
            }

            Location enderChest = getLocation("enderchest_" + name);

            if (enderChest != null) {
                com.minecraft.core.bukkit.api.hologram.type.client.HologramClient hologram = com.minecraft.core.bukkit.BukkitCore.getManager().getHologram()
                        .spawnClient(player, "enderchest_" + name, enderChest.clone().add(0.5, 1.6, 0.5));

                hologram.setText(java.util.Arrays.asList("§5§lBaú pessoal", "§7Somente você acessa"));
                hologram.spawnTo(player);
            }
        }
    }

    protected boolean ableToInteract(Player player) {
        User user = (User) User.of(player.getUniqueId());

        return user != null && user.isPlayer() && user.inState(ArcadeState.ALIVE);
    }

    public Event getEvent() {
        return context.getEvent();
    }

    public List<Team> getTeamList() {
        return getTeams().stream().map(team -> (Team) team).collect(Collectors.toList());
    }

    @Override
    public Team buildTeam(TeamPresetType preset) {
        Location location = getLocation(preset.name().toLowerCase() + "_spawn");
        
        if (location == null) {
            location = getLocation(preset.getName().toLowerCase() + "_spawn");
        }
        
        if (location == null) {
            location = getLocation(preset.name().toLowerCase() + "_bed");
        }

        if (location != null) {
            Core.getLogger().info("[BedWars] Time " + preset.getName() + " criado com spawn em " + 
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
        } else {
            Core.getLogger().warning("[BedWars] Time " + preset.getName() + " criado SEM SPAWN! Localizações tentadas: " +
                preset.name().toLowerCase() + "_spawn, " + preset.getName().toLowerCase() + "_spawn, " + preset.name().toLowerCase() + "_bed");
        }

        return new Team(preset, this, location, getSlot().getMaxPlayers() / 2);
    }

    public List<Team> buildTeams() {
        return Arrays.stream(TeamPresetType.values())
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(this::buildTeam)
                .collect(Collectors.toList());
    }

    public List<Team> buildTeams(ChatColor... colors) {
        List<Team> teamList = buildTeams();

        if (colors == null || colors.length == 0)
            return teamList;

        Set<ChatColor> colorSet = new HashSet<>(Arrays.asList(colors));

        return teamList.stream()
                .filter(team -> colorSet.contains(team.getColor()))
                .collect(Collectors.toList());
    }

    public Team getTeam(Location bedLocation) {
        Team exact = getTeamList().stream().filter(team -> team.isYourBed(bedLocation)).findFirst().orElse(null);

        if (exact != null) return exact;

        if (bedLocation == null || bedLocation.getWorld() == null) return null;

        /* Fallback para camas salvas com deslocamento maior (configs antigas):
         * associa a cama quebrada ao time com a cama registrada mais próxima,
         * desde que esteja a no máximo 2 blocos. */
        return getTeamList().stream()
                .filter(team -> {
                    Location bed = team.getBedLocation();

                    return bed != null && bed.getWorld() != null
                            && bed.getWorld().getName().equalsIgnoreCase(bedLocation.getWorld().getName())
                            && bed.distanceSquared(bedLocation) <= 4.0;
                })
                .min(Comparator.comparingDouble(team -> team.getBedLocation().distanceSquared(bedLocation)))
                .orElse(null);
    }

    public List<Team> getAliveTeams() {
        return getTeamList().stream().filter(team -> !team.isDead()).collect(Collectors.toList());
    }

    public List<User> getAllUsers() {
        return User.getList().stream().filter(user -> isPlayer(user.getAccount().getId()))
                .map(user -> (User) user).collect(Collectors.toList());
    }

    public List<User> getUsers() {
        return User.getList().stream()
                .filter(user -> user != null && user.isPlayer() && isPlayer(user.getAccount().getId()))
                .map(user -> (User) user)
                .collect(Collectors.toList());
    }

    public List<User> getAliveUsers() {
        return getUsers().stream().filter(user -> user.inState(ArcadeState.ALIVE)).collect(Collectors.toList());
    }

    public List<User> getAliveUsers(Team team) {
        return getAliveUsers().stream().filter(user -> user.getTeam() != null && user.getTeam().equals(team)).collect(Collectors.toList());
    }

    public List<User> getTeamUsers(Team team) {
        return getUsers().stream().filter(user -> user.getTeam() != null && user.getTeam().equals(team)).collect(Collectors.toList());
    }

    @Override
    public int getTotalPlayers() {
        return getUsers().size();
    }

    public boolean hasTeamsWithBed() {
        return getTeamList().stream().anyMatch(Team::hasBed);
    }

    public void hideAndShow(User user) {
        Player mainPlayer = user.getAccount().player();

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target == null || target.equals(mainPlayer)) continue;

            ProtocolHandler.hidePlayer(target, mainPlayer);
            ProtocolHandler.hidePlayer(mainPlayer, target);
        }

        for (Player targetPlayer : getPlayers()) {
            if (targetPlayer == null || mainPlayer.equals(targetPlayer))
                continue;

            User target = (User) User.of(targetPlayer.getUniqueId());

            if (target == null || !target.getArena().equals(user.getArena())) continue;

            if (user.isVanish()) {
                ProtocolHandler.showPlayer(mainPlayer, targetPlayer);

                if (target.isVanish())
                    ProtocolHandler.showPlayer(targetPlayer, mainPlayer);
            }

            if (user.isSpectator() && !target.isVanish()) {
                ProtocolHandler.showPlayer(mainPlayer, targetPlayer);

                if (target.isSpectator())
                    ProtocolHandler.showPlayer(targetPlayer, mainPlayer);
            }

            if (user.isPlayer()) {
                if (isPhase(RoomPhase.ENDING) && target.isPlayer()) {
                    ProtocolHandler.showPlayer(mainPlayer, targetPlayer);
                    ProtocolHandler.showPlayer(targetPlayer, mainPlayer);
                }

                if (target.isPlayer() && !target.inState(ArcadeState.ALIVE) && !user.inState(ArcadeState.ALIVE))
                    ProtocolHandler.showPlayer(mainPlayer, targetPlayer);

                if (target.isPlayer() && target.inState(ArcadeState.ALIVE))
                    ProtocolHandler.showPlayer(mainPlayer, targetPlayer);

                if (user.inState(ArcadeState.ALIVE))
                    ProtocolHandler.showPlayer(targetPlayer, mainPlayer);
            }
        }
    }

    /* Linha de cada time: "§cV §fVermelho: §a✓" */
    public String getSidebarTeamRow(Team team) {
        int bots = BotController.alive(this, team);

        /* Time só de bot continua vivo na TAB: sem contar o bot, o time
         * aparecia com ✘ enquanto o bot ainda defendia a cama. */
        String status = (team.getMembers().isEmpty() && bots == 0) || team.isDead()
                ? "§c✘"
                : team.getBedName(getAliveUsers(team).size() + bots);

        return team.getColor() + team.getId() + " §f" + team.getName() + ": " + status;
    }

    public static String getEventRow(Event event) {
        return "§f" + event.getPhase().getName() + " em §2" + TimeUtil.time(event.getTime());
    }

    public void renderSidebarTeams(User user) {
        Sidebar sidebar = user.getSidebar();

        for (Team team : getTeamList()) {
            if (team.getBase() == null) continue;

            sidebar.addRow(team.getCodeId(), getSidebarTeamRow(team));
        }
    }

    public void updateSidebarTeams(User user) {
        Sidebar sidebar = user.getSidebar();

        if (sidebar == null) return;

        for (Team team : getTeamList()) {
            if (team.getBase() == null) continue;

            if (!sidebar.hasRow(team.getCodeId())) {
                getArcade().handleSidebar(user);
                return;
            }
        }

        for (Team team : getTeamList()) {
            if (team.getBase() == null) continue;

            sidebar.updateRow(team.getCodeId(), getSidebarTeamRow(team));
        }
    }

    public void renderSidebarTime(Sidebar sidebar) {
        String prefix = isPhase(RoomPhase.STARTING)
                ? "Inicia em §a"
                : isPhase(RoomPhase.ENDING)
                ? "§cFim de jogo!"
                : "§fAguardando...";

        String suffix = isPhase(RoomPhase.STARTING)
                ? TimeUtil.formatTime(getTime())
                : "";

        sidebar.addRow("time", prefix + suffix);
        sidebar.blankRow();
    }

    public void updateSidebarTime(Sidebar sidebar) {
        if (isPhase(RoomPhase.PLAYING)) {
            return;
        }

        String prefix = isPhase(RoomPhase.STARTING)
                ? "Inicia em §a"
                : isPhase(RoomPhase.ENDING)
                ? "§cFim de jogo!"
                : "§fAguardando...";

        String suffix = isPhase(RoomPhase.STARTING)
                ? TimeUtil.formatTime(getTime())
                : "";

        if (sidebar.hasRow("time")) {
            sidebar.updateRow("time", prefix + suffix);
        }
        if (sidebar.hasRow("players")) {
            sidebar.updateRow("players", "Jogadores: §a(" + getTotalPlayers() + "/" + getMaxPlayers() + ")");
        }
    }

    public Top getTop(TopType type) {
        return context.getTop(type);
    }

    public boolean inTop(User user) {
        return context.getTops().stream().anyMatch(top -> top.inTop(user.getAccount().getId()));
    }

    public void verifyTop(TopType type, UUID id, int value) {
        Top top = getTop(type);

        if (top != null)
            top.setTop(id, value);
    }

    public TopUser getTopUser(TopType type, UUID id) {
        Top top = getTop(type);

        if (top == null) {
            Core.getLogger().warning("O top " + type.name() + " é nulo.");
            return null;
        }

        return top.getUser(id);
    }

    public TopUser getTopUser(TopType type, TopCategory category) {
        Top top = getTop(type);

        if (top == null) {
            Core.getLogger().warning("O top " + type.name() + " é nulo.");
            return null;
        }

        return top.getUser(category);
    }

    public boolean isFinalKill(User user) {
        Team team = user.getTeam();

        if (team == null) {
            Core.getLogger().warning("[BedWars] isFinalKill: Jogador " + user.getPlayer().getName() + " não tem time!");
            return true;
        }

        /* Só elimina quem perde a cama nesta partida. Sem cama no mapa o jogador
         * continua renascendo (antes qualquer morte virava abate final). */
        return team.isBedDestroyed();
    }

    public List<OreGenerator> getGenerators() {
        return context.getGenerators();
    }

    public void spawnGenerators() {
        getLocationsByName("ore_generator_" + BedOre.DIAMOND.name().toLowerCase())
                .forEach(signed -> getGenerators().add(new DiamondGenerator(getTotalGenerators(BedOre.DIAMOND) + 1, this)));

        getLocationsByName("ore_generator_" + BedOre.EMERALD.name().toLowerCase())
                .forEach(signed -> getGenerators().add(new EmeraldGenerator(getTotalGenerators(BedOre.EMERALD) + 1, this)));

        for (Team team : getTeamList()) {
            if (team == null) continue;

            Forge forge = team.getForge();

            if (forge != null)
                forge.spawn();
        }
    }

    public void checkTeamBeds() {
        for (Team team : getTeamList()) {
            if (team.hasExpired()) {
                Core.getPlatform().runSync(() -> {
                    team.destructBed();

                    Core.getReconnectData().list().stream()
                        .filter(reconnect -> reconnect.getRoute().getArenaIdentifier() != null 
                            && reconnect.getRoute().getArenaIdentifier().equals(getIdentifier())
                            && reconnect.getTeamId() != null
                            && reconnect.getTeamId().equalsIgnoreCase(team.getCodeId()))
                        .forEach(reconnect -> Core.getReconnectData().delete(reconnect.getSender()));

                    getUsers().forEach(this::updateSidebarTeams);
                });

                send("",
                     "§f§lCAMA DESTRUÍDA §f» §7A " + team.getColor() + "Cama do " + team.getName() + 
                     "§7 expirou após §c3 minutos §7sem jogadores online.",
                     "");
                
                sound(Sound.ENDERDRAGON_GROWL, 13, 1);
            }
        }
    }

    public void updateGenerators() {
        for (Team team : getTeamList()) {
            if (team == null) continue;

            Forge forge = team.getForge();

            for (ForgeGenerator generator : forge.getGenerators()) {
                if (generator != null && generator.hasPendentUpdate()) {
                    generator.resetTime();

                    Core.getPlatform().runSync(generator::spawn);
                }
            }
        }

        for (OreGenerator generator : getGenerators()) {
            HologramServer hologram = generator.getHologram();

            if (hologram == null) continue;

            generator.setTime(generator.getTime() - 1);

            int time = generator.getTime();

            if (time <= 0) {
                Core.getPlatform().runSync(generator::spawn);

                generator.setTime(generator.getUpdateTime());
            }

            int lastRow = hologram.getRows().size() - 2;

            if (time <= 0)
                time = 1;

            hologram.setText(0, "§eNível §c" + generator.getLevel().getTag());
            hologram.setText(lastRow, "§eGera em §c" + time + "§e segundo" + (time > 1 ? "s" : ""));
        }
    }

    public void upgradeGenerators(BedOre ore, int updateTime) {
        for (OreGenerator generator : getGenerators(ore)) {
            GeneratorLevel next = GeneratorLevel.values()[generator.getLevel().ordinal() + 1];

            generator.setLevel(next);

            generator.setUpdateTime(updateTime);
            generator.setTime(updateTime);
        }
    }

    public GeneratorLevel getGeneratorsLevel(BedOre ore) {
        OreGenerator generator = getGenerators(ore).stream().findFirst().orElse(null);

        return generator != null ? generator.getLevel() : GeneratorLevel.NONE;
    }

    public List<OreGenerator> getGenerators(BedOre ore) {
        return getGenerators().stream().filter(generator -> generator.getOre().equals(ore)).collect(Collectors.toList());
    }

    public OreGenerator getGenerator(BedOre ore, int id) {
        return getGenerators(ore).stream().filter(generator -> generator.getId() == id).findFirst().orElse(null);
    }

    public int getTotalGenerators() {
        return getGenerators().size();
    }

    public int getTotalGenerators(BedOre ore) {
        return getGenerators(ore).size();
    }

    public void handleLeaderboards(User user) {
        Player player = user.getAccount().player();
        if (player == null || !player.isOnline()) return;

        HologramManager manager = BukkitCore.getManager().getHologram();

        Location levelLoc = getLocation("leaderboard_level");
        if (levelLoc != null && manager.notExistsClient(player, "leaderboard_level")) {
            new BedLevelLeaderboard(player, levelLoc).handle();
        }

        Location winsLoc = getLocation("leaderboard_wins");
        if (winsLoc != null && manager.notExistsClient(player, "leaderboard_wins")) {
            new BedWinsLeaderboard(player, winsLoc).handle();
        }

        Location killLoc = getLocation("leaderboard_kill");
        if (killLoc != null && manager.notExistsClient(player, "leaderboard_kills")) {
            new BedFinalKillLeaderboard(player, killLoc).handle();
        }

        Location winstreakLoc = getLocation("leaderboard_winstreak");
        if (winstreakLoc != null && manager.notExistsClient(player, "leaderboard_winstreak")) {
            new BedWinstreakLeaderboard(player, winstreakLoc).handle();
        }
    }

    public void removeLeaderboards(Player player) {
        HologramManager manager = BukkitCore.getManager().getHologram();
        String[] leaderboardTags = {"leaderboard_level", "leaderboard_rank", "leaderboard_wins", "leaderboard_kills", "leaderboard_winstreak"};
        
        for (String tag : leaderboardTags) {
            HologramClient client = manager.getClient(player, tag);
            if (client != null) {
                manager.removeClient(client);
            }
        }
    }
}
