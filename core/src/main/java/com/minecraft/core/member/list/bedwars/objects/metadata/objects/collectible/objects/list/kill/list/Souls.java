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
 * A alma do eliminado sobe ao céu numa espiral dupla roxa e some num clarão.
 */
public class Souls extends FinalKillCollectible {

    public Souls() {
        super(CollectibleRarity.MYTHICAL, "Ascensão", Item.of(Material.NETHER_STAR), 1762200000014L);

        setLore("§7A alma do eliminado sobe", "§7aos céus numa espiral.");
        setRanks(RankType.DUNK_PLUS_1);
    }

    @Override
    public void execute(Location location, String victim) {
        Location center = location.clone();

        BedFx.sound(center, Sound.ENDERMAN_TELEPORT, 1.5F, 0.5F);

        BedFx.animate(45, tick -> {
            for (int strand = 0; strand < 2; strand++) {
                double angle = tick * 0.35 + strand * Math.PI;
                double radius = 0.7 - tick * 0.01;
                Location point = center.clone().add(Math.cos(angle) * radius, tick * 0.09, Math.sin(angle) * radius);

                BedFx.color(point, strand == 0 ? 170 : 90, 40, 255);
                BedFx.particle(ParticleEffect.ENCHANTMENT_TABLE, point, 0.05F, 0F, 2);
            }

            if (tick == 44) {
                Location top = center.clone().add(0, 4, 0);

                BedFx.burst(ParticleEffect.CLOUD, top, 30, 0.12F);
                BedFx.particle(ParticleEffect.SPELL_WITCH, top, 0.4F, 0F, 20);
                BedFx.sound(top, Sound.ORB_PICKUP, 1.5F, 1.8F);
            }
        });
    }
}
