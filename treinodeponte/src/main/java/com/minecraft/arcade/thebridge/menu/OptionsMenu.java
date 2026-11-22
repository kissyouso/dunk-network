package com.minecraft.arcade.thebridge.menu;

import com.minecraft.arcade.thebridge.practice.BridgeBlocks;
import com.minecraft.arcade.thebridge.practice.PracticeCosmetics;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.member.list.thebridge.TheBridgeMember;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class OptionsMenu extends Menu {

    private final TheBridgeUser user;

    public OptionsMenu(Player player, TheBridgeUser user) {
        super(player, "Opções", 3);

        this.user = user;
    }

    @Override
    public void handle() {
        clear();

        TheBridgeMember member = user.getMember();

        addItem(13, Item.of(Material.FIREWORK, "§aAnimações §7(#0401)",
                        "§7Escolha o efeito ao completar a ponte.",
                        "",
                        "§fSelecionado: §e" + PracticeCosmetics.animationOf(member.getAnimation()).getName(),
                        "",
                        "§bClique para ver!")
                .click(event -> {
                    sound(MenuSound.SUCCESS);

                    new AnimationsMenu(getPlayer(), user, this).handle();
                }));

        BridgeBlocks.BridgeBlock block = user.getBlock();

        addItem(16, Item.of(block.getMaterial(), block.getData(),
                        "§aOpções de Bloco §7(" + block.id() + ")",
                        "§7Escolha o bloco usado para construir a ponte.",
                        "",
                        "§fSelecionado: §e" + block.display(),
                        "",
                        "§bClique para ver!")
                .click(event -> {
                    sound(MenuSound.SUCCESS);

                    new BlockSelectorMenu(getPlayer(), user, this).handle();
                }));

        display();
    }
}
