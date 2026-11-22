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
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

/**
 * Uma lápide sobe do chão com "R.I.P" e o nick do eliminado, e afunda de novo.
 */
public class Tombstone extends FinalKillCollectible {

    public Tombstone() {
        super(CollectibleRarity.EPIC, "Lápide", Item.of(Material.COBBLE_WALL), 1762200000012L);

        setLore("§7Uma lápide com o nome do", "§7eliminado surge do chão.");
        setRanks(RankType.DUNK);
    }

    @Override
    public void execute(Location location, String victim) {
        Location base = location.getBlock().getLocation().add(0.5, 0, 0.5);
        base.setYaw(location.getYaw());

        ArmorStand stone = base.getWorld().spawn(base.clone().subtract(0, 1.6, 0), ArmorStand.class);

        stone.setVisible(false);
        stone.setGravity(false);
        stone.setBasePlate(false);
        stone.setHelmet(new ItemStack(Material.COBBLE_WALL));
        stone.setCustomName("§7✝ §fR.I.P §7" + victim + " §7✝");
        stone.setCustomNameVisible(true);

        BedFx.sound(base, Sound.ANVIL_LAND, 1F, 0.5F);

        BedFx.animate(80, tick -> {
            /* Sobe em 15 ticks, fica e afunda nos últimos 15 */
            double height = tick < 15 ? tick / 15D : tick > 65 ? (80 - tick) / 15D : 1;

            stone.teleport(base.clone().subtract(0, 1.6 - height * 1.1, 0));

            if (tick < 15 || tick > 65)
                BedFx.particle(ParticleEffect.SMOKE_NORMAL, base.clone().add(0, 0.1, 0), 0.3F, 0.01F, 4);

            if (tick % 10 == 0)
                BedFx.particle(ParticleEffect.SPELL_WITCH, base.clone().add(0, 1, 0), 0.3F, 0F, 3);
        }, stone::remove);
    }
}
