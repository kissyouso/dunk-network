package com.minecraft.core.backend.database.redis.message.types.network;

import com.minecraft.core.Constant;
import com.minecraft.core.backend.database.redis.message.RedisMessage;
import lombok.Getter;

/**
 * Mensagem enviada pelo proxy para todos os jogadores da rede (exceto quem está no login).
 * Se "command" for informado, as linhas ficam clicáveis e executam o comando.
 */
@Getter
public class NetworkBroadcastMessage extends RedisMessage {

    private final String[] lines;
    private final String command, hover;

    public NetworkBroadcastMessage(String command, String hover, String... lines) {
        super(Constant.REDIS_NETWORK_BROADCAST_CHANNEL);

        this.command = command;
        this.hover = hover;
        this.lines = lines;
    }

    /* Mensagem sem clique */
    public static NetworkBroadcastMessage of(String... lines) {
        return new NetworkBroadcastMessage(null, null, lines);
    }
}
