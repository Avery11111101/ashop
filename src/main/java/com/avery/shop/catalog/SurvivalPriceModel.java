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
 * 依純生存取得方式與稀有度計算基準購買價。
 * <p>
 * 校準基準（使用者提供）：
 * <ul>
 *   <li>閃長岩：購 9 / 售 6</li>
 *   <li>鑽石鎬：購 1000 / 售 700</li>
 *   <li>重錘：購 180000 / 售 120000</li>
 *   <li>鞘翅：購 130000 / 售 9500</li>
 * </ul>
 */
public final class SurvivalPriceModel {

  /** 購買價錨點（精確對齊基準值） */
  private static final Map<Material, Double> BUY_ANCHORS = Map.of(
      Material.DIORITE, 9.0,
      Material.DIAMOND_PICKAXE, 1000.0,
      Material.MACE, 180_000.0,
      Material.ELYTRA, 130_000.0
  );

  /** 收購比例覆寫（預設全域 2/3，鞘翅等特殊物品另設） */
  private static final Map<Material, Double> SELL_RATIO_OVERRIDES = Map.of(
      Material.ELYTRA, 9_500.0 / 130_000.0
  );

  private static final Map<Material, Double> BASE_RESOURCES = new EnumMap<>(Material.class);
  private static final Map<Material, Double> LOOT_PRICES = new EnumMap<>(Material.class);

  static {
    putBase(Material.COBBLESTONE, 1.0);
    putBase(Material.COBBLED_DEEPSLATE, 1.5);
    putBase(Material.DIRT, 0.5);
    putBase(Material.GRASS_BLOCK, 1.0);
    putBase(Material.SAND, 1.0);
    putBase(Material.RED_SAND, 1.5);
    putBase(Material.GRAVEL, 1.0);
    putBase(Material.CLAY, 2.0);
    putBase(Material.STONE, 1.5);
    putBase(Material.DEEPSLATE, 2.0);
    putBase(Material.TUFF, 2.0);
    putBase(Material.CALCITE, 3.0);
    putBase(Material.NETHERRACK, 0.5);
    putBase(Material.END_STONE, 2.5);
    putBase(Material.BASALT, 2.0);
    putBase(Material.BLACKSTONE, 2.5);
    putBase(Material.OAK_LOG, 4.0);
    putBase(Material.SPRUCE_LOG, 4.0);
    putBase(Material.BIRCH_LOG, 4.0);
    putBase(Material.JUNGLE_LOG, 4.0);
    putBase(Material.ACACIA_LOG, 4.0);
    putBase(Material.DARK_OAK_LOG, 4.0);
    putBase(Material.MANGROVE_LOG, 4.0);
    putBase(Material.CHERRY_LOG, 4.5);
    putBase(Material.CRIMSON_STEM, 5.0);
    putBase(Material.WARPED_STEM, 5.0);
    putBase(Material.OAK_PLANKS, 2.0);
    putBase(Material.STICK, 0.5);
    putBase(Material.COAL, 5.0);
    putBase(Material.CHARCOAL, 5.0);
    putBase(Material.RAW_IRON, 12.0);
    putBase(Material.IRON_INGOT, 18.0);
    putBase(Material.RAW_COPPER, 4.0);
    putBase(Material.COPPER_INGOT, 6.5);
    putBase(Material.RAW_GOLD, 20.0);
    putBase(Material.GOLD_INGOT, 30.0);
    putBase(Material.DIAMOND, 333.0);
    putBase(Material.EMERALD, 45.0);
    putBase(Material.LAPIS_LAZULI, 6.0);
    putBase(Material.REDSTONE, 4.0);
    putBase(Material.QUARTZ, 4.0);
    putBase(Material.AMETHYST_SHARD, 8.0);
    putBase(Material.GLOWSTONE_DUST, 10.0);
    putBase(Material.BLAZE_POWDER, 50.0);
    putBase(Material.BLAZE_ROD, 100.0);
    putBase(Material.ENDER_PEARL, 150.0);
    putBase(Material.ENDER_EYE, 320.0);
    putBase(Material.GHAST_TEAR, 250.0);
    putBase(Material.MAGMA_CREAM, 60.0);
    putBase(Material.PHANTOM_MEMBRANE, 180.0);
    putBase(Material.NETHER_WART, 8.0);
    putBase(Material.PRISMARINE_SHARD, 15.0);
    putBase(Material.PRISMARINE_CRYSTALS, 25.0);
    putBase(Material.SLIME_BALL, 12.0);
    putBase(Material.STRING, 3.0);
    putBase(Material.FEATHER, 2.0);
    putBase(Material.LEATHER, 6.0);
    putBase(Material.BONE, 3.0);
    putBase(Material.GUNPOWDER, 8.0);
    putBase(Material.SPIDER_EYE, 10.0);
    putBase(Material.ROTTEN_FLESH, 2.0);
    putBase(Material.EGG, 3.0);
    putBase(Material.WHEAT, 3.0);
    putBase(Material.CARROT, 3.0);
    putBase(Material.POTATO, 3.0);
    putBase(Material.BEETROOT, 3.0);
    putBase(Material.SUGAR_CANE, 3.0);
    putBase(Material.CACTUS, 3.0);
    putBase(Material.BAMBOO, 2.0);
    putBase(Material.KELP, 2.0);
    putBase(Material.SEA_PICKLE, 5.0);
    putBase(Material.HONEYCOMB, 15.0);
    putBase(Material.HONEY_BOTTLE, 25.0);

    putBase(Material.GRANITE, 8.0);
    putBase(Material.ANDESITE, 8.0);
    putBase(Material.POLISHED_DIORITE, 12.0);
    putBase(Material.POLISHED_GRANITE, 11.0);
    putBase(Material.POLISHED_ANDESITE, 11.0);

    putBase(Material.ANCIENT_DEBRIS, 8_000.0);
    putBase(Material.NETHERITE_SCRAP, 9_500.0);
    putBase(Material.NETHERITE_INGOT, 42_000.0);

    putLoot(Material.NAUTILUS_SHELL, 8_000.0);
    putLoot(Material.HEART_OF_THE_SEA, 30_000.0);
    putLoot(Material.SHULKER_SHELL, 25_000.0);
    putLoot(Material.TOTEM_OF_UNDYING, 85_000.0);
    putLoot(Material.NETHER_STAR, 150_000.0);
    putLoot(Material.DRAGON_EGG, 200_000.0);
    putLoot(Material.TRIDENT, 50_000.0);
    putLoot(Material.BREEZE_ROD, 90_000.0);
    putLoot(Material.HEAVY_CORE, 90_000.0);
    putLoot(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 25_000.0);
    putLoot(Material.ECHO_SHARD, 12_000.0);
    putLoot(Material.DISC_FRAGMENT_5, 15_000.0);
  }

  private SurvivalPriceModel() {}

  public static double calculateBuyPrice(CatalogEntry entry) {
    if (entry == null) return 10.0;
    var stack = entry.getTemplate();
    var material = stack != null ? stack.getType() : null;
    if (material == null) return 10.0;

    double basePrice;
    var anchor = BUY_ANCHORS.get(material);
    if (anchor != null) {
      basePrice = anchor;
    } else if (LOOT_PRICES.containsKey(material)) {
      basePrice = LOOT_PRICES.get(material);
    } else {
      var crafted = craftedPrice(material);
      if (crafted > 0) {
        basePrice = crafted;
      } else if (BASE_RESOURCES.containsKey(material)) {
        basePrice = BASE_RESOURCES.get(material);
      } else {
        basePrice = patternPrice(material, entry.getCategory());
      }
    }
    return roundPrice(basePrice * variantMultiplier(stack, entry));
  }

  public static double calculateBuyPrice(Material material, ItemCategory category) {
    if (material == null) return 10.0;
    var anchor = BUY_ANCHORS.get(material);
    if (anchor != null) return roundPrice(anchor);

    var loot = LOOT_PRICES.get(material);
    if (loot != null) return roundPrice(loot);

    var crafted = craftedPrice(material);
    if (crafted > 0) return roundPrice(crafted);

    var base = BASE_RESOURCES.get(material);
    if (base != null) return roundPrice(base);

    return roundPrice(patternPrice(material, category));
  }

  public static Optional<Double> sellRatioOverride(Material material) {
    if (material == Material.ELYTRA) {
      return Optional.of(9_500.0 / 130_000.0);
    }
    if (isOreBlock(material)) {
      return Optional.of(oreSellRatio(material));
    }
    return Optional.ofNullable(SELL_RATIO_OVERRIDES.get(material));
  }

  public static boolean isOreBlock(Material material) {
    var name = material.name();
    return name.endsWith("_ORE") || name.contains("_ORE_") || material == Material.ANCIENT_DEBRIS;
  }

  private static double oreSellRatio(Material material) {
    var name = material.name();
    return switch (name) {
      case "DEEPSLATE_EMERALD_ORE" -> 15_000.0 / 35_000.0;
      case "DEEPSLATE_COAL_ORE" -> 80.0 / 180.0;
      case "COAL_ORE" -> 5.0 / 12.0;
      case "DEEPSLATE_DIAMOND_ORE" -> 300.0 / 620.0;
      case "DIAMOND_ORE" -> 320.0 / 680.0;
      case "DEEPSLATE_COPPER_ORE" -> 40.0 / 90.0;
      case "COPPER_ORE" -> 12.0 / 28.0;
      case "DEEPSLATE_IRON_ORE" -> 16.0 / 38.0;
      case "IRON_ORE" -> 12.0 / 28.0;
      case "DEEPSLATE_GOLD_ORE" -> 25.0 / 58.0;
      case "GOLD_ORE" -> 20.0 / 48.0;
      case "NETHER_GOLD_ORE" -> 10.0 / 25.0;
      case "DEEPSLATE_LAPIS_ORE" -> 45.0 / 120.0;
      case "LAPIS_ORE" -> 35.0 / 95.0;
      case "DEEPSLATE_REDSTONE_ORE" -> 11.0 / 32.0;
      case "REDSTONE_ORE" -> 8.0 / 24.0;
      case "NETHER_QUARTZ_ORE" -> 4.0 / 10.0;
      case "EMERALD_ORE" -> 50.0 / 120.0;
      case "ANCIENT_DEBRIS" -> 5_300.0 / 8_000.0;
      default -> 0.42;
    };
  }

  public static double defaultSellRatio() {
    return 2.0 / 3.0;
  }

  private static void putBase(Material material, double price) {
    BASE_RESOURCES.put(material, price);
  }

  private static void putLoot(Material material, double price) {
    LOOT_PRICES.put(material, price);
  }

  private static double craftedPrice(Material material) {
    var name = material.name();

    var toolPrice = toolOrArmorPrice(material);
    if (toolPrice > 0) return toolPrice;

    if (name.endsWith("_BLOCK") && !name.contains("QUARTZ")) {
      var ingot = blockToIngot(material);
      if (ingot != null) {
        return BASE_RESOURCES.getOrDefault(ingot, 0.0) * 9 * 0.92;
      }
    }

    if (material == Material.DIORITE) {
      return average(2 * res(Material.COBBLESTONE) + 2 * res(Material.QUARTZ)) * 1.8;
    }
    if (material == Material.GRANITE) {
      return average(res(Material.DIORITE) + res(Material.QUARTZ)) * 1.05;
    }
    if (material == Material.ANDESITE) {
      return average(res(Material.DIORITE) + res(Material.COBBLESTONE)) * 1.05;
    }

    if (name.endsWith("_TORCH")) return res(Material.COAL) * 0.4 + res(Material.STICK) * 0.5;
    if (material == Material.LANTERN) return res(Material.IRON_INGOT) * 0.7 + res(Material.TORCH) * 0.5;
    if (material == Material.CHEST) return res(Material.OAK_PLANKS) * 8;
    if (material == Material.CRAFTING_TABLE) return res(Material.OAK_PLANKS) * 4;
    if (material == Material.FURNACE) return res(Material.COBBLESTONE) * 8;
    if (material == Material.ANVIL) return res(Material.IRON_BLOCK) * 3 + res(Material.IRON_INGOT) * 4;
    if (material == Material.ENCHANTING_TABLE) {
      return res(Material.DIAMOND) * 2 + res(Material.OBSIDIAN) * 4 + res(Material.BOOK) * 1;
    }
    if (material == Material.BOOK) return res(Material.PAPER) * 3 + res(Material.LEATHER) * 1;
    if (material == Material.PAPER) return res(Material.SUGAR_CANE) * 3;
    if (material == Material.OBSIDIAN) return 25.0;
    if (material == Material.BEACON) return res(Material.NETHER_STAR) * 1 + res(Material.OBSIDIAN) * 3 + res(Material.GLASS) * 5;

    if (material == Material.BOW) return res(Material.STICK) * 3 + res(Material.STRING) * 3;
    if (material == Material.CROSSBOW) return res(Material.STICK) * 3 + res(Material.IRON_INGOT) * 1
        + res(Material.STRING) * 2;
    if (material == Material.FISHING_ROD) return res(Material.STICK) * 3 + res(Material.STRING) * 2;
    if (material == Material.SHIELD) return res(Material.IRON_INGOT) * 1 + res(Material.OAK_PLANKS) * 6;
    if (material == Material.ARROW) return res(Material.FLINT) * 0.2 + res(Material.STICK) * 0.2 + res(Material.FEATHER) * 0.2;
    if (material == Material.SPECTRAL_ARROW) return res(Material.ARROW) * 1 + res(Material.GLOWSTONE_DUST) * 4;
    if (material == Material.TNT) return res(Material.GUNPOWDER) * 5 + res(Material.SAND) * 4;
    if (material == Material.ENDER_CHEST) return res(Material.OBSIDIAN) * 8 + res(Material.ENDER_EYE) * 1;

    return 0;
  }

  private static double toolOrArmorPrice(Material material) {
    var name = material.name();
    int units;
    double stickCost = 0;

    if (name.endsWith("_HELMET")) units = 5;
    else if (name.endsWith("_CHESTPLATE")) units = 8;
    else if (name.endsWith("_LEGGINGS")) units = 7;
    else if (name.endsWith("_BOOTS")) units = 4;
    else if (name.endsWith("_SWORD") || name.endsWith("_HOE")) {
      units = 2;
      stickCost = res(Material.STICK);
    } else if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) {
      units = 3;
      stickCost = res(Material.STICK) * 2;
    } else if (name.endsWith("_SHOVEL")) {
      units = 1;
      stickCost = res(Material.STICK) * 2;
    } else {
      return 0;
    }

    if (name.startsWith("NETHERITE_")) {
      var diamondName = name.replace("NETHERITE_", "DIAMOND_");
      var diamondMat = Material.matchMaterial(diamondName);
      double diamondCost = diamondMat != null ? toolOrArmorPrice(diamondMat) : 1000.0;
      return diamondCost + res(Material.NETHERITE_INGOT) + res(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
    }

    var tier = toolTier(material);
    if (tier == null) return 0;

    return tier.ingotPrice * units + stickCost;
  }

  private static ToolTier toolTier(Material material) {
    var name = material.name();

    if (name.contains("WOODEN_")) return ToolTier.WOOD;
    if (name.contains("STONE_")) return ToolTier.STONE;
    if (name.contains("COPPER_")) return ToolTier.COPPER;
    if (name.contains("IRON_")) return ToolTier.IRON;
    if (name.contains("GOLDEN_") || name.contains("GOLD_")) return ToolTier.GOLD;
    if (name.contains("DIAMOND_")) return ToolTier.DIAMOND;
    if (name.contains("NETHERITE_")) return ToolTier.NETHERITE;
    return null;
  }

  private enum ToolTier {
    WOOD(2.0),
    STONE(3.0),
    COPPER(6.5),
    IRON(18.0),
    GOLD(30.0),
    DIAMOND(333.0),
    NETHERITE(42_000.0);

    final double ingotPrice;

    ToolTier(double ingotPrice) {
      this.ingotPrice = ingotPrice;
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

  private static double patternPrice(Material material, ItemCategory category) {
    var name = material.name();

    if (name.endsWith("_ORE") || name.startsWith("DEEPSLATE_") && name.endsWith("_ORE") || name.contains("_ORE")) {
      if (name.equals("DEEPSLATE_EMERALD_ORE")) return 35_000.0;
      if (name.equals("EMERALD_ORE")) return 120.0;
      if (name.equals("DEEPSLATE_COAL_ORE")) return 180.0;
      if (name.equals("COAL_ORE")) return 12.0;
      if (name.equals("DEEPSLATE_DIAMOND_ORE")) return 620.0;
      if (name.equals("DIAMOND_ORE")) return 680.0;
      if (name.equals("DEEPSLATE_COPPER_ORE")) return 90.0;
      if (name.equals("COPPER_ORE")) return 28.0;
      if (name.equals("DEEPSLATE_IRON_ORE")) return 38.0;
      if (name.equals("IRON_ORE")) return 28.0;
      if (name.equals("DEEPSLATE_GOLD_ORE")) return 58.0;
      if (name.equals("GOLD_ORE")) return 48.0;
      if (name.equals("NETHER_GOLD_ORE")) return 25.0;
      if (name.equals("DEEPSLATE_LAPIS_ORE")) return 120.0;
      if (name.equals("LAPIS_ORE")) return 95.0;
      if (name.equals("DEEPSLATE_REDSTONE_ORE")) return 32.0;
      if (name.equals("REDSTONE_ORE")) return 24.0;
      if (name.equals("NETHER_QUARTZ_ORE")) return 10.0;
      return 25.0;
    }

    if (name.equals("ANCIENT_DEBRIS")) return 8_000.0;
    if (name.equals("GILDED_BLACKSTONE")) return 45.0;
    if (name.equals("AMETHYST_CLUSTER")) return 35.0;
    if (name.equals("LARGE_AMETHYST_BUD")) return 25.0;
    if (name.equals("MEDIUM_AMETHYST_BUD")) return 18.0;
    if (name.equals("SMALL_AMETHYST_BUD")) return 12.0;

    if (name.startsWith("RAW_")) return 8.0;
    if (name.contains("SHULKER_BOX")) return 30_000.0;
    if (name.contains("BANNER")) return 20.0;
    if (name.contains("BED")) return 12.0;
    if (name.contains("CARPET")) return 2.0;
    if (name.contains("CONCRETE")) return 4.0;
    if (name.contains("TERRACOTTA")) return 5.0;
    if (name.contains("GLASS")) return 2.0;
    if (name.contains("STAIRS") || name.contains("SLAB") || name.contains("WALL")) return 1.5;
    if (name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("FENCE")) return 4.0;
    if (name.contains("BOAT") || name.contains("RAFT")) return 15.0;
    if (name.contains("MINECART")) return 35.0;
    if (name.contains("RAIL")) return 8.0;
    if (material == Material.SADDLE) return 120.0;
    if (material == Material.NAME_TAG) return 250.0;
    if (material == Material.GOLDEN_APPLE) return 120.0;
    if (material == Material.ENCHANTED_GOLDEN_APPLE) return 5_000.0;
    if (material.isEdible()) return 5.0;

    return switch (category) {
      case MINERALS -> 10.0;
      case LOGS -> 4.0;
      case STONES -> 2.0;
      case CROPS -> 4.0;
      case RAW_MEAT -> 6.0;
      case FOOD -> 4.0;
      case BLOCKS -> 3.0;
      case DECORATIONS -> 5.0;
      case REDSTONE -> 12.0;
      case TOOLS -> 25.0;
      case WEAPONS -> 35.0;
      case ARMOR -> 40.0;
      case TRANSPORT -> 50.0;
      case POTIONS -> 35.0;
      case ENCHANTED_BOOKS -> 80.0;
      case MISC -> 8.0;
      default -> 6.0;
    };
  }

  private static double patternPrice(Material material, CatalogEntry entry) {
    return patternPrice(material, entry.getCategory());
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
      // 容錯防護（如無伺服器執行個體環境測試）
    }

    if (entry.getDisplayTag() != null && !entry.getDisplayTag().isBlank()) {
      return 1.15;
    }

    return 1.0;
  }

  private static double potionFactor(PotionType type, Material bottle) {
    if (type == null) return 1.0;
    double factor = switch (type.name()) {
      case "HEALING", "STRONG_HEALING" -> 2.8;
      case "REGENERATION", "STRONG_REGENERATION", "LONG_REGENERATION" -> 2.5;
      case "STRENGTH", "STRONG_STRENGTH", "LONG_STRENGTH" -> 2.2;
      case "SWIFTNESS", "STRONG_SWIFTNESS", "LONG_SWIFTNESS" -> 1.8;
      case "FIRE_RESISTANCE", "LONG_FIRE_RESISTANCE" -> 1.9;
      case "NIGHT_VISION", "LONG_NIGHT_VISION" -> 1.5;
      case "INVISIBILITY", "LONG_INVISIBILITY" -> 2.0;
      case "POISON", "STRONG_POISON", "LONG_POISON" -> 1.6;
      case "HARMING", "STRONG_HARMING" -> 2.0;
      case "SLOWNESS", "LONG_SLOWNESS" -> 1.3;
      case "WEAKNESS", "LONG_WEAKNESS" -> 1.2;
      case "TURTLE_MASTER", "STRONG_TURTLE_MASTER", "LONG_TURTLE_MASTER" -> 2.4;
      case "SLOW_FALLING", "LONG_SLOW_FALLING" -> 1.7;
      case "WATER_BREATHING", "LONG_WATER_BREATHING" -> 1.6;
      case "LEAPING", "STRONG_LEAPING", "LONG_LEAPING" -> 1.5;
      case "LUCK" -> 2.0;
      case "OOZING", "WEAVING", "INFESTED", "WIND_CHARGED" -> 2.1;
      default -> 1.0;
    };

    if (bottle == Material.SPLASH_POTION) factor *= 1.35;
    if (bottle == Material.LINGERING_POTION) factor *= 1.55;
    if (type.name().startsWith("STRONG_")) factor *= 1.25;
    if (type.name().startsWith("LONG_")) factor *= 1.15;
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

  private static double res(Material material) {
    if (BUY_ANCHORS.containsKey(material)) return BUY_ANCHORS.get(material);
    if (LOOT_PRICES.containsKey(material)) return LOOT_PRICES.get(material);
    if (BASE_RESOURCES.containsKey(material) && material != Material.GRANITE && material != Material.ANDESITE) {
      return BASE_RESOURCES.get(material);
    }
    var crafted = craftedPrice(material);
    if (crafted > 0) return crafted;
    return BASE_RESOURCES.getOrDefault(material, 3.0);
  }

  private static double average(double total) {
    return total / 2.0;
  }

  private static double roundPrice(double price) {
    price = Math.max(0.5, Math.min(500_000.0, price));
    if (price >= 10_000) return Math.round(price / 500.0) * 500.0;
    if (price >= 1_000) return Math.round(price / 50.0) * 50.0;
    if (price >= 100) return Math.round(price / 5.0) * 5.0;
    if (price >= 10) return Math.round(price);
    return Math.round(price * 2.0) / 2.0;
  }
}
