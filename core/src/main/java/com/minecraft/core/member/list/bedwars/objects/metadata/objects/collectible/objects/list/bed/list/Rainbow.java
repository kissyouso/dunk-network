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
 * Um arco-íris é desenhado por cima da cama, com nuvens nas pontas e notas musicais.
 */
public class Rainbow extends BedDestroyCollectible {

    private static final int[][] COLORS = {
            {255, 0, 0}, {255, 127, 0}, {255, 255, 0}, {0, 255, 0}, {0, 140, 255}, {75, 0, 200}, {160, 0, 255}
    };

    public Rainbow() {
        super(CollectibleRarity.MYTHICAL, "Arco-Íris", Item.of(Material.WOOL, 14), 1762200000003L);

        setLore("§7Um arco-íris se forma sobre", "§7a cama destruída!");
        setRanks(RankType.DUNK_PLUS_1);
    }

    @Override
    public void execute(Location location) {
        Location center = location.clone().add(0.5, 0.3, 0.5);

        BedFx.animate(70, tick -> {
            double progress = Math.min(1, tick / 30D);

            if (tick < 30 && tick % 4 == 0)
                BedFx.sound(center, Sound.NOTE_PLING, 1.5F, 0.5F + tick / 20F);

            for (int band = 0; band < COLORS.length; band++) {
                double radius = 3.2 - band * 0.14;
                int[] color = COLORS[band];

                for (double angle = 0; angle <= Math.PI * progress; angle += 0.14) {
                    BedFx.color(center.clone().add(Math.cos(angle) * radius, Math.sin(angle) * radius, 0), color[0], color[1], color[2]);
                }
            }

            if (tick % 3 == 0) {
                BedFx.particle(ParticleEffect.CLOUD, center.clone().add(3, 0, 0), 0.3F, 0.01F, 3);
                BedFx.particle(ParticleEffect.CLOUD, center.clone().add(-3, 0, 0), 0.3F, 0.01F, 3);
            }

            if (tick == 30)
                BedFx.sound(center, Sound.LEVEL_UP, 1.5F, 1.4F);
        });
    }
}
