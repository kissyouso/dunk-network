package com.minecraft.core.bukkit.menu.account.punish;

import com.minecraft.core.account.Account;
import com.minecraft.core.api.item.Item;
import com.minecraft.core.api.punishment.Punishment;
import com.minecraft.core.api.punishment.objects.enums.PunishmentCategory;
import com.minecraft.core.api.punishment.objects.enums.PunishmentPreset;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import com.minecraft.core.bukkit.menu.account.history.HistoryMenu;
import com.minecraft.core.util.list.TimeUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * /punir <jogador>: escolha entre banimento e mute.
 */
public class PunishMenu extends Menu {

    /* Cabeças do menu (todas da mesma série de ícones) */
    public static final String BAN = "efdc6d3bad396e7abc57ef56e84e016d016b862747b9f59b055ac20e4e3a0930"; // martelo
    public static final String MUTE = "1440b0acbf98c67c55f20bc19d9491de49f3c8a20de905e3fad5b3de8b4db7b8"; // balão de chat
    public static final String INFO = "cd91f51266eddc6207f12ae8d7a49c5db0415ada04dab92bb7686afdb17f4d4e"; // informação
    public static final String CONFIRM = "ce2a530f42726fa7a31efab8e43dadee188937cf824af88ea8e4c93a49c57294"; // ✔
    public static final String CANCEL = "e9cdb9af38cf41daa53bc8cda7665c509632d14e678f0f19f263f46e541d8a30"; // ✘
    public static final String LOCKED = "2ecb62c63b2575ec8db771c57c8b560515bb504190238a961e6e243ef5602ed4"; // cadeado

    private final Account viewer, target;

    public PunishMenu(Player player, Account viewer, Account target) {
        super(player, "Punir " + target.getName(), 5);

        this.viewer = viewer;
        this.target = target;
    }

    @Override
    public void handle() {
        clear();

        addItem(13, targetHead(target));

        addItem(29, category(PunishmentCategory.BAN, "§c", BAN, "§7Remove o jogador da rede", "§7pelo tempo do motivo."));
        addItem(33, category(PunishmentCategory.MUTE, "§6", MUTE, "§7Impede o jogador de falar", "§7no chat pelo tempo do motivo."));

        int bans = target.getPunishments(PunishmentCategory.BAN).size(),
                mutes = target.getPunishments(PunishmentCategory.MUTE).size();

        addItem(31, Heads.url(INFO).name("§fHistórico")
                .lore("§8Punições anteriores",
                        "",
                        "§7Banimentos: §f" + bans,
                        "§7Mutes: §f" + mutes,
                        "",
                        bans > 0 ? "§eClique esquerdo: §fbanimentos" : "§8Sem banimentos",
                        mutes > 0 ? "§eClique direito: §fmutes" : "§8Sem mutes")
                .click(event -> {
                    PunishmentCategory category = event.isRightClick() ? PunishmentCategory.MUTE : PunishmentCategory.BAN;

                    if (target.getPunishments(category).isEmpty()) {
                        sound(MenuSound.ERROR);
                        return;
                    }

                    sound(MenuSound.PAGINATED);
                    new HistoryMenu(getPlayer(), target, category).handle();
                }));

        addCloseButton();
        display();
    }

    private Item category(PunishmentCategory category, String color, String texture, String... description) {
        List<PunishmentPreset> presets = PunishmentPreset.of(category);
        long available = presets.stream().filter(preset -> viewer.hasRank(preset.getMinimumRank())).count();

        Punishment active = target.getActivePunishment(category);

        List<String> lore = new ArrayList<>();

        lore.add("§8" + category.getName());
        lore.add("");

        for (String line : description)
            lore.add(line);

        lore.add("");
        lore.add("§7Motivos: §f" + presets.size());
        lore.add("§7Disponíveis para você: §f" + available);
        lore.add("");

        if (active != null)
            lore.add("§cO jogador já tem um " + category.getName().toLowerCase() + " ativo.");
        else
            lore.add("§eClique para ver os motivos.");

        return Heads.url(texture).name(color + category.getName()).lore(lore).flags(ItemFlag.values()).click(event -> {
            if (active != null) {
                sound(MenuSound.ERROR);
                return;
            }

            sound(MenuSound.PAGINATED);
            new PunishReasonMenu(getPlayer(), viewer, target, category, this).handle();
        });
    }

    /**
     * Cabeça do jogador com o resumo da situação dele.
     */
    static Item targetHead(Account target) {
        List<String> lore = new ArrayList<>();

        lore.add("§8Jogador");
        lore.add("");
        lore.add("§7Cargo: " + target.getRankType().getColoredName());
        lore.add("");
        lore.add("§7Banimento: " + status(target.getActivePunishment(PunishmentCategory.BAN)));
        lore.add("§7Mute: " + status(target.getActivePunishment(PunishmentCategory.MUTE)));

        return Heads.of(target).name(target.getRankType().getColor() + target.getName()).lore(lore);
    }

    private static String status(Punishment punishment) {
        if (punishment == null) return "§aNenhum";

        return punishment.isTemporary()
                ? "§cAtivo §8(" + TimeUtil.formatTime(punishment.getExpiresAt(), TimeUtil.TimeFormat.SHORT) + ")"
                : "§cPermanente";
    }
}
