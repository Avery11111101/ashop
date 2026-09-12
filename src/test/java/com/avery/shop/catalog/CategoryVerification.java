package com.avery.shop.catalog;

import com.avery.shop.shop.ShopConfigService;
import com.avery.shop.shop.TradeMode;
import org.bukkit.Material;

public class CategoryVerification {
    public static void main(String[] args) {
        System.out.println("Starting CategoryVerification...");
        
        // 1. 驗證 5 大資源分類預設 TradeMode 為 BOTH，其餘一律為 BUY_ONLY
        for (var cat : ItemCategory.values()) {
            var mode = ShopConfigService.defaultTradeModeForCategory(cat);
            boolean isResource = (cat == ItemCategory.MINERALS || cat == ItemCategory.LOGS 
                    || cat == ItemCategory.STONES || cat == ItemCategory.CROPS || cat == ItemCategory.RAW_MEAT);
            var expectedMode = isResource ? TradeMode.BOTH : TradeMode.BUY_ONLY;
            if (mode != expectedMode) {
                throw new AssertionError("Category " + cat.getId() + " TradeMode mismatch: expected " + expectedMode + ", got " + mode);
            }
            System.out.println("TradeMode OK: " + cat.getId() + " -> " + mode);
        }

        // 2. 驗證物品分類歸類
        var catalog = new ItemCatalog(null, null);

        // 原木
        assertCategory(catalog, Material.OAK_LOG, ItemCategory.LOGS);
        assertCategory(catalog, Material.STRIPPED_OAK_LOG, ItemCategory.LOGS);
        assertCategory(catalog, Material.SPRUCE_LOG, ItemCategory.LOGS);
        assertCategory(catalog, Material.CRIMSON_STEM, ItemCategory.LOGS);
        assertCategory(catalog, Material.OAK_WOOD, ItemCategory.LOGS);
        assertCategory(catalog, Material.OAK_PLANKS, ItemCategory.BLOCKS);
        assertCategory(catalog, Material.OAK_STAIRS, ItemCategory.BLOCKS);

        // 石頭
        assertCategory(catalog, Material.STONE, ItemCategory.STONES);
        assertCategory(catalog, Material.COBBLESTONE, ItemCategory.STONES);
        assertCategory(catalog, Material.SMOOTH_STONE, ItemCategory.STONES);
        assertCategory(catalog, Material.DEEPSLATE, ItemCategory.STONES);
        assertCategory(catalog, Material.STONE_BRICKS, ItemCategory.BLOCKS);
        assertCategory(catalog, Material.STONE_STAIRS, ItemCategory.BLOCKS);
        assertCategory(catalog, Material.COBBLESTONE_WALL, ItemCategory.BLOCKS);

        // 礦物（純挖掘產物，排除錠/粒與方塊）
        assertCategory(catalog, Material.DIAMOND_ORE, ItemCategory.MINERALS);
        assertCategory(catalog, Material.DEEPSLATE_DIAMOND_ORE, ItemCategory.MINERALS);
        assertCategory(catalog, Material.ANCIENT_DEBRIS, ItemCategory.MINERALS);
        assertCategory(catalog, Material.RAW_IRON, ItemCategory.MINERALS);
        assertCategory(catalog, Material.RAW_GOLD, ItemCategory.MINERALS);
        assertCategory(catalog, Material.RAW_COPPER, ItemCategory.MINERALS);
        assertCategory(catalog, Material.DIAMOND, ItemCategory.MINERALS);
        assertCategory(catalog, Material.COAL, ItemCategory.MINERALS);
        assertCategory(catalog, Material.LAPIS_LAZULI, ItemCategory.MINERALS);
        assertCategory(catalog, Material.REDSTONE, ItemCategory.MINERALS);
        assertCategory(catalog, Material.EMERALD, ItemCategory.MINERALS);
        assertCategory(catalog, Material.AMETHYST_SHARD, ItemCategory.MINERALS);
        assertCategory(catalog, Material.FLINT, ItemCategory.MINERALS);

        // 排除物
        assertCategory(catalog, Material.IRON_INGOT, ItemCategory.MISC);
        assertCategory(catalog, Material.GOLD_INGOT, ItemCategory.MISC);
        assertCategory(catalog, Material.IRON_BLOCK, ItemCategory.BLOCKS);
        assertCategory(catalog, Material.GOLD_BLOCK, ItemCategory.BLOCKS);
        assertCategory(catalog, Material.RAW_IRON_BLOCK, ItemCategory.BLOCKS);
        assertCategory(catalog, Material.CHARCOAL, ItemCategory.MISC);

        // 農作物
        assertCategory(catalog, Material.WHEAT, ItemCategory.CROPS);
        assertCategory(catalog, Material.CARROT, ItemCategory.CROPS);
        assertCategory(catalog, Material.POTATO, ItemCategory.CROPS);
        assertCategory(catalog, Material.BEETROOT, ItemCategory.CROPS);
        assertCategory(catalog, Material.SUGAR_CANE, ItemCategory.CROPS);
        assertCategory(catalog, Material.APPLE, ItemCategory.CROPS);
        assertCategory(catalog, Material.WHEAT_SEEDS, ItemCategory.CROPS);

        // 生肉
        assertCategory(catalog, Material.BEEF, ItemCategory.RAW_MEAT);
        assertCategory(catalog, Material.PORKCHOP, ItemCategory.RAW_MEAT);
        assertCategory(catalog, Material.CHICKEN, ItemCategory.RAW_MEAT);
        assertCategory(catalog, Material.MUTTON, ItemCategory.RAW_MEAT);
        assertCategory(catalog, Material.COD, ItemCategory.RAW_MEAT);
        assertCategory(catalog, Material.SALMON, ItemCategory.RAW_MEAT);

        // 熟食料理
        assertCategory(catalog, Material.COOKED_BEEF, ItemCategory.FOOD);
        assertCategory(catalog, Material.COOKED_PORKCHOP, ItemCategory.FOOD);
        assertCategory(catalog, Material.COOKED_CHICKEN, ItemCategory.FOOD);
        assertCategory(catalog, Material.COOKED_SALMON, ItemCategory.FOOD);
        assertCategory(catalog, Material.BREAD, ItemCategory.FOOD);

        System.out.println("==================================================");
        System.out.println("ALL VERIFICATIONS COMPLETED AND PASSED 100%!");
        System.out.println("==================================================");
    }

    private static void assertCategory(ItemCatalog catalog, Material material, ItemCategory expected) {
        var actual = catalog.categorize(material);
        if (actual != expected) {
            throw new AssertionError("Expected " + material + " to be " + expected + ", but got " + actual);
        }
        System.out.println("Categorize OK: " + material + " -> " + actual);
    }
}
