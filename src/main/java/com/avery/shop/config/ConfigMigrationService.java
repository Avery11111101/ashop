package com.avery.shop.config;

import com.avery.shop.ShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 設定檔無縫銜接與升級服務 (Config Migration Service)
 * 負責在插件更新時保留使用者原先設定的所有數值，
 * 並將新版本中新增的設定項與註解說明自動合併注入，
 * 同時自動建立備份檔案。
 */
public final class ConfigMigrationService {

    public static final int CURRENT_CONFIG_VERSION = 3;

    private final ShopPlugin plugin;

    public ConfigMigrationService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 執行設定檔自動銜接與升級檢測
     */
    public void migrateIfNeeded() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        try (var res = plugin.getResource("config.yml")) {
            if (res == null) {
                return;
            }

            var reader = new InputStreamReader(res, StandardCharsets.UTF_8);
            var template = YamlConfiguration.loadConfiguration(reader);
            var userConfig = YamlConfiguration.loadConfiguration(configFile);

            int oldVersion = userConfig.getInt("config-version", 1);
            int newVersion = template.getInt("config-version", CURRENT_CONFIG_VERSION);

            boolean needsMigration = oldVersion < newVersion;
            boolean missingKeys = false;

            // 檢查是否有新版本缺少的新欄位
            for (String key : template.getKeys(true)) {
                if (!userConfig.contains(key)) {
                    missingKeys = true;
                    break;
                }
            }

            if (needsMigration || missingKeys) {
                // 1. 自動備份
                File backupFile = new File(plugin.getDataFolder(), "config.backup-v" + oldVersion + ".yml");
                try {
                    Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    plugin.getLogger().warning("無法建立設定檔備份: " + e.getMessage());
                }

                // 2. 補齊缺失欄位與註解
                for (String key : template.getKeys(true)) {
                    if (!userConfig.contains(key)) {
                        userConfig.set(key, template.get(key));
                    }
                    List<String> comments = template.getComments(key);
                    if (comments != null && !comments.isEmpty() && (userConfig.getComments(key) == null || userConfig.getComments(key).isEmpty())) {
                        userConfig.setComments(key, comments);
                    }
                    List<String> inline = template.getInlineComments(key);
                    if (inline != null && !inline.isEmpty() && (userConfig.getInlineComments(key) == null || userConfig.getInlineComments(key).isEmpty())) {
                        userConfig.setInlineComments(key, inline);
                    }
                }

                // 3. 更新版本號並儲存
                userConfig.set("config-version", newVersion);
                userConfig.save(configFile);

                plugin.getLogger().info("設定檔已自動銜接升級至版本 " + newVersion
                        + " (已自動備份舊設定至 " + backupFile.getName() + ")");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("設定檔自動銜接升級時發生異常: " + e.getMessage());
        }
    }
}
