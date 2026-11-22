package com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.bed.list;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.api.collectible.rarity.CollectibleRarity;
import com.minecraft.core.api.collectible.type.hat.HatList;
import com.minecraft.core.api.collectible.util.particle.ParticleEffect;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.effect.BedFx;
import com.minecraft.core.member.list.bedwars.objects.metadata.objects.collectible.objects.list.bed.BedDestroyCollectible;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;

/**
 * Seis fantasmas saem da cama girando em espiral, flutuando e subindo até sumirem numa nuvem.
 */
public class Ghost extends BedDestroyCollectible {

    private static final int GHOSTS = 6;

    public Ghost() {
        super(CollectibleRarity.MYTHICAL, "Fantasmas",
                Item.of(Material.SKULL_ITEM, 3).skullByBase64(HatList.FANTASMA.getCosmetic().getValue()),
                1729792699944L);

        setLore("§7Fantasmas saem da cama girando", "§7e somem assombrando todo mundo!");
        setRanks(RankType.BETA);
    }

    @Override
    public void execute(Location location) {
        World world = location.getWorld();
        Location center = location.clone().add(0.5, 0, 0.5);

        List<ArmorStand> ghosts = new ArrayList<>();

        for (int i = 0; i < GHOSTS; i++) {
            ArmorStand ghost = world.spawn(center, ArmorStand.class);

            ghost.setHelmet(getIcon());
            ghost.setSmall(true);
            ghost.setGravity(false);
            ghost.setVisible(false);
            ghost.setBasePlate(false);

            ghosts.add(ghost);
        }

        BedFx.sound(center, Sound.GHAST_MOAN, 2F, 0.7F);

        BedFx.animate(70, tick -> {
            if (tick == 35)
                BedFx.sound(center, Sound.GHAST_MOAN, 2F, 1.4F);

            for (int i = 0; i < ghosts.size(); i++) {
                ArmorStand ghost = ghosts.get(i);

                double angle = tick * 0.16 + i * (2 * Math.PI / GHOSTS);
                double radius = 0.8 + tick * 0.025;
                double height = tick * 0.045 + Math.sin(tick * 0.3 + i) * 0.25;

                Location point = center.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);

                /* Olhando na direção do giro */
                point.setYaw((float) Math.toDegrees(angle) + 180F);

                ghost.teleport(point);

                Location head = point.clone().add(0, 0.9, 0);

                if (tick % 2 == 0)
                    BedFx.particle(ParticleEffect.SPELL_WITCH, head, 0.1F, 0F, 1);

                BedFx.particle(ParticleEffect.CLOUD, head.clone().subtract(0, 0.3, 0), 0.05F, 0F, 1);
            }
        }, () -> {
            for (ArmorStand ghost : ghosts) {
                BedFx.particle(ParticleEffect.EXPLOSION_NORMAL, ghost.getLocation().add(0, 0.9, 0), 0.2F, 0.02F, 6);
                ghost.remove();
            }

            BedFx.sound(center, Sound.FIZZ, 1.5F, 1.2F);
        });
    }
}
