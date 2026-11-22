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
 * A cama vira um vulcão: estrondo, coluna de fumaça e uma erupção de lava e fogo.
 */
public class Volcano extends BedDestroyCollectible {

    public Volcano() {
        super(CollectibleRarity.EPIC, "Vulcão", Item.of(Material.LAVA_BUCKET), 1762200000002L);

        setLore("§7A cama entra em erupção e", "§7cospe lava para todo lado!");
        setRanks(RankType.DUNK);
    }

    @Override
    public void execute(Location location) {
        Location center = location.clone().add(0.5, 0.6, 0.5);

        BedFx.sound(center, Sound.EXPLODE, 1.5F, 0.6F);

        BedFx.animate(55, tick -> {
            if (tick % 4 == 0)
                BedFx.sound(center, Sound.LAVA_POP, 1.5F, 0.6F + (float) Math.random() * 0.6F);

            if (tick < 45) {
                BedFx.particle(ParticleEffect.LAVA, center, 0.2F, 0F, 2);

                for (int i = 0; i < 4; i++) {
                    Vector up = new Vector((Math.random() - 0.5) * 0.5, 1, (Math.random() - 0.5) * 0.5);

                    BedFx.shoot(ParticleEffect.FLAME, center, up, 0.25F + (float) Math.random() * 0.2F);
                }

                BedFx.particle(ParticleEffect.SMOKE_LARGE, center.clone().add(0, 1.5 + Math.random() * 2, 0), 0.3F, 0.02F, 2);
                BedFx.particle(ParticleEffect.DRIP_LAVA, center.clone().add(0, 2.5, 0), 1.2F, 0F, 3);
            }

            if (tick == 45) {
                BedFx.particle(ParticleEffect.EXPLOSION_LARGE, center.clone().add(0, 1, 0), 0.5F, 0F, 3);
                BedFx.burst(ParticleEffect.FLAME, center.clone().add(0, 1, 0), 60, 0.25F);
                BedFx.sound(center, Sound.EXPLODE, 2F, 1F);
            }
        });
    }
}
