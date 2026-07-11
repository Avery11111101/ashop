package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.ItemCatalog;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 管理員 GUI 編輯 — 商品價格/啟用/移除與 config UI 化
 */
public final class ShopAdminService {

    public record ConfigField(
            String id,
            String configPath,
            ConfigValueType type,
            double min,
            double max,
            double step
    ) {}

    public enum ConfigValueType {
        BOOLEAN, PERCENT, MONEY, INTEGER, RATIO
    }

    private static final List<ConfigField> CONFIG_FIELDS = List.of(
            new ConfigField("sell-ratio", "system-shop.sell-ratio", ConfigValueType.RATIO, 0, 1, 0.05),
            new ConfigField("sell-enabled", "system-shop.sell-to-system", ConfigValueType.BOOLEAN, 0, 1, 1),
            new ConfigField("require-listed", "system-shop.require-listed-item", ConfigValueType.BOOLEAN, 0, 1, 1),
            new ConfigField("exchange-multiply", "shop.pricing.exchange.multiply", ConfigValueType.MONEY, 0, 1000, 0.5),
            new ConfigField("exchange-add", "shop.pricing.exchange.add", ConfigValueType.MONEY, -100000, 100000, 1),
            new ConfigField("dynamic-enabled", "dynamic-pricing.enabled", ConfigValueType.BOOLEAN, 0, 1, 1),
            new ConfigField("per-buy", "dynamic-pricing.per-buy-increase", ConfigValueType.PERCENT, 0, 100, 0.5),
            new ConfigField("per-sell", "dynamic-pricing.per-sell-decrease", ConfigValueType.PERCENT, 0, 100, 0.5),
            new ConfigField("min-mult", "dynamic-pricing.min-multiplier", ConfigValueType.RATIO, 0.01, 10, 0.05),
            new ConfigField("max-mult", "dynamic-pricing.max-multiplier", ConfigValueType.RATIO, 0.1, 100, 0.5),
            new ConfigField("max-buy", "gui.max-buy-amount", ConfigValueType.INTEGER, 1, 100000, 1)
    );

    private final ShopPlugin plugin;
    private final ShopConfigService shopConfig;

    public ShopAdminService(ShopPlugin plugin, ShopConfigService shopConfig) {
        this.plugin = plugin;
        this.shopConfig = shopConfig;
    }

    public List<ConfigField> getConfigFields() {
        return CONFIG_FIELDS;
    }

    public Optional<ConfigField> getConfigField(String id) {
        return CONFIG_FIELDS.stream().filter(f -> f.id().equals(id)).findFirst();
    }

    public String formatConfigValue(ConfigField field) {
        var cfg = plugin.getConfig();
        return switch (field.type()) {
            case BOOLEAN -> cfg.getBoolean(field.configPath()) ? "ON" : "OFF";
            case PERCENT -> String.format(Locale.ROOT, "%.1f%%", cfg.getDouble(field.configPath()));
            case INTEGER -> String.valueOf(cfg.getInt(field.configPath()));
            case RATIO, MONEY -> String.format(Locale.ROOT, "%.2f", cfg.getDouble(field.configPath()));
        };
    }

    public boolean toggleConfigBoolean(String fieldId) {
        var field = getConfigField(fieldId).orElse(null);
        if (field == null || field.type() != ConfigValueType.BOOLEAN) return false;
        var cfg = plugin.getConfig();
        cfg.set(field.configPath(), !cfg.getBoolean(field.configPath()));
        plugin.saveConfig();
        return true;
    }

    public boolean adjustConfigNumber(String fieldId, boolean increase) {
        var field = getConfigField(fieldId).orElse(null);
        if (field == null || field.type() == ConfigValueType.BOOLEAN) return false;
        var cfg = plugin.getConfig();
        double current = field.type() == ConfigValueType.INTEGER
                ? cfg.getInt(field.configPath())
                : cfg.getDouble(field.configPath());
        double next = increase ? current + field.step() : current - field.step();
        next = Math.max(field.min(), Math.min(field.max(), next));
        if (field.type() == ConfigValueType.INTEGER) {
            cfg.set(field.configPath(), (int) Math.round(next));
        } else {
            cfg.set(field.configPath(), next);
        }
        plugin.saveConfig();
        return true;
    }

    public boolean setConfigValue(String fieldId, String raw) {
        var field = getConfigField(fieldId).orElse(null);
        if (field == null) return false;
        var cfg = plugin.getConfig();
        try {
            switch (field.type()) {
                case BOOLEAN -> {
                    var on = raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on")
                            || raw.equals("1") || raw.equalsIgnoreCase("是") || raw.equalsIgnoreCase("開");
                    cfg.set(field.configPath(), on);
                }
                case INTEGER -> {
                    int v = Integer.parseInt(raw.trim());
                    if (v < field.min() || v > field.max()) return false;
                    cfg.set(field.configPath(), v);
                }
                case PERCENT, RATIO, MONEY -> {
                    double v = Double.parseDouble(raw.trim());
                    if (v < field.min() || v > field.max()) return false;
                    cfg.set(field.configPath(), v);
                }
            }
            plugin.saveConfig();
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Optional<ResolvedShopItem> resolveForAdmin(String catalogKey, ItemCatalog catalog) {
        var setting = shopConfig.findItemSetting(catalogKey);
        if (setting.isEmpty()) return Optional.empty();
        var categoryId = shopConfig.findCategoryIdForCatalogKey(catalogKey);
        if (categoryId == null) return Optional.empty();
        var entry = catalog.getByKey(catalogKey);
        if (entry == null) {
            entry = shopConfig.getEnabledEntries(categoryId).stream()
                    .filter(e -> e.getKey().equals(catalogKey))
                    .findFirst()
                    .orElse(null);
        }
        if (entry == null) return Optional.empty();
        return Optional.of(new ResolvedShopItem(entry, setting.get(), categoryId));
    }

    public boolean updateItemPrice(ItemCatalog catalog, String catalogKey, double price) {
        if (price < 0) return false;
        return updateItemYaml(catalog, catalogKey, yamlPath -> {
            yamlPath.yaml.set(yamlPath.path + ".price", price);
        });
    }

    public boolean setItemEnabled(ItemCatalog catalog, String catalogKey, boolean enabled) {
        return updateItemYaml(catalog, catalogKey, yamlPath -> {
            yamlPath.yaml.set(yamlPath.path + ".enabled", enabled);
        });
    }

    public boolean setCategoryAllowBuy(ItemCatalog catalog, String categoryId, boolean allowBuy) {
        var file = shopConfig.getCategoryFile(categoryId);
        if (!file.exists()) return false;
        var yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("allow-buy", allowBuy);
        saveYaml(file, yaml);
        shopConfig.load(catalog);
        return true;
    }

    public boolean removeItem(ItemCatalog catalog, String catalogKey) {
        var categoryId = shopConfig.findCategoryIdForCatalogKey(catalogKey);
        if (categoryId == null) return false;
        var file = shopConfig.getCategoryFile(categoryId);
        var yaml = YamlConfiguration.loadConfiguration(file);
        var path = findYamlItemPath(yaml, catalogKey, catalog);
        if (path == null) return false;
        yaml.set(path, null);
        saveYaml(file, yaml);
        shopConfig.load(catalog);
        return true;
    }

    private boolean updateItemYaml(ItemCatalog catalog, String catalogKey, YamlMutator mutator) {
        var categoryId = shopConfig.findCategoryIdForCatalogKey(catalogKey);
        if (categoryId == null) return false;
        var file = shopConfig.getCategoryFile(categoryId);
        var yaml = YamlConfiguration.loadConfiguration(file);
        var path = findYamlItemPath(yaml, catalogKey, catalog);
        if (path == null) return false;
        mutator.apply(new YamlPath(yaml, path));
        saveYaml(file, yaml);
        shopConfig.load(catalog);
        return true;
    }

    private record YamlPath(YamlConfiguration yaml, String path) {}

    @FunctionalInterface
    private interface YamlMutator {
        void apply(YamlPath yamlPath);
    }

    private String findYamlItemPath(YamlConfiguration yaml, String catalogKey, ItemCatalog catalog) {
        var section = yaml.getConfigurationSection("items");
        if (section == null) return null;
        for (var yamlKey : section.getKeys(false)) {
            var itemSection = section.getConfigurationSection(yamlKey);
            if (itemSection == null) continue;
            var key = itemSection.getString("catalog-key", "");
            var materialId = itemSection.getString("material", yamlKey);
            var resolved = ShopItemResolver.resolve(key, materialId, catalog).orElse(null);
            var resolvedKey = resolved != null ? resolved.getKey()
                    : (!key.isBlank() ? key : yamlKey);
            if (catalogKey.equals(resolvedKey)) {
                return "items." + yamlKey;
            }
        }
        return null;
    }

    private void saveYaml(File file, YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("儲存商店設定失敗 " + file.getName() + "：" + e.getMessage());
        }
    }
}
