package com.minecraft.core.bukkit.menu.server.academy;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.type.ServerType;
import org.bukkit.entity.Player;

/**
 * Envia o jogador para um treino específico do servidor da Academy.
 */
public final class AcademyRoute {

    private AcademyRoute() {
    }

    /**
     * @param arena Timers, Lava, Damage ou Texture
     */
    public static void send(Player player, String arena) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null) return;

        Server server = Core.getServerData().of(ServerType.ACADEMY);

        if (server == null || server.isDead()) {
            player.sendMessage(String.format(Constant.SERVER_NOT_FOUND_MESSAGE, ServerType.ACADEMY.getName()));
            return;
        }

        /* A arena fica salva na conta e é consumida quando o jogador entra na Academy */
        account.getAcademy().setRoute(arena);
        account.saveAcademy();

        player.closeInventory();
        player.sendMessage("§aEnviando para " + arena + "...");

        account.redirect(ServerType.ACADEMY);
    }
}
