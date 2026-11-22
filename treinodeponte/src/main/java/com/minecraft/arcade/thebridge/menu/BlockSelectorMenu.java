package com.minecraft.arcade.thebridge.menu;

import com.minecraft.arcade.thebridge.TheBridge;
import com.minecraft.arcade.thebridge.practice.BridgeBlocks;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class BlockSelectorMenu extends Menu {

    private final TheBridgeUser user;

    public BlockSelectorMenu(Player player, TheBridgeUser user, Menu last) {
        super(player, "Opções de Bloco", last, 6, 28);

        this.user = user;
    }

    @Override
    public void handle() {
        clear();

        String selected = user.getMember().getSelectedBlock();

        buildPageItems(BridgeBlocks.all(), 10, (block, slot) -> {
            boolean active = block.key().equalsIgnoreCase(selected);

            Item item = Item.of(block.getMaterial(), block.getData(),
                            (active ? "§a" : "§f") + block.display(),
                            "§7ID: §e" + block.id(),
                            "",
                            active ? "§aSelecionado!" : "§bClique para selecionar!")
                    .flags(ItemFlag.values());

            if (active)
                item.enchantment(Enchantment.DURABILITY, 1);

            item.click(event -> {
                user.getMember().setSelectedBlock(block.key());
                user.getMember().savePractice();

                sound(MenuSound.DONE);

                close();

                Bukkit.getScheduler().runTask(TheBridge.getInstance(), () -> {
                    user.getArcade().buildHotbar(getPlayer(), user);

                    getPlayer().sendMessage("§aBloco selecionado: §e" + block.display() + " §7(" + block.id() + ")");
                });
            });

            addItem(slot, item);
        });

        if (isReturnable())
            addBackButton(45);

        display();
    }
}
