package com.minecraft.core.api.joinmessage;

import com.minecraft.core.account.context.objects.joinmessage.JoinMessageRarity;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import lombok.Getter;

@Getter
public enum JoinMessageType {
    
    /* As mensagens de entrada são anunciadas para quem é VIP ou superior */
    DEFAULT("default", "Padrão", "entrou no lobby!", JoinMessageRarity.COMMON, RankType.VIP),
    CONNECTED("connected", "Conectado", "se conectou!", JoinMessageRarity.COMMON, RankType.VIP),
    ARRIVED("arrived", "Chegou", "chegou chegando!", JoinMessageRarity.COMMON, RankType.VIP),
    PRESENCE("presence", "Presença", "marcou presença!", JoinMessageRarity.EPIC, RankType.VIP),
    INVADED("invaded", "Invasão", "invadiu o lobby!", JoinMessageRarity.EPIC, RankType.VIP),
    LANDED("landed", "Aterrissagem", "acabou de aterrissar!", JoinMessageRarity.EPIC, RankType.VIP),
    /* Entrada da equipe: anunciada com moldura colorida */
    STAFF("staff", "Entrada da equipe", "entrou no lobby", JoinMessageRarity.LEGENDARY, RankType.HELPER);

    public static final String STAFF_FRAME = "§c§m------§e§m------§6§m------§b§m------§a§m------";
    
    private final String id;
    private final String name;
    private final String message;
    private final JoinMessageRarity rarity;
    private final RankType requiredRank;
    
    JoinMessageType(String id, String name, String message, JoinMessageRarity rarity, RankType requiredRank) {
        this.id = id;
        this.name = name;
        this.message = message;
        this.rarity = rarity;
        this.requiredRank = requiredRank;
    }
    
    public JoinMessage toJoinMessage() {
        return new JoinMessage(id, name, message, rarity, requiredRank);
    }
    
    public static JoinMessageType byId(String id) {
        for (JoinMessageType type : values()) {
            if (type.getId().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}

