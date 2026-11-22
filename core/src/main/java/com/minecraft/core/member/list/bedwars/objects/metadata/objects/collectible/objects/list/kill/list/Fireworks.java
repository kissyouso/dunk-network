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
 * Três explosões de fogos coloridos em sequência sobre o eliminado.
 */
public class Fireworks extends FinalKillCollectible {

    public Fireworks() {
        super(CollectibleRarity.RARE, "Fogos", Item.of(Material.FIREWORK), 1762200000013L);

        setLore("§7Comemore com fogos de", "§7artifício coloridos!");
        setRanks(RankType.VIP);
    }

    @Override
    public void execute(Location location, String victim) {
        Location center = location.clone().add(0, 2.5, 0);

        BedFx.animate(36, tick -> {
            if (tick % 12 != 0) return;

            Location blast = center.clone().add((Math.random() - 0.5) * 2, Math.random(), (Math.random() - 0.5) * 2);
            float hue = (float) Math.random();

            BedFx.burst(ParticleEffect.FIREWORKS_SPARK, blast, 50, 0.22F);

            for (int i = 0; i < 40; i++) {
                double theta = Math.random() * Math.PI * 2, phi = Math.acos(2 * Math.random() - 1);
                double radius = 1.2;

                BedFx.hue(blast.clone().add(Math.sin(phi) * Math.cos(theta) * radius, Math.cos(phi) * radius, Math.sin(phi) * Math.sin(theta) * radius),
                        (hue + (float) Math.random() * 0.15F) % 1F);
            }

            BedFx.sound(blast, tick == 24 ? Sound.FIREWORK_LARGE_BLAST : Sound.FIREWORK_BLAST, 2F, 1F);
        });
    }
}
