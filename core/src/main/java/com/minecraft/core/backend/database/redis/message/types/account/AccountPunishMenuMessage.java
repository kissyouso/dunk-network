package com.minecraft.core.backend.database.redis.message.types.account;

import com.minecraft.core.Constant;
import com.minecraft.core.backend.database.redis.message.RedisMessage;
import lombok.Getter;

import java.util.UUID;

/**
 * Pede para o servidor onde o staff está abrir o menu do /punir.
 */
@Getter
public class AccountPunishMenuMessage extends RedisMessage {

    private final UUID viewer, target;

    public AccountPunishMenuMessage(UUID viewer, UUID target) {
        super(Constant.REDIS_ACCOUNT_PUNISH_MENU_CHANNEL);

        this.viewer = viewer;
        this.target = target;
    }
}
