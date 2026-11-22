package com.minecraft.core.account.context.objects.toggle;

import com.minecraft.core.account.context.objects.tag.prefix.TagPrefix;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Toggle {

    /* Member Toggle List */
    private boolean allowMessages = true, allowFriendRequests = true,
            allowPartyInvite = true, allowClanInvite = true, allowStatsView = true,
            allowClanTag = true, isConfirmToHub = false;

    private boolean visiblePlayersHub = true;

    /* Slot da hotbar onde os blocos comprados (BedWars) são colocados.
     * Índice 1 = segunda tecla (slot 2 do teclado). */
    private int blockSlot = 1;

    private boolean allowChat = true, extraInfo = true;

    /* VIP Toggle List */
    private boolean allowJoinMessage = true;
    private TagPrefix tagPrefix = TagPrefix.DEFAULT;

    /* Staff Toggle List */
    private boolean allowLogs = true, allowBuild = false;

    private boolean autoVanish = true, staffChat = false, anticheatAlerts = true;
}
