package com.avery.shop.shop;

import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemCategory;
import com.avery.shop.pricing.DynamicPricingService;

import java.util.*;

/**
 * 上架索引快取 — 避免每次 GUI 操作全量掃描
 */
public final class ListingIndex {

    private final Map<String, Integer> stockByKey = new HashMap<>();
    private final Map<ItemCategory, Integer> categoryCounts = new EnumMap<>(ItemCategory.class);
    private final Map<UUID, String> keyByListingId = new HashMap<>();

    public ListingIndex() {
        for (var cat : ItemCategory.values()) {
            categoryCounts.put(cat, 0);
        }
    }

    public void rebuild(List<ShopListing> listings, ItemCatalog catalog, DynamicPricingService pricing) {
        stockByKey.clear();
        keyByListingId.clear();
        for (var cat : ItemCategory.values()) {
            categoryCounts.put(cat, 0);
        }

        for (var listing : listings) {
            register(listing, catalog, pricing);
        }
    }

    public void register(ShopListing listing, ItemCatalog catalog, DynamicPricingService pricing) {
        var key = pricing.resolveKey(listing.getItem());
        keyByListingId.put(listing.getId(), key);
        if (key != null) {
            stockByKey.merge(key, 1, Integer::sum);
        }
        var entry = catalog.findMatching(listing.getItem());
        if (entry != null) {
            categoryCounts.merge(entry.getCategory(), 1, Integer::sum);
        }
    }

    public void unregister(ShopListing listing, ItemCatalog catalog) {
        var key = keyByListingId.remove(listing.getId());
        if (key != null) {
            stockByKey.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
        }
        var entry = catalog.findMatching(listing.getItem());
        if (entry != null) {
            categoryCounts.computeIfPresent(entry.getCategory(), (k, v) -> v <= 1 ? 0 : v - 1);
        }
    }

    public int getStock(String catalogKey) {
        if (catalogKey == null) return 0;
        return stockByKey.getOrDefault(catalogKey, 0);
    }

    public int getCategoryCount(ItemCategory category) {
        return categoryCounts.getOrDefault(category, 0);
    }

    public String getKey(UUID listingId) {
        return keyByListingId.get(listingId);
    }
}
