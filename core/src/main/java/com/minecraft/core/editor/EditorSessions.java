package com.minecraft.core.editor;

import com.minecraft.core.Core;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Sessões do editor de permissões (site). O /editor no jogo cria a sessão e o proxy a valida.
 * O Redis guarda só o SHA-256 do token, então quem lê o Redis não consegue usar o link.
 */
public final class EditorSessions {

    public static final String PERMISSION = "dunk.permissions.editor";

    /* Duração de cada sessão (30 minutos) */
    public static final int DURATION_SECONDS = 30 * 60;

    /* Endereço público do site, publicado pelo proxy ao iniciar o editor */
    public static final String URL_KEY = "editor:url";

    private static final String KEY = "editor:session:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private EditorSessions() {
    }

    @Getter
    @AllArgsConstructor
    public static class Session {

        private final String actorId, actor;
        private final long createdAt;

        public UUID getActorUniqueId() {
            return UUID.fromString(actorId);
        }

        public long getExpiresAt() {
            return createdAt + DURATION_SECONDS * 1000L;
        }
    }

    /* Cria uma sessão e devolve o token (vai no fragmento do link) */
    public static String open(UUID actorId, String actor) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Core.getRedis().save(KEY + hash(token), new Session(actorId.toString(), actor, System.currentTimeMillis()), DURATION_SECONDS);

        return token;
    }

    public static Session find(String token) {
        if (token == null || token.isEmpty() || token.length() > 128) return null;

        Session session = Core.getRedis().load(KEY + hash(token), Session.class);

        if (session == null || session.getExpiresAt() <= System.currentTimeMillis())
            return null;

        return session;
    }

    public static void close(String token) {
        Core.getRedis().delete(KEY + hash(token));
    }

    public static String getPublicUrl() {
        Object url = Core.getRedis().getDefault(URL_KEY);

        return url != null ? url.toString() : null;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));

            StringBuilder builder = new StringBuilder();

            for (byte value : digest)
                builder.append(String.format("%02x", value));

            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
