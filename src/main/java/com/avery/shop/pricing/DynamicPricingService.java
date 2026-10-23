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
    private org.bukkit.scheduler.BukkitTask reversionTask;

    public DynamicPricingService(ShopPlugin plugin, ItemCatalog catalog) {
        this.plugin = plugin;
        this.catalog = catalog;
    }

    public void load() {
        market.clear();
        market.putAll(MarketStorage.load(plugin));
        checkAndMigrateConfig();
        startReversionTask();
    }

    public void save() {
        MarketStorage.save(plugin, market);
    }

    public void checkAndMigrateConfig() {
        var cfg = plugin.getConfig();
        if (!cfg.contains("dynamic-pricing.auto-reversion")) {
            cfg.set("dynamic-pricing.auto-reversion.enabled", false);
            cfg.set("dynamic-pricing.auto-reversion.interval-minutes", 60);
            cfg.set("dynamic-pricing.auto-reversion.increase-rate-percent", 1.0);
            cfg.set("dynamic-pricing.auto-reversion.decrease-rate-percent", 1.0);
            plugin.saveConfig();
            plugin.getLogger().info("已自動升級寫入物價自動回歸設定 (dynamic-pricing.auto-reversion) 至 config.yml");
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("dynamic-pricing.enabled", true);
    }

    public boolean isAutoReversionEnabled() {
        return plugin.getConfig().getBoolean("dynamic-pricing.auto-reversion.enabled", false);
    }

    public void stopReversionTask() {
        if (reversionTask != null) {
            reversionTask.cancel();
            reversionTask = null;
        }
    }

    public void startReversionTask() {
        stopReversionTask();
        if (!isEnabled() || !isAutoReversionEnabled()) {
            return;
        }

        int intervalMinutes = plugin.getConfig().getInt("dynamic-pricing.auto-reversion.interval-minutes", 60);
        intervalMinutes = Math.max(1, intervalMinutes);
        long ticks = intervalMinutes * 60 * 20L;

        reversionTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try {
                processAutoReversion();
            } catch (Exception e) {
                plugin.getLogger().warning("物價自動回歸任務執行異常：" + e.getMessage());
            }
        }, ticks, ticks);

        plugin.getLogger().info("已啟動物價自動回歸任務，執行週期：每 " + intervalMinutes + " 分鐘一次");
    }

    public int processAutoReversion() {
        if (!isEnabled() || !isAutoReversionEnabled()) {
            return 0;
        }

        var cfg = readConfig();
        double increaseRatePercent = plugin.getConfig().getDouble("dynamic-pricing.auto-reversion.increase-rate-percent", 1.0);
        double decreaseRatePercent = plugin.getConfig().getDouble("dynamic-pricing.auto-reversion.decrease-rate-percent", 1.0);

        double increaseDelta = increaseRatePercent / 100.0;
        double decreaseDelta = decreaseRatePercent / 100.0;

        if (cfg.perBuy <= 0 && cfg.perSell <= 0) {
            return 0;
        }

        int adjustedCount = 0;
        for (var entry : market.entrySet()) {
            String key = entry.getKey();
            MarketData data = entry.getValue();

            double basePrice = plugin.getShopConfigService().getBasePrice(key,
                    plugin.getShopConfigService().findCategoryIdForCatalogKey(key));

            double currentDev = data.getTotalBuys() * cfg.perBuy - data.getTotalSells() * cfg.perSell;

            if (currentDev > CAP_EPSILON) {
                if (cfg.perBuy <= 0) continue;
                double targetDev = Math.max(0.0, currentDev - decreaseDelta);

                double oldMult = 1.0 + currentDev;
                double newMult = 1.0 + targetDev;
                double oldPrice = Math.max(0.01, basePrice * clamp(oldMult, cfg.minMult, cfg.maxMult));
                double newPrice = Math.max(0.01, basePrice * clamp(newMult, cfg.minMult, cfg.maxMult));

                double newBuys = (targetDev + data.getTotalSells() * cfg.perSell) / cfg.perBuy;
                data.setTotalBuys(newBuys);
                adjustedCount++;

                plugin.getLogger().info(String.format(
                        "[物價調整] 物品 %s 價格從 $%.2f (偏離 +%.1f%%) 依跌幅 -%.1f%% 回歸至 $%.2f (基準價: $%.2f)",
                        key, oldPrice, (oldMult - 1.0) * 100.0, decreaseRatePercent, newPrice, basePrice
                ));
            } else if (currentDev < -CAP_EPSILON) {
                if (cfg.perSell <= 0) continue;
                double targetDev = Math.min(0.0, currentDev + increaseDelta);

                double oldMult = 1.0 + currentDev;
                double newMult = 1.0 + targetDev;
                double oldPrice = Math.max(0.01, basePrice * clamp(oldMult, cfg.minMult, cfg.maxMult));
                double newPrice = Math.max(0.01, basePrice * clamp(newMult, cfg.minMult, cfg.maxMult));

                double newSells = (data.getTotalBuys() * cfg.perBuy - targetDev) / cfg.perSell;
                data.setTotalSells(newSells);
                adjustedCount++;

                plugin.getLogger().info(String.format(
                        "[物價調整] 物品 %s 價格從 $%.2f (偏離 %.1f%%) 依漲幅 +%.1f%% 回歸至 $%.2f (基準價: $%.2f)",
                        key, oldPrice, (oldMult - 1.0) * 100.0, increaseRatePercent, newPrice, basePrice
                ));
            }
        }

        if (adjustedCount > 0) {
            save();
            plugin.getLogger().info("[物價自動回歸] 已完成週期性物價調整，共調整 " + adjustedCount + " 項商品價格向基準值靠攏");
        }
        return adjustedCount;
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
     * 批次報價結果（邊際計價 / 離散積分）
     */
    public record BatchQuote(double totalPrice, double finalMultiplier, double averageUnitPrice) {}

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
     * 計算批次購買報價（邊際定價 / 離散積分）
     * 模擬 0 到 amount-1 次購買時，每次購買後有效 buys 的累加與 multiplier 演進，
     * 累計 totalPrice = sum(unitPrice_k)。單價以 Math.max(0.01, basePrice * clampedMultiplier) 計。
     */
    public BatchQuote calculateBatchBuyQuote(String catalogKey, double basePrice, int activeStock, int amount) {
        if (amount <= 0) {
            return new BatchQuote(0.0, 1.0, Math.max(0.01, basePrice));
        }
        if (!isEnabled() || catalogKey == null) {
            double unitPrice = Math.max(0.01, basePrice);
            return new BatchQuote(unitPrice * amount, 1.0, unitPrice);
        }

        var data = market.getOrDefault(catalogKey, MarketData.EMPTY);
        var cfg = readConfig();

        double simBuys = data.getTotalBuys();
        double simSells = data.getTotalSells();
        double totalPrice = 0.0;

        for (int i = 0; i < amount; i++) {
            double rawMult = rawMultiplier(simBuys, simSells, activeStock, cfg);
            double clampedMult = clamp(rawMult, cfg.minMult, cfg.maxMult);
            double unitPrice = Math.max(0.01, basePrice * clampedMult);
            totalPrice += unitPrice;

            if (rawMult < cfg.maxMult - CAP_EPSILON) {
                simBuys += 1.0;
            }
        }

        double finalRawMult = rawMultiplier(simBuys, simSells, activeStock, cfg);
        double finalMultiplier = clamp(finalRawMult, cfg.minMult, cfg.maxMult);
        double averageUnitPrice = totalPrice / amount;

        return new BatchQuote(totalPrice, finalMultiplier, averageUnitPrice);
    }

    /**
     * 計算批次出售報價（邊際定價 / 離散積分）
     * 模擬 0 到 amount-1 次出售時，每次出售後 effective sells 增加導致 multiplier 下滑，
     * 累計 totalSellIncome = sum(unitSellPrice_k)。單價以 basePrice * clampedMultiplier * sellRatio 計。
     * 內含批次交易套利防護判斷：對於同一批次物品，單位的總出售價格不得高於其重新買入成本。
     */
    public BatchQuote calculateBatchSellQuote(String catalogKey, double basePrice, int activeStock, int amount, double sellRatio) {
        return calculateIncrementalSellQuote(catalogKey, basePrice, activeStock, 0, amount, sellRatio, 1.0);
    }

    public BatchQuote calculateIncrementalSellQuote(String catalogKey, double basePrice, int activeStock,
                                                    int soldOffset, int amount, double sellRatio) {
        return calculateIncrementalSellQuote(catalogKey, basePrice, activeStock, soldOffset, amount, sellRatio, 1.0);
    }

    /**
     * 支援同批次 soldOffset 遞減計價與耐久度係數的出售報價計算
     */
    public BatchQuote calculateIncrementalSellQuote(String catalogKey, double basePrice, int activeStock,
                                                    int soldOffset, int amount, double sellRatio, double durabilityFactor) {
        if (amount <= 0) {
            return new BatchQuote(0.0, 1.0, 0.0);
        }
        double factor = Math.max(0.0, durabilityFactor);
        if (!isEnabled() || catalogKey == null) {
            double unitPrice = Math.max(0.01, basePrice * 1.0 * sellRatio * factor);
            return new BatchQuote(unitPrice * amount, 1.0, unitPrice);
        }

        var data = market.getOrDefault(catalogKey, MarketData.EMPTY);
        var cfg = readConfig();

        double simBuys = data.getTotalBuys();
        double simSells = data.getTotalSells();

        // 1. 先模擬同批次在此之前已賣出的 soldOffset 次對市場價格的推進（遞減效應）
        for (int i = 0; i < soldOffset; i++) {
            double rawMult = rawMultiplier(simBuys, simSells, activeStock, cfg);
            if (rawMult > cfg.minMult + CAP_EPSILON) {
                simSells += 1.0;
            }
        }

        // 2. 模擬本次 amount 次出售的離散積分計算
        double totalIncome = 0.0;
        for (int i = 0; i < amount; i++) {
            double rawMult = rawMultiplier(simBuys, simSells, activeStock, cfg);
            double clampedMult = clamp(rawMult, cfg.minMult, cfg.maxMult);
            double unitPrice = Math.max(0.01, basePrice * clampedMult * sellRatio * factor);
            totalIncome += unitPrice;

            if (rawMult > cfg.minMult + CAP_EPSILON) {
                simSells += 1.0;
            }
        }

        // 3. 套利防護判斷：對於同一批次物品，單位的總出售價格不得高於其重新買入成本（即總利潤不得大於或等於零）
        double rebuyCost = simulateBatchBuyCost(simBuys, simSells, basePrice, activeStock, amount, cfg);
        if (totalIncome >= rebuyCost) {
            totalIncome = Math.max(0.01, rebuyCost * 0.99);
        }

        double finalRawMult = rawMultiplier(simBuys, simSells, activeStock, cfg);
        double finalMultiplier = clamp(finalRawMult, cfg.minMult, cfg.maxMult);
        double averageUnitPrice = totalIncome / amount;

        return new BatchQuote(totalIncome, finalMultiplier, averageUnitPrice);
    }

    /**
     * 模擬在指定的 buys/sells 狀態下買入 amount 個物品所需的總成本
     */
    private double simulateBatchBuyCost(double startBuys, double startSells, double basePrice,
                                        int activeStock, int amount, PricingConfig cfg) {
        double currentBuys = startBuys;
        double cost = 0.0;
        for (int i = 0; i < amount; i++) {
            double rawMult = rawMultiplier(currentBuys, startSells, activeStock, cfg);
            double clampedMult = clamp(rawMult, cfg.minMult, cfg.maxMult);
            cost += Math.max(0.01, basePrice * clampedMult);
            if (rawMult < cfg.maxMult - CAP_EPSILON) {
                currentBuys += 1.0;
            }
        }
        return cost;
    }

    /**
     * 記錄單筆購買
     */
    public void recordBuy(String catalogKey, int activeStock) {
        recordBatchBuy(catalogKey, activeStock, 1);
    }

    /**
     * 批次記錄購買：全部交易寫入 total-buys / allBuys；僅在未達漲停時增加有效 buys
     */
    public void recordBatchBuy(String catalogKey, int activeStock, int amount) {
        if (!isEnabled() || catalogKey == null || amount <= 0) return;
        var data = market.computeIfAbsent(catalogKey, k -> new MarketData(0, 0));
        var cfg = readConfig();
        double currentBuys = data.getTotalBuys();
        double currentSells = data.getTotalSells();
        int effectiveBuys = 0;
        for (int i = 0; i < amount; i++) {
            double rawMult = rawMultiplier(currentBuys, currentSells, activeStock, cfg);
            if (rawMult < cfg.maxMult - CAP_EPSILON) {
                currentBuys += 1.0;
                effectiveBuys++;
            }
            data.recordAllBuy();
        }
        if (effectiveBuys > 0) {
            data.setTotalBuys(data.getTotalBuys() + effectiveBuys);
        }
    }

    /**
     * 記錄單筆出售
     */
    public void recordSell(String catalogKey, int activeStock) {
        recordBatchSell(catalogKey, activeStock, 1);
    }

    /**
     * 批次記錄出售：全部交易寫入 total-sells / allSells；僅在未達跌停時增加有效 sells
     */
    public void recordBatchSell(String catalogKey, int activeStock, int amount) {
        if (!isEnabled() || catalogKey == null || amount <= 0) return;
        var data = market.computeIfAbsent(catalogKey, k -> new MarketData(0, 0));
        var cfg = readConfig();
        double currentBuys = data.getTotalBuys();
        double currentSells = data.getTotalSells();
        int effectiveSells = 0;
        for (int i = 0; i < amount; i++) {
            double rawMult = rawMultiplier(currentBuys, currentSells, activeStock, cfg);
            if (rawMult > cfg.minMult + CAP_EPSILON) {
                currentSells += 1.0;
                effectiveSells++;
            }
            data.recordAllSell();
        }
        if (effectiveSells > 0) {
            data.setTotalSells(data.getTotalSells() + effectiveSells);
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

    private static double rawMultiplier(double totalBuys, double totalSells, int activeStock, PricingConfig cfg) {
        double multiplier = 1.0;
        multiplier += totalBuys * cfg.perBuy;
        multiplier -= totalSells * cfg.perSell;
        int shortage = Math.max(0, cfg.referenceStock - activeStock);
        multiplier += shortage * cfg.perShortage;
        return multiplier;
    }

    private static double rawMultiplier(MarketData data, int activeStock, PricingConfig cfg) {
        return rawMultiplier(data.getTotalBuys(), data.getTotalSells(), activeStock, cfg);
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
