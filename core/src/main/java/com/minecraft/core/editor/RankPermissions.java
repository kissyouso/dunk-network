package com.minecraft.core.editor;

import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.backend.database.postgresql.PostgresDatabase;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Permissões extras de cada rank, editadas pelo site. Somam-se às permissões fixas do {@link RankType}.
 * Ficam no Postgres (tabela rank_permissions) e cada servidor guarda uma cópia em memória,
 * recarregada quando o editor salva (canal {@link Constant#REDIS_RANK_PERMISSIONS_CHANNEL}).
 */
public final class RankPermissions {

    private static volatile Map<RankType, List<Node>> cache = Collections.emptyMap();

    private RankPermissions() {
    }

    @Getter
    @AllArgsConstructor
    public static class Node {

        private final String key;

        /* -1 = permanente */
        private final long expiresAt, addedAt;

        public boolean isActive() {
            return expiresAt < 0 || expiresAt > System.currentTimeMillis();
        }
    }

    public static void createTable(PostgresDatabase postgres) {
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS rank_permissions (" +
                             "    rank VARCHAR(32) NOT NULL," +
                             "    key VARCHAR(128) NOT NULL," +
                             "    expires_at BIGINT NOT NULL DEFAULT -1," +
                             "    added_at BIGINT NOT NULL," +
                             "    PRIMARY KEY (rank, key)" +
                             ");")) {
            stmt.execute();
        } catch (SQLException e) {
            Core.getLogger().severe("[RankPermissions] Erro ao criar a tabela: " + e.getMessage());
        }
    }

    public static void load() {
        Map<RankType, List<Node>> loaded = new EnumMap<>(RankType.class);

        try (Connection conn = Core.getPostgres().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT rank, key, expires_at, added_at FROM rank_permissions")) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                RankType rank;

                try {
                    rank = RankType.valueOf(rs.getString("rank"));
                } catch (IllegalArgumentException e) {
                    continue;
                }

                loaded.computeIfAbsent(rank, ignored -> new ArrayList<>())
                        .add(new Node(rs.getString("key"), rs.getLong("expires_at"), rs.getLong("added_at")));
            }
        } catch (SQLException e) {
            Core.getLogger().severe("[RankPermissions] Erro ao carregar: " + e.getMessage());
            return;
        }

        cache = loaded;
    }

    public static List<Node> nodes(RankType rank) {
        return cache.getOrDefault(rank, Collections.emptyList());
    }

    /* Chaves válidas (não expiradas) de um rank */
    public static List<String> active(RankType rank) {
        return nodes(rank).stream().filter(Node::isActive).map(Node::getKey).collect(Collectors.toList());
    }

    public static boolean has(RankType rank, String key) {
        return nodes(rank).stream().anyMatch(node -> node.isActive() && node.getKey().equalsIgnoreCase(key));
    }

    /* Substitui todas as permissões extras de um rank (transação) */
    public static void replace(RankType rank, List<Node> nodes) throws SQLException {
        try (Connection conn = Core.getPostgres().getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM rank_permissions WHERE rank = ?");
                 PreparedStatement insert = conn.prepareStatement("INSERT INTO rank_permissions (rank, key, expires_at, added_at) VALUES (?, ?, ?, ?)")) {

                delete.setString(1, rank.name());
                delete.executeUpdate();

                for (Node node : nodes) {
                    insert.setString(1, rank.name());
                    insert.setString(2, node.getKey());
                    insert.setLong(3, node.getExpiresAt());
                    insert.setLong(4, node.getAddedAt());
                    insert.addBatch();
                }

                insert.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /* Avisa todos os servidores para recarregarem */
    public static void publish() {
        Core.getRedis().publish(Constant.REDIS_RANK_PERMISSIONS_CHANNEL, "{\"reload\":true}");
    }
}
