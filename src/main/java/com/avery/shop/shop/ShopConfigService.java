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
    private final Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
    private final ItemPriceCalculator priceCalculator;
    private final ServerPriceExchange priceExchange;

    public ShopConfigService(ShopPlugin plugin) {
        this.plugin = plugin;
        this.priceCalculator = new ItemPriceCalculator(plugin);
        this.priceExchange = new ServerPriceExchange(plugin);
    }

    public ServerPriceExchange getPriceExchange() {
        return priceExchange;
    }

    public ItemPriceCalculator getPriceCalculator() {
        return priceCalculator;
    }

    /** 將 shop 基準價換算為伺服器實際金額（× multiply + add） */
    public double applyServerPrice(double basePrice) {
        return priceExchange.apply(basePrice);
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
        var dir = new File(getShopFolder(), categoryId.replace('/', File.separatorChar));
        return new File(dir, "items.yml");
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
        childrenByParent.clear();
        extractTemplateIfAbsent();

        var shopFolder = getShopFolder();
        var loaded = new ArrayList<ShopCategoryData>();
        var topDirs = shopFolder.listFiles(File::isDirectory);
        if (topDirs != null) {
            for (var dir : topDirs) {
                loadCategoryFilesRecursively(shopFolder, dir, catalog, loaded);
            }
        }

        loaded.sort(Comparator
                .comparingInt((ShopCategoryData d) -> d.getDefinition().getSlot())
                .thenComparing(d -> d.getCategoryId()));

        for (var data : loaded) {
            categories.put(data.getCategoryId(), data);
        }
        buildChildrenIndex();

        if (plugin.getConfig().getBoolean("shop.pricing.backfill-missing-prices", true)) {
            backfillMissingPrices(catalog);
        }

        int total = categories.values().stream().mapToInt(d -> d.getEnabledEntries().size()).sum();
        int roots = getRootCategories().size();
        plugin.getLogger().info("商店載入完成：" + roots + " 個頂層分類、"
                + categories.size() + " 個節點、" + total + " 項商品");
    }

    private void loadCategoryFilesRecursively(File shopRoot, File dir, ItemCatalog catalog,
                                              List<ShopCategoryData> loaded) {
        if (dir.getName().startsWith(".") || dir.getName().equalsIgnoreCase(TEMPLATE_FOLDER)) {
            return;
        }

        var itemsFile = new File(dir, "items.yml");
        String categoryId = shopRoot.toPath().relativize(dir.toPath())
                .toString().replace('\\', '/');
        if (categoryId.isEmpty()) {
            categoryId = dir.getName();
        }

        if (itemsFile.exists() && !categoryId.equals(TEMPLATE_FOLDER)) {
            try {
                loaded.add(parseCategoryFile(categoryId, parentCategoryId(categoryId), itemsFile, catalog));
            } catch (Exception e) {
                plugin.getLogger().warning("載入分類 " + categoryId + " 失敗：" + e.getMessage());
            }
        }

        var children = dir.listFiles(File::isDirectory);
        if (children == null) return;
        for (var child : children) {
            if (child.getName().startsWith(".") || child.getName().equalsIgnoreCase(TEMPLATE_FOLDER)) {
                continue;
            }
            loadCategoryFilesRecursively(shopRoot, child, catalog, loaded);
        }
    }

    private static String parentCategoryId(String categoryId) {
        var idx = categoryId.lastIndexOf('/');
        return idx < 0 ? null : categoryId.substring(0, idx);
    }

    private void buildChildrenIndex() {
        childrenByParent.clear();
        for (var data : categories.values()) {
            var parent = data.getDefinition().getParentId();
            if (parent != null) {
                childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>())
                        .add(data.getCategoryId());
            }
        }
        for (var entry : childrenByParent.entrySet()) {
            entry.getValue().sort(Comparator
                    .<String>comparingInt(id -> categories.get(id).getDefinition().getSlot())
                    .thenComparing(id -> id));
        }
    }

    public List<ShopCategoryDefinition> getCategories() {
        return getRootCategories();
    }

    public List<ShopCategoryDefinition> getRootCategories() {
        return categories.values().stream()
                .map(ShopCategoryData::getDefinition)
                .filter(ShopCategoryDefinition::isRoot)
                .sorted(Comparator.comparingInt(ShopCategoryDefinition::getSlot)
                        .thenComparing(ShopCategoryDefinition::getId))
                .toList();
    }

    public List<ShopCategoryDefinition> getChildCategories(String parentId) {
        var childIds = childrenByParent.getOrDefault(parentId, List.of());
        return childIds.stream()
                .map(categories::get)
                .filter(data -> data != null && data.getDefinition().isEnabled())
                .map(ShopCategoryData::getDefinition)
                .toList();
    }

    public boolean hasChildCategories(String categoryId) {
        return !getChildCategories(categoryId).isEmpty();
    }

    public String getParentCategoryId(String categoryId) {
        var data = categories.get(categoryId);
        return data != null ? data.getDefinition().getParentId() : null;
    }

    public Optional<ShopCategoryData> getCategory(String categoryId) {
        return Optional.ofNullable(categories.get(categoryId));
    }

    public boolean isCategoryVisible(String categoryId) {
        var data = categories.get(categoryId);
        return data != null && data.getDefinition().isEnabled();
    }

    /** 此分類自身 allow-buy 設定（不含父分類） */
    public boolean isCategoryAllowBuyLocal(String categoryId) {
        var data = categories.get(categoryId);
        return data != null && data.getDefinition().isAllowBuy();
    }

    /**
     * 分類是否允許玩家購買（含父分類繼承：任一上層關閉則整棵子樹不可購買）
     */
    public boolean isCategoryAllowBuy(String categoryId) {
        var data = categories.get(categoryId);
        if (data == null || !data.getDefinition().isEnabled()) {
            return false;
        }
        if (!data.getDefinition().isAllowBuy()) {
            return false;
        }
        var parent = data.getDefinition().getParentId();
        if (parent != null) {
            return isCategoryAllowBuy(parent);
        }
        return true;
    }

    /** 父分類關閉購買時，回傳阻擋繼承的最近上層分類 id */
    public Optional<String> findBuyBlockedByAncestor(String categoryId) {
        var data = categories.get(categoryId);
        if (data == null) return Optional.empty();
        if (!data.getDefinition().isAllowBuy()) {
            return Optional.of(categoryId);
        }
        var parent = data.getDefinition().getParentId();
        if (parent != null) {
            return findBuyBlockedByAncestor(parent);
        }
        return Optional.empty();
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
        return countEnabledRecursive(categoryId);
    }

    private int countEnabledRecursive(String categoryId) {
        var data = categories.get(categoryId);
        if (data == null || !isCategoryVisible(categoryId)) {
            return 0;
        }
        int count = data.getEnabledEntries().size();
        for (var childId : childrenByParent.getOrDefault(categoryId, List.of())) {
            count += countEnabledRecursive(childId);
        }
        return count;
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

    private ShopCategoryData parseCategoryFile(String categoryId, String parentId, File file,
                                             ItemCatalog catalog) {
        var yaml = YamlConfiguration.loadConfiguration(file);
        var yamlCategoryId = yaml.getString("category", categoryId);
        var displayName = yaml.getString("display-name", "");
        var icon = parseIcon(yaml.getString("icon",
                ShopSubcategoryResolver.iconFor(relativePath(categoryId)).name()));
        var enabled = yaml.getBoolean("enabled", true);
        var allowBuy = yaml.getBoolean("allow-buy", true);
        var defaultSlot = defaultSlotFor(categoryId);
        var slot = yaml.getInt("slot", defaultSlot);
        var defaultPrice = yaml.getDouble("default-price", globalDefaultPrice());

        var definition = new ShopCategoryDefinition(yamlCategoryId, parentId, displayName, icon, enabled, allowBuy, slot);
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

    private static String relativePath(String categoryId) {
        var idx = categoryId.indexOf('/');
        return idx < 0 ? categoryId : categoryId.substring(idx + 1);
    }

    private static int defaultSlotFor(String categoryId) {
        return ShopSubcategoryResolver.slotOrder(relativePath(categoryId));
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
                        <分類名稱>/<子分類>/items.yml   （支援多層子分類）
                        _template/items.yml   （範例，不會顯示在 GUI）

                    巢狀子分類：
                      有子資料夾時，父分類 items.yml 可只設定 display-name / icon，
                      實際商品放在子資料夾的 items.yml。
                      例：shop/blocks/building/wood/items.yml

                    新增分類：
                      1. 建立 shop/我的分類/items.yml
                      2. 參考 _template/items.yml 格式填寫
                      3. 執行 /shop reload

                    items.yml 格式：
                      category: my_category      # 可選，預設為資料夾名稱
                      display-name: 我的分類     # GUI 顯示名稱
                      icon: DIAMOND              # GUI 圖示（Material 名稱）
                      enabled: true              # 是否顯示此分類
                      allow-buy: true            # 是否允許玩家購買（false 時子分類一併禁止）
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
        if (!isItemInShop(entry)) return false;
        var categoryId = findCategoryIdForKey(entry.getKey());
        return categoryId != null && isCategoryAllowBuy(categoryId);
    }

    /**
     * 解析玩家手持物品是否在 shop 設定中（含耐久/normalized 比對）
     */
    public Optional<ResolvedShopItem> resolvePlayerItem(org.bukkit.inventory.ItemStack stack,
                                                          ItemCatalog catalog) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }

        var entry = catalog.findMatching(stack);
        if (entry != null) {
            var resolved = resolveByCatalogKey(entry);
            if (resolved.isPresent()) return resolved;
        }

        var fingerprint = com.avery.shop.catalog.ItemMatcher.fingerprint(
                normalizeForLookup(stack));
        var byFingerprint = resolveByCatalogKey(catalog.getByKey(fingerprint));
        if (byFingerprint.isPresent()) return byFingerprint;

        for (var data : categories.values()) {
            if (!data.getDefinition().isEnabled()) continue;
            for (var shopEntry : data.getEnabledEntries()) {
                if (!com.avery.shop.catalog.ItemMatcher.matchesForTrade(stack, shopEntry.getTemplate())) {
                    continue;
                }
                var setting = data.getItemSetting(shopEntry.getKey());
                if (setting.isPresent() && setting.get().isEnabled()) {
                    return Optional.of(new ResolvedShopItem(shopEntry, setting.get(), data.getCategoryId()));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedShopItem> resolveByCatalogKey(CatalogEntry entry) {
        if (entry == null) return Optional.empty();
        var setting = findItemSetting(entry.getKey());
        if (setting.isEmpty() || !setting.get().isEnabled()) return Optional.empty();
        var categoryId = findCategoryIdForKey(entry.getKey());
        if (categoryId == null || !isCategoryVisible(categoryId)) return Optional.empty();
        return Optional.of(new ResolvedShopItem(entry, setting.get(), categoryId));
    }

    private String findCategoryIdForKey(String catalogKey) {
        for (var data : categories.values()) {
            if (data.getItemSetting(catalogKey).isPresent()) {
                return data.getCategoryId();
            }
        }
        return null;
    }

    public String findCategoryIdForCatalogKey(String catalogKey) {
        return findCategoryIdForKey(catalogKey);
    }

    private static org.bukkit.inventory.ItemStack normalizeForLookup(org.bukkit.inventory.ItemStack stack) {
        var copy = stack.clone();
        copy.setAmount(1);
        var meta = copy.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            damageable.setDamage(0);
            copy.setItemMeta(damageable);
        }
        return copy;
    }

    public boolean canPlayerSell(org.bukkit.inventory.ItemStack stack, ItemCatalog catalog) {
        if (!plugin.getConfig().getBoolean("system-shop.sell-to-system", true)) return false;
        return resolvePlayerItem(stack, catalog).isPresent();
    }

    public boolean canPlayerBuy(org.bukkit.inventory.ItemStack stack, ItemCatalog catalog) {
        var entry = catalog.findMatching(stack);
        return entry != null && isItemPurchasable(entry);
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

            var bySubPath = new LinkedHashMap<String, List<CatalogEntry>>();
            for (var entry : entries) {
                var subPath = ShopSubcategoryResolver.resolve(category, entry);
                bySubPath.computeIfAbsent(subPath, k -> new ArrayList<>()).add(entry);
            }

            categoryCount += writeDefaultCategoryTree(category, bySubPath, locale);
            itemCount += entries.size();
        }

        extractTemplateIfAbsent();
        load(catalog);

        plugin.getLogger().info("shop 已還原預設：" + categoryCount + " 個分類節點、" + itemCount + " 項物品");
        return new ShopRestoreResult(categoryCount, itemCount, removed);
    }

    private int writeDefaultCategoryTree(ItemCategory topCategory,
                                         Map<String, List<CatalogEntry>> bySubPath,
                                         String localeCode) {
        var topId = topCategory.getId();
        boolean needsNesting = bySubPath.size() > 1
                || bySubPath.keySet().stream().anyMatch(path -> path.contains("/"));

        int nodes = 0;
        if (!needsNesting && bySubPath.containsKey("all")) {
            var file = getCategoryFile(topId);
            file.getParentFile().mkdirs();
            writeLeafCategoryFile(topId, null, topCategory.getIcon(), topCategory.ordinal(),
                    bySubPath.get("all"), file, localeCode, topId);
            return 1;
        }

        var rootFile = getCategoryFile(topId);
        rootFile.getParentFile().mkdirs();
        writeContainerCategoryFile(topId, null, topCategory.getIcon(), topCategory.ordinal(),
                rootFile, localeCode, topId);
        nodes++;

        var containerPaths = new java.util.TreeSet<String>();
        for (var path : bySubPath.keySet()) {
            var parts = path.split("/");
            for (int i = 1; i < parts.length; i++) {
                containerPaths.add(String.join("/", java.util.Arrays.copyOfRange(parts, 0, i)));
            }
        }

        for (var containerPath : containerPaths) {
            var fullId = topId + "/" + containerPath;
            var file = getCategoryFile(fullId);
            file.getParentFile().mkdirs();
            writeContainerCategoryFile(fullId, parentCategoryId(fullId),
                    ShopSubcategoryResolver.iconFor(containerPath),
                    ShopSubcategoryResolver.slotOrder(containerPath),
                    file, localeCode, fullId);
            nodes++;
        }

        for (var entry : bySubPath.entrySet()) {
            var fullId = topId + "/" + entry.getKey();
            var file = getCategoryFile(fullId);
            file.getParentFile().mkdirs();
            writeLeafCategoryFile(fullId, parentCategoryId(fullId),
                    ShopSubcategoryResolver.iconFor(entry.getKey()),
                    ShopSubcategoryResolver.slotOrder(entry.getKey()),
                    entry.getValue(), file, localeCode, fullId);
            nodes++;
        }

        return nodes;
    }

    private void writeContainerCategoryFile(String categoryId, String parentId, Material icon,
                                            int slot, File file, String localeCode,
                                            String localeKey) {
        var yaml = new YamlConfiguration();
        var displayName = plugin.getLocaleService().msg(localeCode, "category."
                + ShopSubcategoryResolver.toLocaleSuffix(localeKey));

        yaml.set("category", categoryId);
        yaml.set("display-name", displayName.equals("category." + ShopSubcategoryResolver.toLocaleSuffix(localeKey))
                ? categoryId : displayName);
        yaml.set("icon", icon.name());
        yaml.set("enabled", true);
        yaml.set("allow-buy", true);
        yaml.set("slot", slot);
        yaml.set("default-price", globalDefaultPrice());
        yaml.options().header("""
                ashop 分類容器 — %s
                可在此資料夾下建立子資料夾作為子分類，或直接在此 items 區塊加入商品
                """.formatted(categoryId));
        saveYaml(file, yaml);
    }

    private void writeLeafCategoryFile(String categoryId, String parentId, Material icon,
                                       int slot, List<CatalogEntry> entries, File file,
                                       String localeCode, String localeKey) {
        var yaml = new YamlConfiguration();
        var displayName = plugin.getLocaleService().msg(localeCode, "category."
                + ShopSubcategoryResolver.toLocaleSuffix(localeKey));

        yaml.set("category", categoryId);
        yaml.set("display-name", displayName.equals("category." + ShopSubcategoryResolver.toLocaleSuffix(localeKey))
                ? categoryId : displayName);
        yaml.set("icon", icon.name());
        yaml.set("enabled", true);
        yaml.set("allow-buy", true);
        yaml.set("slot", slot);
        yaml.set("default-price", globalDefaultPrice());
        yaml.options().header("""
                ashop 商品分類 — %s
                由 /shop reset 產生，可自由編輯後 /shop reload
                """.formatted(categoryId));

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

    private void writeDefaultCategoryFile(ItemCategory category, List<CatalogEntry> entries,
                                          File file, String localeCode) {
        writeLeafCategoryFile(category.getId(), null, category.getIcon(), category.ordinal(),
                entries, file, localeCode, category.getId());
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
