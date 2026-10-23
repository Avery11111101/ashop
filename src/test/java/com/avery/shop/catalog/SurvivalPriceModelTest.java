package com.avery.shop.catalog;

import org.bukkit.Material;

public class SurvivalPriceModelTest {

    public static void main(String[] args) {
        System.out.println("Running SurvivalPriceModelTest (工時經濟定價模型驗證)...");

        // 1. 驗證【無連鎖挖礦】手冊基準物資定價 (W_base = 1000)
        testItem(Material.COAL, ItemCategory.MINERALS, 1000.0, false, 5.00, 2.50);
        testItem(Material.RAW_COPPER, ItemCategory.MINERALS, 1000.0, false, 4.00, 1.80);
        testItem(Material.RAW_IRON, ItemCategory.MINERALS, 1000.0, false, 13.00, 6.00);
        testItem(Material.IRON_INGOT, ItemCategory.MINERALS, 1000.0, false, 15.00, 6.80);
        testItem(Material.RAW_GOLD, ItemCategory.MINERALS, 1000.0, false, 30.00, 14.00);
        testItem(Material.GOLD_INGOT, ItemCategory.MINERALS, 1000.0, false, 33.00, 15.50);
        testItem(Material.DIAMOND, ItemCategory.MINERALS, 1000.0, false, 240.00, 140.00);
        testItem(Material.ANCIENT_DEBRIS, ItemCategory.MINERALS, 1000.0, false, 1100.00, 650.00);
        testItem(Material.NETHERITE_SCRAP, ItemCategory.MINERALS, 1000.0, false, 1200.00, 700.00);
        testItem(Material.NETHERITE_INGOT, ItemCategory.MINERALS, 1000.0, false, 5000.00, 3000.00);

        // 建材類
        testItem(Material.COBBLESTONE, ItemCategory.STONES, 1000.0, false, 0.50, 0.20);
        testItem(Material.DIRT, ItemCategory.BLOCKS, 1000.0, false, 0.40, 0.15);
        testItem(Material.SAND, ItemCategory.BLOCKS, 1000.0, false, 1.00, 0.40);
        testItem(Material.OAK_LOG, ItemCategory.LOGS, 1000.0, false, 3.00, 1.20);
        testItem(Material.OBSIDIAN, ItemCategory.BLOCKS, 1000.0, false, 35.00, 15.00);

        // 農產類（受 SuperHarvest 修正）
        testItem(Material.WHEAT, ItemCategory.CROPS, 1000.0, false, 0.80, 0.35);
        testItem(Material.WHEAT_SEEDS, ItemCategory.CROPS, 1000.0, false, 0.10, 0.02);
        testItem(Material.CARROT, ItemCategory.CROPS, 1000.0, false, 1.00, 0.40);
        testItem(Material.POTATO, ItemCategory.CROPS, 1000.0, false, 1.00, 0.40);
        testItem(Material.GOLDEN_CARROT, ItemCategory.FOOD, 1000.0, false, 28.00, 14.00);

        // 畜牧與掉落物
        testItem(Material.BEEF, ItemCategory.RAW_MEAT, 1000.0, false, 3.00, 1.20);
        testItem(Material.COOKED_BEEF, ItemCategory.FOOD, 1000.0, false, 4.00, 1.80);
        testItem(Material.ROTTEN_FLESH, ItemCategory.MISC, 1000.0, false, 0.80, 0.25);
        testItem(Material.BONE, ItemCategory.MISC, 1000.0, false, 3.00, 1.20);
        testItem(Material.GUNPOWDER, ItemCategory.MISC, 1000.0, false, 7.00, 2.80);
        testItem(Material.WITHER_SKELETON_SKULL, ItemCategory.MISC, 1000.0, false, 850.00, 380.00);

        // 維度稀有品
        testItem(Material.ELYTRA, ItemCategory.TRANSPORT, 1000.0, false, 3200.00, 1500.00);
        testItem(Material.HEART_OF_THE_SEA, ItemCategory.MISC, 1000.0, false, 650.00, 300.00);
        testItem(Material.TRIDENT, ItemCategory.WEAPONS, 1000.0, false, 950.00, 450.00);
        testItem(Material.TOTEM_OF_UNDYING, ItemCategory.MISC, 1000.0, false, 400.00, 180.00);

        // 2. 驗證【有連鎖挖礦】動態切換（僅礦物與原木生效）
        testItem(Material.COAL, ItemCategory.MINERALS, 1000.0, true, 1.80, 0.80);
        testItem(Material.RAW_COPPER, ItemCategory.MINERALS, 1000.0, true, 1.60, 0.70);
        testItem(Material.RAW_IRON, ItemCategory.MINERALS, 1000.0, true, 6.00, 2.50);
        testItem(Material.DIAMOND, ItemCategory.MINERALS, 1000.0, true, 165.00, 95.00);
        testItem(Material.ANCIENT_DEBRIS, ItemCategory.MINERALS, 1000.0, true, 900.00, 520.00);
        testItem(Material.OAK_LOG, ItemCategory.LOGS, 1000.0, true, 1.80, 0.70);

        // 農產品與怪物掉落物不受連鎖挖礦影響
        testItem(Material.WHEAT, ItemCategory.CROPS, 1000.0, true, 0.80, 0.35);
        testItem(Material.ROTTEN_FLESH, ItemCategory.MISC, 1000.0, true, 0.80, 0.25);

        // 3. 驗證工時比例縮放 (hourly-wage = 2000，物價等比翻倍)
        testItem(Material.COAL, ItemCategory.MINERALS, 2000.0, false, 10.00, 5.00);
        testItem(Material.DIAMOND, ItemCategory.MINERALS, 2000.0, false, 480.00, 280.00);

        // 4. 嚴格驗證防刷錢安全鎖（所有物品收購比 <= 0.99 且收購價嚴格小於出售價）
        for (var mat : Material.values()) {
            var name = mat.name();
            if (!name.contains("AIR") && !name.startsWith("LEGACY_")) {
                var res = SurvivalPriceModel.calculatePrice(mat, null, 1000.0, false);
                if (res.sellRatio() >= 1.0) {
                    throw new AssertionError("Security violation: sellRatio >= 1.0 for " + mat + " (" + res.sellRatio() + ")");
                }
                if (res.systemBuyPrice() >= res.systemSellPrice()) {
                    throw new AssertionError("Security violation: Buy price >= Sell price for " + mat);
                }
            }
        }

        System.out.println("SurvivalPriceModelTest passed 100%! 所有數值與安全鎖皆驗證成功！");
    }

    private static void testItem(Material material, ItemCategory category, double wage, boolean vein,
                                 double expectedSell, double expectedBuy) {
        var result = SurvivalPriceModel.calculatePrice(material, category, wage, vein);

        if (Math.abs(result.systemSellPrice() - expectedSell) > 0.05) {
            throw new AssertionError("Sell price mismatch for " + material + " (vein=" + vein + ", wage=" + wage + "): expected " + expectedSell + ", got " + result.systemSellPrice());
        }
        if (Math.abs(result.systemBuyPrice() - expectedBuy) > 0.05) {
            throw new AssertionError("Buy price mismatch for " + material + " (vein=" + vein + ", wage=" + wage + "): expected " + expectedBuy + ", got " + result.systemBuyPrice());
        }
        System.out.println("OK: " + material + " [vein=" + vein + "] Sell=$" + result.systemSellPrice() + ", Buy=$" + result.systemBuyPrice() + " (ratio=" + result.sellRatio() + ")");
    }
}
