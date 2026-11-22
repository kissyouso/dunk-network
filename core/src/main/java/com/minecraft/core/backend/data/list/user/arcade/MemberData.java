package com.minecraft.core.backend.data.list.user.arcade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.arcade.category.ArcadeCategory;
import com.minecraft.core.backend.database.postgresql.PostgresDatabase;
import com.minecraft.core.backend.database.redis.RedisDatabase;
import com.minecraft.core.controller.list.MemberController;
import com.minecraft.core.member.Member;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MemberData<T extends Member> {

    private final PostgresDatabase postgres;
    private final RedisDatabase redis;
    private final String tableName;

    private final MemberController controller;

    private final Type type;
    private final Class<T> generic;

    private final String MEMBER_KEY_FORMAT;

    protected final static List<String> MEMBER_TABLE_NAMES = Arrays.asList(
            "members_duels", "members_bedwars", "members_pvp", "members_skywars");

    /* Categorias que foram renomeadas; o JSON guardado ainda traz o nome antigo. */
    private final static Map<String, String> RENAMED_CATEGORIES = Collections.singletonMap(
            "DUELS_BED_FIGHT_SOLO", "DUELS_BED_FIGHT");

    @SuppressWarnings("unchecked")
    public MemberData(PostgresDatabase postgres, RedisDatabase redis, String tableName) {
        this.postgres = postgres;
        this.tableName = tableName;
        this.redis = redis;
        this.controller = Core.getMemberController();

        this.type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.generic = (Class<T>) type;

        MEMBER_KEY_FORMAT = "member:" + tableName + ":";
        
        // Criar tabela se não existir
        createTable(postgres, tableName);
    }
    
    /**
     * Cria a tabela de members no PostgreSQL se ela não existir
     * @param postgres Instância do PostgresDatabase
     * @param tableName Nome da tabela a ser criada
     */
    public static void createTable(PostgresDatabase postgres, String tableName) {
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "    id UUID PRIMARY KEY," +
                "    account_id UUID NOT NULL," +
                "    data JSONB NOT NULL," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE" +
                ");" +
                "CREATE INDEX IF NOT EXISTS idx_" + tableName + "_account ON " + tableName + "(account_id);"
             )) {
            
            stmt.execute();
            Core.getLogger().info("[MemberData] Tabela '" + tableName + "' verificada/criada com sucesso.");
            
        } catch (SQLException e) {
            Core.getLogger().severe("[MemberData] Erro ao criar tabela '" + tableName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<Member> listAll(UUID id) {
        List<Member> members = new ArrayList<>();

        for (String tableName : MEMBER_TABLE_NAMES) {
            try {
                String sql = "SELECT data FROM " + tableName + " WHERE id = ?";
                
                try (Connection conn = Core.getPostgres().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setObject(1, id);
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next()) {
                        String jsonData = rs.getString("data");
                        members.add(Core.GSON.fromJson(readable(jsonData), Member.class));
                    } else {
                        System.out.println("Não encontrou o usuário esperado da tabela " + tableName + "!");
                    }
                }

            } catch (Exception e) {
                System.err.println("Erro ao acessar a tabela " + tableName + "!");
                e.printStackTrace();
            }
        }

        return members;
    }

    public T save(T member) {
        member = reconcile(member);

        String checkSql = "SELECT data FROM " + tableName + " WHERE id = ?";
        
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkSql)) {
            
            stmt.setObject(1, member.getId());
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next()) {
                String insertSql = "INSERT INTO " + tableName + " (id, account_id, data) VALUES (?, ?, ?::jsonb)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setObject(1, member.getId());
                    insertStmt.setObject(2, member.getId());
                    insertStmt.setString(3, Core.GSON.toJson(member));
                    insertStmt.executeUpdate();
                }
                
                controller.save(member);
            } else {
                Member stored = parse(rs.getString("data"), type, member.getId());

                if (stored == null) {
                    /* Nada aproveitavel na linha: grava quem esta chegando em vez de
                     * devolver null e deixar o login sem member. */
                    update(member, "save");
                    return member;
                }

                member = generic.cast(stored);
                member = reconcile(member);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return member;
    }

    private T parse(String jsonData, Type type, UUID id) {
        try {
            return Core.GSON.fromJson(readable(jsonData), type);
        } catch (JsonSyntaxException | IllegalStateException e) {
            Core.getLogger().severe("[MemberData] JSON de " + tableName + "/" + id + " ilegivel: " + e.getMessage());
            return null;
        }
    }

    /**
     * O Gson nao resolve nome de enum desconhecido como chave de mapa: vira null e a
     * segunda null derruba a leitura com "duplicate key: null", o que quebrava o join
     * de quem tinha DUELS_BED_FIGHT_SOLO/_DUO em members_duels (ho so existe
     * DUELS_BED_FIGHT). Aqui as chaves de stats voltam a ser nomes validos e o que
     * nao existe mais cai fora.
     */
    private static String readable(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) return jsonData;

        try {
            JsonObject root = Core.PARSER.parse(jsonData).getAsJsonObject();
            JsonObject context = root.getAsJsonObject("context");
            JsonObject stats = context == null ? null : context.getAsJsonObject("stats");

            if (stats == null) return jsonData;

            JsonObject adjusted = new JsonObject();

            for (Map.Entry<String, JsonElement> entry : stats.entrySet()) {
                String name = RENAMED_CATEGORIES.getOrDefault(entry.getKey(), entry.getKey());

                ArcadeCategory category = ArcadeCategory.of(name);

                if (category == null || adjusted.has(category.name())) continue;

                JsonObject stat = entry.getValue().getAsJsonObject();
                stat.addProperty("arcade", category.name());

                adjusted.add(category.name(), stat);
            }

            context.add("stats", adjusted);

            return root.toString();

        } catch (IllegalStateException | JsonSyntaxException e) {
            return jsonData;
        }
    }

    public T of(UUID id, boolean storeInRedis) {
        Member member = controller.of(id, generic);

        if (member == null) {
            // Carregar direto do PostgreSQL
            String sql = "SELECT data FROM " + tableName + " WHERE id = ?";
            
            try (Connection conn = postgres.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setObject(1, id);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    member = parse(rs.getString("data"), generic, id);
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return reconcile(generic.cast(member));
    }

    /**
     * O member é criado por {@code player.getName()}/{@code event.getName()},
     * que em alguns fluxos carrega o nick do /fake (ou um nome antigo). O XP é
     * salvo pelo UUID e continua correto, mas o nome guardado no JSON vaza para
     * site/rankings. Sempre que a conta estiver carregada, realinha o nome do
     * member com o nome real da conta.
     */
    private T reconcile(T member) {
        if (member == null) return null;

        try {
            Account account = Core.getAccountController() != null
                    ? Core.getAccountController().of(member.getId())
                    : null;

            if (account == null) return member;

            String realName = account.getName();

            if (realName == null || realName.isEmpty() || realName.equals(member.getName()))
                return member;

            Core.getLogger().info("[MemberData] Nome de " + tableName + "/" + member.getId()
                    + " corrigido: " + member.getName() + " -> " + realName);

            member.setName(realName);
        } catch (Exception e) {
            Core.getLogger().warning("[MemberData] Não foi possível realinhar o nome de "
                    + tableName + "/" + member.getId() + ": " + e.getMessage());
        }

        return member;
    }

    public T of(UUID id) {
        return of(id, false);
    }

    public synchronized void update(T member, String field) {
        try {
            String sql = "UPDATE " + tableName + " SET data = ?::jsonb, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            
            try (Connection conn = postgres.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, Core.GSON.toJson(member));
                stmt.setObject(2, member.getId());
                stmt.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Core.getLogger().severe("[Member-Update] Não foi possível atualizar o campo \"" + field + "\" no membro de " + member.getName() + "!");
        }
    }

    public void startExpiration(UUID id) {
        controller.remove(id, generic);

        redis.cache(MEMBER_KEY_FORMAT + id, 300);
    }

    public void cancelExpiration(UUID id) {
        redis.persist(MEMBER_KEY_FORMAT + id);
    }

    public List<T> list() {
        List<T> members = new ArrayList<>();
        String sql = "SELECT id, data FROM " + tableName;

        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                T member = parse(rs.getString("data"), generic, rs.getObject("id", UUID.class));

                if (member != null) members.add(member);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return members;
    }

    /**
     * Ranking pelo campo informado (caminho com ponto, ex: "context.level").
     * Campos numéricos são ordenados no banco; campos que não são números (ex: "metadata.statsList")
     * retornam todos os membros, pois a ordenação é feita em Java por quem chama.
     */
    public Collection<T> ranking(String field, int limit) {
        List<T> members = new ArrayList<>();

        /* "context.level" -> {context,level} (o operador ->> não navega caminhos com ponto) */
        String path = "{" + field.replace(".", ",") + "}";

        String sql = "SELECT id, data, jsonb_typeof(data #> ?::text[]) AS kind FROM " + tableName
                + " ORDER BY CASE WHEN jsonb_typeof(data #> ?::text[]) = 'number' THEN (data #>> ?::text[])::numeric END DESC NULLS LAST";

        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, path);
            stmt.setString(2, path);
            stmt.setString(3, path);

            ResultSet rs = stmt.executeQuery();

            boolean numeric = true;

            while (rs.next()) {
                if (members.isEmpty())
                    numeric = "number".equals(rs.getString("kind"));

                /* Campo numérico: o banco já ordenou, basta o limite (com folga para contas removidas) */
                if (numeric && members.size() >= limit * 2) break;

                T member = parse(rs.getString("data"), generic, rs.getObject("id", UUID.class));

                if (member != null) members.add(member);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return members;
    }
}
