package com.avery.shop.pricing;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemMatcher;
import com.avery.shop.shop.ShopListing;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 動態定價 - 越買越貴、越賣越便宜、物以稀為貴
 */
public final class DynamicPricingService {

    private final ShopPlugin plugin;
    private final ItemCatalog catalog;
    private final Map<String, MarketData> market = new HashMap<>();

    public DynamicPricingService(ShopPlugin plugin, ItemCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    public void load() {
        market.clear();
        market.putAll(MarketStorage.load(plugin));
    }

    public void save() {
        MarketStorage.save(plugin, market);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("dynamic-pricing.enabled", true);
    }

    /**
     * 取得物品 catalog key，無法辨識時用 fingerprint
     */
    public String resolveKey(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        var entry = catalog.findMatching(item);
        if (entry != null) return entry.getKey();
        return ItemMatcher.fingerprint(item);
    }

    /**
     * 計算當前動態價格
     */
    public PriceQuote quote(String catalogKey, double basePrice, int activeStock) {
        if (!isEnabled() || catalogKey == null) {
            return new PriceQuote(basePrice, 1.0, 0.0);
        }

        var data = market.getOrDefault(catalogKey, MarketData.EMPTY);
        var cfg = plugin.getConfig();

        double perBuy = cfg.getDouble("dynamic-pricing.per-buy-increase", 2.0) / 100.0;
        double perSell = cfg.getDouble("dynamic-pricing.per-sell-decrease", 1.5) / 100.0;
        double perShortage = cfg.getDouble("dynamic-pricing.per-stock-shortage-increase", 3.0) / 100.0;
        int referenceStock = cfg.getInt("dynamic-pricing.reference-stock", 5);
        double minMult = cfg.getDouble("dynamic-pricing.min-multiplier", 0.2);
        double maxMult = cfg.getDouble("dynamic-pricing.max-multiplier", 5.0);

        double multiplier = 1.0;
        multiplier += data.getTotalBuys() * perBuy;
        multiplier -= data.getTotalSells() * perSell;

        int shortage = Math.max(0, referenceStock - activeStock);
        multiplier += shortage * perShortage;

        multiplier = Math.max(minMult, Math.min(maxMult, multiplier));

        double price = Math.max(0.01, basePrice * multiplier);
        double changePercent = (multiplier - 1.0) * 100.0;

        return new PriceQuote(price, multiplier, changePercent);
    }

    public PriceQuote quoteForListing(ShopListing listing, int activeStock) {
        var key = resolveKey(listing.getItem());
        var base = getBasePrice(listing);
        return quote(key, base, activeStock);
    }

    /**
     * 取得上架的基準價（動態計算時的起點）
     */
    public double getBasePrice(ShopListing listing) {
        var systemId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (listing.getSellerId().equals(systemId)) {
            return plugin.getConfig().getDouble("dynamic-pricing.base-price",
                    plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        }
        return listing.getPrice();
    }

    public boolean useDynamicForListing(ShopListing listing) {
        if (!isEnabled()) return false;
        var systemId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        if (listing.getSellerId().equals(systemId)) {
            return plugin.getConfig().getBoolean("dynamic-pricing.system-shop", true);
        }
        return plugin.getConfig().getBoolean("dynamic-pricing.player-listings", false);
    }

    public double getEffectivePrice(ShopListing listing, int activeStock) {
        if (!useDynamicForListing(listing)) {
            return listing.getPrice();
        }
        return quoteForListing(listing, activeStock).price();
    }

    public double getSuggestedPrice(ItemStack item, int activeStock) {
        var key = resolveKey(item);
        var base = plugin.getConfig().getDouble("dynamic-pricing.base-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));
        return quote(key, base, activeStock).price();
    }

    public void recordBuy(String catalogKey) {
        if (!isEnabled() || catalogKey == null) return;
        market.computeIfAbsent(catalogKey, k -> new MarketData(0, 0)).recordBuy();
    }

    public void recordSell(String catalogKey) {
        if (!isEnabled() || catalogKey == null) return;
        market.computeIfAbsent(catalogKey, k -> new MarketData(0, 0)).recordSell();
    }

    public Map<String, MarketData> getMarketSnapshot() {
        return Map.copyOf(market);
    }
}
