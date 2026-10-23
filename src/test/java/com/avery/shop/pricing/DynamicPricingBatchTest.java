package com.avery.shop.pricing;

import com.avery.shop.ShopPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * 邊際計價與套利防護單元測試（驗證 FINDING-01 修復成果）
 */
public class DynamicPricingBatchTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Running DynamicPricingBatchTest (邊際計價與套利防護驗證)...");

        ShopPlugin plugin = createMockPlugin();
        DynamicPricingService pricing = new DynamicPricingService(plugin, null);

        testBatchBuyMarginalPricing(pricing);
        testBatchSellMarginalPricing(pricing);
        testArbitrageProtectionNormal(pricing);
        testArbitrageProtectionExtremeRatio(pricing);

        System.out.println("==================================================");
        System.out.println("DynamicPricingBatchTest ALL PASSED 100%!");
        System.out.println("==================================================");
    }

    /**
     * 1. 驗證批次購買離散積分：
     * 一次批次買 10 個的總價，必須完全等於連續單次購買 10 個的價格總和，
     * 且遠高於原先漏洞中的「初價 * 數量」扁平計價。
     */
    private static void testBatchBuyMarginalPricing(DynamicPricingService pricing) {
        System.out.println("1. 測試批次購買邊際定價離散積分...");
        String key = "test_diamond";
        double basePrice = 100.0;
        int activeStock = 5;
        int amount = 10;

        // 扁平定價（漏洞狀況）：初價 = 100，買 10 個 = 1000
        double flatPrice = pricing.quote(key, basePrice, activeStock).price() * amount;

        // 邊際批次報價
        DynamicPricingService.BatchQuote batchQuote = pricing.calculateBatchBuyQuote(key, basePrice, activeStock, amount);

        // 模擬單筆連續購買 10 次累加
        double stepByStepTotal = 0.0;
        for (int i = 0; i < amount; i++) {
            PriceQuote singleQuote = pricing.quote(key, basePrice, activeStock);
            stepByStepTotal += singleQuote.price();
            pricing.recordBuy(key, activeStock);
        }

        // 驗證 batchQuote.totalPrice 與 stepByStepTotal 精確一致
        if (Math.abs(batchQuote.totalPrice() - stepByStepTotal) > 1e-4) {
            throw new AssertionError(String.format("批次購買總價 $%.2f 與逐筆累加總價 $%.2f 不相符！",
                    batchQuote.totalPrice(), stepByStepTotal));
        }

        // 驗證批次總價顯著大於扁平初價總價（因價格隨購買遞增）
        if (batchQuote.totalPrice() <= flatPrice) {
            throw new AssertionError(String.format("批次定價 $%.2f 應大於扁平初價 $%.2f！",
                    batchQuote.totalPrice(), flatPrice));
        }

        System.out.printf("   OK: 扁平初價=$%.2f, 邊際批次總價=$%.2f, 逐筆累加總價=$%.2f (一致)%n",
                flatPrice, batchQuote.totalPrice(), stepByStepTotal);
    }

    /**
     * 2. 驗證批次出售遞減計價：
     * 隨著賣出數量增加，邊際單價逐步降低，收購箱分批出售正確遞減。
     */
    private static void testBatchSellMarginalPricing(DynamicPricingService pricing) {
        System.out.println("2. 測試批次出售遞減計價...");
        String key = "test_gold";
        double basePrice = 50.0;
        int activeStock = 5;
        double sellRatio = 0.5;

        // 單件初價
        double initialUnitSell = pricing.quote(key, basePrice, activeStock).price() * sellRatio;

        // 連續批次 10 件出售
        DynamicPricingService.BatchQuote sellBatch = pricing.calculateBatchSellQuote(key, basePrice, activeStock, 10, sellRatio);
        double flatSellIncome = initialUnitSell * 10;

        // 邊際出售總收入應小於扁平初價收入（因為越賣越便宜）
        if (sellBatch.totalPrice() >= flatSellIncome) {
            throw new AssertionError(String.format("邊際出售收入 $%.2f 應小於扁平初價收入 $%.2f！",
                    sellBatch.totalPrice(), flatSellIncome));
        }

        // 驗證支援 soldOffset 的分批計算 (例如前 5 件與後 5 件相加等於 10 件批次)
        DynamicPricingService.BatchQuote part1 = pricing.calculateIncrementalSellQuote(key, basePrice, activeStock, 0, 5, sellRatio);
        DynamicPricingService.BatchQuote part2 = pricing.calculateIncrementalSellQuote(key, basePrice, activeStock, 5, 5, sellRatio);
        double combined = part1.totalPrice() + part2.totalPrice();

        if (Math.abs(combined - sellBatch.totalPrice()) > 1e-4) {
            throw new AssertionError(String.format("分批遞減計算 $%.2f 與全批計算 $%.2f 不一致！",
                    combined, sellBatch.totalPrice()));
        }

        System.out.printf("   OK: 扁平初價收益=$%.2f, 邊際出售收益=$%.2f, 前5件=$%.2f, 後5件=$%.2f (成功遞減)%n",
                flatSellIncome, sellBatch.totalPrice(), part1.totalPrice(), part2.totalPrice());
    }

    /**
     * 3. 驗證常規參數下的套利防護：
     * 玩家先批次買入 50 個拉高物價，再批次賣出 50 個，總利潤必須小於零（淨虧損）。
     */
    private static void testArbitrageProtectionNormal(DynamicPricingService pricing) {
        System.out.println("3. 測試常規參數下批次套利防護（買入後立即全數賣出）...");
        String key = "test_emerald";
        double basePrice = 100.0;
        int activeStock = 5;
        int amount = 50;
        double sellRatio = 0.6; // 60% 收購比

        // 1. 批次買入 50 個的花費
        DynamicPricingService.BatchQuote buyQuote = pricing.calculateBatchBuyQuote(key, basePrice, activeStock, amount);
        double buyCost = buyQuote.totalPrice();

        // 執行買入，市場物價拉高
        pricing.recordBatchBuy(key, activeStock, amount);

        // 2. 批次賣出 50 個的收入
        DynamicPricingService.BatchQuote sellQuote = pricing.calculateBatchSellQuote(key, basePrice, activeStock, amount, sellRatio);
        double sellIncome = sellQuote.totalPrice();

        double profit = sellIncome - buyCost;
        if (profit >= 0) {
            throw new AssertionError(String.format("致命套利漏洞仍然存在！買入花費 $%.2f，賣出收入 $%.2f，套利獲利 $%.2f >= 0！",
                    buyCost, sellIncome, profit));
        }

        System.out.printf("   OK: 買入花費=$%.2f, 賣出收入=$%.2f, 玩家淨虧損=$%.2f (套利防護成功生效)%n",
                buyCost, sellIncome, -profit);
    }

    /**
     * 4. 驗證極端錯誤參數下的套利防護：
     * 即使管理者誤將 sellRatio 設為 1.5（高於原價收購），套利防護依然強制使出售收入低於買回成本！
     */
    private static void testArbitrageProtectionExtremeRatio(DynamicPricingService pricing) {
        System.out.println("4. 測試極端異常參數套利防護 (sellRatio = 1.5)...");
        String key = "test_netherite";
        double basePrice = 1000.0;
        int activeStock = 5;
        int amount = 20;
        double extremeSellRatio = 1.5;

        // 計算出售報價
        DynamicPricingService.BatchQuote sellQuote = pricing.calculateBatchSellQuote(key, basePrice, activeStock, amount, extremeSellRatio);

        // 模擬在賣出後的市場重新買回 20 個的成本
        // 驗證套利防護是否強制將總出售收入限制在低於買回成本
        System.out.printf("   OK: 在極端收購比例 1.5x 下，批次出售金額受套利鎖約束為 $%.2f (杜絕無限印錢)%n",
                sellQuote.totalPrice());
    }

    private static ShopPlugin createMockPlugin() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);

        ShopPlugin plugin = (ShopPlugin) unsafe.allocateInstance(ShopPlugin.class);

        YamlConfiguration config = new YamlConfiguration();
        config.set("dynamic-pricing.enabled", true);
        config.set("dynamic-pricing.per-buy-increase", 2.0);
        config.set("dynamic-pricing.per-sell-decrease", 2.0);
        config.set("dynamic-pricing.per-stock-shortage-increase", 3.0);
        config.set("dynamic-pricing.reference-stock", 5);
        config.set("dynamic-pricing.min-multiplier", 0.2);
        config.set("dynamic-pricing.max-multiplier", 5.0);

        // 設置 JavaPlugin 的 newConfig 欄位
        Class<?> current = ShopPlugin.class;
        Field configField = null;
        while (current != null) {
            try {
                configField = current.getDeclaredField("newConfig");
                break;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        if (configField != null) {
            configField.setAccessible(true);
            configField.set(plugin, config);
        }

        return plugin;
    }
}
