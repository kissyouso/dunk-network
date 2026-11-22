package com.minecraft.arcade.thebridge.user;

import com.minecraft.arcade.thebridge.arcade.TheBridgeArcade;
import com.minecraft.arcade.thebridge.arcade.arena.TheBridgeArena;
import com.minecraft.arcade.thebridge.practice.BridgeBlocks;
import com.minecraft.core.account.Account;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.bukkit.api.hologram.type.client.HologramClient;
import com.minecraft.core.bukkit.api.sidebar.Sidebar;
import com.minecraft.core.bukkit.user.UserModel;
import com.minecraft.core.member.list.thebridge.TheBridgeMember;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@Getter
@Setter
public class TheBridgeUser extends UserModel {

    private final TheBridgeMember member;
    private final TheBridgeArena arena;

    private HologramClient hologram;

    /* Estado da tentativa atual. volatile porque a sidebar/holograma sao
     * atualizados na task assincrona de 1 segundo enquanto os eventos rodam
     * na thread principal. */
    private volatile long startedAt;
    private volatile long lastTime;
    private volatile int blocks;
    private volatile int distance;

    private volatile boolean resetting;

    public TheBridgeUser(TheBridgeMember member, TheBridgeArena arena, Join join) {
        super(member.getId(), join);

        this.member = member;
        this.arena = arena;
    }

    @Override
    public void handle() {
        Account account = getAccount();

        if (account == null) return;

        Player player = account.player();

        if (player == null) return;

        setSidebar(new Sidebar(player, "TREINO DE PONTE"));

        arena.join(player);
    }

    public TheBridgeArcade getArcade() {
        return arena.getArcade();
    }

    public BridgeBlocks.BridgeBlock getBlock() {
        return BridgeBlocks.of(member.getSelectedBlock());
    }

    /* ------------------------------
            Tentativa
    ------------------------------ */

    public boolean isRunning() {
        return startedAt > 0;
    }

    public synchronized boolean startRun() {
        if (startedAt > 0) return false;

        startedAt = System.currentTimeMillis();
        lastTime = 0;
        blocks = 0;
        distance = 0;

        return true;
    }

    public synchronized long finishRun() {
        if (startedAt <= 0) return 0;

        long time = System.currentTimeMillis() - startedAt;

        lastTime = time;
        startedAt = 0;

        return time;
    }

    public synchronized void resetRun() {
        startedAt = 0;
        lastTime = 0;
        blocks = 0;
        distance = 0;
    }

    public void addBlock() {
        blocks += 1;
    }

    public void updateDistance(Location spawn, Location current) {
        if (spawn == null || current == null) return;

        double dx = spawn.getX() - current.getX();
        double dz = spawn.getZ() - current.getZ();

        distance = (int) Math.round(Math.sqrt((dx * dx) + (dz * dz)));
    }

    public String getCurrentTimeFormat() {
        if (startedAt > 0)
            return TheBridgeMember.formatTime(System.currentTimeMillis() - startedAt);

        return lastTime > 0 ? TheBridgeMember.formatTime(lastTime) : "--:--";
    }

    /* ------------------------------
            Visual
    ------------------------------ */

    public void spawnHologram() {
        if (hologram != null) {
            BukkitCore.getManager().getHologram().removeClient(hologram);
            hologram = null;
        }

        Player player = getPlayer();

        if (player == null) return;

        Location location = arena.getStatsLocation();

        if (location == null || location.getWorld() == null) return;

        hologram = BukkitCore.getManager().getHologram()
                .spawnClient(player, "tb_stats_" + player.getName().toLowerCase(), location);

        hologram.setText(arena.buildStatsLines(this));
    }

    public void updateHologram() {
        arena.updateHologram(this);
    }

    public void updateActionBar() {
        Player player = getPlayer();

        if (player == null) return;

        String text = "";

        switch (player.getInventory().getHeldItemSlot()) {
            case 3:
                text = "§aCosméticos";
                break;
            case 5:
                text = "§aOpções";
                break;
            case 6:
                text = "§cVoltar ao lobby";
                break;
            default:
                break;
        }

        if (text.isEmpty()) return;

        try {
            net.minecraft.server.v1_8_R3.IChatBaseComponent component =
                    net.minecraft.server.v1_8_R3.IChatBaseComponent.ChatSerializer.a(
                            net.md_5.bungee.chat.ComponentSerializer.toString(TextComponent.fromLegacyText(text)));

            ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) player).getHandle().playerConnection
                    .sendPacket(new net.minecraft.server.v1_8_R3.PacketPlayOutChat(component, (byte) 2));
        } catch (Exception ignored) {
        }
    }

    public Player getPlayer() {
        Account account = getAccount();

        return account == null ? null : account.player();
    }
}
