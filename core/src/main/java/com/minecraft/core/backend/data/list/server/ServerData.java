package com.minecraft.core.backend.data.list.server;

import com.minecraft.core.Core;
import com.minecraft.core.backend.database.postgresql.PostgresDatabase;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.payload.ServerPayload;
import com.minecraft.core.server.payload.whitelist.ServerWhitelist;
import com.minecraft.core.server.type.ServerType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;


import static com.minecraft.core.util.Util.sum;

public abstract class ServerData {

    private final String SERVER_KEY_FORMAT = "server:";

    /**
     * O meu proprio servidor, guardado em memoria.
     *
     * <p>Existe por causa de um deadlock real que aconteceu em producao. O
     * {@link Server#isDead()} considera morto quem nao manda heartbeat ha
     * 5 SEGUNDOS, e o heartbeat so e renovado por {@link #updatePlayerCounter}
     * e por {@link #refresh} - os dois depende de {@link #local()}, que so
     * achava o servidor lendo o Redis. Aí um travamento de 5 s (save de mundo,
     * GC, "Can't keep up!") bastava: qualquer um dos outros 8 servidores rodava
     * o {@link #cleanupDeadServers()} e apagava a chave. E com a chave
     * apagada o servidor nao se registrava nunca mais - `local()` voltava null,
     * `updatePlayerCounter` nao renewava, `refresh` nao reescrevia, e o
     * heartbeat ficava congelado para sempre.
     *
     * <p>O preco era um servidor inteiro invisivel para a rede (proxy nao
     * roteava, contadores sumiam, o lobby spammava NPE todo segundo) ate
     * alguem reiniciar o processo na mao. Guardar a instancia local faz
     * `local()` nunca devolver null depois do `start()`, e o proximo
     * `refresh()` recria a chave sozinho: o travamento vira um piscada de 1 s
     * em vez de uma queda.
     */
    private volatile Server localServer;

    public abstract int getPort();

    public void start(int maxPlayers) {
        ServerType serverType = Core.getServerType();
        
        // Limpar servidores mortos antes de registrar um novo
        cleanupDeadServers();

        // Verificar se já existe um servidor ativo com esta porta
        Server existingServer = of(getPort());
        
        if (existingServer != null) {
            // Se o servidor existe e está ativo (não está morto), apenas retornar
            if (!existingServer.isDead() && existingServer.getType().equals(serverType)) {
                this.localServer = existingServer;

                Core.getLogger().info("[ServerData] Servidor já registrado: " + existingServer.getIdentifier());
                return;
            }
            
            // Se o servidor está morto ou é de tipo diferente, remover o antigo
            Core.getLogger().info("[ServerData] Removendo servidor antigo/morto antes de registrar novo: " + existingServer.getIdentifier());
            Core.getRedis().delete(SERVER_KEY_FORMAT + existingServer.getIdentifier());
        }

        // Calcular novo ID baseado em servidores ATIVOS do mesmo tipo (não mortos)
        int possibleId = (int) getServerList(server -> 
            server.getType().equals(serverType) && !server.isDead()
        ).size() + 1;

        Server server = new Server(possibleId, getPort(), serverType, new ServerPayload(maxPlayers));

        /* Heartbeat inicial no futuro para evitar que o servidor morra durante o carregamento */
        server.getPayload().setHeartBeat(System.currentTimeMillis() + 120_000L);

        Core.setServerId(possibleId);
        Core.setServerPort(getPort());

        // Carregar whitelist do PostgreSQL
        loadWhitelist(server);

        // Forçar salvamento (permitir sobrescrever se necessário)
        String key = SERVER_KEY_FORMAT + server.getIdentifier();
        Core.getRedis().delete(key); // Garantir que não existe
        Core.getRedis().save(key, server);

        this.localServer = server;

        Core.getLogger().info("[ServerData] Servidor registrado com sucesso: " + server.getIdentifier());
    };

    public void stop() {
        Server server = local();

        /* Esvazia o cache junto: `stop()` roda no desligamento, e deixar a
         * instancia em memoria faria o `local()` continuar devolvendo um
         * servidor que ja foi apagado do Redis. */
        this.localServer = null;

        if (server != null)
            Core.getRedis().delete(SERVER_KEY_FORMAT + server.getIdentifier());
        else
            Core.getLogger().info("[Server] Não foi possível encerrar o servidor. Servidor não encontrado!");
    };

    public void refresh(Server server) {
        String key = SERVER_KEY_FORMAT + server.getIdentifier();

        if (Core.getRedis().exists(key)) {
            Core.getRedis().update(key, server);

            return;
        };

        /* A chave deste servidor pode sumir, e o `isDead()` de 5 s faz isso
         * sempre que o processo engasga 5 s (save de mundo, GC, "Can't keep
         * up!"), porque qualquer um dos outros 8 rodando `cleanupDeadServers()`
         * apaga o que acha velho demais.
         *
         * Sem isto, o efeito era o pior dos dois: o servidor continuava
         * batendo, `updatePlayerCounter` nao reclamava de nada (o `local()`
         * devolve a instancia em memoria) e mesmo assim cada batimento era
         * jogado fora com "[Redis] Tentativa de atualizar chave inexistente",
         * uma vez por segundo, para sempre. Reproduzido antes desta correcao:
         * chave apagada na mao, 22 tentativas em 4 s, zero recuperacao.
         *
         * SO PARA O SERVIDOR DESTE PROCESSO. `refresh()` e publico e o
         * /whitelist do proxy chama em servidor remoto: recriar la a chave de
         * um processo que morreu devolveria um servidor caido para a lista, e
         * o proxy comecaria a mandar jogador para o vazio. Quem nao esta vivo
         * nao pode ressuscitar - ele so volta pelo proprio `start()`. */
        if (server.getPort() != getPort()) {
            Core.getLogger().warning("[ServerData] A chave de " + key
                    + " sumiu e este processo nao e o dono dela. O servidor esta caido; "
                    + "so o start() dele mesmo pode trazer de volta.");

            return;
        };

        Core.getLogger().info("[ServerData] Chave de registro sumiu, recriando: " + key);

        Core.getRedis().save(key, server);
    };

    public void updatePlayerCounter(int players) {
        Server server = local();

        if (server != null) {
            ServerPayload payload = server.getPayload();

            payload.setHeartBeat(System.currentTimeMillis());
            payload.setPlayers(players);

            this.refresh(server);
        }
    }

    /* Manager Servers */
    /* Para avisar no console uma vez só (e de novo se a quantidade mudar) */
    private static int lastIgnored = -1;

    public List<Server> getServerList() {
        List<Server> list = Core.getRedis().loadAll(SERVER_KEY_FORMAT, Server.class);

        /*
         * Registros com tipo que esta source não conhece (ex.: servidores de outra rede/versão usando o mesmo Redis)
         * voltam com type nulo e quebravam tudo que percorre a lista (start, conexão no proxy, /whitelist...).
         */
        int before = list.size();

        list.removeIf(server -> server == null || server.getType() == null || server.getPayload() == null);

        int ignored = before - list.size();

        if (ignored != lastIgnored) {
            lastIgnored = ignored;

            if (ignored > 0)
                Core.getLogger().warning("[ServerData] Ignorando " + ignored + " servidor(es) do Redis com tipo desconhecido "
                        + "(provavelmente de outra rede ou versão usando o mesmo Redis).");
        }

        return list;
    };

    public List<Server> getServerList(Predicate<Server> predicate) {
        return getServerList().stream().filter(predicate).collect(Collectors.toList());
    };

    public Server of(Predicate<Server> predicate) {
        return getServerList(predicate).stream().findFirst().orElse(null);
    };

    public Server of(int port) {
        return of(server -> server.getPort() == port);
    };

    public Server of(ServerType serverType) {
        List<Server> serverList = getServerList(server -> server.getType().equals(serverType));

        if (serverList.isEmpty()) {
            return null;
        }

        // Primeiro, tentar encontrar um servidor disponível (não cheio e não morto)
        Optional<Server> bestServer = serverList.stream().filter(server -> !server.getPayload().isFull() && !server.isDead()).findFirst();

        if (bestServer.isPresent()) {
            return bestServer.get();
        }

        // Se não encontrou um servidor disponível, tentar encontrar qualquer servidor não morto
        Optional<Server> aliveServer = serverList.stream().filter(server -> !server.isDead()).findFirst();

        if (aliveServer.isPresent()) {
            return aliveServer.get();
        }

        // Se todos estão mortos, retornar o primeiro servidor encontrado (fallback)
        return serverList.stream().findFirst().orElse(null);
    };

    public Server of(String identifier) {
        return of(server -> server.getIdentifier().equalsIgnoreCase(identifier));
    };

    public Server findByNameAndId(String nameAndId) {
        return of(server -> server.getNameAndId().equalsIgnoreCase(nameAndId));
    };
    
    public Server findByProxyServer(net.md_5.bungee.api.config.ServerInfo serverInfo) {
        if (serverInfo == null) return null;
        return of(server -> {
            net.md_5.bungee.api.config.ServerInfo proxyServer = server.getProxyServer();
            return proxyServer != null && proxyServer.getName().equals(serverInfo.getName());
        });
    };

    /**
     * O servidor deste processo.
     *
     * <p>O Redis manda, porque e ele quem tem o heartbeat atualizado de todo
     * mundo. Mas quando a chave deste servidor nao esta la - que e
     * exatamente o que o {@link Server#isDead()} de 5 s provoca quando o
     * processo trava - a resposta e a instancia em memoria, nunca null.
     *
     * <p>Devolver a instancia e o que fecha o ciclo: o proximo
     * {@link #refresh()} reescreve a chave e o servidor reaparece na rede sem
     * ninguem reiniciar nada. Antes este metodo devolvia null, e o chamador
     * que precisa do `isArcade()` (o lobby, todo segundo) quebrava com NPE.
     */
    public Server local() {
        /* O ping do proxy e o heartbeat do lobby chamam este metodo o tempo
         * todo. Ir no Redis (KEYS + HGETALL de todo mundo) em cada chamada
         * travava o ProxyPingEvent por segundos quando o Jedis dava timeout.
         * Depois do start() a instancia local ja esta em memoria e e a mesma
         * que o refresh() atualiza, entao o cache e seguro. */
        Server cached = localServer;

        if (cached != null && cached.getPort() == getPort())
            return cached;

        Server server = of(getPort());

        if (server != null) {
            this.localServer = server;

            return server;
        };

        if (localServer == null)
            Core.getLogger().warning("[ServerData] local() sem servidor no Redis e sem cache para a porta "
                    + getPort() + ". O start() desta instancia nao rodou?");

        return localServer;
    }

    public boolean isNeedFindAnother(Server server) {
        return server.isArcade() && server.getPayload().getPlayers() >= 100;
    };

    public int getOnlinePlayers() {
        return getOnlinePlayers(ServerType.BUNGEE);
    };

    public int getOnlinePlayers(ServerType... serverTypes) {
        List<ServerType> list = Arrays.asList(serverTypes);

        return sum(getServerList(server -> server.getPayload() != null && list.contains(server.getType())
                && (server.getType() == ServerType.BUNGEE || !server.isDead()))
                .stream()
                .map(server -> server.getPayload().getPlayers())
                .collect(Collectors.toList()));
    };

    public void cleanupDeadServers() {
        try {
            // Executar de forma assíncrona para não bloquear a inicialização
            Core.getPlatform().runAsync(() -> {
                try {
                    List<Server> deadServers = getServerList(Server::isDead);
                    
                    if (deadServers.isEmpty()) {
                        return;
                    };
                    
                    Core.getLogger().info("[ServerData] Limpando " + deadServers.size() + " servidor(es) morto(s)...");
                    
                    for (Server server : deadServers) {
                        // Nunca limpar o proxy (BUNGEE): e o backbone de roteamento
                        // da rede e a fonte do contador (getOnlinePlayers). Um lag
                        // de batimento aqui apagava a chave e fazia a scoreboard
                        // piscar 0/1.
                        if (server.getType() == ServerType.BUNGEE)
                            continue;

                        String key = SERVER_KEY_FORMAT + server.getIdentifier();
                        Core.getRedis().delete(key);
                        Core.getLogger().info("[ServerData] Servidor morto removido: " + server.getIdentifier() + " (Última atualização: " + 
                            (System.currentTimeMillis() - server.getPayload().getHeartBeat()) + "ms atrás)");
                    }
                } catch (Exception e) {
                    Core.getLogger().severe("[ServerData] Erro ao limpar servidores mortos: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            Core.getLogger().severe("[ServerData] Erro ao iniciar limpeza de servidores mortos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Salva a whitelist de um servidor no PostgreSQL
     */
    public void saveWhitelist(Server server) {
        PostgresDatabase postgres = Core.getPostgres();
        
        if (postgres == null) {
            Core.getLogger().warning("[ServerData] PostgreSQL não está disponível para salvar whitelist!");
            return;
        }

        // Criar tabela se não existir
        createWhitelistTable(postgres);

        ServerWhitelist whitelist = server.getWhitelist();
        String serverIdentifier = server.getNameAndId();

        try (Connection conn = postgres.getConnection()) {
            // Verificar se já existe
            String checkSql = "SELECT id FROM server_whitelists WHERE server_identifier = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, serverIdentifier);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    // Atualizar
                    String updateSql = "UPDATE server_whitelists SET enabled = ?, players = ?::jsonb, updated_at = CURRENT_TIMESTAMP WHERE server_identifier = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setBoolean(1, whitelist.isEnabled());
                        updateStmt.setString(2, Core.GSON.toJson(whitelist.getPlayers()));
                        updateStmt.setString(3, serverIdentifier);
                        updateStmt.executeUpdate();
                    }
                } else {
                    // Inserir
                    String insertSql = "INSERT INTO server_whitelists (server_identifier, enabled, players) VALUES (?, ?, ?::jsonb)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, serverIdentifier);
                        insertStmt.setBoolean(2, whitelist.isEnabled());
                        insertStmt.setString(3, Core.GSON.toJson(whitelist.getPlayers()));
                        insertStmt.executeUpdate();
                    }
                }
            };

            Core.getLogger().info("[ServerData] Whitelist salva para " + serverIdentifier);

        } catch (SQLException e) {
            Core.getLogger().severe("[ServerData] Erro ao salvar whitelist: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Carrega a whitelist de um servidor do PostgreSQL
     */
    public void loadWhitelist(Server server) {
        PostgresDatabase postgres = Core.getPostgres();
        
        if (postgres == null) {
            return;
        };

        String serverIdentifier = server.getNameAndId();

        try (Connection conn = postgres.getConnection()) {
            // Criar tabela se não existir antes de tentar ler
            createWhitelistTable(postgres);
            
            String sql = "SELECT enabled, players FROM server_whitelists WHERE server_identifier = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, serverIdentifier);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    ServerWhitelist whitelist = server.getWhitelist();
                    whitelist.setEnabled(rs.getBoolean("enabled"));
                    
                    String playersJson = rs.getString("players");
                    if (playersJson != null) {
                        List<String> players = Core.GSON.fromJson(playersJson, List.class);
                        whitelist.getPlayers().clear();
                        whitelist.getPlayers().addAll(players);
                    };

                    Core.getLogger().info("[ServerData] Whitelist carregada para " + serverIdentifier + 
                                         " (Ativa: " + whitelist.isEnabled() + ", Jogadores: " + whitelist.getPlayers().size() + ")");
                }
            }

        } catch (SQLException e) {
            Core.getLogger().warning("[ServerData] Erro ao carregar whitelist: " + e.getMessage() + " (ignorando...)");
            // Não fazer stack trace completo, apenas logar o erro
        }
    }

    /**
     * Cria a tabela de whitelists no PostgreSQL
     */
    private void createWhitelistTable(PostgresDatabase postgres) {
        try (Connection conn = postgres.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "CREATE TABLE IF NOT EXISTS server_whitelists (" +
                "    id SERIAL PRIMARY KEY," +
                "    server_identifier VARCHAR(50) UNIQUE NOT NULL," +
                "    enabled BOOLEAN DEFAULT false," +
                "    players JSONB DEFAULT '[]'::jsonb," +
                "    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ");" +
                "CREATE INDEX IF NOT EXISTS idx_server_whitelists_identifier ON server_whitelists(server_identifier);"
                )) {
            
            stmt.execute();

        } catch (SQLException e) {
            Core.getLogger().severe("[ServerData] Erro ao criar tabela de whitelists: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


