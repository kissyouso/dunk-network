package com.minecraft.core.bukkit.menu.server.gadget;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.FireworkEffectMeta;

public class GadgetMenu extends Menu {

    public GadgetMenu(Player player, Menu last) {
        super(player, "Engenhocas", last, 3);
    }

    public GadgetMenu(Player player) {
        this(player, null);
    }

    /**
     * Item da hotbar dos lobbies que abre este menu.
     */
    public static Item createHotbarItem() {
        Item item = Item.of(Material.FIREWORK_CHARGE, "§aEngenhocas",
                "§7Utilize engenhocas divertidas",
                "§7no lobby!",
                "",
                "§eClique para abrir!");

        FireworkEffectMeta meta = (FireworkEffectMeta) item.getItemMeta();
        meta.setEffect(FireworkEffect.builder().withColor(Color.WHITE).build());
        item.updateMeta(meta);

        return item.interact(event -> {
            event.setCancelled(true);
            new GadgetMenu(event.getPlayer()).handle();
        });
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());

        int slot = 11;

        for (Gadget gadget : Gadget.values()) {
            boolean owned = gadget.has(account);

            addItem(slot++, Item.of(Material.INK_SACK, owned ? 10 : 8,
                            (owned ? "§a" : "§c") + gadget.getName(),
                            "§7" + gadget.getDescription(),
                            "",
                            "§7Disponivel para Rank §6Style§e+",
                            "§7Cooldown: §f" + gadget.getCooldown() + "s",
                            "",
                            owned ? "§eClique para equipar!" : "§cVocê não possui esse item.")
                    .flags(ItemFlag.values())
                    .click(event -> {
                        if (!owned) {
                            sound(MenuSound.ERROR);
                            return;
                        }

                        sound(MenuSound.SUCCESS);
                        close();
                        gadget.equip(getPlayer(), account);
                    }));
        }

        if (isReturnable())
            addBackButton(22);

        display();
    }
}
