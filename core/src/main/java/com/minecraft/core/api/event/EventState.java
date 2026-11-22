package com.minecraft.core.api.event;

import com.minecraft.core.Core;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Estado do evento presencial atual (publicado pelo servidor de eventos no Redis).
 */
@Getter
@AllArgsConstructor
public class EventState {

    public static final String KEY = "event:state";

    private final String type, name;

    /* CALLING (chamadas, aberto para entrar) ou RUNNING (em andamento, só camarote) */
    private final String phase;

    private final int players, calls;
    private final boolean vipOnly, spectators;

    public boolean isOpen() {
        return "CALLING".equals(phase);
    }

    public static EventState current() {
        try {
            return Core.getRedis().load(KEY, EventState.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void publish(EventState state) {
        if (state == null)
            Core.getRedis().delete(KEY);
        else
            Core.getRedis().save(KEY, state, 15);
    }
}
