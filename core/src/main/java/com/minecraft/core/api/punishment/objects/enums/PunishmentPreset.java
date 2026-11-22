package com.minecraft.core.api.punishment.objects.enums;

import com.minecraft.core.account.context.objects.rank.type.RankType;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Motivos de punição usados pelo /punir (menu e chat).
 * Cada motivo tem a duração padrão, o cargo mínimo para aplicar e quando deve ser usado.
 */
@Getter
public enum PunishmentPreset {

    /* Banimentos */
    CHEATING("Uso de Trapaças", PunishmentCategory.BAN, PunishmentReason.CHEATING, "-1", RankType.HELPER,
            "Hacks, macros, autoclicker,",
            "x-ray ou qualquer cliente que",
            "dê vantagem injusta."),
    BUG_ABUSE("Abuso de Bugs", PunishmentCategory.BAN, PunishmentReason.CHEATING, "7d", RankType.HELPER,
            "Usar falhas do servidor para",
            "ganhar vantagem ou atrapalhar",
            "outros jogadores."),
    TEAMING("Time Combinado", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "3d", RankType.HELPER,
            "Se aliar a jogadores de outro",
            "time em modos solo/times."),
    HARASSMENT("Assédio Sexual", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "7d", RankType.HELPER,
            "Mensagens ou atitudes de",
            "cunho sexual com outros",
            "jogadores."),
    DISCRIMINATION("Discriminação", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "14d", RankType.HELPER,
            "Racismo, homofobia, xenofobia",
            "ou qualquer preconceito."),
    FAKE_PROOF("Falsificação de Provas", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "14d", RankType.HELPER,
            "Editar prints ou vídeos para",
            "prejudicar outro jogador."),
    IMPERSONATION("Falsidade Ideológica", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "14d", RankType.HELPER,
            "Se passar pela equipe, por",
            "youtubers ou outros jogadores."),
    INAPPROPRIATE_SKIN("Skin Inapropriada", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "14d", RankType.HELPER,
            "Skin ou capa com conteúdo",
            "ofensivo, sexual ou de ódio."),
    PLAGIARISM("Plágio", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "14d", RankType.HELPER,
            "Copiar construções, mapas ou",
            "conteúdo de outros sem crédito."),
    PUNISHMENT_EVASION("Burlar Punição", PunishmentCategory.BAN, PunishmentReason.COMMUNITY, "-1", RankType.HELPER,
            "Usar outra conta para fugir",
            "de um banimento ou mute."),

    /* Mutes */
    SPAM("Spam/Flood", PunishmentCategory.MUTE, PunishmentReason.COMMUNITY, "1h", RankType.HELPER,
            "Repetir mensagens, letras ou",
            "caracteres em sequência."),
    TOXICITY("Comportamento Tóxico", PunishmentCategory.MUTE, PunishmentReason.COMMUNITY, "6h", RankType.HELPER,
            "Linguagem inadequada, ofensas",
            "ou provocações no chat."),
    STAFF_DISRESPECT("Desrespeito à Equipe", PunishmentCategory.MUTE, PunishmentReason.COMMUNITY, "12h", RankType.HELPER,
            "Ofender ou debochar de membros",
            "da equipe."),
    THREATS("Ameaças", PunishmentCategory.MUTE, PunishmentReason.COMMUNITY, "3d", RankType.HELPER,
            "Ameaçar jogadores (ddos, vazar",
            "dados, etc.), mesmo de brincadeira."),
    ADVERTISING("Divulgação", PunishmentCategory.MUTE, PunishmentReason.COMMUNITY, "7d", RankType.HELPER,
            "Divulgar outros servidores,",
            "links ou vendas no chat.");

    private final String name;
    private final PunishmentCategory category;
    private final PunishmentReason reason;

    /* Formato do TimeUtil ("1h", "7d"...) ou "-1" para permanente */
    private final String time;
    private final RankType minimumRank;

    private final List<String> description;

    PunishmentPreset(String name, PunishmentCategory category, PunishmentReason reason, String time, RankType minimumRank, String... description) {
        this.name = name;
        this.category = category;
        this.reason = reason;
        this.time = time;
        this.minimumRank = minimumRank;
        this.description = Arrays.asList(description);
    }

    public boolean isPermanent() {
        return time.equals("-1");
    }

    /**
     * Duração escrita por extenso: "Permanente", "14 dias", "1 hora"...
     */
    public String getDurationName() {
        if (isPermanent()) return "Permanente";

        int amount = Integer.parseInt(time.substring(0, time.length() - 1));

        switch (time.charAt(time.length() - 1)) {
            case 'm':
                return amount + (amount == 1 ? " minuto" : " minutos");
            case 'h':
                return amount + (amount == 1 ? " hora" : " horas");
            case 'd':
                return amount + (amount == 1 ? " dia" : " dias");
            default:
                return time;
        }
    }

    /**
     * Comando do modo manual que aplica este motivo.
     */
    public String toCommand(String target) {
        return "punir " + category.name() + " " + reason.name() + " " + time + " " + target + " " + name;
    }

    public static List<PunishmentPreset> of(PunishmentCategory category) {
        return Arrays.stream(values()).filter(preset -> preset.getCategory() == category).collect(Collectors.toList());
    }

    public static PunishmentPreset ofName(String name) {
        return Arrays.stream(values()).filter(preset -> preset.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
