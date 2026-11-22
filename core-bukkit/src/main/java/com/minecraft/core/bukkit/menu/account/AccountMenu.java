package com.minecraft.core.bukkit.menu.account;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.appearance.AppearanceMenu;
import com.minecraft.core.bukkit.menu.account.max.MaxResourcesMenu;
import com.minecraft.core.bukkit.menu.account.preference.PreferenceMenu;
import com.minecraft.core.bukkit.menu.account.social.SocialMenu;
import com.minecraft.core.bukkit.menu.account.stats.StatsMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AccountMenu extends Menu {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    private final Account account;

    public AccountMenu(Player player) {
        this(player, Core.getAccountController().of(player.getUniqueId()));
    }

    public AccountMenu(Player player, Account target) {
        super(player, "Perfil de: " + target.getName(), 3);

        this.account = target;
    }

    @Override
    public void handle() {
        clear();

        Tag tag = account.getDefaultTag();

        addItem(10, Heads.of(account)
                .name("§f" + account.getName())
                .lore("§fGrupo: " + (tag == Tag.MEMBER ? "§7Membro" : account.formatTag(tag, account.getTagPrefix()).trim()),
                        "§fTipo da conta: " + (account.isPremium() ? "§aOriginal" : "§cPirata"),
                        "",
                        "§fPrimeiro acesso: §7" + DATE_FORMAT.format(new Date(account.getContext().getCreatedAt())),
                        "§fÚltimo acesso: §7" + DATE_FORMAT.format(new Date(account.getContext().getLastLogin())),
                        "",
                        "§fTempo online: §a" + formatTime(account.getOnlineTime())));

        addItem(12, Item.of(Material.INK_SACK, 2, "§6Recursos Style§e+",
                        "§7Acesse diversos recursos",
                        "§7da sua assinatura §6Style§e+§7.",
                        "",
                        "§7Prévia: " + account.formatTag(account.getPlusTag(), com.minecraft.core.account.context.objects.tag.prefix.TagPrefix.DEFAULT) + account.getName(),
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new MaxResourcesMenu(getPlayer(), this).handle();
                }));

        addItem(13, Item.of(Material.CAKE, "§aSocial",
                        "§7Acesse Party, Clan e Amigos",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new SocialMenu(getPlayer(), this).handle();
                }));

        addItem(14, Item.of(Material.ITEM_FRAME, "§aAparência",
                        "§7Personalize sua skin e medalhas",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new AppearanceMenu(getPlayer(), this).handle();
                }));

        addItem(15, Item.of(Material.REDSTONE_COMPARATOR, "§aPreferências",
                        "§7Defina suas preferências para ter",
                        "§7a melhor experiência possível no",
                        "§7servidor!",
                        "",
                        "§eClique para acessar!")
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new PreferenceMenu(getPlayer(), this).handle();
                }));

        addItem(16, Item.of(Material.PAPER, "§aEstatísticas",
                        "§7Explore todo o seu desempenho em",
                        "§7todas nossas modalidades de jogo.",
                        "",
                        "§eClique para acessar!")
                .flags(ItemFlag.values())
                .click(event -> {
                    sound(MenuSound.PAGINATED);
                    new StatsMenu(getPlayer(), account, this).handle();
                }));

        display();
    }

    public static boolean isDunkPlus(Account account) {
        return account.isDunkPlus();
    }

    public static String formatTime(long millis) {
        long seconds = millis / 1000;

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder builder = new StringBuilder();

        if (days > 0) builder.append(days).append("d ");
        if (hours > 0) builder.append(hours).append("h ");
        if (minutes > 0) builder.append(minutes).append("m ");
        if (secs > 0 || builder.length() == 0) builder.append(secs).append("s");

        return builder.toString().trim();
    }
}
