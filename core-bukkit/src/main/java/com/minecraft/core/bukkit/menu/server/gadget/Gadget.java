package com.minecraft.core.bukkit.menu.server.gadget;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.menu.account.AccountMenu;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

/**
 * Engenhocas dos lobbies (exclusivas do Style+).
 */
@Getter
@AllArgsConstructor
public enum Gadget {

    SPEED("Velocidade", "Concede Velocidade V por 30 segundos", 60, player -> {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30 * 20, 4), true);
        player.playSound(player.getLocation(), Sound.FIZZ, 1f, 1.5f);
    }),

    SLINGSHOT("Estilingue", "Dá um impulso para frente e para cima", 30, player -> {
        Vector direction = player.getLocation().getDirection().normalize().multiply(2.2).setY(1.1);

        player.setVelocity(direction);
        player.playSound(player.getLocation(), Sound.ENDERDRAGON_WINGS, 1f, 1.2f);
    }),

    LIGHTNING("Raio", "Invoca um raio que atinge inimigos próximos", 30, player -> {
        Location target = player.getTargetBlock((java.util.Set<Material>) null, 20).getLocation().add(0.5, 1, 0.5);

        target.getWorld().strikeLightningEffect(target);

        for (Entity entity : target.getWorld().getNearbyEntities(target, 4, 4, 4)) {
            if (!(entity instanceof Player) || entity.equals(player)) continue;

            Vector push = entity.getLocation().toVector().subtract(target.toVector()).normalize().multiply(1.2).setY(0.6);
            entity.setVelocity(push);
        }
    }),

    SMOKE_GRENADE("Granada de Fumaça", "Lança uma granada que cria fumaça e aplica cegueira", 45, player -> {
        org.bukkit.entity.Item grenade = player.getWorld().dropItem(player.getEyeLocation(), new ItemStack(Material.FIREWORK_CHARGE));

        grenade.setPickupDelay(Integer.MAX_VALUE);
        grenade.setVelocity(player.getLocation().getDirection().normalize().multiply(1.3));

        Core.getPlatform().runSync(() -> {
            Location location = grenade.getLocation();
            grenade.remove();

            World world = location.getWorld();

            for (int i = 0; i < 40; i++) {
                Location offset = location.clone().add(Core.RANDOM.nextDouble() * 4 - 2, Core.RANDOM.nextDouble() * 2, Core.RANDOM.nextDouble() * 4 - 2);
                world.playEffect(offset, Effect.LARGE_SMOKE, 0);
            }

            world.playSound(location, Sound.FIZZ, 1f, 0.6f);

            for (Entity entity : world.getNearbyEntities(location, 4, 3, 4)) {
                if (entity instanceof Player && !entity.equals(player))
                    ((Player) entity).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3 * 20, 0), true);
            }
        }, 30L);
    }),

    FIREWORKS("Lança Fogos", "Lança fogos de artifício no alto", 25, player -> {
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();

        Color[] colors = {Color.AQUA, Color.FUCHSIA, Color.LIME, Color.ORANGE, Color.YELLOW, Color.RED, Color.WHITE};

        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.values()[Core.RANDOM.nextInt(FireworkEffect.Type.values().length)])
                .withColor(colors[Core.RANDOM.nextInt(colors.length)])
                .withFade(colors[Core.RANDOM.nextInt(colors.length)])
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);

        firework.setFireworkMeta(meta);
    });

    public static final int HOTBAR_SLOT = 3;

    private final String name, description;
    private final int cooldown;
    private final Consumer<Player> action;

    public String getCooldownKey() {
        return "gadget." + name().toLowerCase();
    }

    public boolean has(Account account) {
        return AccountMenu.isDunkPlus(account);
    }

    public Item createItem() {
        return Item.of(Material.INK_SACK, 10, "§a" + name + " §7(Clique direito)",
                "§7" + description,
                "",
                "§7Cooldown: §f" + cooldown + "s");
    }

    public void use(Player player, Account account) {
        if (!has(account)) {
            account.send("§cVocê não possui esse item.");
            return;
        }

        if (account.hasCooldown(getCooldownKey())) {
            account.send("§cAguarde " + account.getFormattedCooldown(getCooldownKey()) + " para usar " + name + " novamente.");
            return;
        }

        account.setCooldown(getCooldownKey(), cooldown * 1000L);
        action.accept(player);
    }

    public void equip(Player player, Account account) {
        player.getInventory().setItem(HOTBAR_SLOT, createItem()
                .interact(event -> {
                    event.setCancelled(true);

                    if (event.getAction().name().contains("RIGHT"))
                        use(event.getPlayer(), Core.getAccountController().of(event.getPlayer().getUniqueId()));
                }));

        player.updateInventory();
        account.send("§aVocê equipou a engenhoca " + name + ".");
    }
}
