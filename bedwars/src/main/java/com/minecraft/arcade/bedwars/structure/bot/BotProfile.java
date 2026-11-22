package com.minecraft.arcade.bedwars.structure.bot;

import com.minecraft.arcade.bedwars.BedWars;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ajustes do bot. Carregado do config.yml e copiado por sala em
 * {@code arena.getProperty("bots")}, para que o administrador mude uma sala sem
 * mexer no resto do servidor.
 */
@Getter
@Setter
public class BotProfile {

    private static final List<String> DEFAULT_NAMES = Arrays.asList(
            "Ferro-Velho", "Ponte-Seca", "Cutelo", "Maré-Alta", "Tijolo", "Brasa", "Estrepado", "Cacto"
    );

    private boolean enabled = true;

    /** Completar salas curtas em vez de só quando existe um único humano. */
    private boolean fillShortRooms = true;
    private int maxTotal = 4, maxPerTeam = 1;
    private boolean fillTeammates = true;

    /* Um time só de bots contra o jogador: é assim que o treino faz sentido
     * (senão os bots se aniquilam entre si antes de chegar em alguém). */
    private boolean groupInOneTeam = true;
    private String team = "";

    private int respawnSeconds = 10;
    private double speed = 0.23;

    private BotMode mode = BotMode.TACTICAL;
    private BridgeStyle bridgeStyle = BridgeStyle.DIAGONAL;
    private double blocksPerSecond = 2.0;
    private int capacity = 96;

    private double engageDistance = 12.0, attackDistance = 3.0;
    private double strafe = 0.5, knockback = 0.45;
    private boolean circleStrafe = true, leap = true, blockHit = true;
    private double retreatHealth = 7.0;
    private double maxHealth = 20, attackDamage = 4.0;

    private boolean guardBed = true, attackBeds = true, buyUpgrades = true;
    private double bedBreakSeconds = 2.0;

    private int ironPerSecond = 1;
    private double goldPerSecond = 0.2;
    private int woolCostIron = 4, woolBlocks = 16;

    /* Abate de bot não vira estatística global por padrão: sem isso a sala
     * viraria fazenda de XP. */
    private boolean killsInTop = false, giveXp = false;

    private final List<String> names = new ArrayList<>(DEFAULT_NAMES);

    private String suffix = "§8[§7BOT§8]";

    public static BotProfile load() {
        FileConfiguration config = BedWars.getInstance().getConfig();

        BotProfile profile = new BotProfile();

        profile.setEnabled(config.getBoolean("bots.enabled", true));
        profile.setFillShortRooms(config.getBoolean("bots.fill-short-rooms", true));
        profile.setMaxTotal(config.getInt("bots.max-total", 4));
        profile.setMaxPerTeam(config.getInt("bots.max-per-team", 1));
        profile.setFillTeammates(config.getBoolean("bots.fill-teammates", true));
        profile.setGroupInOneTeam(config.getBoolean("bots.group-in-one-team", true));
        profile.setTeam(config.getString("bots.team", ""));
        profile.setRespawnSeconds(config.getInt("bots.respawn-seconds", 10));
        profile.setSpeed(config.getDouble("bots.speed", 0.23));

        profile.setMode(BotMode.of(config.getString("bots.mode", "TACTICAL")));
        profile.setBridgeStyle(BridgeStyle.of(config.getString("bots.bridge.style", "DIAGONAL")));
        profile.setBlocksPerSecond(config.getDouble("bots.bridge.blocks-per-second", 2.0));
        profile.setCapacity(config.getInt("bots.bridge.capacity", 96));

        profile.setEngageDistance(config.getDouble("bots.combat.engage-distance", 12.0));
        profile.setAttackDistance(config.getDouble("bots.combat.attack-distance", 3.0));
        profile.setStrafe(config.getDouble("bots.combat.strafe", 0.5));
        profile.setKnockback(config.getDouble("bots.combat.knockback", 0.45));
        profile.setCircleStrafe(config.getBoolean("bots.combat.circle-strafe", true));
        profile.setLeap(config.getBoolean("bots.combat.leap", true));
        profile.setBlockHit(config.getBoolean("bots.combat.block-hit", true));
        profile.setRetreatHealth(config.getDouble("bots.combat.retreat-health", 7.0));
        profile.setMaxHealth(config.getDouble("bots.combat.max-health", 20.0));
        profile.setAttackDamage(config.getDouble("bots.combat.attack-damage", 4.0));

        profile.setGuardBed(config.getBoolean("bots.strategy.guard-bed", true));
        profile.setAttackBeds(config.getBoolean("bots.strategy.attack-beds", true));
        profile.setBuyUpgrades(config.getBoolean("bots.strategy.buy-upgrades", true));
        profile.setBedBreakSeconds(config.getDouble("bots.strategy.bed-break-seconds", 2.0));

        profile.setIronPerSecond(config.getInt("bots.economy.iron-per-second", 1));
        profile.setGoldPerSecond(config.getDouble("bots.economy.gold-per-second", 0.2));
        profile.setWoolCostIron(config.getInt("bots.economy.wool-cost-iron", 4));
        profile.setWoolBlocks(config.getInt("bots.economy.wool-blocks", 16));

        profile.setKillsInTop(config.getBoolean("bots.credit.kills-in-top", false));
        profile.setGiveXp(config.getBoolean("bots.credit.xp", false));

        List<String> names = config.getStringList("bots.names");

        if (!names.isEmpty()) {
            profile.getNames().clear();
            profile.getNames().addAll(names);
        }

        profile.setSuffix(config.getString("bots.suffix", "§8[§7BOT§8]"));

        return profile;
    }

    /** Tiques entre dois ciclos de ponte: converte blocos/segundo em cooldown. */
    public int bridgeCooldown() {
        double rate = Math.max(0.5, Math.min(8.0, blocksPerSecond)) * bridgeStyle.getSpeedFactor();

        return (int) Math.max(2L, Math.round(20.0 / rate));
    }

    public String nextName(int index) {
        return names.get(Math.floorMod(index, names.size()));
    }
}
