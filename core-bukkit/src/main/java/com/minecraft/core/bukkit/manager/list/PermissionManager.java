package com.minecraft.core.bukkit.manager.list;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.Rank;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.bukkit.BukkitCore;
import com.minecraft.core.editor.RankPermissions;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PermissionManager {

    /* Um anexo por jogador, trocado inteiro a cada recarga (o editor do site muda permissões ao vivo) */
    private static final Map<java.util.UUID, PermissionAttachment> attachments = new java.util.concurrent.ConcurrentHashMap<>();

    public static void loadPermissions(Player player) {
        Account account = Core.getAccountController().of(player.getUniqueId());

        if (account == null) return;

        PermissionAttachment previous = attachments.remove(player.getUniqueId());

        if (previous != null) {
            try {
                player.removeAttachment(previous);
            } catch (IllegalArgumentException ignored) {
            }
        }

        PermissionAttachment attachment = player.addAttachment(BukkitCore.getInstance());
        attachments.put(player.getUniqueId(), attachment);

        // Iniciando lista com permissões do rank atual (contas antigas podem ter tipo nulo)
        List<String> permissions = new ArrayList<>();

        Rank current = account.getRank();

        if (current != null && current.getType() != null) {
            permissions.addAll(current.getType().getPermissions());
            permissions.addAll(RankPermissions.active(current.getType()));
        }

        // Adicionando permissões da lista de ranks
        account.getAvailableRanks().forEach(rank -> {
            if (rank.hasExpired()) return;

            RankType type = rank.getType();

            if (type == null) return;

            permissions.addAll(type.getPermissions());
            permissions.addAll(RankPermissions.active(type));
        });

        // Adicionando permissões da conta
        account.getPermissions().forEach(entry -> {
            if (!entry.hasExpired())
                permissions.add(entry.getKey());
        });

        permissions.forEach(permission -> attachment.setPermission(permission, true));
    }

    public static void reloadAll() {
        org.bukkit.Bukkit.getOnlinePlayers().forEach(PermissionManager::loadPermissions);
    }

    public static void forget(Player player) {
        attachments.remove(player.getUniqueId());
    }

    public static void unloadPermissions(Player player) {
        for (PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
            PermissionAttachment attachment = permission.getAttachment();

            if (attachment == null) return;

            Map<String, Boolean> flags = attachment.getPermissions();

            flags.forEach((key, value) -> attachment.setPermission(key, false));
        }
    }
}