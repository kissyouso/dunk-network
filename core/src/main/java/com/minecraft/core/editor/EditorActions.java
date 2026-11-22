package com.minecraft.core.editor;

import com.minecraft.core.Core;
import com.minecraft.core.backend.database.postgresql.PostgresDatabase;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Histórico do editor de permissões: quem mudou o quê e quando.
 */
public final class EditorActions {

    private EditorActions() {
    }

    @Getter
    @AllArgsConstructor
    public static class Action {
        private final long at;
        private final String actor, description;
    }

    public static void createTable(PostgresDatabase postgres) {
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS editor_actions (" +
                             "    id BIGSERIAL PRIMARY KEY," +
                             "    at BIGINT NOT NULL," +
                             "    actor VARCHAR(32) NOT NULL," +
                             "    description TEXT NOT NULL" +
                             ");" +
                             "CREATE INDEX IF NOT EXISTS idx_editor_actions_at ON editor_actions(at DESC);")) {
            stmt.execute();
        } catch (SQLException e) {
            Core.getLogger().severe("[EditorActions] Erro ao criar a tabela: " + e.getMessage());
        }
    }

    public static void log(String actor, List<String> descriptions) throws SQLException {
        if (descriptions.isEmpty()) return;

        try (Connection conn = Core.getPostgres().getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO editor_actions (at, actor, description) VALUES (?, ?, ?)")) {

            long now = System.currentTimeMillis();

            for (String description : descriptions) {
                stmt.setLong(1, now);
                stmt.setString(2, actor);
                stmt.setString(3, description.length() > 2000 ? description.substring(0, 2000) : description);
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    public static List<Action> recent(int limit) {
        List<Action> actions = new ArrayList<>();

        try (Connection conn = Core.getPostgres().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT at, actor, description FROM editor_actions ORDER BY id DESC LIMIT ?")) {

            stmt.setInt(1, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next())
                actions.add(new Action(rs.getLong("at"), rs.getString("actor"), rs.getString("description")));
        } catch (SQLException e) {
            Core.getLogger().severe("[EditorActions] Erro ao carregar: " + e.getMessage());
        }

        return actions;
    }
}
