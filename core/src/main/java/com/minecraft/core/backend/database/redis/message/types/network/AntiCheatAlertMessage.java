package com.minecraft.core.backend.database.redis.message.types.network;

import lombok.Getter;

/**
 * Alerta publicado pela ponte (DunkACBridge) que roda junto do Anticheat em cada servidor. A ponte mantém o nome técnico.
 * Payload simples para o proxy montar a mensagem para a staff.
 */
@Getter
public class AntiCheatAlertMessage {

    private String uuid;
    private String name;
    private String server;
    private String hack;
    private int violations;
    private int count;
    private int ping;
}
