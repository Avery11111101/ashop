package com.avery.shop.locale;

import com.avery.shop.ShopPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 多語系服務 - 支援 data/locales 自訂語系檔與 config 擴充語言
 */
public final class LocaleService {

    private final ShopPlugin plugin;
    private final Map<String, Map<String, String>> localeData = new LinkedHashMap<>();
    private final Map<String, String> displayNames = new LinkedHashMap<>();
    private final Map<UUID, String> playerLocales = new HashMap<>();
    private String defaultLocale = "zh_tw";
    private String fallbackLocale = "en_us";
    private List<String> availableLocales = List.of("zh_tw", "en_us");

    public LocaleService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        LocaleFileLoader.extractBundledDefaults(plugin);
        parseLanguageConfig();

        localeData.clear();
        // 先載入 fallback，再載入其餘語言（供自訂語系繼承）
        var loadOrder = new ArrayList<String>();
        if (fallbackLocale != null && availableLocales.contains(fallbackLocale)) {
            loadOrder.add(fallbackLocale);
        }
        for (var loc : availableLocales) {
            if (!loadOrder.contains(loc)) loadOrder.add(loc);
        }

        for (var locale : loadOrder) {
            var inheritFrom = locale.equals(fallbackLocale) ? null : fallbackLocale;
            var data = LocaleFileLoader.loadLocale(plugin, locale, inheritFrom, localeData);
            if ("en_us".equals(locale) && data.size() < 50) {
                buildEnglishFallback(data);
            }
            localeData.put(locale, data);
        }

        autoPopulateFromPaperApi();

        loadPlayerLocales();
        plugin.getLogger().info("已載入 " + localeData.size() + " 種語言，預設："
                + defaultLocale + "，fallback：" + fallbackLocale);
        for (var loc : availableLocales) {
            if (!localeData.containsKey(loc) || localeData.get(loc).isEmpty()) {
                plugin.getLogger().warning("語言「" + loc + "」未載入任何翻譯，請檢查 locales/" + loc + ".properties");
            }
        }
    }

    private void autoPopulateFromPaperApi() {
        var twData = localeData.computeIfAbsent("zh_tw", k -> new HashMap<>());
        int loadedCount = 0;

        for (Material mat : Material.values()) {
            if (mat.isAir() || !mat.isItem()) continue;
            String key = mat.name().toLowerCase(Locale.ROOT);

            if (!twData.containsKey(key)) {
                String paperName = fetchPaperItemName(mat);
                if (paperName != null && !paperName.isBlank()) {
                    twData.put(key, paperName);
                    twData.put("minecraft:" + key, paperName);
                    loadedCount++;
                }
            }
        }

        if (loadedCount > 0) {
            plugin.getLogger().info("已透過 Paper API 動態自動載入 " + loadedCount + " 種 Minecraft 官方物品名稱");
        }
    }

    private String fetchPaperItemName(Material mat) {
        if (mat == null || mat.isAir()) return null;

        try {
            String transKey = mat.getItemTranslationKey();
            var comp = net.kyori.adventure.text.Component.translatable(transKey);
            var rendered = net.kyori.adventure.translation.GlobalTranslator.render(comp, Locale.TAIWAN);
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(rendered);

            if (text != null && !text.isBlank() && !text.equals(transKey)) {
                return text;
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> langClass = Class.forName("net.minecraft.locale.Language");
            java.lang.reflect.Method getInstance = langClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            java.lang.reflect.Method getOrDefault = langClass.getMethod("getOrDefault", String.class);

            String itemKey = "item.minecraft." + mat.name().toLowerCase(Locale.ROOT);
            String blockKey = "block.minecraft." + mat.name().toLowerCase(Locale.ROOT);

            String val = (String) getOrDefault.invoke(instance, itemKey);
            if (val != null && !val.equals(itemKey)) return val;

            val = (String) getOrDefault.invoke(instance, blockKey);
            if (val != null && !val.equals(blockKey)) return val;
        } catch (Throwable ignored) {}

        return null;
    }

    private void parseLanguageConfig() {
        displayNames.clear();
        availableLocales = new ArrayList<>();

        defaultLocale = plugin.getConfig().getString("languages.default", "zh_tw")
                .toLowerCase(Locale.ROOT);
        fallbackLocale = plugin.getConfig().getString("languages.fallback", "en_us")
                .toLowerCase(Locale.ROOT);

        var localesSection = plugin.getConfig().getConfigurationSection("languages.locales");
        if (localesSection != null) {
            for (var key : localesSection.getKeys(false)) {
                var code = key.toLowerCase(Locale.ROOT);
                availableLocales.add(code);
                displayNames.put(code, localesSection.getString(key, code));
            }
        } else {
            for (var code : plugin.getConfig().getStringList("languages.available")) {
                code = code.toLowerCase(Locale.ROOT);
                availableLocales.add(code);
                displayNames.putIfAbsent(code, code);
            }
        }

        if (availableLocales.isEmpty()) {
            availableLocales.add("zh_tw");
            availableLocales.add("en_us");
            displayNames.put("zh_tw", "繁體中文");
            displayNames.put("en_us", "English");
        }

        if (!availableLocales.contains(defaultLocale)) {
            plugin.getLogger().warning("languages.default (" + defaultLocale
                    + ") 不在 locales 清單中，已改用第一個語言");
            defaultLocale = availableLocales.getFirst();
        }
        if (!availableLocales.contains(fallbackLocale)) {
            fallbackLocale = availableLocales.contains("en_us") ? "en_us" : defaultLocale;
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
                var saved = section.getString(key, defaultLocale).toLowerCase(Locale.ROOT);
                if (isAvailable(saved)) {
                    playerLocales.put(UUID.fromString(key), saved);
                }
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

    public String getFallbackLocale() {
        return fallbackLocale;
    }

    public List<String> getAvailableLocales() {
        return Collections.unmodifiableList(availableLocales);
    }

    public boolean isAvailable(String locale) {
        if (locale == null) return false;
        return availableLocales.contains(locale.toLowerCase(Locale.ROOT));
    }

    public String getLanguageDisplayName(String locale) {
        locale = locale.toLowerCase(Locale.ROOT);
        if (displayNames.containsKey(locale)) {
            return displayNames.get(locale);
        }
        var key = "lang.name." + locale;
        var name = getRaw(defaultLocale, key);
        if (!name.equals(key)) return name;
        return locale;
    }

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
        if (!locale.equals(defaultLocale)) {
            var def = localeData.get(defaultLocale);
            if (def != null && def.containsKey(normalized)) {
                return def.get(normalized);
            }
        }
        if (!locale.equals(fallbackLocale)) {
            var fb = localeData.get(fallbackLocale);
            if (fb != null && fb.containsKey(normalized)) {
                return fb.get(normalized);
            }
        }
        return key;
    }

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
            if (data.containsKey("item.minecraft." + key)) return data.get("item.minecraft." + key);
            if (data.containsKey("block.minecraft." + key)) return data.get("block.minecraft." + key);
            if (data.containsKey("entity.minecraft." + key)) return data.get("entity.minecraft." + key);
        }
        return formatEnglishName(key);
    }

    public String getDisplayName(Player player, Material material) {
        return getDisplayName(getPlayerLocale(player), material);
    }

    public String getCategoryName(Player player, String categoryId) {
        return getCategoryDisplayName(getPlayerLocale(player), categoryId);
    }

    public String getCategoryDisplayName(String locale, String categoryId) {
        var key = "category." + categoryId.replace('/', '.');
        var translated = msg(locale, key);
        if (translated.equals(key)) {
            var twMsg = msg("zh_tw", key);
            if (!twMsg.equals(key)) {
                return twMsg;
            }
            var leaf = categoryId.contains("/") ? categoryId.substring(categoryId.lastIndexOf('/') + 1) : categoryId;
            var leafKey = "category." + leaf;
            var leafMsg = msg("zh_tw", leafKey);
            if (!leafMsg.equals(leafKey)) {
                return leafMsg;
            }
            return categoryId;
        }
        return translated;
    }

    public String getMaterialId(Material material) {
        return "minecraft:" + material.name().toLowerCase(Locale.ROOT);
    }

    public String[] getSearchableTexts(String locale, Material material, String extraTag) {
        var id = material.name().toLowerCase(Locale.ROOT);
        var mcId = "minecraft:" + id;
        var english = formatEnglishName(material.name());

        var texts = new LinkedHashSet<String>();
        texts.add(id);
        texts.add(mcId);
        texts.add(english);
        texts.add(getDisplayName(locale, material));

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

    public String getFullDisplayName(String locale, com.avery.shop.catalog.CatalogEntry entry) {
        if (entry == null) return "未知物品";
        org.bukkit.inventory.ItemStack stack = entry.getTemplate();
        if (stack == null || stack.getType().isAir()) return "未知物品";

        String baseName = getDisplayName(locale, stack.getType());

        if (stack.hasItemMeta()) {
            var meta = stack.getItemMeta();
            if (meta.hasDisplayName()) {
                return meta.getDisplayName();
            }

            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta enchantMeta) {
                var stored = enchantMeta.getStoredEnchants();
                if (!stored.isEmpty()) {
                    List<String> enchantNames = new ArrayList<>();
                    for (var e : stored.entrySet()) {
                        enchantNames.add(getEnchantmentName(locale, e.getKey(), e.getValue()));
                    }
                    return baseName + " (" + String.join(", ", enchantNames) + ")";
                }
            }

            if (meta instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
                var potionType = potionMeta.getBasePotionType();
                if (potionType != null) {
                    return baseName + " (" + getPotionTypeName(locale, potionType) + ")";
                }
            }
        }

        if (entry.getDisplayTag() != null && !entry.getDisplayTag().isBlank()) {
            String variantName = parseDisplayTag(locale, entry.getDisplayTag());
            return baseName + " (" + variantName + ")";
        }

        return baseName;
    }

    public String parseDisplayTag(String locale, String tag) {
        if (tag.contains(":")) {
            String[] parts = tag.split(":");
            String key = parts[0].toLowerCase(Locale.ROOT);
            String levelStr = parts[1];
            try {
                int lvl = Integer.parseInt(levelStr);
                return getEnchantmentBaseName(locale, key) + " " + toRoman(lvl);
            } catch (Exception ignored) {}
        }
        return getVariantName(locale, tag);
    }

    public String getEnchantmentName(String locale, org.bukkit.enchantments.Enchantment enchant, int level) {
        String key = enchant.getKey().getKey().toLowerCase(Locale.ROOT);
        String name = getEnchantmentBaseName(locale, key);
        return name + " " + toRoman(level);
    }

    public String getEnchantmentBaseName(String locale, String key) {
        var rawKey = "enchantment.minecraft." + key;
        var name = getRaw(locale, rawKey);
        if (name.equals(rawKey)) {
            var rawShortKey = "enchantment." + key;
            name = getRaw(locale, rawShortKey);
            if (name.equals(rawShortKey)) {
                return formatEnglishName(key);
            }
        }
        return name;
    }

    public String getPotionTypeName(String locale, org.bukkit.potion.PotionType potionType) {
        String key = potionType.name().toLowerCase(Locale.ROOT);
        var rawKey = "potion." + key;
        var name = getRaw(locale, rawKey);
        if (name.equals(rawKey)) {
            return formatEnglishName(key);
        }
        return name;
    }

    public static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }
}
