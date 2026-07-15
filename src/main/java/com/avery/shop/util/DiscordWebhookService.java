package com.avery.shop.util;

import com.avery.shop.ShopPlugin;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhookService {

    private final ShopPlugin plugin;

    public DiscordWebhookService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendPayload(String jsonPayload) {
        sendPayloadWithFile(jsonPayload, null, null);
    }

    public void sendPayloadWithFile(String jsonPayload, String fileName, String fileContent) {
        boolean enabled = plugin.getConfig().getBoolean("discord-webhook.enabled", false);
        if (!enabled) return;

        String webhookUrl = plugin.getConfig().getString("discord-webhook.url", "");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("User-Agent", "Java-DiscordWebhook");
                connection.setDoOutput(true);

                if (fileName != null && fileContent != null) {
                    String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write(("Content-Disposition: form-data; name=\"payload_json\"\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write(("Content-Type: application/json\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write((jsonPayload + "\r\n").getBytes(StandardCharsets.UTF_8));

                        os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write(("Content-Disposition: form-data; name=\"file[0]\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write(("Content-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write((fileContent + "\r\n").getBytes(StandardCharsets.UTF_8));
                        os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    }
                } else {
                    connection.setRequestProperty("Content-Type", "application/json");
                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                    }
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("無法發送 Discord Webhook，狀態碼: " + responseCode);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("發送 Discord Webhook 時發生錯誤: " + e.getMessage());
            }
        });
    }

    public void sendMessage(String message) {
        sendPayload("{\"content\": \"" + escapeJson(message) + "\"}");
    }

    public String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f");
    }
}
