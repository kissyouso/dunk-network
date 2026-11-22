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
 * Respingos vermelhos espirram do eliminado.
 */
public class Blood extends FinalKillCollectible {

    public Blood() {
        super(CollectibleRarity.COMUM, "Sangue", Item.of(Material.INK_SACK, 1), 1762200000015L);

        setLore("§7Respingos vermelhos para", "§7todo lado!");
    }

    @Override
    public void execute(Location location, String victim) {
        Location center = location.clone().add(0, 1, 0);

        BedFx.sound(center, Sound.HURT_FLESH, 1.5F, 0.6F);

        BedFx.animate(14, tick -> {
            for (int i = 0; i < 18; i++) {
                double theta = Math.random() * Math.PI * 2;
                double distance = tick * 0.12 + Math.random() * 0.3;
                double drop = tick * tick * 0.008;

                BedFx.color(center.clone().add(Math.cos(theta) * distance, 0.3 - drop + (Math.random() - 0.5) * 0.4, Math.sin(theta) * distance),
                        140 + (int) (Math.random() * 80), 0, 0);
            }
        });
    }
}
