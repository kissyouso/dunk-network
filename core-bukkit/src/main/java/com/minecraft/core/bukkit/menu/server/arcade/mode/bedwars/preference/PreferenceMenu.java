package com.minecraft.core.bukkit.menu.server.arcade.mode.bedwars.preference;

import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.metadata.BedMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class PreferenceMenu extends Menu {

    private final BedMember member;

    public PreferenceMenu(Player player, BedMember member, Menu last) {
        super(player, "Preferências do BedWars", last, 3);

        this.member = member;
    }

    @Override
    public void handle() {
        clear();

        BedMetadata metadata = member.getMetadata();
        boolean enabled = metadata.isChestHologram();

        addItem(13, Item.of(Material.CHEST, "§aHolograma dos baús",
                        "§7Mostra o holograma",
                        "§7acima dos baús das ilhas.",
                        "",
                        "§7Status: " + (enabled ? "§aAtivado" : "§cDesativado"),
                        "",
                        enabled ? "§eClique para desativar." : "§eClique para ativar.")
                .click(event -> {
                    metadata.setChestHologram(!metadata.isChestHologram());
                    member.saveMetadata(metadata);

                    sound(MenuSound.SUCCESS);
                    handle();
                }));

        if (isReturnable())
            addBackButton(22);

        display();
    }
}
