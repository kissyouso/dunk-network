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
 * Foguete sobe girando com rastro de fogo e explode numa esfera de fagulhas e anéis coloridos.
 */
public class Rocket extends BedDestroyCollectible {

    public Rocket() {
        super(CollectibleRarity.RARE, "Foguetinho", Item.of(Material.FIREWORK), 1729792699944L);

        setLore("§7Um foguete sobe girando e", "§7explode em mil cores!");
        setRanks(RankType.VIP);
    }

    @Override
    public void execute(Location location) {
        Location center = location.clone().add(0.5, 0.6, 0.5);

        BedFx.sound(center, Sound.FIREWORK_LAUNCH, 2F, 0.8F);

        BedFx.animate(50, tick -> {
            if (tick < 24) {
                double angle = tick * 0.7;
                Location rocket = center.clone().add(Math.cos(angle) * 0.3, tick * 0.32, Math.sin(angle) * 0.3);

                BedFx.particle(ParticleEffect.FLAME, rocket, 0.02F, 0.01F, 2);
                BedFx.particle(ParticleEffect.SMOKE_NORMAL, rocket.clone().subtract(0, 0.2, 0), 0.03F, 0.01F, 2);
                BedFx.particle(ParticleEffect.FIREWORKS_SPARK, rocket, 0.05F, 0.02F, 1);
                return;
            }

            Location apex = center.clone().add(0, 24 * 0.32, 0);

            if (tick == 24) {
                BedFx.burst(ParticleEffect.FIREWORKS_SPARK, apex, 90, 0.38F);
                BedFx.particle(ParticleEffect.EXPLOSION_LARGE, apex);
                BedFx.sound(apex, Sound.FIREWORK_LARGE_BLAST, 3F, 1F);
            }

            if (tick == 29)
                BedFx.sound(apex, Sound.FIREWORK_TWINKLE, 3F, 1F);

            /* Anéis coloridos se abrindo e uma chuva de brilho caindo */
            int age = tick - 24;

            if (age < 14) {
                BedFx.ring(apex, 0.6 + age * 0.28, 28, age * 0.07F);
                BedFx.ring(apex.clone().add(0, 0.3, 0), 0.4 + age * 0.2, 20, 0.5F + age * 0.07F);
            }

            for (int i = 0; i < 6; i++) {
                double spread = 1.5 + age * 0.12;
                Location glitter = apex.clone().add((Math.random() - 0.5) * spread * 2, -age * 0.18 + (Math.random() - 0.5), (Math.random() - 0.5) * spread * 2);

                BedFx.hue(glitter, (float) Math.random());
            }
        });
    }
}
