package com.avery.shop.shop;

import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 單一分類的商店設定與已啟用商品快取
 */
public final class ShopCategoryData {

    private final ShopCategoryDefinition definition;
    private final double defaultPrice;
    private final Map<String, ShopItemSetting> items = new LinkedHashMap<>();
    private List<CatalogEntry> enabledEntries = List.of();

    public ShopCategoryData(ShopCategoryDefinition definition, double defaultPrice) {
        this.definition = definition;
        this.defaultPrice = defaultPrice;
    }

    public ShopCategoryDefinition getDefinition() {
        return definition;
    }

    public String getCategoryId() {
        return definition.getId();
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

    public Optional<ShopItemSetting> getItemSetting(String catalogKey) {
        return Optional.ofNullable(items.get(catalogKey));
    }

    public double resolveBasePrice(String catalogKey) {
        return getItemSetting(catalogKey).map(ShopItemSetting::getPrice).orElse(defaultPrice);
    }

    public void rebuildEnabledEntries(ItemCatalog catalog) {
        var list = new ArrayList<CatalogEntry>();
        for (var setting : items.values()) {
            if (!setting.isEnabled()) continue;
            ShopItemResolver.resolve(setting.getCatalogKey(), setting.getMaterialId(), catalog)
                    .ifPresent(list::add);
        }
        enabledEntries = List.copyOf(list);
    }
}
