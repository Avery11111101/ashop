package com.avery.shop.locale;

import com.avery.shop.ShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 多語系服務 - 物品名稱、介面訊息、玩家語言偏好
 */
public final class LocaleService {

    private final ShopPlugin plugin;
    private final Map<String, Map<String, String>> localeData = new LinkedHashMap<>();
    private final Map<UUID, String> playerLocales = new HashMap<>();
    private String defaultLocale = "zh_tw";
    private List<String> availableLocales = List.of("zh_tw", "en_us");

    public LocaleService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        defaultLocale = plugin.getConfig().getString("languages.default", "zh_tw");
        availableLocales = plugin.getConfig().getStringList("languages.available");
        if (availableLocales.isEmpty()) {
            availableLocales = List.of("zh_tw", "en_us");
        }

        localeData.clear();
        for (var locale : availableLocales) {
            loadLocaleFile(locale);
        }

        loadPlayerLocales();
        plugin.getLogger().info("已載入 " + localeData.size() + " 種語言，預設：" + defaultLocale);
    }

    private void loadLocaleFile(String locale) {
        var data = new HashMap<String, String>();
        var resource = "locales/" + locale + ".properties";
        try (var stream = plugin.getResource(resource)) {
            if (stream == null) {
                plugin.getLogger().warning("找不到語系檔：" + resource);
                if ("en_us".equals(locale)) buildEnglishFallback(data);
                localeData.put(locale, data);
                return;
            }
            parseProperties(stream, data);
        } catch (Exception e) {
            plugin.getLogger().severe("載入語系 " + locale + " 失敗：" + e.getMessage());
        }
        if ("en_us".equals(locale) && data.size() < 50) {
            buildEnglishFallback(data);
        }
        localeData.put(locale, data);
    }

    private void parseProperties(java.io.InputStream stream, Map<String, String> data) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                var key = line.substring(0, eq).trim();
                var value = line.substring(eq + 1).trim();
                data.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
    }

    private void buildEnglishFallback(Map<String, String> data) {
        for (var mat : Material.values()) {
            if (!mat.isItem() || mat.isLegacy()) continue;
            var key = mat.name().toLowerCase(Locale.ROOT);
            var name = formatEnglishName(mat.name());
            data.putIfAbsent(key, name);
            data.putIfAbsent("minecraft:" + key, name);
        }
    }

    private static String formatEnglishName(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void loadPlayerLocales() {
        playerLocales.clear();
        var file = getPlayerLocaleFile();
        if (!file.exists()) return;

        var yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("players");
        if (section == null) return;

        for (var key : section.getKeys(false)) {
            try {
                playerLocales.put(UUID.fromString(key), section.getString(key, defaultLocale));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void savePlayerLocales() {
        var file = getPlayerLocaleFile();
        var yaml = new YamlConfiguration();
        for (var entry : playerLocales.entrySet()) {
            yaml.set("players." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("儲存玩家語系失敗：" + e.getMessage());
        }
    }

    private File getPlayerLocaleFile() {
        return new File(plugin.getDataFolder(), "player-locales.yml");
    }

    // --- 玩家語系 ---

    public String getPlayerLocale(Player player) {
        return playerLocales.getOrDefault(player.getUniqueId(), defaultLocale);
    }

    public String getPlayerLocale(UUID uuid) {
        return playerLocales.getOrDefault(uuid, defaultLocale);
    }

    public boolean setPlayerLocale(Player player, String locale) {
        if (!isAvailable(locale)) return false;
        playerLocales.put(player.getUniqueId(), locale.toLowerCase(Locale.ROOT));
        savePlayerLocales();
        return true;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public List<String> getAvailableLocales() {
        return Collections.unmodifiableList(availableLocales);
    }

    public boolean isAvailable(String locale) {
        return locale != null && availableLocales.contains(locale.toLowerCase(Locale.ROOT));
    }

    public String getLanguageDisplayName(String locale) {
        var key = "lang.name." + locale.toLowerCase(Locale.ROOT);
        var name = getRaw(defaultLocale, key);
        if (name.equals(key)) {
            return locale;
        }
        return name;
    }

    // --- 訊息 ---

    public String msg(Player player, String key, Object... args) {
        return format(getPlayerLocale(player), key, args);
    }

    public String msg(UUID uuid, String key, Object... args) {
        return format(getPlayerLocale(uuid), key, args);
    }

    public String msg(String locale, String key, Object... args) {
        return format(locale, key, args);
    }

    private String format(String locale, String key, Object... args) {
        var text = getRaw(locale, key);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    private String getRaw(String locale, String key) {
        var normalized = key.toLowerCase(Locale.ROOT);
        var data = localeData.get(locale);
        if (data != null && data.containsKey(normalized)) {
            return data.get(normalized);
        }
        // fallback 至預設語系
        var fallback = localeData.get(defaultLocale);
        if (fallback != null && fallback.containsKey(normalized)) {
            return fallback.get(normalized);
        }
        // fallback 至 en_us
        var en = localeData.get("en_us");
        if (en != null && en.containsKey(normalized)) {
            return en.get(normalized);
        }
        return key;
    }

    // --- 物品名稱 ---

    public String getDisplayName(String locale, Material material) {
        return getDisplayName(locale, material.name());
    }

    public String getDisplayName(String locale, String materialKey) {
        var key = materialKey.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        var data = localeData.get(locale);
        if (data != null) {
            if (data.containsKey(key)) return data.get(key);
            if (data.containsKey("minecraft:" + key)) return data.get("minecraft:" + key);
        }
        return formatEnglishName(key);
    }

    public String getDisplayName(Player player, Material material) {
        return getDisplayName(getPlayerLocale(player), material);
    }

    public String getCategoryName(Player player, String categoryId) {
        return msg(player, "category." + categoryId);
    }

    public String getMaterialId(Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 取得物品所有可搜尋文字（含當前語系與全部已載入語系名稱）
     */
    public String[] getSearchableTexts(String locale, Material material, String extraTag) {
        var id = material.name().toLowerCase(Locale.ROOT);
        var mcId = "minecraft:" + id;
        var english = formatEnglishName(material.name());

        var texts = new LinkedHashSet<String>();
        texts.add(id);
        texts.add(mcId);
        texts.add(english);
        texts.add(getDisplayName(locale, material));

        // 跨語系搜尋：任一語言名稱皆可命中
        for (var loc : availableLocales) {
            texts.add(getDisplayName(loc, material).toLowerCase(Locale.ROOT));
        }

        if (extraTag != null && !extraTag.isBlank()) {
            texts.add(extraTag.toLowerCase(Locale.ROOT));
            texts.add(getVariantName(locale, extraTag).toLowerCase(Locale.ROOT));
        }
        return texts.toArray(new String[0]);
    }

    public String getVariantName(String locale, String variantKey) {
        var key = "variant." + variantKey.toLowerCase(Locale.ROOT).replace(' ', '_');
        var result = getRaw(locale, key);
        if (!result.equals(key)) return result;
        return variantKey;
    }

    public String getVariantDisplay(String locale, Material material, String variantKey) {
        return getDisplayName(locale, material) + " " + getVariantName(locale, variantKey);
    }
}
