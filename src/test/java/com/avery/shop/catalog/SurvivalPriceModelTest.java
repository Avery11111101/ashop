package com.avery.shop.catalog;

import org.bukkit.Material;

public class SurvivalPriceModelTest {

    public static void main(String[] args) {
        System.out.println("Running SurvivalPriceModelTest...");

        // 1. 驗證獄髓材料計算（過去此處會拋出 StackOverflowError）
        testPrice(Material.NETHERITE_INGOT, ItemCategory.MISC, 42_000.0);
        testPrice(Material.NETHERITE_SCRAP, ItemCategory.MISC, 9_500.0);
        testPrice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, ItemCategory.MISC, 25_000.0);
        testPrice(Material.NETHERITE_BLOCK, ItemCategory.BLOCKS, 348_000.0);

        // 2. 驗證全套獄髓裝備與工具計算（鑽石裝備 + 獄髓錠 42000 + 模板 25000）
        testPrice(Material.NETHERITE_SWORD, ItemCategory.WEAPONS, 67_500.0);
        testPrice(Material.NETHERITE_PICKAXE, ItemCategory.TOOLS, 68_000.0);
        testPrice(Material.NETHERITE_AXE, ItemCategory.TOOLS, 68_000.0);
        testPrice(Material.NETHERITE_SHOVEL, ItemCategory.TOOLS, 67_500.0);
        testPrice(Material.NETHERITE_HOE, ItemCategory.TOOLS, 67_500.0);
        testPrice(Material.NETHERITE_HELMET, ItemCategory.ARMOR, 68_500.0);
        testPrice(Material.NETHERITE_CHESTPLATE, ItemCategory.ARMOR, 69_500.0);
        testPrice(Material.NETHERITE_LEGGINGS, ItemCategory.ARMOR, 69_500.0);
        testPrice(Material.NETHERITE_BOOTS, ItemCategory.ARMOR, 68_500.0);

        // 3. 驗證鑽石工具與原木、石頭等基礎物品
        testPrice(Material.DIAMOND_PICKAXE, ItemCategory.TOOLS, 1000.0);
        testPrice(Material.DIORITE, ItemCategory.STONES, 9.0);
        testPrice(Material.MACE, ItemCategory.WEAPONS, 180_000.0);
        testPrice(Material.ELYTRA, ItemCategory.TRANSPORT, 130_000.0);

        System.out.println("SurvivalPriceModelTest passed 100%!");
    }

    private static void testPrice(Material material, ItemCategory category, double expectedPrice) {
        double actualPrice = SurvivalPriceModel.calculateBuyPrice(material, category);

        if (Math.abs(actualPrice - expectedPrice) > 1.0) {
            throw new AssertionError("Price mismatch for " + material + ": expected " + expectedPrice + ", got " + actualPrice);
        }
        System.out.println("Price OK: " + material + " = $" + actualPrice);
    }
}