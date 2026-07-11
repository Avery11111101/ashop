package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemCategory;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 系統商店 — 完全由 plugins/ashop/shop/ 資料夾驅動
 */
public final class ShopConfigService {

    private static final String TEMPLATE_FOLDER = "_template";

    private final ShopPlugin plugin;
    private final Map<String, ShopCategoryData> categories = new LinkedHashMap<>();
    private final ItemPriceCalculator priceCalculator;

    public ShopConfigService(ShopPlugin plugin) {
        this.plugin = plugin;
        this.priceCalculator = new ItemPriceCalculator(plugin);
    }

    public ItemPriceCalculator getPriceCalculator() {
        return priceCalculator;
    }

    public File getShopFolder() {
        var folderName = plugin.getConfig().getString("shop.folder", "shop");
        var folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    public File getCategoryFile(String categoryId) {
        return new File(new File(getShopFolder(), categoryId), "items.yml");
    }

    public void extractTemplateIfAbsent() {
        writeShopReadmeIfAbsent();
        if (!plugin.getConfig().getBoolean("shop.extract-template", true)) {
            return;
        }

        var templateFile = getCategoryFile(TEMPLATE_FOLDER);
        if (templateFile.exists()) return;

        templateFile.getParentFile().mkdirs();
        try (InputStream stream = plugin.getResource("shop/_template/items.yml")) {
            if (stream == null) {
                plugin.getLogger().warning("找不到內建 shop 範本");
                return;
            }
            Files.copy(stream, templateFile.toPath());
            plugin.getLogger().info("已釋出 shop 範本：shop/" + TEMPLATE_FOLDER + "/items.yml");
        } catch (IOException e) {
            plugin.getLogger().warning("釋出 shop 範本失敗：" + e.getMessage());
        }
    }

    public void load(ItemCatalog catalog) {
        categories.clear();
        extractTemplateIfAbsent();

        var shopFolder = getShopFolder();
        var dirs = shopFolder.listFiles(File::isDirectory);
        if (dirs == null) {
            plugin.getLogger().warning("shop 資料夾為空，請建立 shop/<分類>/items.yml");
            return;
        }

        var loaded = new ArrayList<ShopCategoryData>();
        for (var dir : dirs) {
            var categoryId = dir.getName();
            if (categoryId.startsWith(".") || categoryId.equalsIgnoreCase(TEMPLATE_FOLDER)) {
                continue;
            }

            var file = new File(dir, "items.yml");
            if (!file.exists()) {
                plugin.getLogger().warning("略過分類 " + categoryId + "：缺少 items.yml");
                continue;
            }

            try {
                loaded.add(parseCategoryFile(categoryId, file, catalog));
            } catch (Exception e) {
                plugin.getLogger().warning("載入分類 " + categoryId + " 失敗：" + e.getMessage());
            }
        }

        loaded.sort(Comparator
                .comparingInt((ShopCategoryData d) -> d.getDefinition().getSlot())
                .thenComparing(d -> d.getCategoryId()));

        for (var data : loaded) {
            categories.put(data.getCategoryId(), data);
        }

        if (plugin.getConfig().getBoolean("shop.pricing.backfill-missing-prices", true)) {
            backfillMissingPrices(catalog);
        }

        int total = categories.values().stream().mapToInt(d -> d.getEnabledEntries().size()).sum();
        plugin.getLogger().info("商店載入完成：" + categories.size() + " 個分類、" + total + " 項商品（來自 shop/ 資料夾）");
    }

    public List<ShopCategoryDefinition> getCategories() {
        return categories.values().stream()
                .map(ShopCategoryData::getDefinition)
                .toList();
    }

    public Optional<ShopCategoryData> getCategory(String categoryId) {
        return Optional.ofNullable(categories.get(categoryId));
    }

    public boolean isCategoryVisible(String categoryId) {
        var data = categories.get(categoryId);
        return data != null && data.getDefinition().isEnabled();
    }

    public String getCategoryDisplayName(Player player, String categoryId) {
        var data = categories.get(categoryId);
        if (data == null) return categoryId;

        var yamlName = data.getDefinition().getDisplayName();
        if (yamlName != null && !yamlName.isBlank()) {
            return yamlName;
        }
        return plugin.getLocaleService().getCategoryName(player, categoryId);
    }

    public List<CatalogEntry> getEnabledEntries(String categoryId) {
        var data = categories.get(categoryId);
        if (data == null || !isCategoryVisible(categoryId)) {
            return List.of();
        }
        return data.getEnabledEntries();
    }

    public int getEnabledCount(String categoryId) {
        return getEnabledEntries(categoryId).size();
    }

    public Optional<ShopItemSetting> findItemSetting(String catalogKey) {
        for (var data : categories.values()) {
            var setting = data.getItemSetting(catalogKey);
            if (setting.isPresent()) return setting;
        }
        return Optional.empty();
    }

    public double getBasePrice(String catalogKey, String categoryId) {
        var data = categories.get(categoryId);
        if (data != null) {
            return data.resolveBasePrice(catalogKey);
        }
        return findItemSetting(catalogKey).map(ShopItemSetting::getPrice).orElse(globalDefaultPrice());
    }

    public double getBasePriceForEntry(CatalogEntry entry) {
        return findItemSetting(entry.getKey())
                .map(ShopItemSetting::getPrice)
                .orElseGet(() -> priceCalculator.calculate(entry));
    }

    public double resolveBasePrice(ItemCatalog catalog, String catalogKey, double globalDefault) {
        var setting = findItemSetting(catalogKey);
        if (setting.isPresent()) {
            return setting.get().getPrice();
        }
        var entry = catalog.getByKey(catalogKey);
        if (entry != null) {
            return priceCalculator.calculate(entry);
        }
        return globalDefault;
    }

    public List<CatalogEntry> search(Player player, String query, ItemCatalog catalog) {
        if (query == null || query.isBlank()) return List.of();

        var catalogResults = catalog.search(player, query);
        var enabledKeys = new java.util.HashSet<String>();
        for (var data : categories.values()) {
            if (!data.getDefinition().isEnabled()) continue;
            for (var entry : data.getEnabledEntries()) {
                enabledKeys.add(entry.getKey());
            }
        }

        var results = new ArrayList<CatalogEntry>();
        for (var entry : catalogResults) {
            if (enabledKeys.contains(entry.getKey())) {
                results.add(entry);
            }
        }
        return results;
    }

    public boolean isItemInShop(CatalogEntry entry) {
        if (entry == null) return false;
        return findItemSetting(entry.getKey())
                .map(s -> s.isEnabled() && isCategoryContaining(entry.getKey()))
                .orElse(false);
    }

    private boolean isCategoryContaining(String catalogKey) {
        for (var data : categories.values()) {
            if (!data.getDefinition().isEnabled()) continue;
            if (data.getItemSetting(catalogKey).filter(ShopItemSetting::isEnabled).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private ShopCategoryData parseCategoryFile(String folderId, File file, ItemCatalog catalog) {
        var yaml = YamlConfiguration.loadConfiguration(file);
        var categoryId = yaml.getString("category", folderId);
        var displayName = yaml.getString("display-name", "");
        var icon = parseIcon(yaml.getString("icon", "CHEST"));
        var enabled = yaml.getBoolean("enabled", true);
        var slot = yaml.getInt("slot", 100);
        var defaultPrice = yaml.getDouble("default-price", globalDefaultPrice());

        var definition = new ShopCategoryDefinition(categoryId, displayName, icon, enabled, slot);
        var data = new ShopCategoryData(definition, defaultPrice);

        var section = yaml.getConfigurationSection("items");
        if (section != null) {
            for (var yamlKey : section.getKeys(false)) {
                var itemSection = section.getConfigurationSection(yamlKey);
                if (itemSection == null) continue;

                var materialId = itemSection.getString("material", yamlKey);
                var catalogKey = itemSection.getString("catalog-key", "");
                var itemEnabled = itemSection.getBoolean("enabled", true);
                var price = resolveItemPrice(itemSection, catalogKey, materialId, catalog, defaultPrice);

                var resolved = ShopItemResolver.resolve(catalogKey, materialId, catalog);
                var resolvedKey = resolved.map(CatalogEntry::getKey).orElse(
                        catalogKey != null && !catalogKey.isBlank() ? catalogKey : yamlKey);

                data.putItem(new ShopItemSetting(resolvedKey, materialId, itemEnabled, price));
            }
        }

        data.rebuildEnabledEntries(catalog);
        return data;
    }

    private double resolveItemPrice(org.bukkit.configuration.ConfigurationSection section,
                                    String catalogKey, String materialId,
                                    ItemCatalog catalog, double fallback) {
        if (section.contains("price")) {
            return section.getDouble("price");
        }
        var entry = ShopItemResolver.resolve(catalogKey, materialId, catalog).orElse(null);
        if (entry != null) {
            return priceCalculator.calculate(entry);
        }
        return fallback;
    }

    private void backfillMissingPrices(ItemCatalog catalog) {
        int updated = 0;
        for (var categoryId : categories.keySet()) {
            var file = getCategoryFile(categoryId);
            if (!file.exists()) continue;

            var yaml = YamlConfiguration.loadConfiguration(file);
            var section = yaml.getConfigurationSection("items");
            if (section == null) continue;

            boolean changed = false;
            for (var yamlKey : section.getKeys(false)) {
                var itemSection = section.getConfigurationSection(yamlKey);
                if (itemSection == null || itemSection.contains("price")) continue;

                var materialId = itemSection.getString("material", yamlKey);
                var catalogKey = itemSection.getString("catalog-key", "");
                var entry = ShopItemResolver.resolve(catalogKey, materialId, catalog).orElse(null);
                if (entry == null) continue;

                yaml.set("items." + yamlKey + ".price", priceCalculator.calculate(entry));
                changed = true;
                updated++;
            }

            if (changed) {
                saveYaml(file, yaml);
            }
        }

        if (updated > 0) {
            plugin.getLogger().info("已補上 " + updated + " 項缺漏 price 至 shop 設定檔");
        }
    }

    private Material parseIcon(String name) {
        var material = ShopItemResolver.parseMaterial(name);
        return material != null ? material : Material.CHEST;
    }

    private double globalDefaultPrice() {
        return plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
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
                    ashop 系統商店 — 完全由資料夾設定
                    ==================================

                    結構：
                      shop/
                        <分類名稱>/items.yml
                        _template/items.yml   （範例，不會顯示在 GUI）

                    新增分類：
                      1. 建立 shop/我的分類/items.yml
                      2. 參考 _template/items.yml 格式填寫
                      3. 執行 /shop reload

                    items.yml 格式：
                      category: my_category      # 可選，預設為資料夾名稱
                      display-name: 我的分類     # GUI 顯示名稱
                      icon: DIAMOND              # GUI 圖示（Material 名稱）
                      enabled: true              # 是否顯示此分類
                      slot: 0                    # 排序（數字越小越前面）
                      default-price: 10.0        # 未指定 price 時的預設值

                      items:
                        stone:
                          material: STONE        # 或 minecraft:stone
                          price: 2.5
                          enabled: true
                        custom_potion:
                          material: POTION
                          catalog-key: "..."     # NBT 變體用（可選）
                          price: 20.0

                    修改後執行 /shop reload 或重啟伺服器。
                    """, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("無法寫入 shop/README.txt：" + e.getMessage());
        }
    }

    public boolean isItemSellable(CatalogEntry entry) {
        return isItemInShop(entry);
    }

    public boolean isItemPurchasable(CatalogEntry entry) {
        return isItemSellable(entry);
    }

    /**
     * 還原 shop/ 為預設 12 分類 + 全原版物品（管理員指令用）
     */
    public ShopRestoreResult restoreDefaults(ItemCatalog catalog) {
        var shopFolder = getShopFolder();
        int removed = 0;

        var dirs = shopFolder.listFiles(File::isDirectory);
        if (dirs != null) {
            for (var dir : dirs) {
                if (dir.getName().equalsIgnoreCase(TEMPLATE_FOLDER)) continue;
                try {
                    deleteDirectory(dir);
                    removed++;
                } catch (IOException e) {
                    plugin.getLogger().warning("刪除分類資料夾失敗 " + dir.getName() + "：" + e.getMessage());
                }
            }
        }

        int categoryCount = 0;
        int itemCount = 0;
        var locale = plugin.getLocaleService().getDefaultLocale();

        for (var category : ItemCategory.values()) {
            var entries = catalog.getByCategory(category);
            if (entries.isEmpty()) continue;

            var file = getCategoryFile(category.getId());
            file.getParentFile().mkdirs();
            writeDefaultCategoryFile(category, entries, file, locale);
            categoryCount++;
            itemCount += entries.size();
        }

        extractTemplateIfAbsent();
        load(catalog);

        plugin.getLogger().info("shop 已還原預設：" + categoryCount + " 分類、" + itemCount + " 項物品");
        return new ShopRestoreResult(categoryCount, itemCount, removed);
    }

    private void writeDefaultCategoryFile(ItemCategory category, List<CatalogEntry> entries,
                                          File file, String localeCode) {
        var yaml = new YamlConfiguration();
        var displayName = plugin.getLocaleService().msg(localeCode, "category." + category.getId());

        yaml.set("category", category.getId());
        yaml.set("display-name", displayName);
        yaml.set("icon", category.getIcon().name());
        yaml.set("enabled", true);
        yaml.set("slot", category.ordinal());
        yaml.set("default-price", globalDefaultPrice());
        yaml.options().header("""
                ashop 預設分類 — %s
                由 /shop reset 產生，可自由編輯後 /shop reload
                """.formatted(category.getId()));

        var usedKeys = new java.util.HashSet<String>();
        for (var entry : entries) {
            var yamlKey = uniqueYamlKey(entry, usedKeys);
            var path = "items." + yamlKey;
            yaml.set(path + ".catalog-key", entry.getKey());
            yaml.set(path + ".material", entry.getMaterialId());
            yaml.set(path + ".enabled", true);
            yaml.set(path + ".price", priceCalculator.calculate(entry));
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

    private static void deleteDirectory(File dir) throws IOException {
        var children = dir.listFiles();
        if (children != null) {
            for (var child : children) {
                deleteDirectory(child);
            }
        }
        if (!dir.delete()) {
            throw new IOException("無法刪除 " + dir.getAbsolutePath());
        }
    }
}
