package com.minecraft.core.bungee.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraft.core.Constant;
import com.minecraft.core.Core;
import com.minecraft.core.account.Account;
import com.minecraft.core.account.context.AccountContext;
import com.minecraft.core.account.context.objects.assignment.Assignment;
import com.minecraft.core.account.context.objects.medal.Medal;
import com.minecraft.core.account.context.objects.permission.Permission;
import com.minecraft.core.account.context.objects.rank.Rank;
import com.minecraft.core.account.context.objects.rank.info.RankInfo;
import com.minecraft.core.account.context.objects.rank.type.RankType;
import com.minecraft.core.account.context.objects.tag.Tag;
import com.minecraft.core.editor.EditorActions;
import com.minecraft.core.editor.EditorSessions;
import com.minecraft.core.editor.RankPermissions;
import com.minecraft.core.server.Server;
import com.minecraft.core.server.access.NetworkAccess;
import com.minecraft.core.server.payload.whitelist.ServerWhitelist;
import com.minecraft.core.server.type.ServerType;
import lombok.Getter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * API do editor de permissões. Tudo responde JSON; erros são {"error":{"code","message"}}.
 */
public class EditorApi {

    private static final Pattern NODE_PATTERN = Pattern.compile("^[a-z0-9_.*+-]{1,128}$");
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{32,36}$");

    private static final int MAX_CHANGES = 500, MAX_NODES = 5000;

    @Getter
    public static class Response {

        private final int status;
        private final JsonElement body;

        private Response(int status, JsonElement body) {
            this.status = status;
            this.body = body;
        }

        static Response ok(JsonElement body) {
            return new Response(200, body);
        }

        static Response error(int status, String code, String message) {
            JsonObject error = new JsonObject();
            error.addProperty("code", code);
            error.addProperty("message", message);

            JsonObject body = new JsonObject();
            body.add("error", error);

            return new Response(status, body);
        }
    }

    private static class Invalid extends RuntimeException {
        private final String code;

        Invalid(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    /* ---------------- Roteamento ---------------- */

    public Response handle(String method, String path, String query, String authorization, String body) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7) ? authorization.substring(7).trim() : null;

        if (token == null || token.isEmpty())
            return Response.error(401, "editor.unauthenticated", "Missing bearer token");

        EditorSessions.Session session;

        try {
            session = EditorSessions.find(token);
        } catch (Exception e) {
            return Response.error(503, "editor.unavailable", "Redis unavailable");
        }

        if (session == null)
            return Response.error(401, "editor.session-expired", "The editor session expired or does not exist");

        /* Quem perdeu a permissão é recusado e a sessão é encerrada */
        Account actor = Core.getAccountData().of(session.getActorUniqueId(), false);

        if (actor == null || !canEdit(actor)) {
            EditorSessions.close(token);
            return Response.error(403, "editor.forbidden", "The session owner lost the editor permission");
        }

        try {
            if (method.equals("GET")) {
                if (path.equals("/api/session")) return session(session, actor);
                if (path.equals("/api/access")) return access(actor);
                if (path.equals("/api/data")) return Response.ok(data());
                if (path.equals("/api/users/search")) return searchUser(param(query, "name"));
                if (path.startsWith("/api/users/")) return user(path.substring("/api/users/".length()));
                if (path.startsWith("/api/ranks/") && path.endsWith("/members"))
                    return members(path.substring("/api/ranks/".length(), path.length() - "/members".length()));
                if (path.equals("/api/actions")) return actions(param(query, "limit"));
            } else {
                if (path.equals("/api/changes")) return save(session, body);
                if (path.equals("/api/access")) return saveAccess(session, actor, body);
                if (path.equals("/api/access/server")) return saveServerWhitelist(session, actor, body);

                if (path.equals("/api/logout")) {
                    EditorSessions.close(token);
                    return Response.ok(new JsonObject());
                }
            }
        } catch (Invalid invalid) {
            return Response.error(400, invalid.code, invalid.getMessage());
        } catch (Exception e) {
            Core.getLogger().warning("[Editor] Erro na API (" + path + "): " + e);
            return Response.error(503, "editor.unavailable", "The server could not finish the request");
        }

        return Response.error(404, "editor.not-found", "Not found");
    }

    /* A página "Acesso" (manutenção, slots, whitelists) é só para Admin+ e desenvolvedores */
    public static boolean canEditAccess(Account account) {
        return account.hasRank(RankType.ADMIN) || Core.isUntouchable(account.getId());
    }

    public static boolean canEdit(Account account) {
        return account.hasRank(RankType.ADMIN) || account.hasPermission(EditorSessions.PERMISSION) || Core.isUntouchable(account.getId());
    }

    private static String param(String query, String name) {
        if (query == null) return null;

        for (String part : query.split("&")) {
            int separator = part.indexOf('=');

            if (separator > 0 && part.substring(0, separator).equals(name))
                return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
        }

        return null;
    }

    /* ---------------- Leitura ---------------- */

    private Response session(EditorSessions.Session session, Account actor) {
        JsonObject json = new JsonObject();
        json.addProperty("canEditAccess", canEditAccess(actor));
        json.addProperty("actor", session.getActor());
        json.addProperty("actorId", session.getActorId());
        json.addProperty("createdAt", session.getCreatedAt());
        json.addProperty("expiresAt", session.getExpiresAt());
        json.addProperty("now", System.currentTimeMillis());
        return Response.ok(json);
    }

    static String amp(String text) {
        return text == null ? "" : text.replace('§', '&');
    }

    static String id(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private JsonObject data() {
        JsonObject json = new JsonObject();

        json.addProperty("defaultRank", id(RankType.MEMBER));
        json.addProperty("editorPermission", EditorSessions.PERMISSION);

        JsonArray ranks = new JsonArray();

        for (RankType type : RankType.values()) {
            JsonObject rank = new JsonObject();

            rank.addProperty("id", id(type));
            rank.addProperty("name", type.getName());
            rank.addProperty("color", amp(type.getColor().toString()));
            rank.addProperty("role", type.getRole().name());
            rank.addProperty("weight", type.ordinal());

            Tag tag = Tag.of(type);
            rank.addProperty("tag", tag != null ? id(tag) : null);

            JsonArray base = new JsonArray();
            type.getPermissions().forEach(base::add);
            rank.add("base", base);

            JsonArray nodes = new JsonArray();

            for (RankPermissions.Node node : RankPermissions.nodes(type))
                nodes.add(node(node.getKey(), node.getExpiresAt(), node.getAddedAt()));

            rank.add("nodes", nodes);
            ranks.add(rank);
        }

        json.add("ranks", ranks);

        JsonArray tags = new JsonArray();

        for (Tag tag : Tag.values()) {
            JsonObject object = new JsonObject();

            object.addProperty("id", id(tag));
            object.addProperty("name", tag.getName());
            object.addProperty("prefix", amp(tag.getPrefix()));
            object.addProperty("color", amp(tag.getColor().toString()));
            object.addProperty("role", tag.getRole().name());
            object.addProperty("source", id(tag.getSource()));
            object.addProperty("permission", tag.getPermission());
            object.addProperty("weight", tag.ordinal());

            tags.add(object);
        }

        json.add("tags", tags);

        JsonArray medals = new JsonArray();

        for (Medal medal : Medal.values()) {
            if (medal == Medal.NONE) continue;

            JsonObject object = new JsonObject();

            object.addProperty("id", id(medal));
            object.addProperty("name", medal.getName());
            object.addProperty("symbol", medal.getSymbol());
            object.addProperty("color", amp(medal.getColor().toString()));
            object.addProperty("permission", medal.getPermission());
            object.addProperty("weight", medal.ordinal());

            medals.add(object);
        }

        json.add("medals", medals);

        return json;
    }

    private static JsonObject node(String key, long expiresAt, long addedAt) {
        JsonObject node = new JsonObject();

        node.addProperty("key", key);

        if (expiresAt > 0)
            node.addProperty("expiresAt", expiresAt);
        else
            node.add("expiresAt", null);

        node.addProperty("addedAt", addedAt);
        return node;
    }

    private Response searchUser(String name) {
        if (name == null || name.trim().isEmpty() || name.length() > 36)
            throw new Invalid("editor.invalid-request", "Name required");

        Account account = UUID_PATTERN.matcher(name).matches() ? findById(name) : Core.getAccountData().of(name.trim());

        if (account == null)
            return Response.error(404, "editor.user-not-found", "User not found");

        return Response.ok(user(account));
    }

    private Response user(String raw) {
        Account account = findById(raw);

        if (account == null)
            return Response.error(404, "editor.user-not-found", "User not found");

        return Response.ok(user(account));
    }

    private static Account findById(String raw) {
        try {
            String clean = raw.length() == 32
                    ? raw.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")
                    : raw;

            return Core.getAccountData().of(UUID.fromString(clean), false);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private JsonObject user(Account account) {
        AccountContext context = account.getContext();

        JsonObject json = new JsonObject();

        json.addProperty("id", account.getId().toString());
        json.addProperty("username", account.getName());
        json.addProperty("premium", context.isPremium());
        json.addProperty("online", context.isOnline());
        json.addProperty("firstSeenAt", context.getCreatedAt());
        json.addProperty("lastSeenAt", context.getLastLogin());

        JsonArray ranks = new JsonArray();

        for (Rank rank : account.getAllRanks()) {
            if (rank == null || rank.getType() == RankType.MEMBER) continue;

            JsonObject object = new JsonObject();
            object.addProperty("rank", id(rank.getType()));

            if (rank.isPermanent())
                object.add("expiresAt", null);
            else
                object.addProperty("expiresAt", rank.getExpiresAt());

            object.addProperty("assignedAt", rank.getAssignedAt());
            object.addProperty("author", rank.getAuthorName());
            ranks.add(object);
        }

        json.add("ranks", ranks);

        JsonArray nodes = new JsonArray();

        for (Permission permission : account.getPermissions())
            nodes.add(node(permission.getKey(), permission.isPermanent() ? -1 : permission.getExpiresAt(), permission.getAssignedAt()));

        json.add("nodes", nodes);

        JsonArray medals = new JsonArray();

        for (Medal medal : context.getMedals()) {
            if (medal != null && medal != Medal.NONE)
                medals.add(id(medal));
        }

        json.add("medals", medals);

        Medal selectedMedal = context.getMedal();
        json.addProperty("selectedMedal", selectedMedal != null && selectedMedal != Medal.NONE ? id(selectedMedal) : null);

        Tag selectedTag = context.getTag();
        json.addProperty("selectedTag", selectedTag != null ? id(selectedTag) : null);

        return json;
    }

    private Response members(String rankId) throws Exception {
        RankType rank = rank(rankId);

        JsonObject json = new JsonObject();
        JsonArray members = new JsonArray();

        try (Connection conn = Core.getPostgres().getConnection()) {
            try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM accounts WHERE data #>> '{context,rankInfo,rank,type}' = ?")) {
                count.setString(1, rank.name());

                ResultSet rs = count.executeQuery();
                json.addProperty("total", rs.next() ? rs.getInt(1) : 0);
            }

            try (PreparedStatement list = conn.prepareStatement("SELECT id, name FROM accounts WHERE data #>> '{context,rankInfo,rank,type}' = ? ORDER BY LOWER(name) LIMIT 200")) {
                list.setString(1, rank.name());

                ResultSet rs = list.executeQuery();

                while (rs.next()) {
                    JsonObject member = new JsonObject();
                    member.addProperty("id", rs.getString("id"));
                    member.addProperty("username", rs.getString("name"));
                    members.add(member);
                }
            }
        }

        json.add("members", members);
        return Response.ok(json);
    }

    private Response actions(String rawLimit) {
        int limit = 100;

        try {
            if (rawLimit != null)
                limit = Math.max(1, Math.min(500, Integer.parseInt(rawLimit)));
        } catch (NumberFormatException ignored) {
        }

        JsonArray json = new JsonArray();

        for (EditorActions.Action action : EditorActions.recent(limit)) {
            JsonObject object = new JsonObject();
            object.addProperty("at", action.getAt());
            object.addProperty("actor", action.getActor());
            object.addProperty("description", action.getDescription());
            json.add(object);
        }

        return Response.ok(json);
    }

    /* ---------------- Salvar ---------------- */

    private Response save(EditorSessions.Session session, String body) throws Exception {
        JsonObject root;

        try {
            root = Core.PARSER.parse(body).getAsJsonObject();
        } catch (Exception e) {
            throw new Invalid("editor.invalid-request", "Invalid JSON");
        }

        if (!root.has("changes") || !root.get("changes").isJsonArray())
            throw new Invalid("editor.invalid-request", "Missing changes");

        JsonArray changes = root.getAsJsonArray("changes");

        if (changes.size() > MAX_CHANGES)
            throw new Invalid("editor.invalid-request", "Too many changes");

        UUID actorId = session.getActorUniqueId();

        /* Valida tudo antes de gravar qualquer coisa */
        List<Runnable> apply = new ArrayList<>();
        List<String> log = new ArrayList<>();
        Map<RankType, List<RankPermissions.Node>> rankChanges = new LinkedHashMap<>();

        for (JsonElement element : changes) {
            JsonObject change = element.getAsJsonObject();
            String type = string(change, "type");

            if ("put-rank".equals(type)) {
                JsonObject rank = change.getAsJsonObject("rank");
                RankType rankType = rank(string(rank, "id"));

                List<RankPermissions.Node> nodes = rankNodes(rank.getAsJsonArray("nodes"));
                rankChanges.put(rankType, nodes);

                log.add("rank " + id(rankType) + ": " + diffKeys(
                        RankPermissions.nodes(rankType).stream().map(RankPermissions.Node::getKey).collect(Collectors.toList()),
                        nodes.stream().map(RankPermissions.Node::getKey).collect(Collectors.toList())));
            } else if ("put-user".equals(type)) {
                prepareUser(change.getAsJsonObject("user"), actorId, apply, log);
            } else
                throw new Invalid("editor.invalid-request", "Unknown change type: " + type);
        }

        for (Map.Entry<RankType, List<RankPermissions.Node>> entry : rankChanges.entrySet())
            RankPermissions.replace(entry.getKey(), entry.getValue());

        apply.forEach(Runnable::run);

        if (!rankChanges.isEmpty()) {
            RankPermissions.load();
            RankPermissions.publish();
        }

        EditorActions.log(session.getActor(), log);

        Core.getLogger().info("[Editor] " + session.getActor() + " salvou " + changes.size() + " alteração(ões): " + String.join(" | ", log));

        JsonObject json = new JsonObject();
        json.addProperty("saved", changes.size());
        json.add("data", data());
        return Response.ok(json);
    }

    private void prepareUser(JsonObject json, UUID actorId, List<Runnable> apply, List<String> log) {
        Account account = findById(string(json, "id"));

        if (account == null)
            throw new Invalid("editor.user-not-found", "User not found");

        AccountContext context = account.getContext();

        /* Ranks: o de maior peso vira o ativo, o resto fica na lista de ranks disponíveis */
        List<Rank> current = account.getAllRanks().stream().filter(Objects::nonNull).collect(Collectors.toList());
        List<Rank> ranks = new ArrayList<>();
        Set<RankType> seen = EnumSet.noneOf(RankType.class);

        for (JsonElement element : array(json, "ranks")) {
            JsonObject object = element.getAsJsonObject();
            RankType type = rank(string(object, "rank"));

            if (type == RankType.MEMBER || !seen.add(type)) continue;

            long expiresAt = expiry(object);

            Rank existing = current.stream()
                    .filter(rank -> rank.getType() == type && (rank.isPermanent() ? expiresAt < 0 : rank.getExpiresAt() == expiresAt))
                    .findFirst().orElse(null);

            ranks.add(existing != null ? existing : Rank.builder().type(type).assignment(Assignment.STAFF).author(actorId).expiresAt(expiresAt).build());
        }

        Rank active = ranks.stream().filter(rank -> !rank.hasExpired())
                .max(Comparator.comparing(rank -> rank.getType().ordinal()))
                .orElse(Rank.builder().type(RankType.MEMBER).build());

        List<Rank> available = ranks.stream().filter(rank -> rank != active).collect(Collectors.toList());

        /* Permissões */
        JsonArray nodesJson = array(json, "nodes");

        if (nodesJson.size() > MAX_NODES)
            throw new Invalid("editor.invalid-request", "Too many permissions");

        List<Permission> permissions = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (JsonElement element : nodesJson) {
            JsonObject object = element.getAsJsonObject();
            String key = validKey(string(object, "key"));
            long expiresAt = expiry(object);

            if (!keys.add(key)) continue;

            Permission existing = account.getPermissions().stream()
                    .filter(permission -> permission.getKey().equalsIgnoreCase(key)
                            && (permission.isPermanent() ? expiresAt < 0 : permission.getExpiresAt() == expiresAt))
                    .findFirst().orElse(null);

            permissions.add(existing != null ? existing : new Permission(key, Assignment.STAFF, actorId, expiresAt));
        }

        /* Medalhas da coleção (permanentes) */
        List<Medal> medals = new ArrayList<>();
        medals.add(Medal.NONE);

        for (JsonElement element : array(json, "medals")) {
            Medal medal = medal(element.getAsString());

            if (medal != Medal.NONE && !medals.contains(medal))
                medals.add(medal);
        }

        Medal selectedMedal = json.has("selectedMedal") && !json.get("selectedMedal").isJsonNull()
                ? medal(json.get("selectedMedal").getAsString()) : Medal.NONE;

        Tag selectedTag = json.has("selectedTag") && !json.get("selectedTag").isJsonNull()
                ? tag(json.get("selectedTag").getAsString()) : Tag.of(active.getType());

        if (selectedTag == null)
            selectedTag = Tag.MEMBER;

        /* Descrição para o histórico */
        List<String> parts = new ArrayList<>();

        String rankDiff = diffKeys(current.stream().filter(rank -> rank.getType() != RankType.MEMBER).map(rank -> id(rank.getType())).collect(Collectors.toList()),
                ranks.stream().map(rank -> id(rank.getType())).collect(Collectors.toList()));

        if (!rankDiff.equals("sem mudanças")) parts.add("ranks " + rankDiff);

        String permissionDiff = diffKeys(account.getPermissions().stream().map(Permission::getKey).collect(Collectors.toList()),
                permissions.stream().map(Permission::getKey).collect(Collectors.toList()));

        if (!permissionDiff.equals("sem mudanças")) parts.add("permissões " + permissionDiff);

        String medalDiff = diffKeys(context.getMedals().stream().filter(medal -> medal != Medal.NONE).map(EditorApi::id).collect(Collectors.toList()),
                medals.stream().filter(medal -> medal != Medal.NONE).map(EditorApi::id).collect(Collectors.toList()));

        if (!medalDiff.equals("sem mudanças")) parts.add("medalhas " + medalDiff);

        if (context.getMedal() != selectedMedal) parts.add("medalha escolhida " + id(context.getMedal()) + " → " + id(selectedMedal));
        if (context.getTag() != selectedTag) parts.add("tag " + (context.getTag() != null ? id(context.getTag()) : "?") + " → " + id(selectedTag));

        log.add("jogador " + account.getName() + ": " + (parts.isEmpty() ? "sem mudanças" : String.join(", ", parts)));

        Medal finalMedal = selectedMedal;
        Tag finalTag = selectedTag;

        apply.add(() -> {
            RankType previous = account.getRankType();

            RankInfo info = new RankInfo();
            info.setRank(active);
            info.setAvailableRanks(available);

            context.setRankInfo(info);

            account.getPermissions().clear();
            account.getPermissions().addAll(permissions);

            context.getMedals().clear();
            context.getMedals().addAll(medals);

            context.setMedal(finalMedal);
            context.setTag(finalTag);

            account.saveContext(context);

            /* Mesmo aviso do setRank: os servidores atualizam tag e permissões do jogador */
            if (previous != active.getType()) {
                JsonObject message = new JsonObject();
                message.addProperty("id", account.getId().toString());
                message.addProperty("rank", Core.GSON.toJson(active));

                Core.getRedis().publish(Constant.REDIS_ACCOUNT_RANK_UPDATE_CHANNEL, message.toString());
            }
        });
    }

    /* ---------------- Validação ---------------- */

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
            throw new Invalid("editor.invalid-request", "Missing " + key);

        return object.get(key).getAsString();
    }

    private static JsonArray array(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull())
            return new JsonArray();

        if (!object.get(key).isJsonArray())
            throw new Invalid("editor.invalid-request", key + " must be a list");

        return object.getAsJsonArray(key);
    }

    /* -1 = permanente; datas no passado são recusadas */
    private static long expiry(JsonObject object) {
        if (!object.has("expiresAt") || object.get("expiresAt").isJsonNull())
            return -1L;

        long expiresAt = object.get("expiresAt").getAsLong();

        if (expiresAt <= System.currentTimeMillis())
            throw new Invalid("editor.invalid-request", "Expiration in the past");

        return expiresAt;
    }

    private static String validKey(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);

        if (!NODE_PATTERN.matcher(key).matches())
            throw new Invalid("editor.invalid-request", "Invalid permission: " + raw);

        return key;
    }

    private static List<RankPermissions.Node> rankNodes(JsonArray json) {
        if (json == null) return new ArrayList<>();

        if (json.size() > MAX_NODES)
            throw new Invalid("editor.invalid-request", "Too many permissions");

        Map<String, RankPermissions.Node> nodes = new LinkedHashMap<>();

        for (JsonElement element : json) {
            JsonObject object = element.getAsJsonObject();
            String key = validKey(string(object, "key"));

            long addedAt = object.has("addedAt") && !object.get("addedAt").isJsonNull()
                    ? Math.min(object.get("addedAt").getAsLong(), System.currentTimeMillis()) : System.currentTimeMillis();

            nodes.putIfAbsent(key, new RankPermissions.Node(key, expiry(object), addedAt));
        }

        return new ArrayList<>(nodes.values());
    }

    private static RankType rank(String id) {
        try {
            return RankType.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Invalid("permission.unknown-rank", "Unknown rank: " + id);
        }
    }

    private static Tag tag(String id) {
        try {
            return Tag.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Invalid("permission.unknown-tag", "Unknown tag: " + id);
        }
    }

    private static Medal medal(String id) {
        try {
            return Medal.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Invalid("permission.unknown-medal", "Unknown medal: " + id);
        }
    }

    /* ---------------- Acesso (manutenção, slots, servidores) ---------------- */

    private static final Pattern NICK_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final int MAX_LIST = 500;

    private void requireAccess(Account actor) {
        if (!canEditAccess(actor))
            throw new Invalid("access.forbidden", "Only Admin+ can edit network access");
    }

    private Response access(Account actor) {
        requireAccess(actor);

        NetworkAccess access = NetworkAccess.load();

        if (access == null)
            throw new Invalid("editor.unavailable", "PostgreSQL unavailable");

        JsonObject json = new JsonObject();

        json.add("access", Core.GSON.toJsonTree(access));
        json.addProperty("online", Core.getServerData().getOnlinePlayers());

        JsonArray types = new JsonArray();

        for (ServerType type : ServerType.values()) {
            if (type == ServerType.BUNGEE || type == ServerType.AUTH) continue;

            JsonObject object = new JsonObject();
            object.addProperty("id", type.name());
            object.addProperty("name", type.getName());
            object.addProperty("lobby", type.isLobby());
            types.add(object);
        }

        json.add("serverTypes", types);

        JsonArray servers = new JsonArray();

        for (Server server : Core.getServerData().getServerList()) {
            JsonObject object = new JsonObject();
            ServerWhitelist whitelist = server.getWhitelist();

            object.addProperty("identifier", server.getIdentifier());
            object.addProperty("name", server.getNameAndId());
            object.addProperty("type", server.getType().name());
            object.addProperty("typeName", server.getType().getName());
            object.addProperty("players", server.getPayload().getPlayers());
            object.addProperty("maxPlayers", server.getPayload().getMaxPlayers());
            object.addProperty("online", !server.isDead());

            JsonObject list = new JsonObject();
            list.addProperty("enabled", whitelist.isEnabled());

            JsonArray ranks = new JsonArray();
            whitelist.getRanks().forEach(rank -> ranks.add(id(rank)));
            list.add("ranks", ranks);

            JsonArray players = new JsonArray();
            whitelist.getPlayers().forEach(players::add);
            list.add("players", players);

            object.add("whitelist", list);
            servers.add(object);
        }

        json.add("servers", servers);
        return Response.ok(json);
    }

    private Response saveAccess(EditorSessions.Session session, Account actor, String body) throws Exception {
        requireAccess(actor);

        JsonObject root = parse(body);
        JsonObject json = root.has("access") && root.get("access").isJsonObject() ? root.getAsJsonObject("access") : null;

        if (json == null)
            throw new Invalid("editor.invalid-request", "Missing access");

        NetworkAccess before = NetworkAccess.load();

        if (before == null)
            throw new Invalid("editor.unavailable", "PostgreSQL unavailable");

        NetworkAccess access = new NetworkAccess();

        access.setMaintenance(json.has("maintenance") && json.get("maintenance").getAsBoolean());
        access.setMaintenanceRank(id(rank(string(json, "maintenanceRank"))));
        access.setMaintenancePlayers(nicks(array(json, "maintenancePlayers")));
        access.setMaintenanceMotd(text(json, "maintenanceMotd", 80));
        access.setMaintenanceMessage(text(json, "maintenanceMessage", 600));

        int slots;

        try {
            slots = json.get("slots").getAsInt();
        } catch (Exception e) {
            throw new Invalid("editor.invalid-request", "Invalid slots");
        }

        if (slots < 0 || slots > 100000)
            throw new Invalid("editor.invalid-request", "Slots out of range");

        access.setSlots(slots);
        access.setSlotsRank(id(rank(string(json, "slotsRank"))));
        access.setFullMessage(text(json, "fullMessage", 600));

        Map<String, String> serverRanks = new LinkedHashMap<>();

        if (json.has("serverRanks") && json.get("serverRanks").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("serverRanks").entrySet()) {
                ServerType type;

                try {
                    type = ServerType.valueOf(entry.getKey());
                } catch (IllegalArgumentException e) {
                    throw new Invalid("editor.invalid-request", "Unknown server type: " + entry.getKey());
                }

                if (entry.getValue().isJsonNull() || entry.getValue().getAsString().isEmpty()) continue;

                RankType rank = rank(entry.getValue().getAsString());

                if (rank != RankType.MEMBER)
                    serverRanks.put(type.name(), id(rank));
            }
        }

        access.setServerRanks(serverRanks);

        NetworkAccess.save(access, session.getActor());

        List<String> log = describe(before, access);

        if (log.isEmpty())
            log.add("acesso: salvo sem mudanças");

        EditorActions.log(session.getActor(), log);
        Core.getLogger().info("[Editor] " + session.getActor() + " alterou o acesso da rede: " + String.join(" | ", log));

        return access(actor);
    }

    private Response saveServerWhitelist(EditorSessions.Session session, Account actor, String body) throws Exception {
        requireAccess(actor);

        JsonObject root = parse(body);
        Server server = Core.getServerData().of(string(root, "identifier"));

        if (server == null)
            throw new Invalid("access.server-not-found", "Server not found");

        boolean enabled = root.has("enabled") && root.get("enabled").getAsBoolean();

        List<RankType> ranks = new ArrayList<>();

        for (JsonElement element : array(root, "ranks")) {
            RankType rank = rank(element.getAsString());

            if (!ranks.contains(rank))
                ranks.add(rank);
        }

        List<String> players = nicks(array(root, "players"));

        boolean wasEnabled = server.getWhitelist().isEnabled();

        server.setWhitelist(enabled, ranks, players);
        server.refresh();
        Core.getServerData().saveWhitelist(server);

        String description = "whitelist " + server.getNameAndId() + ": " + (enabled ? "ativa" : "desativada")
                + (enabled != wasEnabled ? " (antes " + (wasEnabled ? "ativa" : "desativada") + ")" : "")
                + ", cargos [" + ranks.stream().map(EditorApi::id).collect(Collectors.joining(", ")) + "]"
                + ", " + players.size() + " jogador(es)";

        EditorActions.log(session.getActor(), Collections.singletonList(description));
        Core.getLogger().info("[Editor] " + session.getActor() + " alterou a " + description);

        return access(actor);
    }

    private static JsonObject parse(String body) {
        try {
            return Core.PARSER.parse(body).getAsJsonObject();
        } catch (Exception e) {
            throw new Invalid("editor.invalid-request", "Invalid JSON");
        }
    }

    private static String text(JsonObject json, String key, int max) {
        String value = string(json, key).replace("\r", "");

        if (value.length() > max)
            throw new Invalid("editor.invalid-request", key + " is too long");

        return value;
    }

    private static List<String> nicks(JsonArray array) {
        if (array.size() > MAX_LIST)
            throw new Invalid("editor.invalid-request", "Too many players");

        List<String> nicks = new ArrayList<>();

        for (JsonElement element : array) {
            String nick = element.getAsString().trim();

            if (!NICK_PATTERN.matcher(nick).matches())
                throw new Invalid("editor.invalid-request", "Invalid nickname: " + nick);

            if (nicks.stream().noneMatch(other -> other.equalsIgnoreCase(nick)))
                nicks.add(nick);
        }

        return nicks;
    }

    /* Descreve o que mudou para o histórico do editor */
    private static List<String> describe(NetworkAccess before, NetworkAccess after) {
        List<String> log = new ArrayList<>();

        if (before.isMaintenance() != after.isMaintenance())
            log.add("manutenção da rede " + (after.isMaintenance() ? "ATIVADA" : "desativada"));

        if (!before.getMaintenanceRank().equals(after.getMaintenanceRank()))
            log.add("manutenção: cargo mínimo " + before.getMaintenanceRank() + " -> " + after.getMaintenanceRank());

        if (!before.getMaintenancePlayers().equals(after.getMaintenancePlayers()))
            log.add("manutenção: jogadores liberados " + diffKeys(before.getMaintenancePlayers(), after.getMaintenancePlayers()));

        if (!before.getMaintenanceMotd().equals(after.getMaintenanceMotd()))
            log.add("manutenção: MOTD -> " + after.getMaintenanceMotd());

        if (!before.getMaintenanceMessage().equals(after.getMaintenanceMessage()))
            log.add("manutenção: mensagem de expulsão alterada");

        if (before.getSlots() != after.getSlots())
            log.add("slots: " + (before.getSlots() == 0 ? "sem limite" : before.getSlots()) + " -> " + (after.getSlots() == 0 ? "sem limite" : after.getSlots()));

        if (!before.getSlotsRank().equals(after.getSlotsRank()))
            log.add("slots: cargo que entra lotado " + before.getSlotsRank() + " -> " + after.getSlotsRank());

        if (!before.getFullMessage().equals(after.getFullMessage()))
            log.add("slots: mensagem de rede cheia alterada");

        Set<String> types = new LinkedHashSet<>(before.getServerRanks().keySet());
        types.addAll(after.getServerRanks().keySet());

        for (String type : types) {
            String old = before.getServerRanks().get(type), now = after.getServerRanks().get(type);

            if (!Objects.equals(old, now))
                log.add("servidor " + type.toLowerCase(Locale.ROOT) + ": " + (old == null ? "todos" : old) + " -> " + (now == null ? "todos" : now));
        }

        return log;
    }

    private static String diffKeys(List<String> before, List<String> after) {
        List<String> parts = new ArrayList<>();

        for (String key : after) {
            if (!before.contains(key)) parts.add("+" + key);
        }

        for (String key : before) {
            if (!after.contains(key)) parts.add("-" + key);
        }

        return parts.isEmpty() ? "sem mudanças" : String.join(" ", parts);
    }
}
