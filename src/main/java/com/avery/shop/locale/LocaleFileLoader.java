package com.avery.shop.locale;

import com.avery.shop.ShopPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 語系檔載入器 — 支援 data 資料夾自訂語言與 JAR 內建合併
 */
public final class LocaleFileLoader {

    private LocaleFileLoader() {}

    public static File getLocalesFolder(ShopPlugin plugin) {
        var folder = new File(plugin.getDataFolder(), "locales");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public static File getLocaleFile(ShopPlugin plugin, String localeCode) {
        return new File(getLocalesFolder(plugin), localeCode + ".properties");
    }

    /**
     * 首次啟動時釋出內建語系與範本到 data/locales/
     */
    public static void extractBundledDefaults(ShopPlugin plugin) {
        getLocalesFolder(plugin);
        copyBundledIfAbsent(plugin, "zh_tw");
        copyBundledIfAbsent(plugin, "en_us");
        copyBundledIfAbsent(plugin, "_template");

        var readme = new File(getLocalesFolder(plugin), "README.txt");
        if (!readme.exists()) {
            try {
                Files.writeString(readme.toPath(), """
                        ashop 自訂語系說明
                        ==================

                        1. 在 config.yml 的 languages.locales 加入你的語言代碼與顯示名稱
                           例：
                             languages:
                               locales:
                                 ja_jp: 日本語

                        2. 在此資料夾建立 ja_jp.properties（可複製 _template.properties 修改）

                        3. 執行 /shop reload 或重啟伺服器

                        優先順序：data/locales/<代碼>.properties 會覆蓋 JAR 內建翻譯
                        缺少的 key 會 fallback 至 languages.fallback 設定的語言
                        """, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("無法寫入 locales/README.txt：" + e.getMessage());
            }
        }
    }

    private static void copyBundledIfAbsent(ShopPlugin plugin, String name) {
        var target = new File(getLocalesFolder(plugin), name + ".properties");
        if (target.exists()) return;

        var resource = "locales/" + name + ".properties";
        try (var stream = plugin.getResource(resource)) {
            if (stream == null) return;
            Files.copy(stream, target.toPath());
            plugin.getLogger().info("已釋出語系檔：" + target.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("釋出語系檔失敗 " + name + "：" + e.getMessage());
        }
    }

    /**
     * 載入語系：fallback 基底 → JAR 內建 → data 自訂（後者覆蓋）
     */
    public static Map<String, String> loadLocale(ShopPlugin plugin, String localeCode,
                                                  String fallbackCode,
                                                  Map<String, Map<String, String>> loaded) {
        var data = new HashMap<String, String>();

        if (fallbackCode != null && !fallbackCode.equals(localeCode)) {
            var fallbackData = loaded.get(fallbackCode);
            if (fallbackData != null) {
                data.putAll(fallbackData);
            }
        }

        if ("zh_tw".equals(localeCode)) {
            fetchAndCacheOfficialLanguageJson(plugin, data);
        }

        mergeFromResource(plugin, "locales/" + localeCode + ".properties", data);

        var userFile = getLocaleFile(plugin, localeCode);
        if (!userFile.exists()) {
            ensureCustomLocaleFile(plugin, localeCode, fallbackCode, loaded);
        }
        mergeFromFile(plugin, userFile, data);

        return data;
    }

    public static void fetchAndCacheOfficialLanguageJson(ShopPlugin plugin, Map<String, String> data) {
        File cacheDir = getLocalesFolder(plugin);
        File cacheFile = new File(cacheDir, "cache_zh_tw.json");

        if (!cacheFile.exists() || cacheFile.length() < 1000) {
            plugin.getLogger().info("正在從 Mojang 官方資產庫線上自動下載 1.21 繁體中文 (zh_tw.json) 語言包…");
            boolean success = downloadOfficialJson(plugin, cacheFile);
            if (success) {
                plugin.getLogger().info("官方繁體中文語言包下載並快取成功！");
            } else {
                plugin.getLogger().warning("線上自動下載官方語言包失敗，將嘗試使用已存在快取。");
            }
        }

        if (cacheFile.exists() && cacheFile.length() > 0) {
            try (var is = new FileInputStream(cacheFile)) {
                parseJson(is, data);
            } catch (IOException e) {
                plugin.getLogger().warning("無法讀取快取之官方語言包：" + e.getMessage());
            }
        }
    }

    private static boolean downloadOfficialJson(ShopPlugin plugin, File targetFile) {
        String[] urls = new String[] {
            "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/lang/zh_tw.json",
            "https://raw.githubusercontent.com/misode/misode.github.io/main/assets/1.21/assets/minecraft/lang/zh_tw.json"
        };

        for (String urlStr : urls) {
            try {
                java.net.URL url = java.net.URI.create(urlStr).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (PaperMC Plugin)");

                if (conn.getResponseCode() == 200) {
                    try (var is = conn.getInputStream();
                         var os = new FileOutputStream(targetFile)) {
                        is.transferTo(os);
                    }
                    if (targetFile.length() > 1000) {
                        return true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("下載 " + urlStr + " 失敗: " + e.getMessage());
            }
        }
        return false;
    }

    private static void ensureCustomLocaleFile(ShopPlugin plugin, String localeCode,
                                                String fallbackCode,
                                                Map<String, Map<String, String>> loaded) {
        var userFile = getLocaleFile(plugin, localeCode);
        if (userFile.exists()) return;

        // 內建語系已在 extractBundledDefaults 處理
        if (plugin.getResource("locales/" + localeCode + ".properties") != null) {
            copyBundledIfAbsent(plugin, localeCode);
            return;
        }

        plugin.getLogger().info("偵測到自訂語言「" + localeCode + "」，正在建立語系檔範本…");

        var content = new StringBuilder();
        content.append("# ashop 自訂語系：").append(localeCode).append("\n");
        content.append("# 複製自 _template.properties，請翻譯以下內容\n\n");

        var template = loadFileOrResource(plugin, getLocaleFile(plugin, "_template"),
                "locales/_template.properties");
        if (!template.isEmpty()) {
            content.append(template);
        }

        if (fallbackCode != null && loaded.containsKey(fallbackCode)) {
            content.append("\n# --- 以下為 fallback (").append(fallbackCode)
                    .append(") 參考，可刪除 ---\n");
            for (var entry : loaded.get(fallbackCode).entrySet()) {
                if (entry.getKey().startsWith("#")) continue;
                content.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
        }

        try {
            Files.writeString(userFile.toPath(), content.toString(), StandardCharsets.UTF_8);
            plugin.getLogger().info("已建立自訂語系檔：" + userFile.getPath());
        } catch (IOException e) {
            plugin.getLogger().severe("建立自訂語系檔失敗：" + e.getMessage());
        }
    }

    private static void mergeFromResource(ShopPlugin plugin, String resource, Map<String, String> data) {
        try (var stream = plugin.getResource(resource)) {
            if (stream == null) return;
            if (resource.endsWith(".json")) {
                parseJson(stream, data);
            } else {
                parseProperties(stream, data, false);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("讀取內建語系 " + resource + " 失敗：" + e.getMessage());
        }
    }

    private static void parseJson(InputStream stream, Map<String, String> data) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            var jsonObject = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : jsonObject.entrySet()) {
                var rawKey = entry.getKey().toLowerCase(Locale.ROOT);
                var value = entry.getValue().getAsString();

                data.putIfAbsent(rawKey, value);

                if (rawKey.startsWith("item.minecraft.")) {
                    String shortKey = rawKey.substring("item.minecraft.".length());
                    data.putIfAbsent(shortKey, value);
                    data.putIfAbsent("minecraft:" + shortKey, value);
                } else if (rawKey.startsWith("block.minecraft.")) {
                    String shortKey = rawKey.substring("block.minecraft.".length());
                    data.putIfAbsent(shortKey, value);
                    data.putIfAbsent("minecraft:" + shortKey, value);
                } else if (rawKey.startsWith("enchantment.minecraft.")) {
                    String shortKey = rawKey.substring("enchantment.minecraft.".length());
                    data.putIfAbsent("enchantment." + shortKey, value);
                } else if (rawKey.startsWith("entity.minecraft.")) {
                    String shortKey = rawKey.substring("entity.minecraft.".length());
                    data.putIfAbsent(shortKey, value);
                    data.putIfAbsent("minecraft:" + shortKey, value);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void mergeFromFile(ShopPlugin plugin, File file, Map<String, String> data) {
        if (!file.exists()) return;
        try (var stream = new FileInputStream(file)) {
            parseProperties(stream, data, true);
        } catch (IOException e) {
            plugin.getLogger().warning("讀取語系檔失敗 " + file.getName() + "：" + e.getMessage());
        }
    }

    private static String loadFileOrResource(ShopPlugin plugin, File file, String resource) {
        if (file.exists()) {
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
        try (var stream = plugin.getResource(resource)) {
            if (stream == null) return "";
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static void parseProperties(InputStream stream, Map<String, String> data, boolean override) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                var key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                var value = line.substring(eq + 1).trim();
                if (override || !data.containsKey(key)) {
                    data.put(key, value);
                }
            }
        }
    }
}
