package com.minecraft.core.bungee.editor;

import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * plugins/core-bungee/editor.yml
 */
@Getter
public class EditorConfig {

    private final boolean enabled;
    private final boolean automaticPublicUrl;
    private final String bind, publicUrl;
    private final int port, requestsPerMinute, failedLoginsPerTenMinutes, maxBodyBytes;

    private EditorConfig(Configuration config) {
        this.enabled = config.getBoolean("enabled", true);
        this.bind = config.getString("bind", "0.0.0.0");
        this.port = config.getInt("port", 1031);

        String url = config.getString("public-url", "auto").trim();

        /* Sem endereço fixo: o proxy usa o host pelo qual o jogador entrou e, se não houver, o IP público */
        this.automaticPublicUrl = url.isEmpty() || url.equalsIgnoreCase("auto") || url.contains("127.0.0.1") || url.contains("localhost");

        if (automaticPublicUrl)
            url = detectPublicUrl(port);

        this.publicUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;

        this.requestsPerMinute = Math.max(10, config.getInt("requests-per-minute", 240));
        this.failedLoginsPerTenMinutes = Math.max(1, config.getInt("failed-logins-per-ten-minutes", 20));
        this.maxBodyBytes = Math.max(1024, config.getInt("max-body-bytes", 1048576));
    }

    /**
     * http://<ip público>:<porta>. Se não der para descobrir o IP, usa o domínio do servidor.
     */
    private static String detectPublicUrl(int port) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL("https://api.ipify.org").openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()))) {
                String ip = reader.readLine();

                if (ip != null && ip.trim().matches("[0-9a-fA-F:.]+")) {
                    String host = ip.trim();

                    /* IPv6 precisa de colchetes na URL */
                    if (host.contains(":") && !host.startsWith("["))
                        host = "[" + host + "]";

                    return "http://" + host + ":" + port;
                }
            }
        } catch (Exception ignored) {
        }

        return "http://" + com.minecraft.core.Constant.SERVER_DOMAIN + ":" + port;
    }

    /* Caminho público quando o editor fica atrás de um reverse proxy (ex.: https://redestyle.com.br/editor) */
    public String getPathPrefix() {
        try {
            String path = java.net.URI.create(publicUrl).getPath();

            if (path == null || path.isEmpty() || path.equals("/"))
                return "";

            return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        } catch (Exception ignored) {
            return "";
        }
    }

    public static EditorConfig load(File folder) throws IOException {
        File file = new File(folder, "editor.yml");
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);

        if (!file.exists()) {
            folder.mkdirs();

            Configuration defaults = new Configuration();
            defaults.set("enabled", true);
            defaults.set("bind", "0.0.0.0");
            defaults.set("port", 1031);
            /* "auto" = usa o host pelo qual o jogador entrou e, se falhar, o IP público.
               Domínio/reverse proxy: https://editor.seudominio.com ou https://seudominio.com/editor.
               A porta abaixo precisa estar liberada/alocada no painel. */
            defaults.set("public-url", "auto");
            defaults.set("requests-per-minute", 240);
            defaults.set("failed-logins-per-ten-minutes", 20);
            defaults.set("max-body-bytes", 1048576);

            provider.save(defaults, file);
        }

        return new EditorConfig(provider.load(file));
    }
}
