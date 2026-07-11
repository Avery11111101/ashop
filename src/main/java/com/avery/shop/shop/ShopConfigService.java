package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 系統商店分類設定 — plugins/ashop/shop/&lt;分類&gt;/items.yml
 */
public final class ShopConfigService {

    private final ShopPlugin plugin;
    private final Map<ItemCategory, ShopCategoryData> categories = new EnumMap<>(ItemCategory.class);

    public ShopConfigService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public File getShopFolder() {
        var folderName = plugin.getConfig().getString("shop.folder", "shop");
        var folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getCategoryFolder(ItemCategory category) {
        var folder = new File(getShopFolder(), category.getId());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getCategoryFile(ItemCategory category) {
        return new File(getCategoryFolder(category), "items.yml");
    }

    /**
     * 首次啟動釋出 shop 資料夾說明，並依目錄自動建立各分類設定
     */
    public void seedDefaults(ItemCatalog catalog) {
        writeShopReadmeIfAbsent();

        if (!plugin.getConfig().getBoolean("shop.auto-seed", true)) {
            return;
        }

        int created = 0;
        for (var category : ItemCategory.values()) {
            var file = getCategoryFile(category);
            if (!file.exists()) {
                writeCategoryFile(category, catalog, file, true);
                created++;
            } else if (plugin.getConfig().getBoolean("shop.sync-new-items", true)) {
                syncMissingItems(category, catalog, file);
            }
        }

        if (created > 0) {
            plugin.getLogger().info("已自動建立 " + created + " 個分類商店設定（shop/<分類>/items.yml）");
        }
    }

    public void load(ItemCatalog catalog) {
        categories.clear();
        seedDefaults(catalog);

        for (var category : ItemCategory.values()) {
            var file = getCategoryFile(category);
            var data = file.exists()
                    ? parseCategoryFile(category, file)
                    : createEmptyCategoryData(category);
            data.rebuildEnabledEntries(catalog);
            categories.put(category, data);
        }

        int total = categories.values().stream().mapToInt(d -> d.getEnabledEntries().size()).sum();
        plugin.getLogger().info("商店分類設定載入完成：" + total + " 項可購買商品");
    }

    public boolean isCategoryEnabled(ItemCategory category) {
        if (!plugin.getConfig().getBoolean("categories." + category.getId(), true)) {
            return false;
        }
        var data = categories.get(category);
        return data == null || data.isEnabled();
    }

    public List<CatalogEntry> getEnabledEntries(ItemCategory category) {
        var data = categories.get(category);
        if (data == null || !isCategoryEnabled(category)) {
            return List.of();
        }
        return data.getEnabledEntries();
    }

    public int getEnabledCount(ItemCategory category) {
        return getEnabledEntries(category).size();
    }

    public double getBasePrice(String catalogKey, ItemCategory category) {
        var global = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        var data = categories.get(category);
        if (data == null) return global;
        return data.resolveBasePrice(catalogKey, global);
    }

    public double getBasePriceForEntry(CatalogEntry entry) {
        return getBasePrice(entry.getKey(), entry.getCategory());
    }

    public double resolveBasePrice(ItemCatalog catalog, String catalogKey, double globalDefault) {
        var entry = catalog.getByKey(catalogKey);
        if (entry != null) {
            return getBasePriceForEntry(entry);
        }
        return globalDefault;
    }

    public List<CatalogEntry> search(Player player, String query, ItemCatalog catalog) {
        if (query == null || query.isBlank()) return List.of();

        var catalogResults = catalog.search(player, query);
        var enabledKeys = new java.util.HashSet<String>();
        for (var data : categories.values()) {
            for (var entry : data.getEnabledEntries()) {
                enabledKeys.add(entry.getKey());
            }
        }

        var results = new ArrayList<CatalogEntry>();
        for (var entry : catalogResults) {
            if (enabledKeys.contains(entry.getKey()) && isCategoryEnabled(entry.getCategory())) {
                results.add(entry);
            }
        }
        return results;
    }

    private ShopCategoryData parseCategoryFile(ItemCategory category, File file) {
        var yaml = YamlConfiguration.loadConfiguration(file);
        var global = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        var defaultPrice = yaml.getDouble("default-price", global);
        var enabled = yaml.getBoolean("enabled", true);

        var data = new ShopCategoryData(category, enabled, defaultPrice);
        var section = yaml.getConfigurationSection("items");
        if (section != null) {
            for (var yamlKey : section.getKeys(false)) {
                var path = "items." + yamlKey;
                var catalogKey = section.getString(yamlKey + ".catalog-key", yamlKey);
                var materialId = section.getString(yamlKey + ".material", yamlKey);
                var itemEnabled = section.getBoolean(yamlKey + ".enabled", true);
                Double price = section.contains(yamlKey + ".price")
                        ? section.getDouble(yamlKey + ".price")
                        : null;
                data.putItem(new ShopItemSetting(catalogKey, materialId, itemEnabled, price));
            }
        }
        return data;
    }

    private ShopCategoryData createEmptyCategoryData(ItemCategory category) {
        var global = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        return new ShopCategoryData(category, true, global);
    }

    private void syncMissingItems(ItemCategory category, ItemCatalog catalog, File file) {
        var yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("items");
        var existingKeys = new java.util.HashSet<String>();
        if (section != null) {
            for (var yamlKey : section.getKeys(false)) {
                var catalogKey = section.getString(yamlKey + ".catalog-key", yamlKey);
                existingKeys.add(catalogKey);
            }
        }

        boolean changed = false;
        for (var entry : catalog.getByCategory(category)) {
            if (existingKeys.contains(entry.getKey())) continue;

            var yamlKey = sanitizeYamlKey(entry);
            var path = "items." + yamlKey;
            yaml.set(path + ".catalog-key", entry.getKey());
            yaml.set(path + ".material", entry.getMaterialId());
            yaml.set(path + ".enabled", true);
            changed = true;
        }

        if (changed) {
            saveYaml(file, yaml);
            plugin.getLogger().info("已同步新物品至 shop/" + category.getId() + "/items.yml");
        }
    }

    private void writeCategoryFile(ItemCategory category, ItemCatalog catalog, File file, boolean allEnabled) {
        var yaml = new YamlConfiguration();
        var global = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));

        yaml.set("category", category.getId());
        yaml.set("enabled", true);
        yaml.set("default-price", global);
        yaml.options().header("""
                ashop 系統商店 — %s 分類
                管理員可編輯此檔控制哪些物品出現在商店。
                enabled: 分類或單項是否上架
                price: 可選，覆寫該項基準價（動態定價仍會套用）
                修改後執行 /shop reload
                """.formatted(category.getId()));

        var usedKeys = new java.util.HashSet<String>();
        for (var entry : catalog.getByCategory(category)) {
            var yamlKey = uniqueYamlKey(entry, usedKeys);
            var path = "items." + yamlKey;
            yaml.set(path + ".catalog-key", entry.getKey());
            yaml.set(path + ".material", entry.getMaterialId());
            yaml.set(path + ".enabled", allEnabled);
        }

        saveYaml(file, yaml);
    }

    private static String sanitizeYamlKey(CatalogEntry entry) {
        var base = entry.getMaterialId();
        if (entry.getDisplayTag() != null && !entry.getDisplayTag().isBlank()) {
            base = base + "_" + entry.getDisplayTag();
        }
        return base.toLowerCase(Locale.ROOT).replace(':', '_');
    }

    private static String uniqueYamlKey(CatalogEntry entry, java.util.Set<String> used) {
        var base = sanitizeYamlKey(entry);
        var key = base;
        int i = 1;
        while (used.contains(key)) {
            key = base + "_" + i++;
        }
        used.add(key);
        return key;
    }

    private void saveYaml(File file, YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("儲存商店設定失敗 " + file.getName() + "：" + e.getMessage());
        }
    }

    private void writeShopReadmeIfAbsent() {
        var readme = new File(getShopFolder(), "README.txt");
        if (readme.exists()) return;

        try {
            Files.writeString(readme.toPath(), """
                    ashop 系統商店分類設定
                    ======================

                    資料夾結構：
                      shop/
                        blocks/items.yml      方塊
                        tools/items.yml       工具
                        weapons/items.yml     武器
                        armor/items.yml       護甲
                        food/items.yml        食物
                        potions/items.yml     藥水
                        enchanted_books/      附魔書
                        redstone/             紅石
                        transport/            交通
                        decorations/          裝飾
                        spawn_eggs/           生怪蛋
                        misc/                 雜項

                    首次啟動會依原版目錄自動建立各分類 items.yml。
                    管理員可直接編輯：
                      - 分類 enabled: false 可關閉整個分類
                      - 單項 enabled: false 可下架該物品
                      - price: 可覆寫單項基準價

                    修改後執行 /shop reload 或重啟伺服器。
                    config.yml 的 categories.* 仍可關閉 GUI 分類入口。
                    """, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("無法寫入 shop/README.txt：" + e.getMessage());
        }
    }

    public boolean isItemSellable(CatalogEntry entry) {
        if (entry == null) return false;
        if (!isCategoryEnabled(entry.getCategory())) return false;
        var data = categories.get(entry.getCategory());
        if (data == null) return false;
        var setting = data.getItemSetting(entry.getKey());
        return setting != null && setting.isEnabled();
    }

    public boolean isItemPurchasable(CatalogEntry entry) {
        return isItemSellable(entry);
    }
}
