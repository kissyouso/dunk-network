package com.minecraft.lobby.halloween;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HalloweenListener implements Listener {

    /* Botão direito na cabeça do doce */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();

        if (block == null || block.getType() != Material.SKULL) return;

        Halloween.Candy candy = Halloween.of(block);

        if (candy == null) return;

        event.setCancelled(true);

        if (!Halloween.isEnabled()) {
            event.getPlayer().sendMessage("§cO evento de Halloween não está ativo no momento.");
            return;
        }

        Account account = Core.getAccountController().of(event.getPlayer().getUniqueId());

        if (account != null)
            Halloween.collect(event.getPlayer(), account, candy);
    }

    /* Impede que um doce seja quebrado sem ser removido pelo /halloween */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Halloween.Candy candy = Halloween.of(event.getBlock());

        if (candy == null) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§cEste bloco é um doce de Halloween. Use §f/halloween remove §colhando para ele.");
    }
}
