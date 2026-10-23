package com.avery.shop.catalog;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Minecraft 伺服器物品逐一定價標準手冊與經濟模型體系 (Standard Labor-Time Valuation Model)
 * <p>
 * 核心公式：
 *   系統收購價 P_buy = [(T_gather / Q_yield) * W_base * C_tool * R_risk * K_tier] / (E_vein * E_plugin * A_farm)
 *   系統出售價 P_sell = P_buy * (1 + M_spread)
 *   收購折讓比 sellRatio = Math.min(0.99, P_buy / P_sell) (實作防呆安全閥，保證絕對低於售價，防套利刷錢)
 */
public final class SurvivalPriceModel {

  /** 定價結果包裝物件：包含系統出售價（商店售價）、系統收購價（回收價）、收購折讓比 */
  public record ItemPriceResult(double systemSellPrice, double systemBuyPrice, double sellRatio) {
    public static ItemPriceResult of(double systemSellPrice, double systemBuyPrice) {
      double sell = Math.max(0.01, Math.round(systemSellPrice * 100.0) / 100.0);
      double buy = Math.max(0.005, Math.round(systemBuyPrice * 100.0) / 100.0);
      // 物理防禦：收購價絕對不可大於或等於售價
      if (buy >= sell) {
        buy = Math.max(0.005, Math.round(sell * 0.7 * 100.0) / 100.0);
      }
      double ratio = Math.min(0.99, Math.max(0.01, buy / sell));
      ratio = Math.round(ratio * 1000.0) / 1000.0;
      return new ItemPriceResult(sell, buy, ratio);
    }
  }

  /** 手冊基準價格項：包含無連鎖 (NoVein) 與有連鎖 (Vein) 之收購價與出售價 */
  private record Benchmark(double buyNoVein, double sellNoVein, double buyVein, double sellVein) {
    Benchmark(double buy, double sell) {
      this(buy, sell, buy, sell);
    }
    double getBuy(boolean vein) { return vein ? buyVein : buyNoVein; }
    double getSell(boolean vein) { return vein ? sellVein : sellNoVein; }
  }

  private static final Map<Material, Benchmark> BENCHMARKS = new EnumMap<>(Material.class);

  static {
    // 1. 基礎原材與建材類 (Blocks & Construction)
    put(Material.COBBLESTONE, new Benchmark(0.20, 0.50, 0.12, 0.30));
    put(Material.COBBLED_DEEPSLATE, new Benchmark(0.35, 0.80, 0.20, 0.50));
    put(Material.DIRT, new Benchmark(0.15, 0.40, 0.10, 0.30));
    put(Material.GRASS_BLOCK, new Benchmark(0.30, 0.80, 0.20, 0.60));
    put(Material.SAND, new Benchmark(0.40, 1.00, 0.25, 0.60));
    put(Material.RED_SAND, new Benchmark(0.80, 2.00, 0.50, 1.30));
    put(Material.GRAVEL, new Benchmark(0.35, 0.90, 0.20, 0.50));
    put(Material.FLINT, new Benchmark(0.50, 1.20, 0.35, 0.80));
    put(Material.CLAY_BALL, new Benchmark(0.60, 1.50, 0.45, 1.10));
    put(Material.CLAY, new Benchmark(2.40, 6.00, 1.80, 4.40));
    put(Material.STONE, new Benchmark(0.30, 0.80, 0.18, 0.50));
    put(Material.DEEPSLATE, new Benchmark(0.45, 1.00, 0.25, 0.65));
    put(Material.TUFF, new Benchmark(0.40, 0.90, 0.22, 0.60));
    put(Material.CALCITE, new Benchmark(0.80, 2.00, 0.50, 1.30));
    put(Material.BASALT, new Benchmark(0.40, 0.90, 0.25, 0.60));
    put(Material.BLACKSTONE, new Benchmark(0.50, 1.20, 0.30, 0.70));
    put(Material.OBSIDIAN, new Benchmark(15.00, 35.00, 12.00, 28.00));
    put(Material.CRYING_OBSIDIAN, new Benchmark(25.00, 60.00, 20.00, 50.00));

    // 原木類（連鎖伐木有益）
    var standardLogs = new Material[]{
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
        Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.CHERRY_LOG,
        Material.CRIMSON_STEM, Material.WARPED_STEM,
        Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG,
        Material.STRIPPED_JUNGLE_LOG, Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
        Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_CRIMSON_STEM, Material.STRIPPED_WARPED_STEM
    };
    for (var log : standardLogs) {
      put(log, new Benchmark(1.20, 3.00, 0.70, 1.80));
    }
    put(Material.MANGROVE_LOG, new Benchmark(2.00, 5.00, 1.20, 3.00));
    put(Material.STRIPPED_MANGROVE_LOG, new Benchmark(2.00, 5.00, 1.20, 3.00));
    put(Material.OAK_PLANKS, new Benchmark(0.40, 1.00, 0.25, 0.60));
    put(Material.STICK, new Benchmark(0.10, 0.25, 0.06, 0.15));

    // 2. 礦物與金屬類 (Ores & Metals)
    put(Material.COAL, new Benchmark(2.50, 5.00, 0.80, 1.80));
    put(Material.CHARCOAL, new Benchmark(2.50, 5.00, 0.80, 1.80));
    put(Material.RAW_COPPER, new Benchmark(1.80, 4.00, 0.70, 1.60));
    put(Material.COPPER_INGOT, new Benchmark(2.20, 5.00, 1.00, 2.30));
    put(Material.RAW_IRON, new Benchmark(6.00, 13.00, 2.50, 6.00));
    put(Material.IRON_INGOT, new Benchmark(6.80, 15.00, 3.20, 7.50));
    put(Material.RAW_GOLD, new Benchmark(14.00, 30.00, 7.50, 16.00));
    put(Material.GOLD_INGOT, new Benchmark(15.50, 33.00, 8.80, 19.00));
    put(Material.REDSTONE, new Benchmark(2.20, 5.00, 1.00, 2.20));
    put(Material.LAPIS_LAZULI, new Benchmark(1.80, 4.00, 0.90, 2.00));
    put(Material.EMERALD, new Benchmark(20.00, 45.00, 12.00, 28.00));
    put(Material.DIAMOND, new Benchmark(140.00, 240.00, 95.00, 165.00));
    put(Material.ANCIENT_DEBRIS, new Benchmark(650.00, 1100.00, 520.00, 900.00));
    put(Material.NETHERITE_SCRAP, new Benchmark(700.00, 1200.00, 560.00, 980.00));
    put(Material.NETHERITE_INGOT, new Benchmark(3000.00, 5000.00, 2400.00, 4100.00));
    put(Material.AMETHYST_SHARD, new Benchmark(4.00, 9.00, 2.50, 5.50));

    // 礦石方塊 (Silk Touch)
    put(Material.COAL_ORE, new Benchmark(3.00, 6.50, 1.00, 2.50));
    put(Material.DEEPSLATE_COAL_ORE, new Benchmark(3.50, 7.50, 1.20, 3.00));
    put(Material.IRON_ORE, new Benchmark(7.50, 16.00, 3.20, 7.50));
    put(Material.DEEPSLATE_IRON_ORE, new Benchmark(8.00, 17.50, 3.50, 8.00));
    put(Material.COPPER_ORE, new Benchmark(3.50, 8.00, 1.50, 3.50));
    put(Material.DEEPSLATE_COPPER_ORE, new Benchmark(4.00, 9.00, 1.80, 4.20));
    put(Material.GOLD_ORE, new Benchmark(17.00, 36.00, 9.00, 20.00));
    put(Material.DEEPSLATE_GOLD_ORE, new Benchmark(18.50, 39.00, 10.00, 22.00));
    put(Material.NETHER_GOLD_ORE, new Benchmark(8.00, 18.00, 4.00, 9.00));
    put(Material.REDSTONE_ORE, new Benchmark(10.00, 22.00, 5.00, 11.00));
    put(Material.DEEPSLATE_REDSTONE_ORE, new Benchmark(11.00, 24.00, 5.50, 12.00));
    put(Material.LAPIS_ORE, new Benchmark(12.00, 26.00, 6.00, 14.00));
    put(Material.DEEPSLATE_LAPIS_ORE, new Benchmark(13.00, 28.00, 6.50, 15.00));
    put(Material.EMERALD_ORE, new Benchmark(30.00, 65.00, 18.00, 40.00));
    put(Material.DEEPSLATE_EMERALD_ORE, new Benchmark(40.00, 85.00, 24.00, 55.00));
    put(Material.DIAMOND_ORE, new Benchmark(170.00, 290.00, 115.00, 200.00));
    put(Material.DEEPSLATE_DIAMOND_ORE, new Benchmark(180.00, 310.00, 125.00, 215.00));
    put(Material.NETHER_QUARTZ_ORE, new Benchmark(4.00, 9.00, 1.80, 4.20));

    // 3. 農業與經濟作物類 (Farming & Crops) —— 受 SuperHarvest 右鍵秒收修正
    put(Material.WHEAT, new Benchmark(0.35, 0.80));
    put(Material.WHEAT_SEEDS, new Benchmark(0.02, 0.10));
    put(Material.CARROT, new Benchmark(0.40, 1.00));
    put(Material.POTATO, new Benchmark(0.40, 1.00));
    put(Material.POISONOUS_POTATO, new Benchmark(1.50, 4.00));
    put(Material.BEETROOT, new Benchmark(0.30, 0.80));
    put(Material.BEETROOT_SEEDS, new Benchmark(0.02, 0.10));
    put(Material.PUMPKIN, new Benchmark(1.20, 2.80));
    put(Material.PUMPKIN_SEEDS, new Benchmark(0.05, 0.15));
    put(Material.MELON_SLICE, new Benchmark(0.15, 0.40));
    put(Material.MELON, new Benchmark(1.20, 3.00));
    put(Material.MELON_SEEDS, new Benchmark(0.05, 0.15));
    put(Material.SUGAR_CANE, new Benchmark(0.80, 2.00));
    put(Material.BAMBOO, new Benchmark(0.10, 0.30));
    put(Material.CACTUS, new Benchmark(0.30, 0.80));
    put(Material.COCOA_BEANS, new Benchmark(0.60, 1.50));
    put(Material.NETHER_WART, new Benchmark(1.80, 4.50));
    put(Material.GOLDEN_CARROT, new Benchmark(14.00, 28.00));
    put(Material.GLISTERING_MELON_SLICE, new Benchmark(14.00, 28.00));
    put(Material.APPLE, new Benchmark(0.80, 2.00));
    put(Material.GOLDEN_APPLE, new Benchmark(130.00, 280.00));
    put(Material.ENCHANTED_GOLDEN_APPLE, new Benchmark(800.00, 2000.00));
    put(Material.SWEET_BERRIES, new Benchmark(0.25, 0.60));
    put(Material.GLOW_BERRIES, new Benchmark(0.30, 0.70));
    put(Material.KELP, new Benchmark(0.20, 0.50));
    put(Material.DRIED_KELP, new Benchmark(0.30, 0.70));
    put(Material.SEA_PICKLE, new Benchmark(0.80, 2.00));
    put(Material.HONEYCOMB, new Benchmark(2.50, 6.00));
    put(Material.HONEY_BOTTLE, new Benchmark(3.50, 8.00));

    // 4. 畜牧與水產類 (Husbandry & Fishing) —— 受 ChunkEntityLimiter 限制
    put(Material.BEEF, new Benchmark(1.20, 3.00));
    put(Material.COOKED_BEEF, new Benchmark(1.80, 4.00));
    put(Material.LEATHER, new Benchmark(2.50, 6.00));
    put(Material.MUTTON, new Benchmark(1.00, 2.50));
    put(Material.COOKED_MUTTON, new Benchmark(1.50, 3.50));
    put(Material.WHITE_WOOL, new Benchmark(0.60, 1.50));
    put(Material.CHICKEN, new Benchmark(0.60, 1.50));
    put(Material.COOKED_CHICKEN, new Benchmark(1.00, 2.20));
    put(Material.FEATHER, new Benchmark(0.50, 1.20));
    put(Material.EGG, new Benchmark(0.15, 0.40));
    put(Material.PORKCHOP, new Benchmark(1.20, 3.00));
    put(Material.COOKED_PORKCHOP, new Benchmark(1.80, 4.00));
    put(Material.RABBIT, new Benchmark(1.20, 3.00));
    put(Material.COOKED_RABBIT, new Benchmark(1.80, 4.00));
    put(Material.RABBIT_HIDE, new Benchmark(0.80, 2.00));
    put(Material.RABBIT_FOOT, new Benchmark(15.00, 35.00));
    put(Material.MILK_BUCKET, new Benchmark(12.00, 25.00));
    put(Material.COD, new Benchmark(0.90, 2.20));
    put(Material.COOKED_COD, new Benchmark(1.40, 3.00));
    put(Material.SALMON, new Benchmark(1.10, 2.50));
    put(Material.COOKED_SALMON, new Benchmark(1.60, 3.50));
    put(Material.TROPICAL_FISH, new Benchmark(3.00, 7.00));
    put(Material.PUFFERFISH, new Benchmark(5.00, 12.00));

    // 5. 敵對生物與打怪掉落物 (Mob Drops) —— 受 MoneyFromMobs 雙重防通膨壓低
    put(Material.ROTTEN_FLESH, new Benchmark(0.25, 0.80));
    put(Material.BONE, new Benchmark(1.20, 3.00));
    put(Material.BONE_MEAL, new Benchmark(0.40, 1.00));
    put(Material.GUNPOWDER, new Benchmark(2.80, 7.00));
    put(Material.STRING, new Benchmark(0.80, 2.00));
    put(Material.SPIDER_EYE, new Benchmark(1.00, 2.50));
    put(Material.FERMENTED_SPIDER_EYE, new Benchmark(3.00, 7.00));
    put(Material.SLIME_BALL, new Benchmark(6.00, 15.00));
    put(Material.MAGMA_CREAM, new Benchmark(8.00, 18.00));
    put(Material.BLAZE_ROD, new Benchmark(7.50, 18.00));
    put(Material.BLAZE_POWDER, new Benchmark(3.80, 9.00));
    put(Material.ENDER_PEARL, new Benchmark(6.00, 15.00));
    put(Material.ENDER_EYE, new Benchmark(12.00, 28.00));
    put(Material.GHAST_TEAR, new Benchmark(35.00, 80.00));
    put(Material.WITHER_SKELETON_SKULL, new Benchmark(380.00, 850.00));
    put(Material.SCULK_SENSOR, new Benchmark(18.00, 45.00));
    put(Material.SCULK_CATALYST, new Benchmark(30.00, 75.00));
    put(Material.SCULK_SHRIEKER, new Benchmark(35.00, 85.00));
    put(Material.SCULK, new Benchmark(0.50, 1.50));
    put(Material.PHANTOM_MEMBRANE, new Benchmark(15.00, 35.00));
    put(Material.BREEZE_ROD, new Benchmark(25.00, 60.00));
    put(Material.HEAVY_CORE, new Benchmark(1500.00, 3500.00));
    put(Material.MACE, new Benchmark(1800.00, 4200.00));

    // 6. 地獄、終界與探險稀有資源 (Dimensions & Exploration)
    put(Material.NETHERRACK, new Benchmark(0.10, 0.30));
    put(Material.QUARTZ, new Benchmark(3.50, 8.00, 1.50, 3.50));
    put(Material.GLOWSTONE_DUST, new Benchmark(1.20, 3.00));
    put(Material.GLOWSTONE, new Benchmark(5.00, 12.50));
    put(Material.END_STONE, new Benchmark(0.50, 1.20));
    put(Material.CHORUS_FRUIT, new Benchmark(1.00, 2.50));
    put(Material.POPPED_CHORUS_FRUIT, new Benchmark(1.30, 3.00));
    put(Material.SHULKER_SHELL, new Benchmark(220.00, 450.00));
    put(Material.ELYTRA, new Benchmark(1500.00, 3200.00));
    put(Material.HEART_OF_THE_SEA, new Benchmark(300.00, 650.00));
    put(Material.NAUTILUS_SHELL, new Benchmark(40.00, 90.00));
    put(Material.CONDUIT, new Benchmark(650.00, 1400.00));
    put(Material.TRIDENT, new Benchmark(450.00, 950.00));
    put(Material.TOTEM_OF_UNDYING, new Benchmark(180.00, 400.00));
    put(Material.NETHER_STAR, new Benchmark(1200.00, 2800.00));
    put(Material.BEACON, new Benchmark(1300.00, 3000.00));
    put(Material.DRAGON_EGG, new Benchmark(2500.00, 6000.00));
    put(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, new Benchmark(200.00, 500.00));

    // 衍生與實用方塊
    put(Material.PAPER, new Benchmark(0.80, 2.00));
    put(Material.BOOK, new Benchmark(3.00, 7.50));
    put(Material.TORCH, new Benchmark(0.70, 1.60));
    put(Material.SOUL_TORCH, new Benchmark(0.80, 1.80));
    put(Material.LANTERN, new Benchmark(5.50, 13.00));
    put(Material.SOUL_LANTERN, new Benchmark(6.00, 14.00));
    put(Material.CHEST, new Benchmark(3.50, 8.00));
    put(Material.CRAFTING_TABLE, new Benchmark(1.80, 4.00));
    put(Material.FURNACE, new Benchmark(1.80, 4.00));
    put(Material.BLAST_FURNACE, new Benchmark(18.00, 40.00));
    put(Material.SMOKER, new Benchmark(6.00, 14.00));
    put(Material.ANVIL, new Benchmark(75.00, 160.00));
    put(Material.ENCHANTING_TABLE, new Benchmark(350.00, 600.00));
    put(Material.ENDER_CHEST, new Benchmark(140.00, 320.00));
    put(Material.BOW, new Benchmark(2.50, 6.00));
    put(Material.CROSSBOW, new Benchmark(8.00, 18.00));
    put(Material.FISHING_ROD, new Benchmark(2.00, 5.00));
    put(Material.SHIELD, new Benchmark(8.00, 18.00));
    put(Material.ARROW, new Benchmark(0.40, 1.00));
    put(Material.SPECTRAL_ARROW, new Benchmark(1.50, 3.50));
    put(Material.TNT, new Benchmark(15.00, 35.00));
  }

  private SurvivalPriceModel() {}

  /**
   * 計算物品完整定價結果（支援自訂工時與連鎖開關）
   */
  public static ItemPriceResult calculatePrice(CatalogEntry entry, double hourlyWage, boolean veinMiner) {
    if (entry == null) return ItemPriceResult.of(10.0, 5.0);
    var stack = entry.getTemplate();
    var material = stack != null ? stack.getType() : null;
    if (material == null) return ItemPriceResult.of(10.0, 5.0);

    var res = calculateMaterialPrice(material, entry.getCategory(), hourlyWage, veinMiner);
    double variantMul = variantMultiplier(stack, entry);
    if (variantMul != 1.0) {
      double sell = res.systemSellPrice() * variantMul;
      double buy = res.systemBuyPrice() * variantMul;
      return ItemPriceResult.of(sell, buy);
    }
    return res;
  }

  /**
   * 計算特定材質的定價結果
   */
  public static ItemPriceResult calculatePrice(Material material, ItemCategory category, double hourlyWage, boolean veinMiner) {
    return calculateMaterialPrice(material, category, hourlyWage, veinMiner);
  }

  private static ItemPriceResult calculateMaterialPrice(Material material, ItemCategory category, double hourlyWage, boolean veinMiner) {
    if (material == null) return ItemPriceResult.of(10.0, 5.0);

    double wageScale = Math.max(0.01, hourlyWage / 1000.0);

    // 1. 手冊基準表命中
    var bench = BENCHMARKS.get(material);
    if (bench != null) {
      // 唯有礦物 (MINERALS) 與原木 (LOGS) 受連鎖挖礦修正，其他分類強制使用手動係數保障新手與生態平衡
      boolean allowVein = veinMiner && isVeinEligible(material, category);
      double buy = bench.getBuy(allowVein) * wageScale;
      double sell = bench.getSell(allowVein) * wageScale;
      return ItemPriceResult.of(sell, buy);
    }

    // 2. 礦石方塊或特殊金屬方塊拆解
    var blockIngot = blockToIngot(material);
    if (blockIngot != null) {
      var ingotResult = calculateMaterialPrice(blockIngot, ItemCategory.MINERALS, hourlyWage, veinMiner);
      double sell = ingotResult.systemSellPrice() * 9.0 * 0.95;
      double buy = ingotResult.systemBuyPrice() * 9.0 * 0.95;
      return ItemPriceResult.of(sell, buy);
    }

    // 3. 工具與防具合成階級推導
    var toolOrArmor = calculateToolOrArmorPrice(material, hourlyWage, veinMiner);
    if (toolOrArmor != null) {
      return toolOrArmor;
    }

    // 4. 合成衍生品推導（如台階、石磚、玻璃等）
    var crafted = craftedPatternPrice(material, category, hourlyWage, veinMiner);
    if (crafted != null) {
      return crafted;
    }

    // 5. 依分類與名稱模式兜底估算
    return fallbackPatternPrice(material, category, wageScale);
  }

  /**
   * 向下相容接口：傳回商店購買價（系統出售價）
   */
  public static double calculateBuyPrice(CatalogEntry entry) {
    return calculatePrice(entry, 1000.0, false).systemSellPrice();
  }

  public static double calculateBuyPrice(CatalogEntry entry, double hourlyWage, boolean veinMiner) {
    return calculatePrice(entry, hourlyWage, veinMiner).systemSellPrice();
  }

  public static double calculateBuyPrice(Material material, ItemCategory category) {
    return calculatePrice(material, category, 1000.0, false).systemSellPrice();
  }

  public static double calculateBuyPrice(Material material, ItemCategory category, double hourlyWage, boolean veinMiner) {
    return calculatePrice(material, category, hourlyWage, veinMiner).systemSellPrice();
  }

  /**
   * 取得指定物品的專用收購折讓比
   */
  public static Optional<Double> sellRatioOverride(Material material, boolean veinMiner) {
    if (material == null) return Optional.empty();
    var res = calculateMaterialPrice(material, null, 1000.0, veinMiner);
    return Optional.of(res.sellRatio());
  }

  public static Optional<Double> sellRatioOverride(Material material) {
    return sellRatioOverride(material, false);
  }

  public static double defaultSellRatio() {
    return 0.50;
  }

  /**
   * 判定是否屬於受 VeinMiner 影響的物資（嚴格限定礦石與原木，防止污染農產與建材）
   */
  public static boolean isVeinEligible(Material material, ItemCategory category) {
    if (category == ItemCategory.MINERALS || category == ItemCategory.LOGS) {
      return true;
    }
    var name = material.name();
    return name.endsWith("_ORE") || name.contains("_ORE_") || name.endsWith("_LOG")
        || name.endsWith("_STEM") || name.startsWith("RAW_") || material == Material.ANCIENT_DEBRIS
        || material == Material.COAL || material == Material.DIAMOND || material == Material.EMERALD
        || material == Material.NETHER_QUARTZ_ORE || material == Material.QUARTZ;
  }

  private static void put(Material material, Benchmark benchmark) {
    if (material != null) {
      BENCHMARKS.put(material, benchmark);
    }
  }

  private static Material blockToIngot(Material block) {
    return switch (block) {
      case IRON_BLOCK -> Material.IRON_INGOT;
      case GOLD_BLOCK -> Material.GOLD_INGOT;
      case DIAMOND_BLOCK -> Material.DIAMOND;
      case EMERALD_BLOCK -> Material.EMERALD;
      case COPPER_BLOCK -> Material.COPPER_INGOT;
      case NETHERITE_BLOCK -> Material.NETHERITE_INGOT;
      case LAPIS_BLOCK -> Material.LAPIS_LAZULI;
      case REDSTONE_BLOCK -> Material.REDSTONE;
      case COAL_BLOCK -> Material.COAL;
      case RAW_IRON_BLOCK -> Material.RAW_IRON;
      case RAW_GOLD_BLOCK -> Material.RAW_GOLD;
      case RAW_COPPER_BLOCK -> Material.RAW_COPPER;
      default -> null;
    };
  }

  private static ItemPriceResult calculateToolOrArmorPrice(Material material, double hourlyWage, boolean veinMiner) {
    var name = material.name();
    int units;
    double stickCount = 0;

    if (name.endsWith("_HELMET")) units = 5;
    else if (name.endsWith("_CHESTPLATE")) units = 8;
    else if (name.endsWith("_LEGGINGS")) units = 7;
    else if (name.endsWith("_BOOTS")) units = 4;
    else if (name.endsWith("_SWORD")) { units = 2; stickCount = 1; }
    else if (name.endsWith("_HOE")) { units = 2; stickCount = 2; }
    else if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) { units = 3; stickCount = 2; }
    else if (name.endsWith("_SHOVEL")) { units = 1; stickCount = 2; }
    else return null;

    // 獄髓升級鍛造：鑽石本體 + 獄髓錠 + 升級模板
    if (name.startsWith("NETHERITE_")) {
      var diamondName = name.replace("NETHERITE_", "DIAMOND_");
      var diamondMat = Material.matchMaterial(diamondName);
      var diamondPrice = diamondMat != null
          ? calculateMaterialPrice(diamondMat, ItemCategory.TOOLS, hourlyWage, veinMiner)
          : ItemPriceResult.of(750.0, 400.0);
      var netheriteIngot = calculateMaterialPrice(Material.NETHERITE_INGOT, ItemCategory.MINERALS, hourlyWage, veinMiner);
      var template = calculateMaterialPrice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, ItemCategory.MISC, hourlyWage, veinMiner);

      double sell = diamondPrice.systemSellPrice() + netheriteIngot.systemSellPrice() + template.systemSellPrice();
      double buy = diamondPrice.systemBuyPrice() + netheriteIngot.systemBuyPrice() + template.systemBuyPrice();
      return ItemPriceResult.of(sell, buy);
    }

    Material ingotMat;
    if (name.contains("WOODEN_")) ingotMat = Material.OAK_PLANKS;
    else if (name.contains("STONE_")) ingotMat = Material.COBBLESTONE;
    else if (name.contains("COPPER_")) ingotMat = Material.COPPER_INGOT;
    else if (name.contains("IRON_")) ingotMat = Material.IRON_INGOT;
    else if (name.contains("GOLDEN_") || name.contains("GOLD_")) ingotMat = Material.GOLD_INGOT;
    else if (name.contains("DIAMOND_")) ingotMat = Material.DIAMOND;
    else return null;

    var ingotResult = calculateMaterialPrice(ingotMat, ItemCategory.MINERALS, hourlyWage, veinMiner);
    var stickResult = calculateMaterialPrice(Material.STICK, ItemCategory.MISC, hourlyWage, veinMiner);

    double sell = ingotResult.systemSellPrice() * units + stickResult.systemSellPrice() * stickCount + 1.0;
    double buy = ingotResult.systemBuyPrice() * units + stickResult.systemBuyPrice() * stickCount;
    return ItemPriceResult.of(sell, buy);
  }

  private static ItemPriceResult craftedPatternPrice(Material material, ItemCategory category, double hourlyWage, boolean veinMiner) {
    var name = material.name();

    // 各色玻璃、混凝土、陶瓦
    if (name.contains("GLASS")) return ItemPriceResult.of(1.5, 0.6);
    if (name.contains("CONCRETE")) return ItemPriceResult.of(2.0, 0.8);
    if (name.contains("TERRACOTTA")) return ItemPriceResult.of(2.5, 1.0);
    if (name.contains("CARPET")) return ItemPriceResult.of(1.0, 0.4);
    if (name.contains("BED")) return ItemPriceResult.of(8.0, 3.5);
    if (name.contains("BANNER")) return ItemPriceResult.of(12.0, 5.0);

    // 台階、樓梯、牆（依建材價格折算）
    if (name.contains("SLAB")) return ItemPriceResult.of(0.6, 0.2);
    if (name.contains("STAIRS")) return ItemPriceResult.of(1.2, 0.4);
    if (name.contains("WALL") || name.contains("FENCE")) return ItemPriceResult.of(1.5, 0.5);
    if (name.contains("DOOR") || name.contains("TRAPDOOR")) return ItemPriceResult.of(3.0, 1.2);
    if (name.contains("BOAT") || name.endsWith("_RAFT")) return ItemPriceResult.of(10.0, 4.0);
    if (name.contains("MINECART")) return ItemPriceResult.of(38.0, 16.0);
    if (name.contains("RAIL")) return ItemPriceResult.of(6.0, 2.5);

    // 界伏盒
    if (name.contains("SHULKER_BOX")) {
      var shell = calculateMaterialPrice(Material.SHULKER_SHELL, ItemCategory.MISC, hourlyWage, veinMiner);
      var chest = calculateMaterialPrice(Material.CHEST, ItemCategory.MISC, hourlyWage, veinMiner);
      double sell = shell.systemSellPrice() * 2 + chest.systemSellPrice();
      double buy = shell.systemBuyPrice() * 2 + chest.systemBuyPrice();
      return ItemPriceResult.of(sell, buy);
    }

    return null;
  }

  private static ItemPriceResult fallbackPatternPrice(Material material, ItemCategory category, double wageScale) {
    var name = material.name();

    double baseSell;
    double baseBuy;

    if (category == null) {
      baseSell = 5.0;
      baseBuy = 2.0;
    } else {
      switch (category) {
        case MINERALS -> { baseSell = 15.0; baseBuy = 6.0; }
        case LOGS -> { baseSell = 3.0; baseBuy = 1.2; }
        case STONES -> { baseSell = 1.0; baseBuy = 0.4; }
        case CROPS -> { baseSell = 1.0; baseBuy = 0.4; }
        case RAW_MEAT -> { baseSell = 3.0; baseBuy = 1.2; }
        case FOOD -> { baseSell = 3.5; baseBuy = 1.5; }
        case BLOCKS -> { baseSell = 2.0; baseBuy = 0.8; }
        case DECORATIONS -> { baseSell = 4.0; baseBuy = 1.6; }
        case REDSTONE -> { baseSell = 8.0; baseBuy = 3.2; }
        case TOOLS -> { baseSell = 20.0; baseBuy = 8.0; }
        case WEAPONS -> { baseSell = 25.0; baseBuy = 10.0; }
        case ARMOR -> { baseSell = 30.0; baseBuy = 12.0; }
        case TRANSPORT -> { baseSell = 25.0; baseBuy = 10.0; }
        case POTIONS -> { baseSell = 25.0; baseBuy = 10.0; }
        case ENCHANTED_BOOKS -> { baseSell = 50.0; baseBuy = 20.0; }
        case MISC -> { baseSell = 6.0; baseBuy = 2.5; }
        default -> { baseSell = 5.0; baseBuy = 2.0; }
      }
    }

    try {
      if (material.isEdible()) {
        baseSell = Math.max(baseSell, 3.0);
        baseBuy = Math.max(baseBuy, 1.2);
      }
    } catch (Throwable ignored) {
    }

    return ItemPriceResult.of(baseSell * wageScale, baseBuy * wageScale);
  }

  private static double variantMultiplier(ItemStack stack, CatalogEntry entry) {
    if (stack == null) return 1.0;
    try {
      var meta = stack.getItemMeta();
      if (meta instanceof PotionMeta pm) {
        return potionFactor(pm.getBasePotionType(), stack.getType());
      }
      if (meta instanceof EnchantmentStorageMeta esm && !esm.getStoredEnchants().isEmpty()) {
        double total = 1.0;
        for (var enchEntry : esm.getStoredEnchants().entrySet()) {
          total += enchantBookFactor(enchEntry.getKey(), enchEntry.getValue());
        }
        return total;
      }
    } catch (Throwable ignored) {
    }

    if (entry.getDisplayTag() != null && !entry.getDisplayTag().isBlank()) {
      return 1.15;
    }
    return 1.0;
  }

  private static double potionFactor(PotionType type, Material bottle) {
    if (type == null) return 1.0;
    double factor = switch (type.name()) {
      case "HEALING", "STRONG_HEALING" -> 2.5;
      case "REGENERATION", "STRONG_REGENERATION", "LONG_REGENERATION" -> 2.2;
      case "STRENGTH", "STRONG_STRENGTH", "LONG_STRENGTH" -> 2.0;
      case "SWIFTNESS", "STRONG_SWIFTNESS", "LONG_SWIFTNESS" -> 1.6;
      case "FIRE_RESISTANCE", "LONG_FIRE_RESISTANCE" -> 1.8;
      case "NIGHT_VISION", "LONG_NIGHT_VISION" -> 1.4;
      case "INVISIBILITY", "LONG_INVISIBILITY" -> 1.8;
      default -> 1.2;
    };
    if (bottle == Material.SPLASH_POTION) factor *= 1.3;
    if (bottle == Material.LINGERING_POTION) factor *= 1.5;
    return factor;
  }

  private static double enchantBookFactor(Enchantment enchant, int level) {
    int max = Math.max(1, enchant.getMaxLevel());
    double levelScale = 0.85 + (level / (double) max) * 1.15;
    double rarity = enchant.isTreasure() ? 1.8 : 1.0;
    if (enchant.getKey().getKey().contains("mending")) rarity = 2.5;
    if (enchant.getKey().getKey().contains("unbreaking")) rarity = 1.2;
    return levelScale * rarity * 0.55;
  }
}
