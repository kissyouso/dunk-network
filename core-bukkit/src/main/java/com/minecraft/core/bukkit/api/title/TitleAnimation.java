package com.minecraft.core.bukkit.api.title;

import com.minecraft.core.bukkit.BukkitCore;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.TimeUnit;

public class TitleAnimation {

    private static final String[] WIN_FRAMES = {
            "§e§lV§f§lITORIA",
            "§6§lV§e§lI§f§lTORIA",
            "§6§lVI§e§lT§f§lORIA",
            "§6§lVIT§e§lO§f§lRIA",
            "§6§lVITO§e§lR§f§lIA",
            "§6§lVITOR§e§lI§f§lA",
            "§6§lVITORI§e§lA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§f§lVITORIA",
            "§f§lVITORIA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§f§lVITORIA",
            "§f§lVITORIA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§f§lVITORIA",
            "§f§lVITORIA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§6§lVITORIA",
            "§f§lVITORIA",
            "§f§lVITORIA"
    }, DEFEAT_FRAMES = {
            "§c§lD§f§lERROTA",
            "§4§lD§c§lE§f§lRROTA",
            "§4§lDE§c§lR§f§lROTA",
            "§4§lDER§c§lR§f§lOTA",
            "§4§lDERR§c§lO§f§lTA",
            "§4§lDERRO§c§lT§f§lA",
            "§4§lDERROT§c§lA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§c§lDERROTA",
            "§c§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§c§lDERROTA",
            "§c§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§c§lDERROTA",
            "§c§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA",
            "§4§lDERROTA"
    };

    protected static void sendFrames(Player player, String[] FRAMES, String subTitle, long endAt) {
        new BukkitRunnable() {
            int frameIndex = 0;

            final long endTime = System.currentTimeMillis() + endAt;

            final World world = player.getWorld();

            @Override
            public void run() {
                if (endTime < System.currentTimeMillis() || !player.getWorld().equals(world)) {
                    cancel();
                    return;
                }

                PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
                connection.sendPacket(new PacketPlayOutTitle(0, 20, 60));
                if (FRAMES[frameIndex] != null)
                    connection.sendPacket(new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.TITLE, CraftChatMessage.fromString(FRAMES[frameIndex])[0]));
                if (subTitle != null)
                    connection.sendPacket(new PacketPlayOutTitle(PacketPlayOutTitle.EnumTitleAction.SUBTITLE, CraftChatMessage.fromString(subTitle)[0]));

                frameIndex++;

                if (frameIndex >= FRAMES.length)
                    frameIndex = 0;
            }
        }.runTaskTimer(BukkitCore.getInstance(), 0, 2);
    }

    public static void sendWinFrame(Player player, String subTitle, long endAt) {
        sendFrames(player, WIN_FRAMES, subTitle, endAt);
    }

    public static void sendWinFrame(Player player, String subTitle) {
        sendWinFrame(player, subTitle, TimeUnit.SECONDS.toMillis(15));
    }

    public static void sendDefeatFrame(Player player, String subTitle, long endAt) {
        sendFrames(player, DEFEAT_FRAMES, subTitle, endAt);
    }

    public static void sendDefeatFrame(Player player, String subTitle) {
        sendDefeatFrame(player, subTitle, TimeUnit.SECONDS.toMillis(15));
    }
}
