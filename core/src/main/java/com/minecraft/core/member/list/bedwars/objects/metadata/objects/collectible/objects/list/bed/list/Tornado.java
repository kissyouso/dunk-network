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
 * Um tornado de nuvens nasce na cama, cresce girando e se desfaz.
 */
public class Tornado extends BedDestroyCollectible {

    public Tornado() {
        super(CollectibleRarity.EPIC, "Tornado", Item.of(Material.FEATHER), 1762200000000L);

        setLore("§7Um tornado arranca a cama", "§7do chão e leva tudo embora!");
        setRanks(RankType.VIP);
    }

    @Override
    public void execute(Location location) {
        Location center = location.clone().add(0.5, 0, 0.5);

        BedFx.animate(60, tick -> {
            if (tick % 15 == 0)
                BedFx.sound(center, Sound.ENDERDRAGON_WINGS, 1.5F, 0.6F + tick * 0.01F);

            /* Cresce no começo e encolhe no final */
            double scale = Math.min(1, Math.min(tick / 15D, (60 - tick) / 15D));
            int layers = 14;

            for (int layer = 0; layer < layers; layer++) {
                double height = layer * 0.35 * scale;
                double radius = (0.2 + layer * 0.16) * scale;
                double angle = tick * 0.55 + layer * 0.45;

                for (int arm = 0; arm < 3; arm++) {
                    double a = angle + arm * (2 * Math.PI / 3);

                    BedFx.particle(ParticleEffect.CLOUD, center.clone().add(Math.cos(a) * radius, height, Math.sin(a) * radius));
                }
            }

            if (tick % 4 == 0)
                BedFx.particle(ParticleEffect.SMOKE_LARGE, center.clone().add(0, 0.3, 0), 0.4F, 0.02F, 3);
        });
    }
}
