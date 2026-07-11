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

  /** 基礎資源單價（購買價） */
  private static final Map<Material, Double> BASE_RESOURCES = new EnumMap<>(Material.class);

  /** 結構戰利品 / 稀有掉落（無法以配方拆解者） */
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
    putBase(Material.RAW_COPPER, 5.0);
    putBase(Material.COPPER_INGOT, 8.0);
    putBase(Material.RAW_GOLD, 22.0);
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
    putBase(Material.NETHERITE_SCRAP, 10_000.0);
    putBase(Material.NETHERITE_INGOT, 45_000.0);

    putLoot(Material.NAUTILUS_SHELL, 8_000.0);
    putLoot(Material.HEART_OF_THE_SEA, 30_000.0);
    putLoot(Material.SHULKER_SHELL, 25_000.0);
    putLoot(Material.TOTEM_OF_UNDYING, 85_000.0);
    putLoot(Material.NETHER_STAR, 150_000.0);
    putLoot(Material.DRAGON_EGG, 200_000.0);
    putLoot(Material.TRIDENT, 50_000.0);
    putLoot(Material.BREEZE_ROD, 90_000.0);
    putLoot(Material.HEAVY_CORE, 90_000.0);
    putLoot(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 30_000.0);
    putLoot(Material.ECHO_SHARD, 12_000.0);
    putLoot(Material.DISC_FRAGMENT_5, 15_000.0);
  }

  private SurvivalPriceModel() {}

  public static double calculateBuyPrice(CatalogEntry entry) {
    var stack = entry.getTemplate();
    var material = stack.getType();

    var anchor = BUY_ANCHORS.get(material);
    if (anchor != null) {
      return roundPrice(anchor * variantMultiplier(stack, entry));
    }

    var loot = LOOT_PRICES.get(material);
    if (loot != null) {
      return roundPrice(loot * variantMultiplier(stack, entry));
    }

    var crafted = craftedPrice(material);
    if (crafted > 0) {
      return roundPrice(crafted * variantMultiplier(stack, entry));
    }

  var base = BASE_RESOURCES.get(material);
    if (base != null) {
      return roundPrice(base * variantMultiplier(stack, entry));
    }

    return roundPrice(patternPrice(material, entry) * variantMultiplier(stack, entry));
  }

  public static Optional<Double> sellRatioOverride(Material material) {
    return Optional.ofNullable(SELL_RATIO_OVERRIDES.get(material));
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
    var tier = toolTier(material);
    if (tier == null) return 0;

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
    COPPER(8.0),
    IRON(18.0),
    GOLD(30.0),
    DIAMOND(333.0),
    NETHERITE(45_000.0);

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

    if (name.endsWith("_ORE") || name.startsWith("DEEPSLATE_") && name.endsWith("_ORE")) {
      if (name.contains("DIAMOND")) return 280.0;
      if (name.contains("EMERALD")) return 40.0;
      if (name.contains("ANCIENT_DEBRIS")) return 7_500.0;
      if (name.contains("GOLD")) return 20.0;
      if (name.contains("IRON")) return 10.0;
      if (name.contains("COPPER")) return 4.0;
      if (name.contains("LAPIS")) return 5.0;
      if (name.contains("REDSTONE")) return 3.0;
      if (name.contains("COAL")) return 4.0;
      return 6.0;
    }

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
