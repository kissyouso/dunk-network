package com.minecraft.core.bukkit.menu.account.skin;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.skin.Skin;
import com.minecraft.core.api.skin.objects.SkinType;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.skin.library.SkinLibraryCategoryMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

public class SkinMenu extends Menu {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public SkinMenu(Player player, Menu last) {
        super(player, "Sua skin", last, 6);
    }

    public static String getSkinName(Account account) {
        Skin skin = account.getSkin();

        if (skin == null || !skin.isValid() || skin.getDisplayName() == null)
            return "Unknown";

        return skin.getDisplayName();
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());
        Skin skin = account.getSkin();

        boolean valid = skin != null && skin.isValid();
        String type = !valid ? "Variada" : skin.getType() == SkinType.PROFILE ? "Original" : skin.getType() == SkinType.LIBRARY ? "Biblioteca" : "Variada";
        String updated = account.hasLastSkin() ? DATE_FORMAT.format(new Date(account.getSkinMetadata().getUpdatedAt())) : "Nunca";

        addItem(13, (valid ? Heads.of(getPlayer()) : Heads.url(Heads.QUESTION))
                .name("§aSuas informações")
                .lore("§fFonte: §7" + getSkinName(account),
                        "§fTipo de skin: §7" + type,
                        "",
                        "§fÚltima atualização: §7" + updated,
                        "",
                        "§fUse §e/skin <nick> §fpara usar",
                        "§fa skin de outro jogador."));

        addItem(29, Item.of(Material.BARRIER, "§cRestaurar",
                        "§7Volte para sua skin original.",
                        "",
                        "§eClique para restaurar.")
                .click(event -> {
                    sound(MenuSound.SUCCESS);
                    close();
                    getPlayer().chat("/skin reset");
                }));

        addItem(33, Item.of(Material.PAINTING, "§aBiblioteca de skins",
                        "§7Acesse a biblioteca de skins e",
                        "§7tenha acesso a diversos estilos.",
                        "",
                        "§eClique para ver.")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new SkinLibraryCategoryMenu(getPlayer(), this).handle();
                }));

        if (isReturnable())
            addBackButton(49);

        display();
    }
}
