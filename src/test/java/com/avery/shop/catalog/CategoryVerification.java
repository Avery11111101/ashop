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

        // 3. 驗證 Subcategory 子目錄解析
        assertSubcategory(ItemCategory.BLOCKS, Material.OAK_STAIRS, "building/wood");
        assertSubcategory(ItemCategory.BLOCKS, Material.OAK_DOOR, "building/wood");
        assertSubcategory(ItemCategory.BLOCKS, Material.WHITE_WOOL, "dyed/wool");
        assertSubcategory(ItemCategory.BLOCKS, Material.WHITE_CARPET, "dyed/carpet");
        assertSubcategory(ItemCategory.BLOCKS, Material.WHITE_CONCRETE, "dyed/concrete");
        assertSubcategory(ItemCategory.BLOCKS, Material.BROWN_MUSHROOM, "natural/flowers");

        // 4. 修復異常分類回歸測試
        // 工作台 / 合成台：不再被 RAFT 誤判為船，正確歸入 BLOCKS -> functional
        assertCategory(catalog, Material.CRAFTING_TABLE, ItemCategory.BLOCKS);
        assertSubcategory(ItemCategory.BLOCKS, Material.CRAFTING_TABLE, "functional");
        assertCategory(catalog, Material.CRAFTER, ItemCategory.REDSTONE);

        // 界符盒 / 潛影盒：不再被誤判為石製建材，正確歸入 BLOCKS -> functional
        assertCategory(catalog, Material.SHULKER_BOX, ItemCategory.BLOCKS);
        assertSubcategory(ItemCategory.BLOCKS, Material.SHULKER_BOX, "functional");
        assertCategory(catalog, Material.WHITE_SHULKER_BOX, ItemCategory.BLOCKS);
        assertSubcategory(ItemCategory.BLOCKS, Material.WHITE_SHULKER_BOX, "functional");

        // 蜘蛛眼 / 腐肉：不再因食用性被誤判為點心，正確歸入 MISC -> brewing / materials
        assertCategory(catalog, Material.SPIDER_EYE, ItemCategory.MISC);
        assertSubcategory(ItemCategory.MISC, Material.SPIDER_EYE, "brewing");
        assertCategory(catalog, Material.FERMENTED_SPIDER_EYE, ItemCategory.MISC);
        assertSubcategory(ItemCategory.MISC, Material.FERMENTED_SPIDER_EYE, "brewing");
        assertCategory(catalog, Material.ROTTEN_FLESH, ItemCategory.MISC);
        assertSubcategory(ItemCategory.MISC, Material.ROTTEN_FLESH, "materials");

        // 銅製品（塗蠟）：不再因 WAXED 含有 AXE 被誤判為工具雜項，正確歸入 BLOCKS -> building/copper
        assertCategory(catalog, Material.WAXED_COPPER_BLOCK, ItemCategory.BLOCKS);
        assertSubcategory(ItemCategory.BLOCKS, Material.WAXED_COPPER_BLOCK, "building/copper");
        assertCategory(catalog, Material.WAXED_CUT_COPPER, ItemCategory.BLOCKS);
        assertSubcategory(ItemCategory.BLOCKS, Material.WAXED_CUT_COPPER, "building/copper");

        // 木碗：不再因 BOW 誤判為武器
        assertCategory(catalog, Material.BOWL, ItemCategory.MISC);

        // 5. 26.2 / 26.3 新增物品相容性動態驗證 (若執行環境具備該 Material 則嚴格比對)
        verifyIfPresent(catalog, "POTENT_SULFUR", ItemCategory.BLOCKS, "natural/terrain");
        verifyIfPresent(catalog, "SULFUR_BLOCK", ItemCategory.BLOCKS, "natural/terrain");
        verifyIfPresent(catalog, "POPLAR_LOG", ItemCategory.LOGS, "all");
        verifyIfPresent(catalog, "POPLAR_WOOD", ItemCategory.LOGS, "all");
        verifyIfPresent(catalog, "POPLAR_PLANKS", ItemCategory.BLOCKS, "building/wood");
        verifyIfPresent(catalog, "POPLAR_STAIRS", ItemCategory.BLOCKS, "building/wood");
        verifyIfPresent(catalog, "POPLAR_DOOR", ItemCategory.BLOCKS, "building/wood");
        verifyIfPresent(catalog, "WHITE_WOOL_STAIRS", ItemCategory.BLOCKS, "dyed/wool");
        verifyIfPresent(catalog, "WHITE_WOOL_SLAB", ItemCategory.BLOCKS, "dyed/wool");
        verifyIfPresent(catalog, "WHITE_CONCRETE_STAIRS", ItemCategory.BLOCKS, "dyed/concrete");
        verifyIfPresent(catalog, "WHITE_CONCRETE_SLAB", ItemCategory.BLOCKS, "dyed/concrete");
        verifyIfPresent(catalog, "WHITE_CUSHION", ItemCategory.DECORATIONS, "cushions");
        verifyIfPresent(catalog, "STRAW_BED", ItemCategory.BLOCKS, "functional");
        verifyIfPresent(catalog, "SHELF_MUSHROOM", ItemCategory.BLOCKS, "natural/flowers");
        verifyIfPresent(catalog, "RED_SHRUB", ItemCategory.BLOCKS, "natural/flowers");

        // 6. 靜態字串防回歸檢驗：確保 STRAW_BED 等非礦物絕不被 isMineral 誤判
        if (ItemCatalog.isMineral("STRAW_BED", null)) {
            throw new AssertionError("Regression: STRAW_BED must not be recognized as mineral!");
        }

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

    private static void assertSubcategory(ItemCategory topCat, Material material, String expectedSub) {
        var actual = com.avery.shop.shop.ShopSubcategoryResolver.resolve(topCat, material);
        if (!expectedSub.equals(actual)) {
            throw new AssertionError("Expected subcategory for " + material + " to be " + expectedSub + ", but got " + actual);
        }
        System.out.println("Subcategory OK: " + material + " -> " + actual);
    }

    private static void verifyIfPresent(ItemCatalog catalog, String materialName, ItemCategory expectedCat, String expectedSub) {
        var mat = Material.matchMaterial(materialName);
        if (mat != null) {
            assertCategory(catalog, mat, expectedCat);
            assertSubcategory(expectedCat, mat, expectedSub);
        } else {
            System.out.println("Notice: 26.3 material " + materialName + " not present in current test classpath (skipped)");
        }
    }
}
