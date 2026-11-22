package com.minecraft.arcade.thebridge.arcade.arena;

import com.minecraft.arcade.thebridge.arcade.TheBridgeArcade;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.arcade.room.Room;
import com.minecraft.core.arcade.room.map.Map;
import com.minecraft.core.arcade.room.slot.Slot;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.firework.FireworkApi;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.core.member.list.thebridge.TheBridgeMember;
import com.minecraft.core.util.list.bukkit.BukkitUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TheBridgeArena extends Room {

    private volatile boolean ready;
    private final String worldName;

    private final java.util.Map<UUID, Set<Location>> placedBlocks = new ConcurrentHashMap<>();
    private final java.util.Map<UUID, SessionTime> sessionTimes = new ConcurrentHashMap<>();

    public TheBridgeArena(int id, TheBridgeArcade arcade, Map map, Slot slot) {
        super(id, arcade, map, slot);
        worldName = "tb-" + arcade.getMode().toLowerCase(java.util.Locale.ROOT) + "-" + id + "-" + UUID.randomUUID();
    }

    public TheBridgeArcade getArcade() {
        return (TheBridgeArcade) super.getArcade();
    }

    public boolean isReady() {
        return ready;
    }

    public void markReady() {
        this.ready = true;
    }

    public boolean isPractice() {
        return getArcade().isPractice();
    }

    public String getTemplateName() {
        return getArcade().getTemplate(getMap());
    }

    public String getWorldName() {
        return worldName;
    }

    @Override
    public World getWorld() {
        return org.bukkit.Bukkit.getWorld(worldName);
    }

    public void applyWorldSettings() {
        World world = getWorld();

        if (world == null) return;

        world.setTime(6000);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(Integer.MAX_VALUE);

        handleBorder();
    }

    @Override
    public void join(Player player) {
        TheBridgeUser user = (TheBridgeUser) UserModel.of(player.getUniqueId());

        if (user == null) return;

        Account account = user.getAccount();

        if (!ready || getWorld() == null) {
            if (account != null) {
                account.send("§cA sala ainda está carregando. Tente novamente em alguns segundos.");
                account.redirect(com.minecraft.core.server.type.ServerType.HUB);
            }
            return;
        }

        Join join = user.getJoin();

        getPlayers().add(player);

        BukkitUtil.resetMovement(player);
        player.setMaxHealth(20);
        BukkitUtil.resetPlayerState(player);
        player.getInventory().setArmorContents(null);
        player.getEnderChest().clear();
        player.setLevel(0);
        player.setExp(0F);

        if (account != null)
            account.setRoute(this, join);

        user.resetRun();

        player.teleport(getSpawn());

        getArcade().buildHotbar(player, user);
        getArcade().handleSidebar(user);

        user.spawnHologram();
        user.updateActionBar();
        user.updateHologram();
    }

    @Override
    public void quit(Player player) {
        getPlayers().remove(player);

        destroyBlocks(player.getUniqueId());
        placedBlocks.remove(player.getUniqueId());

        BukkitCore.getManager().getHologram().removeClients(player);

        if (!getPlayers().isEmpty()) return;

        sessionTimes.clear();

        if (!isPractice()) return;

        /* Sala privada vazia: espera alguns segundos (reconexao rapida) e
         * desliga o mundo. */
        Core.getPlatform().runSync(() -> {
            if (getPlayers().isEmpty())
                getArcade().removeRoom(this);
        }, 100L);
    }

    /* ------------------------------
            Tentativa / tempo
    ------------------------------ */

    public void startRun(TheBridgeUser user) {
        if (user.startRun()) {
            TheBridgeMember member = user.getMember();

            member.addAttempt();

            user.updateHologram();
            getArcade().updateSidebar(user);
        }
    }

    /**
     * Reinicia a tentativa (queda ou conclusao): limpa os blocos colocados,
     * devolve o jogador ao spawn e zera tempo/blocos/distancia.
     */
    public void resetAttempt(Player player) {
        TheBridgeUser user = (TheBridgeUser) UserModel.of(player.getUniqueId());

        if (user == null || user.isResetting()) return;

        user.setResetting(true);

        destroyBlocks(player.getUniqueId());

        Core.getPlatform().runSync(() -> {
            if (!player.isOnline() || user.getArena() != this) {
                user.setResetting(false);
                return;
            }

            player.setFallDistance(0F);
            player.setFireTicks(0);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);

            Location spawn = getSpawn();

            if (spawn != null)
                player.teleport(spawn);

            user.resetRun();
            user.setResetting(false);

            getArcade().buildHotbar(player, user);
            getArcade().updateSidebar(user);
            user.updateHologram();
        });
    }

    public void finishRun(Player player, TheBridgeUser user) {
        long time = user.finishRun();

        if (time <= 0) return;

        TheBridgeMember member = user.getMember();

        member.addCompletion(time);

        SessionTime current = sessionTimes.get(player.getUniqueId());

        if (current == null || time < current.time)
            sessionTimes.put(player.getUniqueId(), new SessionTime(player.getName(), time));

        playAnimation(player);

        Account account = user.getAccount();

        if (account != null) {
            account.title("§a§lPONTE COMPLETA!", "§e" + TheBridgeMember.formatTime(time));
            account.send("§a§lPONTE COMPLETA! §fSeu tempo: §e" + TheBridgeMember.formatTime(time));
        }

        getArcade().updateSidebar(user);
        user.updateHologram();

        Core.getPlatform().runSync(() -> {
            if (player.isOnline() && user.getArena() == this)
                resetAttempt(player);
        }, 60L);
    }

    private void playAnimation(Player player) {
        switch (userAnimation(player)) {
            case "RAIO": {
                Location finish = getFinish();

                if (finish != null && finish.getWorld() != null)
                    finish.getWorld().strikeLightningEffect(finish);

                break;
            }
            case "NENHUMA":
                break;
            default: {
                Location finish = getFinish();

                if (finish != null && finish.getWorld() != null)
                    FireworkApi.random(finish.clone().add(0, 1, 0));

                break;
            }
        }
    }

    private String userAnimation(Player player) {
        TheBridgeUser user = (TheBridgeUser) UserModel.of(player.getUniqueId());

        return user == null ? "FOGUETE" : user.getMember().getAnimation();
    }

    public boolean checkFinish(Player player, TheBridgeUser user) {
        if (!user.isRunning()) return false;

        Location finish = getFinish();

        if (finish == null || finish.getWorld() == null) return false;

        Location location = player.getLocation();

        if (!finish.getWorld().equals(location.getWorld())) return false;

        double dx = finish.getX() - location.getX(), dz = finish.getZ() - location.getZ();
        double radius = getFinishRadius();

        if ((dx * dx) + (dz * dz) > radius * radius) return false;

        if (Math.abs(finish.getY() - location.getY()) > 3.0) return false;

        finishRun(player, user);

        return true;
    }

    public void checkVoid(Player player, TheBridgeUser user) {
        if (user.isResetting()) return;

        if (player.getLocation().getY() < getVoidY())
            resetAttempt(player);
    }

    /* ------------------------------
            Blocos
    ------------------------------ */

    public void trackBlock(Player player, Block block) {
        placedBlocks.computeIfAbsent(player.getUniqueId(), key -> ConcurrentHashMap.newKeySet())
                .add(block.getLocation());
    }

    public boolean isTracked(Location location) {
        return placedBlocks.values().stream().anyMatch(blocks -> blocks.contains(location));
    }

    public void untrackBlock(Location location) {
        placedBlocks.values().forEach(blocks -> blocks.remove(location));
    }

    public void destroyBlocks(UUID id) {
        Set<Location> blocks = placedBlocks.remove(id);

        if (blocks == null) return;

        for (Location location : blocks) {
            if (location.getWorld() == null) continue;

            Block block = location.getBlock();

            if (block.getType() != Material.AIR)
                block.setType(Material.AIR);
        }
    }

    /* ------------------------------
            Locations / config
    ------------------------------ */

    public Location getSpawn() {
        return getLocation("spawn");
    }

    public Location getStatsLocation() {
        Location location = getLocation("stats");

        if (location != null) return location;

        Location spawn = getSpawn();

        return spawn == null ? null : spawn.clone().add(0, 2.4, 0);
    }

    public Location getFinish() {
        Location finish = getLocation("finish");

        if (finish != null) return finish;

        Location spawn = getSpawn();

        if (spawn == null) return null;

        double distance = getMap().getConfig().has("finish_distance")
                ? getMap().getConfig().get("finish_distance").getAsDouble()
                : 30.0;

        Vector direction = spawn.getDirection().setY(0);

        if (direction.lengthSquared() < 0.001)
            direction = new Vector(0, 0, 1);
        else
            direction.normalize();

        return spawn.clone().add(direction.multiply(distance));
    }

    public double getFinishRadius() {
        return getMap().getConfig().has("finish_radius")
                ? getMap().getConfig().get("finish_radius").getAsDouble()
                : 2.5;
    }

    public int getVoidY() {
        if (getMap().getConfig().has("void_y"))
            return getMap().getConfig().get("void_y").getAsInt();

        Location spawn = getSpawn();

        return spawn == null ? 0 : spawn.getBlockY() - 10;
    }

    /* ------------------------------
            Holograma / sessao
    ------------------------------ */

    public List<String> buildStatsLines(TheBridgeUser user) {
        TheBridgeMember member = user.getMember();

        return Arrays.asList(
                "§f§lSUAS INFORMAÇÕES",
                "§fMelhor tempo: §f" + member.getBestTimeFormat(),
                "§bTotal de Tentativas: §b" + member.getAttempts(),
                "§aTentativas completas: §a" + member.getCompletions()
        );
    }

    public void updateHologram(TheBridgeUser user) {
        HologramClient hologram = user.getHologram();

        if (hologram == null) return;

        hologram.setText(buildStatsLines(user));
    }

    public List<String> getSessionTopLines(int size) {
        List<SessionTime> sorted = new ArrayList<>(sessionTimes.values());

        sorted.sort((a, b) -> Long.compare(a.time, b.time));

        List<String> lines = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            if (i < sorted.size()) {
                SessionTime entry = sorted.get(i);

                lines.add("§f" + (i + 1) + ". §a" + entry.name + " §e" + TheBridgeMember.formatTime(entry.time));
            } else {
                lines.add("§7" + (i + 1) + ". §7-");
            }
        }

        return lines;
    }

    private static class SessionTime {

        private final String name;
        private final long time;

        private SessionTime(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }
}
