package com.minecraft.arcade.thebridge.menu;

import com.minecraft.arcade.thebridge.practice.PracticeCosmetics;
import com.minecraft.arcade.thebridge.user.TheBridgeUser;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class AnimationsMenu extends Menu {

    private final TheBridgeUser user;

    public AnimationsMenu(Player player, TheBridgeUser user, Menu last) {
        super(player, "Animações", last, 3, 7);

        this.user = user;
    }

    @Override
    public void handle() {
        clear();

        String selected = user.getMember().getAnimation();

        buildPageItems(PracticeCosmetics.ANIMATIONS, 10, (animation, slot) -> {
            boolean active = animation.getId().equalsIgnoreCase(selected);

            Item item = Item.of(animation.getIcon(),
                            (active ? "§a" : "§f") + animation.getName(),
                            "§7Escolha o efeito ao completar a ponte.",
                            "",
                            active ? "§aSelecionado!" : "§bClique para selecionar!")
                    .flags(ItemFlag.values());

            if (active)
                item.enchantment(Enchantment.DURABILITY, 1);

            item.click(event -> {
                user.getMember().setAnimation(animation.getId());
                user.getMember().savePractice();

                sound(MenuSound.DONE);

                handle();
            });

            addItem(slot, item);
        });

        if (isReturnable())
            addBackButton();

        display();
    }
}
