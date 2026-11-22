package com.minecraft.core.member.list.bedwars.objects.enums;

import com.minecraft.core.api.item.Item;
import com.minecraft.core.member.list.bedwars.BedMember;
import com.minecraft.core.member.list.bedwars.objects.enums.item.BedWarsItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@AllArgsConstructor
public enum BedShop {

    /* Ícones: cabeças com textura (minecraft-heads.com) */
    FAVORITE("Compra Rápida", head("9169e85a8a4fca5c595a9d293f5ddf1068dc6db99f0700dd4d5b673865501a67")), // estrela
    BLOCKS("Blocos", head("1eb866eac8e40726cbf5059198ea3d28b7d36ccc5cffb5cc4fe7be40368532e5")), // lã colorida
    COMBAT("Combate", head("6a5361b52daf4f1c5c5480a39faaa10897595fa5763f757bdda3956588fec678")), // espadas cruzadas
    ARMOR("Armadura", head("25d922aa556524704573a582f9c5284bd9ffb52f49d1ea226652f2310b958f2c")), // elmo de cavaleiro
    TOOLS("Ferramentas", head("8362438ff4ecf8f4a2caa1277561c9513c9a986dbe38a80b9bafcbfed8b2a9c8")), // caixa de ferramentas
    ARTILLERY("Artilharia", head("86fcaefa19669d8be02cf5ba9a7f2cf6d27e636410496ffcfa62b03dceb9d378")), // alvo
    POTION("Poções", head("e18c989034104772edb9ef39f089f005c79cd7f56261a066594e9ab25c8817da")), // poção
    UTIL("Utilidades", head("1afb17eeb518768f06f75de33f03c365b7cf4823a90d088938fb553ab554ec3c")); // TNT

    private final String name;
    private final ItemStack icon;

    private static ItemStack head(String texture) {
        return new Item(Material.SKULL_ITEM, 1, 3).skullByUrl(texture);
    }

    public List<BedWarsItem> items() {
        return Arrays.stream(BedWarsItem.values())
                .filter(item -> item.getShop().equals(this))
                .collect(Collectors.toList());
    }

    public List<BedWarsItem> getItemsList(BedMember member) {
        Predicate<BedWarsItem> predicate = this == FAVORITE
                ? member::isFavoriteItem
                : item -> item.getShop().equals(this);

        return Stream.of(BedWarsItem.values())
                .filter(predicate)
                .collect(Collectors.toList());
    }
}
