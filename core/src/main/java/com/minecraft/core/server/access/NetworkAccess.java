package com.minecraft.core.server.access;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.server.type.ServerType;
import com.minecraft.core.util.Util;
import lombok.Getter;
import lombok.Setter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Manutenção e acesso da rede, editados pelo site do editor (página "Acesso").
 * Fica no PostgreSQL (tabela network_settings) e cada proxy guarda em cache por alguns segundos.
 */
@Getter
@Setter
public class NetworkAccess {

    private static final String KEY = "access";
    private static final long CACHE_MILLIS = 5000L;

    private static NetworkAccess cached;
    private static long cachedAt;
    private static boolean tableReady;

    /* ---------------- Manutenção da rede ---------------- */

    private boolean maintenance;

    /* Cargo mínimo que entra mesmo em manutenção (id do RankType em minúsculas) */
    private String maintenanceRank = "trial";

    /* Nicks liberados durante a manutenção */
    private List<String> maintenancePlayers = new ArrayList<>();

    /* Segunda linha do MOTD durante a manutenção */
    private String maintenanceMotd = "&c&lESTAMOS EM MANUTENÇÃO!";

    /* Mensagem de quem tenta entrar durante a manutenção (\n quebra a linha) */
    private String maintenanceMessage = "&6&lREDE STYLE\n\n&cEstamos em manutenção!\n&7Voltamos em breve com novidades.\n\n&7Acompanhe em: &e" + Constant.SERVER_DISCORD;

    /* ---------------- Slots ---------------- */

    /* Limite de jogadores da rede (0 = sem limite) */
    private int slots;

    /* Cargo mínimo que entra com a rede cheia */
    private String slotsRank = "vip";

    private String fullMessage = "&6&lREDE STYLE\n\n&cA rede está cheia!\n&7Adquira &aVIP &7para entrar mesmo com a rede lotada.\n\n&e" + Constant.SERVER_STORE;

    /* ---------------- Servidores exclusivos ---------------- */

    /* Tipo de servidor (ServerType.name()) -> cargo mínimo para entrar */
    private Map<String, String> serverRanks = new LinkedHashMap<>();

    private String updatedBy;
    private long updatedAt;

    /* ---------------- Regras ---------------- */

    public static RankType rank(String id) {
        if (id == null || id.isEmpty()) return null;

        try {
            return RankType.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean canBypassMaintenance(Account account) {
        if (Core.isUntouchable(account.getId())) return true;

        RankType rank = rank(maintenanceRank);

        if (rank != null && account.hasRank(rank)) return true;

        return maintenancePlayers.stream().anyMatch(name -> name.equalsIgnoreCase(account.getName()));
    }

    public boolean canBypassSlots(Account account) {
        RankType rank = rank(slotsRank);

        return Core.isUntouchable(account.getId()) || rank != null && account.hasRank(rank) || account.isStaffer();
    }

    /**
     * Cargo mínimo para entrar num tipo de servidor (null = liberado para todos).
     */
    public RankType getRequiredRank(ServerType type) {
        return type == null ? null : rank(serverRanks.get(type.name()));
    }

    public boolean canJoin(Account account, ServerType type) {
        RankType required = getRequiredRank(type);

        return required == null || account.hasRank(required) || Core.isUntouchable(account.getId());
    }

    public String getMaintenanceMotdColored() {
        return Util.color(maintenanceMotd);
    }

    public String getMaintenanceMessageColored() {
        return Util.color(maintenanceMessage.replace("\\n", "\n"));
    }

    public String getFullMessageColored() {
        return Util.color(fullMessage.replace("\\n", "\n"));
    }

    /* ---------------- Leitura / gravação ---------------- */

    /**
     * Configuração atual (cache de 5s). Sem banco, devolve a padrão (tudo liberado).
     */
    public static NetworkAccess current() {
        long now = System.currentTimeMillis();

        if (cached != null && now - cachedAt < CACHE_MILLIS)
            return cached;

        NetworkAccess loaded = load();

        cached = loaded != null ? loaded : (cached != null ? cached : new NetworkAccess());
        cachedAt = now;

        return cached;
    }

    public static NetworkAccess load() {
        if (Core.getPostgres() == null) return null;

        ensureTable();

        try (Connection conn = Core.getPostgres().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT value FROM network_settings WHERE key = ?")) {

            stmt.setString(1, KEY);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return new NetworkAccess();

                NetworkAccess access = Core.GSON.fromJson(rs.getString("value"), NetworkAccess.class);

                return access != null ? access.fill() : new NetworkAccess();
            }
        } catch (Exception e) {
            Core.getLogger().warning("[Acesso] Não foi possível carregar: " + e.getMessage());
            return null;
        }
    }

    public static void save(NetworkAccess access, String actor) throws Exception {
        if (Core.getPostgres() == null)
            throw new IllegalStateException("PostgreSQL indisponível");

        ensureTable();

        access.setUpdatedBy(actor);
        access.setUpdatedAt(System.currentTimeMillis());

        try (Connection conn = Core.getPostgres().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO network_settings (key, value, updated_at) VALUES (?, ?::jsonb, ?) " +
                             "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at")) {

            stmt.setString(1, KEY);
            stmt.setString(2, Core.GSON.toJson(access));
            stmt.setLong(3, access.getUpdatedAt());
            stmt.executeUpdate();
        }

        cached = access;
        cachedAt = System.currentTimeMillis();
    }

    private static void ensureTable() {
        if (tableReady) return;

        try (Connection conn = Core.getPostgres().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS network_settings (" +
                             "    key VARCHAR(64) PRIMARY KEY," +
                             "    value JSONB NOT NULL," +
                             "    updated_at BIGINT NOT NULL" +
                             ")")) {
            stmt.execute();
            tableReady = true;
        } catch (Exception e) {
            Core.getLogger().severe("[Acesso] Erro ao criar a tabela network_settings: " + e.getMessage());
        }
    }

    /* Campos que faltarem no JSON salvo (versões antigas) voltam para o padrão */
    private NetworkAccess fill() {
        NetworkAccess defaults = new NetworkAccess();

        if (maintenanceRank == null) maintenanceRank = defaults.maintenanceRank;
        if (maintenancePlayers == null) maintenancePlayers = new ArrayList<>();
        if (maintenanceMotd == null) maintenanceMotd = defaults.maintenanceMotd;
        if (maintenanceMessage == null) maintenanceMessage = defaults.maintenanceMessage;
        if (slotsRank == null) slotsRank = defaults.slotsRank;
        if (fullMessage == null) fullMessage = defaults.fullMessage;
        if (serverRanks == null) serverRanks = new LinkedHashMap<>();

        return this;
    }
}
