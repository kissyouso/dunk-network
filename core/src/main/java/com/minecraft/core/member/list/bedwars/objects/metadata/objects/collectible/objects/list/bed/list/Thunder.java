package com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.bed.list;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.collectible.rarity.CollectibleRarity;
import com.minecraft.core.api.collectible.util.particle.ParticleEffect;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.effect.BedFx;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.bed.BedDestroyCollectible;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * Três raios caem em volta da cama e uma onda de choque elétrica se espalha pelo chão.
 */
public class Thunder extends BedDestroyCollectible {

    public Thunder() {
        super(CollectibleRarity.EPIC, "Trovão", Item.of(Material.BLAZE_ROD), 1762200000001L);

        setLore("§7Raios caem do céu e uma onda", "§7elétrica toma conta do chão!");
        setRanks(RankType.VIP);
    }

    @Override
    public void execute(Location location) {
        Location center = location.clone().add(0.5, 0.1, 0.5);

        BedFx.animate(45, tick -> {
            if (tick == 0 || tick == 8) {
                double angle = Math.random() * Math.PI * 2;

                center.getWorld().strikeLightningEffect(center.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2));
            }

            if (tick == 16) {
                center.getWorld().strikeLightningEffect(center);
                BedFx.burst(ParticleEffect.FIREWORKS_SPARK, center.clone().add(0, 0.5, 0), 50, 0.3F);
            }

            /* Onda de choque */
            if (tick >= 16) {
                double radius = (tick - 16) * 0.22;
                int points = 16 + (tick - 16) * 2;

                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);

                    BedFx.particle(ParticleEffect.CRIT_MAGIC, point);

                    if (i % 4 == 0)
                        BedFx.color(point.clone().add(0, 0.2, 0), 120, 200, 255);
                }
            }
        });
    }
}
