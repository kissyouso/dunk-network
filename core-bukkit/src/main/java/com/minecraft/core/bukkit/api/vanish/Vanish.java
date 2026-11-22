package com.minecraft.core.bukkit.api.vanish;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.bukkit.api.protocol.ProtocolHandler;
import com.minecraft.core.bukkit.event.type.account.AccountVanishLogEvent;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

@Getter
public class Vanish {

    @Getter
    private static final Set<Vanish> list = new HashSet<>();

    private final Player player;

    public Vanish(Player player) {
        this.player = player;

        list.add(this);
    }

    public static void handle(Player player) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null || player == null) return;

        if (!has(player)) {
            // Entrar no modo vanish

            hideAndShow(player, AccountVanishLogEvent.VanishState.JOIN);

            account.send("",
                    "§dVocê entrou no modo vanish.",
                    "§dAgora você está invisível para " + account.getRankType().bellow().getColoredName() + "§d e abaixo.",
                    "");

            /* Helper e Trial têm as mesmas funções: ambos entram em criativo no vanish. */
            if (account.hasRank(RankType.HELPER))
                player.setGameMode(GameMode.CREATIVE);

            player.playSound(player.getLocation(), Sound.FALL_BIG, 1.0f, 2.0f);

            new Vanish(player);
        } else {
            hideAndShow(player, AccountVanishLogEvent.VanishState.LEAVE);

            account.send("",
                    "§cVocê saiu do modo vanish.",
                    "§cAgora você está visível para todos os jogadores.",
                    "");

            /* Só devolve o survival se o criativo veio do vanish; não mexe em
             * quem já estava em outro modo de jogo. */
            if (player.getGameMode().equals(GameMode.CREATIVE))
                player.setGameMode(GameMode.SURVIVAL);

            player.playSound(player.getLocation(), Sound.BAT_DEATH, 1.0f, 2.0f);

            remove(player);
        }

        new AccountVanishLogEvent(account, has(player) ? AccountVanishLogEvent.VanishState.JOIN : AccountVanishLogEvent.VanishState.LEAVE).call();
    }

    public static boolean has(Player player) {
        return list.stream().anyMatch(vanish -> vanish.getPlayer().getUniqueId().equals(player.getUniqueId()));
    }

    public static void remove(Player player) {
        list.removeIf(vanish -> vanish.getPlayer().getUniqueId().equals(player.getUniqueId()));
    }

    public static void hideAndShow(Player player, AccountVanishLogEvent.VanishState state) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null) return;

        if (state == AccountVanishLogEvent.VanishState.LEAVE) {
            Bukkit.getOnlinePlayers().forEach(target -> ProtocolHandler.showPlayer(target, player));
            return;
        }

        /* Ao entrar no vanish, esconde o staff de quem tem rank inferior.
         * Staff que também está em vanish precisa continuar se vendo,
         * independentemente do rank (admin x helper). */
        for (Account target : Core.getAccountController().filter(target -> target.player() != null)) {
            Player targetPlayer = target.player();

            if (targetPlayer.equals(player)) continue;

            /* Quem está em vanish enxerga todo mundo (inclusive o jogador que
             * está sendo espectado), mas continua invisível no TAB/entidade de
             * quem tem rank inferior. */
            ProtocolHandler.showPlayer(player, targetPlayer);

            if (has(targetPlayer)) {
                ProtocolHandler.showPlayer(targetPlayer, player);
                continue;
            }

            if (target.hasRank(account.getRank()))
                ProtocolHandler.showPlayer(targetPlayer, player);
            else
                ProtocolHandler.hidePlayer(targetPlayer, player);
        }
    }
}
