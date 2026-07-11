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
 * <p>
 * 漲停／跌停後的買賣仍寫入 total-buys / total-sells，但不增加有效 buys / sells，
 * 避免「買 100 個漲停、卻要賣 100 個才恢復」的問題。
 */
public final class DynamicPricingService {

    private static final double CAP_EPSILON = 1e-9;

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
        var cfg = readConfig();

        double rawMultiplier = rawMultiplier(data, activeStock, cfg);
        double multiplier = clamp(rawMultiplier, cfg.minMult, cfg.maxMult);

        double price = Math.max(0.01, basePrice * multiplier);
        double changePercent = (multiplier - 1.0) * 100.0;
        var cap = resolveCap(rawMultiplier, multiplier, cfg);

        return new PriceQuote(price, multiplier, changePercent, cap);
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

    /**
     * 記錄購買：全部交易寫入 total-buys；僅在未達漲停時增加有效 buys
     */
    public void recordBuy(String catalogKey, int activeStock) {
        if (!isEnabled() || catalogKey == null) return;
        var data = market.computeIfAbsent(catalogKey, k -> new MarketData(0, 0));
        data.recordAllBuy();
        if (!isAtMaxCap(catalogKey, activeStock)) {
            data.recordBuy();
        }
    }

    /**
     * 記錄出售：全部交易寫入 total-sells；僅在未達跌停時增加有效 sells
     */
    public void recordSell(String catalogKey, int activeStock) {
        if (!isEnabled() || catalogKey == null) return;
        var data = market.computeIfAbsent(catalogKey, k -> new MarketData(0, 0));
        data.recordAllSell();
        if (!isAtMinCap(catalogKey, activeStock)) {
            data.recordSell();
        }
    }

    public Map<String, MarketData> getMarketSnapshot() {
        return Map.copyOf(market);
    }

    private boolean isAtMaxCap(String catalogKey, int activeStock) {
        var cfg = readConfig();
        var data = market.getOrDefault(catalogKey, MarketData.EMPTY);
        return rawMultiplier(data, activeStock, cfg) >= cfg.maxMult - CAP_EPSILON;
    }

    private boolean isAtMinCap(String catalogKey, int activeStock) {
        var cfg = readConfig();
        var data = market.getOrDefault(catalogKey, MarketData.EMPTY);
        return rawMultiplier(data, activeStock, cfg) <= cfg.minMult + CAP_EPSILON;
    }

    private static double rawMultiplier(MarketData data, int activeStock, PricingConfig cfg) {
        double multiplier = 1.0;
        multiplier += data.getTotalBuys() * cfg.perBuy;
        multiplier -= data.getTotalSells() * cfg.perSell;
        int shortage = Math.max(0, cfg.referenceStock - activeStock);
        multiplier += shortage * cfg.perShortage;
        return multiplier;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static PriceQuote.PriceCap resolveCap(double rawMultiplier, double clampedMultiplier,
                                                   PricingConfig cfg) {
        if (rawMultiplier >= cfg.maxMult - CAP_EPSILON
                || clampedMultiplier >= cfg.maxMult - CAP_EPSILON) {
            return PriceQuote.PriceCap.MAX;
        }
        if (rawMultiplier <= cfg.minMult + CAP_EPSILON
                || clampedMultiplier <= cfg.minMult + CAP_EPSILON) {
            return PriceQuote.PriceCap.MIN;
        }
        return PriceQuote.PriceCap.NONE;
    }

    private PricingConfig readConfig() {
        var cfg = plugin.getConfig();
        return new PricingConfig(
                cfg.getDouble("dynamic-pricing.per-buy-increase", 2.0) / 100.0,
                cfg.getDouble("dynamic-pricing.per-sell-decrease", 2.0) / 100.0,
                cfg.getDouble("dynamic-pricing.per-stock-shortage-increase", 3.0) / 100.0,
                cfg.getInt("dynamic-pricing.reference-stock", 5),
                cfg.getDouble("dynamic-pricing.min-multiplier", 0.2),
                cfg.getDouble("dynamic-pricing.max-multiplier", 5.0)
        );
    }

    private record PricingConfig(
            double perBuy,
            double perSell,
            double perShortage,
            int referenceStock,
            double minMult,
            double maxMult
    ) {}
}
