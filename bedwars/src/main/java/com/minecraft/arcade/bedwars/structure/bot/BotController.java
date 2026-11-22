package com.minecraft.arcade.bedwars.structure.bot;

import com.minecraft.arcade.bedwars.arcade.arena.Arena;
import com.minecraft.arcade.bedwars.structure.team.Team;
import com.minecraft.arcade.bedwars.user.User;
import com.minecraft.core.Core;
import com.minecraft.core.arcade.room.phase.RoomPhase;
import lombok.Getter;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Registro e ciclo de vida dos bots de treino.
 * <p>
 * O bot é uma entidade do servidor (não um jogador falso: o fork não expõe API
 * de fake player e um NPC de protocolo não apanha de espada), então tudo que a
 * partida precisa saber sobre ele passa por aqui: quantos estão vivos por time,
 * quem deu o último abate e o que acontece quando ele destrói uma cama.
 */
public final class BotController {

    public static final String PROPERTY = "bots";

    /**
     * Ordem de distribuição: time sem humano primeiro e, entre eles, o que tem
     * menos bots — sem contar bot, o min() sempre devolvia o mesmo time e o
     * lote inteiro caía na mesma ilha.
     */
    private static Comparator<Team> byLoad(Arena arena) {
        return Comparator
                .comparingInt((Team team) -> team.getMembers().isEmpty() ? 0 : 1)
                .thenComparingInt(team -> team.getMembers().size() + alive(arena, team));
    }

    /* id do bot -> controlador */
    private static final Map<UUID, BedWarsBot> BOTS = new ConcurrentHashMap<>();

    /* sala -> bots */
    private static final Map<String, List<BedWarsBot>> ARENAS = new ConcurrentHashMap<>();

    /* vitima -> ultimo bot que bateu nela */
    private static final Map<UUID, Attack> ATTACKS = new ConcurrentHashMap<>();

    private BotController() {
    }

    @Getter
    public static class Attack {
        private final Team team;
        private final String name;
        private final long time;

        private Attack(Team team, String name) {
            this.team = team;
            this.name = name;
            this.time = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - time > TimeUnit.SECONDS.toMillis(4);
        }
    }

    /* Configuração */

    /**
     * Perfil da sala: cópia do config.yml guardada nas propriedades do Room,
     * para o /bwbot e o menu ajustarem uma sala sem mexer no servidor inteiro.
     */
    public static BotProfile profile(Arena arena) {
        if (arena.getProperty(PROPERTY) instanceof BotProfile profile) return profile;

        BotProfile profile = BotProfile.load();

        arena.writeProperty(PROPERTY, profile);
        return profile;
    }

    public static boolean isEnabled(Arena arena) {
        return profile(arena).isEnabled();
    }

    /**
     * Quantos bots a sala precisa para abrir. Sala cheia de humano não recebe
     * bot, e com {@code fill-short-rooms} desligado só o jogador sozinho é
     * acompanhado.
     */
    public static int plan(Arena arena, int humans) {
        if (humans <= 0 || !isEnabled(arena)) return 0;

        BotProfile profile = profile(arena);

        if (!profile.isFillShortRooms() && humans > 1) return 0;

        int missing = arena.getMinPlayers() - humans;

        if (missing <= 0) return 0;

        return Math.min(profile.getMaxTotal(), missing);
    }

    public static boolean willFill(Arena arena) {
        return plan(arena, arena.getTotalPlayers()) > 0;
    }

    /* Registro */

    public static void register(BedWarsBot bot) {
        BOTS.put(bot.getId(), bot);
        ARENAS.computeIfAbsent(bot.getArena().getIdentifier(), key -> new ArrayList<>()).add(bot);
    }

    public static void unregister(BedWarsBot bot) {
        BOTS.remove(bot.getId());

        List<BedWarsBot> list = ARENAS.get(bot.getArena().getIdentifier());

        if (list != null) list.remove(bot);
    }

    public static List<BedWarsBot> bots(Arena arena) {
        return ARENAS.getOrDefault(arena.getIdentifier(), Collections.emptyList());
    }

    public static boolean hasBots(Arena arena) {
        return !bots(arena).isEmpty();
    }

    public static boolean hasBots(Arena arena, Team team) {
        return alive(arena, team) > 0;
    }

    public static int alive(Arena arena, Team team) {
        return (int) bots(arena).stream().filter(bot -> bot.getTeam().equals(team) && bot.isAlive()).count();
    }

    public static int alive(Arena arena) {
        return (int) bots(arena).stream().filter(BedWarsBot::isAlive).count();
    }

    public static BedWarsBot of(Entity entity) {
        if (!entity.hasMetadata(BedWarsBot.KEY)) return null;

        Object value = entity.getMetadata(BedWarsBot.KEY).get(0).value();

        return value instanceof UUID id ? BOTS.get(id) : null;
    }

    public static boolean isBot(Entity entity) {
        return entity != null && entity.hasMetadata(BedWarsBot.KEY);
    }

    /* Partida */

    /**
     * Preenche a sala no início do PLAYING (na thread principal).
     * <p>
     * Com {@code group-in-one-team} ligado (padrão) todos os bots vão para o
     * mesmo time vazio — o "time adversário" contra o jogador. Sem isso eles se
     * procuram, se matam entre si e a partida acaba antes de alguém chegar
     * perto do humano. Desligado, os bots são distribuídos um por time livre
     * (respeitando max-per-team) e só depois viram companheiros de time.
     */
    public static void fill(Arena arena) {
        int wanted = plan(arena, arena.getTotalPlayers());

        if (wanted <= 0 || arena.isPhase(RoomPhase.WAITING)) return;

        BotProfile profile = profile(arena);

        List<Team> teams = arena.getTeamList().stream()
                .filter(team -> team.getBase() != null)
                .collect(Collectors.toList());

        if (teams.isEmpty()) {
            Core.getLogger().warning("[BedWars] Bots solicitados mas a sala \"" + arena.getIdentifier()
                    + "\" não tem time com base.");
            return;
        }

        List<Team> free = teams.stream()
                .filter(team -> team.getMembers().isEmpty())
                .collect(Collectors.toList());

        int created = 0;

        if (profile.isGroupInOneTeam() && !free.isEmpty()) {
            Team home = pickTeam(arena, free, profile.getTeam());

            while (created < wanted) {
                spawn(arena, home, profile, created);
                created++;
            }
        } else
            while (created < wanted) {
                Team pick = teams.stream()
                        .filter(team -> alive(arena, team) < profile.getMaxPerTeam())
                        .filter(team -> team.getMembers().isEmpty() || profile.isFillTeammates())
                        .min(byLoad(arena))
                        .orElse(null);

                if (pick == null) break;

                spawn(arena, pick, profile, created);
                created++;
            }

        if (created > 0)
            Core.getLogger().info("[BedWars] " + created + " bot(s) de treino entraram na sala "
                    + arena.getIdentifier() + " (" + arena.getTotalPlayers() + " humanos).");
    }

    /**
     * Time do agrupamento: o pedido na configuração se ele estiver livre e sem
     * humano, senão o primeiro time vazio (e na falta de um, o menos cheio).
     */
    private static Team pickTeam(Arena arena, List<Team> free, String wanted) {
        if (wanted != null && !wanted.isEmpty()) {
            Team match = free.stream()
                    .filter(team -> wanted.equalsIgnoreCase(team.getTypeName())
                            || wanted.equalsIgnoreCase(team.getName())
                            || wanted.equalsIgnoreCase(team.getId()))
                    .findFirst()
                    .orElse(null);

            if (match != null) return match;

            Core.getLogger().warning("[BedWars] Time de bot \"" + wanted + "\" não está livre na sala "
                    + arena.getIdentifier() + "; usando outro time vazio.");
        }

        return free.get(0);
    }

    private static BedWarsBot spawn(Arena arena, Team team, BotProfile profile, int index) {
        BedWarsBot bot = BedWarsBot.spawn(arena, team, profile, profile.nextName(index));

        /* Base sem ponto no mapa: o bot não nasce e não entra no registro. */
        if (bot == null) return null;

        register(bot);
        return bot;
    }

    public static void despawn(Arena arena) {
        List<BedWarsBot> list = ARENAS.get(arena.getIdentifier());

        if (list == null || list.isEmpty()) return;

        new ArrayList<>(list).forEach(BedWarsBot::despawn);

        ATTACKS.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /* Abate */

    public static void registerAttack(BedWarsBot bot, Player victim) {
        ATTACKS.put(victim.getUniqueId(), new Attack(bot.getTeam(), bot.getName()));
    }

    /**
     * Nome de quem aparece na mensagem quando um bot dá o abate (o bot não é
     * {@link User}, então não passa pelo crédito normal de kill).
     */
    public static String lastAttacker(Player victim) {
        Attack attack = ATTACKS.get(victim.getUniqueId());

        if (attack == null || attack.isExpired()) return null;

        return attack.getTeam().getColor() + attack.getName();
    }

    public static void forget(Player victim) {
        ATTACKS.remove(victim.getUniqueId());
    }

    /* Ajuste a quente */

    /**
     * Reaplica o perfil da sala em cada bot vivo (o /bwbot mexe no objeto já
     * guardado nas propriedades do room).
     */
    public static void reload(Arena arena) {
        bots(arena).forEach(BedWarsBot::refresh);
    }

    /**
     * Manda bots agora, fora do roteiro do PLAYING, para o administrador testar
     * a configuração sem reiniciar a sala.
     */
    public static int add(Arena arena, int count) {
        BotProfile profile = profile(arena);

        List<Team> teams = arena.getTeamList().stream()
                .filter(team -> team.getBase() != null)
                .collect(Collectors.toList());

        if (teams.isEmpty()) return 0;

        List<Team> free = teams.stream()
                .filter(team -> team.getMembers().isEmpty())
                .collect(Collectors.toList());

        Team home = profile.isGroupInOneTeam()
                ? pickTeam(arena, free.isEmpty() ? teams : free, profile.getTeam())
                : null;

        int created = 0;

        while (created < count) {
            Team pick = home;

            if (pick == null) {
                pick = teams.stream()
                        .filter(team -> alive(arena, team) < Math.max(1, profile.getMaxPerTeam()))
                        .filter(team -> team.getMembers().isEmpty() || profile.isFillTeammates())
                        .min(byLoad(arena))
                        .orElse(null);

                if (pick == null) break;
            }

            if (spawn(arena, pick, profile, alive(arena) + created) == null) break;

            created++;
        }

        return created;
    }

    /* Cama */

    /**
     * cama quebrada por bot: mesmo roteiro do abate por jogador (time perde a
     * cama, membros ficam sem renascer, sidebar e título atualizam), sem os
     * cosméticos do autor.
     */
    public static void destroyBed(Arena arena, Team bedTeam, BedWarsBot bot) {
        if (!bedTeam.hasBed()) return;

        bedTeam.destructBed();

        Location bed = bedTeam.getBedLocation();

        if (bed != null) bed.getWorld().playEffect(bed.clone().add(0.5, 0.5, 0.5), Effect.EXPLOSION, 0);

        bedTeam.getAliveUsers().forEach(User::setBedLost);

        bedTeam.getPlayers().forEach(player -> player.sendTitle("§c§lCAMA DESTRUÍDA", "§7Você não vai mais renascer!"));

        arena.send("",
                "§f§lCAMA DESTRUÍDA §f» §7A " + bedTeam.getColor() + "Cama do " + bedTeam.getName()
                        + "§7 foi desmontada por " + bot.getDisplayName() + "§7.",
                "");

        arena.sound(Sound.ENDERDRAGON_GROWL, 13, 1);

        Core.getPlatform().runSync(() -> arena.getUsers().forEach(arena::updateSidebarTeams), 2L);
    }
}
