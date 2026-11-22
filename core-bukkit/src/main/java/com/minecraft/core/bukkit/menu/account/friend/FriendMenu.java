package com.minecraft.core.bukkit.menu.account.friend;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.friend.Friend;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.util.list.DateUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Setter
public class FriendMenu extends Menu {

    private FriendFilter filter;

    public FriendMenu(Player player, Menu last) {
        super(player, "Amigos", last, 6, 21);

        this.filter = FriendFilter.DATE;
    }

    @Override
    public void handle() {
        clear();

        Account account = Core.getAccountController().of(getPlayer().getUniqueId());
        List<Friend> list = filter.getAction().handle(account.getFriends());

        if (list.isEmpty()) {
            addItem(22, Item.of(Material.WEB, "§aNenhum amigo encontrado",
                    "§7Adicione jogadores para montar sua lista."));
        } else {
            buildPageItems(list, 46, 51, 10, (friend, slot) -> {
                Account target = Core.getAccountData().of(friend.getId());

                if (target == null) return;

                addItem(slot, Heads.of(target)
                        .name("§a" + target.getName())
                        .lore("§7Tipo: §f" + friend.getType().getName(),
                                "§7Amigos desde: §f" + DateUtil.getSimpleDate(friend.getStartedAt()),
                                "",
                                target.isOnline() ? "§aOnline" : "§cOffline"));
            });
        }

        addItem(47, Item.of(Material.ANVIL, "§aAdicionar amigo",
                        "§7Envia uma solicitação de amizade.")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new AnvilFriendMenu(getPlayer()).open();
                }));

        int requestSize = account.getFriendsRequests().size();

        addItem(49, Item.of(Material.PAPER, "§aSolicitações",
                        "§7Gerencie pedidos de amizade recebidos.")
                .amount(Math.max(1, Math.min(64, requestSize)))
                .click(event -> {
                    if (requestSize == 0) {
                        sound(MenuSound.ERROR);
                        account.send("§cVocê não possui solicitações de amizade pendentes.");
                    } else {
                        sound(MenuSound.PAGINATED);
                        new FriendRequestMenu(getPlayer(), this).handle();
                    }
                }));

        List<String> filterLore = new ArrayList<>(Arrays.asList("§7Alterna a ordenação da lista.", ""));

        for (FriendFilter filter : FriendFilter.values())
            filterLore.add((this.filter.equals(filter) ? "§a" + Constant.ARROW_ALT_SYMBOL + " " : "§7") + filter.getName());

        addItem(50, Item.of(Material.HOPPER, "§aFiltro", filterLore)
                .click(event -> {
                    setFilter(filter.next());

                    sound(MenuSound.PAGINATED);
                    handle();
                }));

        if (isReturnable())
            addBackButton(53);

        display();
    }

    @Getter
    @AllArgsConstructor
    public enum FriendFilter {

        DATE("Mais recentes", list -> list.stream().sorted(Comparator.comparing(Friend::getStartedAt)).collect(Collectors.toList())),
        A_Z("A-Z", list -> list.stream().sorted(Comparator.comparing(Friend::getName)).collect(Collectors.toList())),
        Z_A("Z-A", list -> list.stream().sorted(Comparator.comparing(Friend::getName).reversed()).collect(Collectors.toList()));

        private final String name;
        private final FriendFilterAction action;

        public interface FriendFilterAction {
            List<Friend> handle(List<Friend> list);
        }

        public FriendFilter next() {
            return this != Z_A ? values()[ordinal() + 1] : DATE;
        }
    }
}
