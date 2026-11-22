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

public class Lightning extends FinalKillCollectible {

    public Lightning() {
        super(CollectibleRarity.RARE, "Raio", Item.of(Material.BLAZE_ROD), 1762200000010L);

        setLore("§7Um raio fulmina o jogador", "§7eliminado!");
        setRanks(RankType.VIP);
    }

    @Override
    public void execute(Location location, String victim) {
        Location center = location.clone();

        center.getWorld().strikeLightningEffect(center);

        BedFx.animate(12, tick -> {
            Location point = center.clone().add(0, 0.2, 0);
            double radius = tick * 0.3;

            for (int i = 0; i < 20; i++) {
                double angle = 2 * Math.PI * i / 20;

                BedFx.particle(ParticleEffect.FLAME, point.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius));
            }

            if (tick == 0)
                BedFx.burst(ParticleEffect.FIREWORKS_SPARK, center.clone().add(0, 1, 0), 40, 0.25F);
        });
    }
}
