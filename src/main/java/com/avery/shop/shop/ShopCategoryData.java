package com.avery.shop.shop;

import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 單一分類的商店設定與已啟用商品快取
 */
public final class ShopCategoryData {

    private final ItemCategory category;
    private final boolean enabled;
    private final double defaultPrice;
    private final Map<String, ShopItemSetting> items = new LinkedHashMap<>();
    private List<CatalogEntry> enabledEntries = List.of();

    public ShopCategoryData(ItemCategory category, boolean enabled, double defaultPrice) {
        this.category = category;
        this.enabled = enabled;
        this.defaultPrice = defaultPrice;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getDefaultPrice() {
        return defaultPrice;
    }

    public Map<String, ShopItemSetting> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public void putItem(ShopItemSetting setting) {
        items.put(setting.getCatalogKey(), setting);
    }

    public void setEnabledEntries(List<CatalogEntry> entries) {
        enabledEntries = List.copyOf(entries);
    }

    public List<CatalogEntry> getEnabledEntries() {
        return enabledEntries;
    }

    public ShopItemSetting getItemSetting(String catalogKey) {
        return items.get(catalogKey);
    }

    public double resolveBasePrice(String catalogKey, double globalDefault) {
        var setting = items.get(catalogKey);
        if (setting != null && setting.getPrice() != null) {
            return setting.getPrice();
        }
        return defaultPrice > 0 ? defaultPrice : globalDefault;
    }

    public void rebuildEnabledEntries(com.avery.shop.catalog.ItemCatalog catalog) {
        var list = new ArrayList<CatalogEntry>();
        for (var setting : items.values()) {
            if (!setting.isEnabled()) continue;
            var entry = catalog.getByKey(setting.getCatalogKey());
            if (entry != null) {
                list.add(entry);
            }
        }
        enabledEntries = List.copyOf(list);
    }
}
