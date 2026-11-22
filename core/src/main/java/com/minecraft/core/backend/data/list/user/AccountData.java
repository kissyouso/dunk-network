package com.minecraft.core.backend.data.list.user;

import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.backend.data.template.UserTemplate;
import com.minecraft.core.backend.database.postgresql.PostgresDatabase;
import com.minecraft.core.backend.database.redis.RedisDatabase;
import com.minecraft.core.backend.database.redis.message.types.account.AccountUpdateMessage;
import com.minecraft.core.controller.list.AccountController;
import com.minecraft.core.util.list.JsonUtil;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AccountData implements UserTemplate<Account> {

    private final PostgresDatabase postgres;
    private final RedisDatabase redis;

    private final AccountController controller;

    private final String ACCOUNT_KEY_FORMAT = "account:";

    public AccountData(PostgresDatabase postgres, RedisDatabase redis) {
        this.postgres = postgres;
        this.redis = redis;
        this.controller = Core.getAccountController();
        createTable(postgres);
    }

    public static void createTable(PostgresDatabase postgres) {
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS accounts ( " +
                     "id UUID PRIMARY KEY, " +
                     "name VARCHAR(16) NOT NULL, " +
                     "data JSONB NOT NULL, " +
                     "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                     "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP " +
                     "); " +
                     "CREATE INDEX IF NOT EXISTS idx_accounts_name ON accounts(LOWER(name)); " +
                     "CREATE INDEX IF NOT EXISTS idx_accounts_ip_path ON accounts((data #>> '{context,ipAddress}')); ")) {
            
            stmt.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Account save(Account account) {
        String sql = "SELECT data FROM accounts WHERE id = ?";
        
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, account.getId());
            ResultSet rs = stmt.executeQuery();
            
            if (!rs.next()) {
                // Conta não existe, criar nova
                String insertSql = "INSERT INTO accounts (id, name, data) VALUES (?, ?, ?::jsonb)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setObject(1, account.getId());
                    insertStmt.setString(2, account.getName());
                    insertStmt.setString(3, Core.GSON.toJson(account));
                    insertStmt.executeUpdate();
                }
                
                controller.save(account);
            } else {
                // Conta existe, carregar do banco
                String jsonData = rs.getString("data");
                account = Core.GSON.fromJson(jsonData, Account.class);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return account;
    }

    @Override
    public void delete(Account account) {
        String sql = "DELETE FROM accounts WHERE id = ?";
        
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setObject(1, account.getId());
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                controller.remove(account.getId());
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            Core.getLogger().severe("[Account-Delete] Erro ao deletar conta de " + account.getName());
        }
    }

    public void clearCache() {
        controller.getCache().clear();

        redis.removeAll(ACCOUNT_KEY_FORMAT);
    }

    @Override
    public void update(Account account, String field) {
        try {
            JsonObject tree = JsonUtil.jsonTree(account);
            String sql = "UPDATE accounts SET data = ?::jsonb, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            
            try (Connection conn = postgres.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, Core.GSON.toJson(account));
                stmt.setObject(2, account.getId());
                
                int updated = stmt.executeUpdate();
                
                if (updated > 0) {
                    new AccountUpdateMessage(account, field, tree.get(field)).send();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void startExpiration(Account account) {
        controller.remove(account.getId());

        redis.cache(ACCOUNT_KEY_FORMAT + account.getId(), 80);
    }

    public void removeCache(Account account) {
        controller.remove(account.getId());

        redis.delete(ACCOUNT_KEY_FORMAT + account.getId());
    }

    @Override
    public void cancelExpiration(Account account) {
        redis.persist(ACCOUNT_KEY_FORMAT + account.getId());
    }

    @Override
    public Account of(UUID id, boolean storeInRedis) {
        Account account = controller.of(id);

        if (account == null) {
            // Carregar direto do PostgreSQL
            String sql = "SELECT data FROM accounts WHERE id = ?";
            
            try (Connection conn = postgres.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setObject(1, id);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String jsonData = rs.getString("data");
                    account = Core.GSON.fromJson(jsonData, Account.class);
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return account;
    }

    @Override
    public Account of(String name) {
        Account account = controller.of(name);

        if (account == null) {
            String sql = "SELECT data FROM accounts WHERE LOWER(name) = LOWER(?)";
            
            try (Connection conn = postgres.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String jsonData = rs.getString("data");
                    account = Core.GSON.fromJson(jsonData, Account.class);
                }
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return account;
    }

    public List<Account> getAlts(String ipAddress) {
        List<Account> accounts = new ArrayList<>();

        /* IP local/loopback ou vazio é compartilhado por contas que nunca tiveram o IP real
         * capturado, então não representa uma conta alternativa de verdade. */
        if (isUnusableAddress(ipAddress))
            return accounts;

        String sql = "SELECT data FROM accounts WHERE data #>> '{context,ipAddress}' = ?";
        
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, ipAddress);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String jsonData = rs.getString("data");
                Account account = Core.GSON.fromJson(jsonData, Account.class);
                accounts.add(account);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return accounts;
    }

    private static boolean isUnusableAddress(String ipAddress) {
        if (ipAddress == null) return true;

        String ip = ipAddress.trim();

        return ip.isEmpty() || ip.equals("0.0.0.0") || ip.equals("::1") || ip.equals("::")
                || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.equalsIgnoreCase("localhost");
    }

    /**
     * Top contas por um campo numérico do JSON (ex: "context.eventWins").
     */
    public List<Account> top(String field, int limit) {
        List<Account> accounts = new ArrayList<>();
        String path = "{" + field.replace(".", ",") + "}";

        String sql = "SELECT data FROM accounts WHERE jsonb_typeof(data #> ?::text[]) = 'number' AND (data #>> ?::text[])::numeric > 0"
                + " ORDER BY (data #>> ?::text[])::numeric DESC LIMIT ?";

        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, path);
            stmt.setString(2, path);
            stmt.setString(3, path);
            stmt.setInt(4, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next())
                accounts.add(Core.GSON.fromJson(rs.getString("data"), Account.class));

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return accounts;
    }

    public boolean isExceededAccountLimit(String name, String ipAddress) {
        final int altSize = getAlts(ipAddress).size(),
                accountLimit = 3;

        Account existingAccount = of(name);

        return existingAccount == null && altSize >= accountLimit;
    }

    @Override
    public List<Account> list() {
        List<Account> accounts = new ArrayList<>();
        String sql = "SELECT data FROM accounts";
        
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String jsonData = rs.getString("data");
                Account account = Core.GSON.fromJson(jsonData, Account.class);
                accounts.add(account);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return accounts;
    }

    @Override
    public Collection<Account> ranking(String field, int limit) {
        List<Account> accounts = new ArrayList<>();
        String sql = "SELECT data FROM accounts ORDER BY (data->>?)::numeric DESC LIMIT ?";
        
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, field);
            stmt.setInt(2, limit * 2);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                String jsonData = rs.getString("data");
                Account account = Core.GSON.fromJson(jsonData, Account.class);
                accounts.add(account);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return accounts;
    }
}
