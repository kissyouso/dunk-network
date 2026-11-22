package com.minecraft.core.bukkit.menu.server.arcade;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.api.party.Party;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.arcade.room.slot.Slot;
import com.minecraft.core.arcade.route.ArcadeRouteContext;
import com.minecraft.core.arcade.route.join.Join;
import com.minecraft.core.backend.database.redis.message.types.route.arcade.ArenaSearchMessage;
import com.minecraft.core.server.Server;
import org.bukkit.entity.Player;

/**
 * Coloca o jogador (e sua party, se for o dono) na fila de um modo.
 */
public final class ArcadeQueue {

    private ArcadeQueue() {
    }

    public static void join(Player player, ArcadeCategory arcade) {
        join(player, arcade, arcade.getSlots().isEmpty() ? Slot.SOLO : arcade.getSlots().get(0));
    }

    public static void join(Player player, ArcadeCategory arcade, Slot slot) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null) return;

        Server server = Core.getServerData().of(arcade.getServer());

        if (server == null || server.isDead()) {
            player.sendMessage(String.format(Constant.SERVER_NOT_FOUND_MESSAGE, arcade.getServer().getName()));
            return;
        }

        ArcadeRouteContext route = ArcadeRouteContext.builder()
                .arcade(arcade)
                .slot(slot)
                .join(Join.PLAYER)
                .build();

        Party party = account.getParty();

        if (party != null) {
            if (party.isAuthor(account.getId()))
                route.setLink(party.getMembersId());
            else {
                account.send("§cApenas o dono da party pode procurar salas.");
                return;
            }
        }

        new ArenaSearchMessage(player.getUniqueId(), route).send();
    }
}
