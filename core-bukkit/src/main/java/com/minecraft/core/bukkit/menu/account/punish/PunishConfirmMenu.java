package com.minecraft.core.bukkit.menu.account.punish;

import com.minecraft.core.account.Account;
import com.minecraft.core.api.punishment.objects.enums.PunishmentPreset;
import com.minecraft.core.backend.database.redis.message.types.account.AccountExecuteCommandMessage;
import com.minecraft.core.bukkit.api.menu.Heads;
import com.minecraft.core.bukkit.api.menu.Menu;
import com.minecraft.core.bukkit.api.menu.sound.MenuSound;
import org.bukkit.entity.Player;

/**
 * Confirmação final antes de aplicar a punição.
 */
public class PunishConfirmMenu extends Menu {

    private final Account viewer, target;
    private final PunishmentPreset preset;

    public PunishConfirmMenu(Player player, Account viewer, Account target, PunishmentPreset preset, Menu last) {
        super(player, "Confirmar punição", last, 3);

        this.viewer = viewer;
        this.target = target;
        this.preset = preset;
    }

    @Override
    public void handle() {
        clear();

        String color = PunishReasonMenu.color(preset.getCategory());

        addItem(13, Heads.of(target).name(target.getRankType().getColor() + target.getName())
                .lore("§8Resumo",
                        "",
                        "§7Punição: " + color + preset.getCategory().getName(),
                        "§7Motivo: §f" + preset.getName(),
                        "§7Duração: " + (preset.isPermanent() ? "§cPermanente" : "§f" + preset.getDurationName()),
                        "",
                        "§7Aplicada por: " + viewer.getRankType().getColor() + viewer.getName()));

        addItem(11, Heads.url(PunishMenu.CONFIRM).name("§aConfirmar")
                .lore("§7Aplica a punição agora.")
                .click(event -> {
                    close();
                    sound(MenuSound.DONE);

                    /* O proxy aplica (mesma validação, desconexão e logs do /punir manual) */
                    new AccountExecuteCommandMessage(getPlayer().getUniqueId(), preset.toCommand(target.getName())).send();
                }));

        addItem(15, Heads.url(PunishMenu.CANCEL).name("§cCancelar")
                .lore("§7Volta para os motivos.")
                .click(event -> {
                    sound(MenuSound.PAGINATED);

                    if (getLast() != null)
                        getLast().handle();
                    else
                        close();
                }));

        display();
    }
}
