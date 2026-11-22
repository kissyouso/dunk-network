package com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.kill.list;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.collectible.rarity.CollectibleRarity;
import com.minecraft.core.api.collectible.util.particle.ParticleEffect;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.effect.BedFx;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.kill.FinalKillCollectible;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * Um coração vermelho aparece sobre o jogador e se parte ao meio, caindo.
 */
public class BrokenHeart extends FinalKillCollectible {

    public BrokenHeart() {
        super(CollectibleRarity.EPIC, "Coração Partido", Item.of(Material.REDSTONE), 1762200000011L);

        setLore("§7Um coração se parte em dois", "§7sobre o eliminado.");
        setRanks(RankType.VIP);
    }

    @Override
    public void execute(Location location, String victim) {
        Location center = location.clone().add(0, 2.2, 0);

        /* Direção lateral (perpendicular a quem olha de frente) */
        double yaw = Math.toRadians(location.getYaw());
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw));

        BedFx.sound(center, Sound.ORB_PICKUP, 1.5F, 0.5F);

        BedFx.animate(40, tick -> {
            double split = tick < 15 ? 0 : (tick - 15) * 0.05;
            double fall = tick < 15 ? 0 : Math.pow(tick - 15, 2) * 0.004;

            if (tick == 15)
                BedFx.sound(center, Sound.GLASS, 1.5F, 0.8F);

            for (double t = 0; t < Math.PI * 2; t += 0.13) {
                /* Curva do coração */
                double x = 16 * Math.pow(Math.sin(t), 3) / 22;
                double y = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 22;

                double offset = x >= 0 ? split : -split;
                Vector horizontal = side.clone().multiply(x + offset);

                BedFx.color(center.clone().add(horizontal).add(0, y - fall, 0), 230, 20, 40);
            }

            if (tick % 5 == 0)
                BedFx.particle(ParticleEffect.HEART, center, 0.6F, 0F, 1);
        });
    }
}
