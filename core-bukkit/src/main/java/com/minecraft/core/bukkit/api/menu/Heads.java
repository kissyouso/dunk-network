package com.minecraft.core.bukkit.api.menu;

import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Cabeças com textura usadas nos menus.
 */
public final class Heads {

    /* Seta de madeira para a esquerda (Voltar / Página anterior) */
    public static final String ARROW_LEFT = "bd69e06e5dadfd84e5f3d1c21063f2553b2fa945ee1d4d7152fdc5425bc12a9";

    /* Seta de madeira para a direita (Próxima página) */
    public static final String ARROW_RIGHT = "19bf3292e126a105b54eba713aa1b152d541a1d8938829c56364d178ed22bf";

    /* Ponto de interrogação preto (skin desconhecida) */
    public static final String QUESTION = "badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a";

    /* Aldeão (vendedores do BedWars) */
    public static final String VILLAGER = "822d8e751c8f2fd4c8942c44bdb2f5ca4d8ae8e575ed3eb34c18a86e93b";

    private Heads() {
    }

    public static Item url(String url) {
        return Item.of(Material.SKULL_ITEM, 3).skullByUrl(url);
    }

    public static Item back() {
        return url(ARROW_LEFT);
    }

    public static Item next() {
        return url(ARROW_RIGHT);
    }

    /**
     * Cabeça com a skin real do jogador (lida do perfil que o servidor está usando).
     */
    public static Item of(Player player) {
        Item item = Item.of(Material.SKULL_ITEM, 3);

        if (player != null) {
            Collection<Property> textures = ((CraftPlayer) player).getProfile().getProperties().get("textures");

            if (textures != null && !textures.isEmpty())
                return item.skullByBase64(textures.iterator().next().getValue());

            return item.skullByName(player.getName());
        }

        return url(QUESTION);
    }

    public static Item of(Account account) {
        if (account == null)
            return url(QUESTION);

        Player player = account.player();

        if (player != null && player.isOnline())
            return of(player);

        if (account.getSkin() != null && account.getSkin().getValue() != null && !account.getSkin().getValue().isEmpty())
            return Item.of(Material.SKULL_ITEM, 3).skullByBase64(account.getSkin().getValue());

        return Item.of(Material.SKULL_ITEM, 3).skullByName(account.getName());
    }
}
