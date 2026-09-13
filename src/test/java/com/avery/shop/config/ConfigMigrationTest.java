package com.avery.shop.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class ConfigMigrationTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Running ConfigMigrationTest...");

        String templateYaml = """
                # ==========================================
                # ashop 商店插件設定檔
                # ==========================================
                config-version: 3

                languages:
                  # 預設語言
                  default: zh_tw
                  fallback: en_us

                # 自動更新設定 (方案 C 雙重推播制)
                updater:
                  # 伺服器開機時是否自動檢查更新
                  check-on-startup: false
                  # 專案名稱
                  repo: "Avery11111101/ashop"
                  # 自動更新通道
                  channel: RELEASE
                """;

        String userYaml = """
                # 使用者的舊設定檔 (v2)
                config-version: 2
                languages:
                  default: en_us
                updater:
                  check-on-startup: true
                  repo: "Avery11111101/ashop"
                """;

        File tempDir = Files.createTempDirectory("ashop_test").toFile();
        File userFile = new File(tempDir, "config.yml");
        Files.writeString(userFile.toPath(), userYaml, StandardCharsets.UTF_8);

        YamlConfiguration template = YamlConfiguration.loadConfiguration(new StringReader(templateYaml));
        YamlConfiguration user = YamlConfiguration.loadConfiguration(userFile);

        int oldVersion = user.getInt("config-version", 1);
        int newVersion = template.getInt("config-version", 3);

        if (oldVersion < newVersion) {
            File backup = new File(tempDir, "config.backup-v" + oldVersion + ".yml");
            Files.copy(userFile.toPath(), backup.toPath());
            if (!backup.exists()) throw new AssertionError("Backup failed!");

            for (String key : template.getKeys(true)) {
                if (!user.contains(key)) {
                    user.set(key, template.get(key));
                }
                List<String> comments = template.getComments(key);
                if (comments != null && !comments.isEmpty() && (user.getComments(key) == null || user.getComments(key).isEmpty())) {
                    user.setComments(key, comments);
                }
                List<String> inlineComments = template.getInlineComments(key);
                if (inlineComments != null && !inlineComments.isEmpty() && (user.getInlineComments(key) == null || user.getInlineComments(key).isEmpty())) {
                    user.setInlineComments(key, inlineComments);
                }
            }
            user.set("config-version", newVersion);
            user.save(userFile);
        }

        String savedContent = Files.readString(userFile.toPath(), StandardCharsets.UTF_8);
        System.out.println("=== Saved Config Content ===");
        System.out.println(savedContent);

        if (!savedContent.contains("channel: RELEASE")
                || !user.getString("languages.default").equals("en_us")
                || !user.getBoolean("updater.check-on-startup")
                || user.getInt("config-version") != 3) {
            throw new AssertionError("Migration failed!");
        }
        System.out.println("ConfigMigrationTest passed 100%!");
    }
}
