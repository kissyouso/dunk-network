package com.minecraft.core.bukkit.menu.account.preference;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.toggle.Toggle;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.manager.list.TagManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class PreferenceMenu extends Menu {

    private static final int[] ICON_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final Account account;
    private Category category = Category.GENERAL;

    public PreferenceMenu(Player player, Menu last) {
        super(player, "Preferências", last, 6);

        this.account = Core.getAccountController().of(player.getUniqueId());
    }

    public static PreferenceMenu createStaffMenu(Player player, Menu last) {
        PreferenceMenu menu = new PreferenceMenu(player, last);
        menu.category = Category.STAFF;
        return menu;
    }

    @Override
    public void handle() {
        clear();

        for (Category category : Category.values()) {
            if (category == Category.STAFF && !account.isStaffer()) continue;

            boolean selected = this.category == category;

            addItem(category.getSlot(), Item.of(category.getIcon(), "§a" + category.getName(),
                            selected ? "§aSelecionado!" : "§eClique para selecionar!")
                    .flags(ItemFlag.values())
                    .click(event -> {
                        if (selected) return;

                        this.category = category;
                        sound(MenuSound.PAGINATED);
                        handle();
                    }));
        }

        Toggle toggle = account.getToggle();
        int index = 0;

        switch (category) {
            case GENERAL: {
                index = add(index, Material.BOOK, "Bate-papo", toggle, Toggle::isAllowChat, t -> t.setAllowChat(!t.isAllowChat()),
                        "§7Mostrar o bate-papo", "§7geral.");
                index = add(index, Material.SIGN, "Mensagens privadas", toggle, Toggle::isAllowMessages, t -> t.setAllowMessages(!t.isAllowMessages()),
                        "§7Defina quem pode te", "§7enviar mensagens privadas.");
                index = add(index, Material.PAPER, "Informações extra", toggle, Toggle::isExtraInfo, t -> t.setExtraInfo(!t.isExtraInfo()),
                        "§7Visualizar informações", "§7extras como o tempo de", "§7recarga de kits e vida", "§7regenerada ao usar sopa.");
                index = add(index, Material.BED, "Confirmar voltar para o lobby", toggle, Toggle::isConfirmToHub, t -> t.setConfirmToHub(!t.isConfirmToHub()));
                index = add(index, Material.HOPPER, "Visibilidade de estatísticas", toggle, Toggle::isAllowStatsView, t -> t.setAllowStatsView(!t.isAllowStatsView()),
                        "§7Permitir outros jogadores", "§7visualizarem suas estatísticas", "§7utilizando o /stats");
                addBlockSlot(index);
                break;
            }
            case SOCIAL: {
                index = add(index, Material.BOOK, "Convite para party", toggle, Toggle::isAllowPartyInvite, t -> t.setAllowPartyInvite(!t.isAllowPartyInvite()),
                        "§7Permitir receber convites", "§7de party.");
                index = add(index, Material.ITEM_FRAME, "Convite para clan", toggle, Toggle::isAllowClanInvite, t -> t.setAllowClanInvite(!t.isAllowClanInvite()),
                        "§7Permitir receber convites", "§7de clan.");
                add(index, Material.COOKIE, "Receber pedidos de amizade", toggle, Toggle::isAllowFriendRequests, t -> t.setAllowFriendRequests(!t.isAllowFriendRequests()),
                        "§7Receber pedidos de amizade.");
                break;
            }
            case VIP: {
                if (account.isVIP()) {
                    index = add(index, Material.BOOK_AND_QUILL, "Mensagem de entrada", toggle, Toggle::isAllowJoinMessage, t -> t.setAllowJoinMessage(!t.isAllowJoinMessage()),
                            "§7Anunciar sua entrada", "§7nos lobbies.");
                }

                if (account.hasClan()) {
                    add(index, Material.NAME_TAG, "Tag do clan", toggle, Toggle::isAllowClanTag, t -> {
                        t.setAllowClanTag(!t.isAllowClanTag());
                        TagManager.updateTag(account);
                    }, "§7Mostrar a tag do seu", "§7clan no nickname.");
                }
                break;
            }
            case STAFF: {
                index = add(index, Material.PAPER, "Staffchat", toggle, Toggle::isStaffChat, t -> t.setStaffChat(!t.isStaffChat()),
                        "§7Receber as mensagens", "§7do staffchat.");
                index = add(index, Material.NETHER_STAR, "Auto vanish", toggle, Toggle::isAutoVanish, t -> t.setAutoVanish(!t.isAutoVanish()),
                        "§7Entrar invisível", "§7automaticamente.");
                index = add(index, Material.DIAMOND_SWORD, "Alertas do anticheat", toggle, Toggle::isAnticheatAlerts, t -> t.setAnticheatAlerts(!t.isAnticheatAlerts()),
                        "§7Receber as flags do", "§7anticheat no chat.", "§7Atalho: /ac");
                add(index, Material.CHEST, "Logs", toggle, Toggle::isAllowLogs, t -> t.setAllowLogs(!t.isAllowLogs()),
                        "§7Receber as logs gerais", "§7do servidor.");
                break;
            }
        }

        if (isReturnable())
            addBackButton(49);

        display();
    }

    private int add(int index, Material icon, String name, Toggle toggle, Predicate<Toggle> getter, Consumer<Toggle> switcher, String... lore) {
        if (index >= ICON_SLOTS.length) return index;

        int slot = ICON_SLOTS[index];
        boolean enabled = getter.test(toggle);

        Consumer<org.bukkit.event.inventory.InventoryClickEvent> click = event -> {
            switcher.accept(toggle);
            account.setToggle(toggle);

            sound(MenuSound.SUCCESS);
            handle();
        };

        addItem(slot, Item.of(icon, "§a" + name, lore)
                .flags(ItemFlag.values())
                .click(click::accept));

        addItem(slot + 9, preferenceDye(enabled, name, lore)
                .click(click::accept));

        return index + 1;
    }

    private void addBlockSlot(int index) {
        if (index >= ICON_SLOTS.length) return;

        Toggle toggle = account.getToggle();

        int hotbar = Math.max(0, Math.min(8, toggle.getBlockSlot()));
        int slot = ICON_SLOTS[index];

        Consumer<org.bukkit.event.inventory.InventoryClickEvent> click = event -> {
            int next = toggle.getBlockSlot() + 1;

            if (next > 8)
                next = 0;

            toggle.setBlockSlot(next);
            account.setToggle(toggle);

            sound(MenuSound.SUCCESS);
            handle();
        };

        addItem(slot, Item.of(Material.WOOL, hotbar, "§aSlot dos blocos",
                        "§7Os blocos comprados na loja",
                        "§7vão para a tecla §e" + (hotbar + 1) + "§7.",
                        "",
                        "§eClique para escolher outro slot!")
                .flags(ItemFlag.values())
                .click(click::accept));
    }

    @Getter
    @AllArgsConstructor
    private enum Category {

        GENERAL("Gerais", Material.COMPASS, 17),
        SOCIAL("Social", Material.COOKIE, 26),
        VIP("VIPs", Material.EMERALD, 35),
        STAFF("Staff", Material.NETHER_STAR, 44);

        private final String name;
        private final Material icon;
        private final int slot;
    }
}
