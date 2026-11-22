package com.dunk.anticheat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Publica as flags no Redis da rede falando RESP direto no socket. O plugin nao pode depender
 * do core: cada plugin de jogo ja embute o proprio bootstrap do core no jar.
 */
final class RedisAlertPublisher {

    static final String CHANNEL = "anticheat-alert-channel";

    private static final long ERROR_LOG_DELAY = 60_000L;
    private static final long RETRY_DELAY = 30_000L;

    private final DunkACBridge plugin;

    private final String host;
    private final int port;
    private final String password;

    private Socket socket;
    private OutputStream out;
    private InputStream in;

    private long lastErrorLog;
    private long nextAttemptAt;

    RedisAlertPublisher(DunkACBridge plugin) {
        this.plugin = plugin;

        this.host = System.getProperty("redis_host", "localhost");
        this.port = Integer.getInteger("redis_port", 6379);
        this.password = System.getProperty("redis_password", "");
    }

    synchronized boolean publish(String uuid, String name, String server, String hack, int violations, int ping) {
        long now = System.currentTimeMillis();

        if (now < nextAttemptAt) return false;

        String payload = "{\"uuid\":\"" + escape(uuid) + "\",\"name\":\"" + escape(name)
                + "\",\"server\":\"" + escape(server) + "\",\"hack\":\"" + escape(hack)
                + "\",\"violations\":" + violations + ",\"count\":1,\"ping\":" + ping + "}";

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                connect();

                out.write(encode("PUBLISH", CHANNEL, payload));
                out.flush();

                readReply();
                return true;
            } catch (IOException e) {
                close();

                if (attempt == 1) {
                    nextAttemptAt = System.currentTimeMillis() + RETRY_DELAY;
                    logError(name, hack, e);
                }
            }
        }

        return false;
    }

    private void connect() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setSoTimeout(3000);

        out = socket.getOutputStream();
        in = socket.getInputStream();

        if (password.isEmpty()) return;

        out.write(encode("AUTH", password));
        out.flush();

        if (!readReply())
            throw new IOException("AUTH recusado pelo Redis");
    }

    private boolean readReply() throws IOException {
        String line = readLine();

        if (line.isEmpty()) throw new IOException("resposta vazia do Redis");
        if (line.charAt(0) == '-') throw new IOException(line);

        return line.charAt(0) == '+' || line.charAt(0) == ':';
    }

    private String readLine() throws IOException {
        StringBuilder builder = new StringBuilder();
        int current;

        while ((current = in.read()) != -1) {
            if (current == '\r') {
                in.read();
                break;
            }

            builder.append((char) current);
        }

        return builder.toString();
    }

    private static byte[] encode(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        write(buffer, "*" + args.length);

        for (String arg : args) {
            byte[] value = arg.getBytes(StandardCharsets.UTF_8);

            write(buffer, "$" + value.length);
            buffer.write(value, 0, value.length);
            write(buffer, "");
        }

        return buffer.toByteArray();
    }

    private static void write(ByteArrayOutputStream buffer, String line) {
        byte[] bytes = (line + "\r\n").getBytes(StandardCharsets.UTF_8);
        buffer.write(bytes, 0, bytes.length);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void logError(String name, String hack, IOException e) {
        long now = System.currentTimeMillis();

        if (now - lastErrorLog < ERROR_LOG_DELAY) return;
        lastErrorLog = now;

        plugin.getLogger().warning("Falha ao publicar flag de " + name + " (" + hack + ") no Redis: " + e.getMessage());
    }

    synchronized void close() {
        if (socket == null) return;

        try {
            socket.close();
        } catch (IOException ignored) {
        }

        socket = null;
        out = null;
        in = null;
    }
}
