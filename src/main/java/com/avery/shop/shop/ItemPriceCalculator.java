package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCategory;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;

/**
 * 依物品類型、材質與變體計算獨立基準價
 */
public final class ItemPriceCalculator {

    private final ShopPlugin plugin;

    public ItemPriceCalculator(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public double calculate(CatalogEntry entry) {
        var stack = entry.getTemplate();
        var category = entry.getCategory();
        var ref = plugin.getConfig().getDouble("shop.pricing.reference-price",
                plugin.getConfig().getDouble("default-prices.base-price", 10.0));

        double price = ref * categoryFactor(category);
        price *= materialFactor(stack.getType());
        price *= variantFactor(stack, entry);
        return clampAndRound(price);
    }

    private double categoryFactor(ItemCategory category) {
        return switch (category) {
            case FOOD -> 0.45;
            case BLOCKS -> 0.55;
            case DECORATIONS -> 0.65;
            case REDSTONE -> 1.1;
            case TOOLS -> 1.4;
            case WEAPONS -> 1.6;
            case ARMOR -> 1.7;
            case TRANSPORT -> 1.8;
            case POTIONS -> 2.2;
            case ENCHANTED_BOOKS -> 2.5;
            case SPAWN_EGGS -> 4.5;
            case MISC -> 0.9;
        };
    }

    private double materialFactor(Material material) {
        var name = material.name();

        if (name.endsWith("_SPAWN_EGG")) return 6.0;
        if (material == Material.ENCHANTED_BOOK) return 1.0;
        if (material == Material.ELYTRA) return 12.0;
        if (material == Material.TRIDENT) return 8.0;
        if (material == Material.MACE) return 7.0;
        if (material == Material.NETHER_STAR) return 25.0;
        if (material == Material.DRAGON_EGG) return 50.0;
        if (material == Material.TOTEM_OF_UNDYING) return 20.0;
        if (material == Material.HEART_OF_THE_SEA) return 15.0;
        if (material == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) return 18.0;

        if (name.contains("NETHERITE")) return 9.0;
        if (name.contains("DIAMOND")) return 5.5;
        if (name.contains("EMERALD")) return 4.5;
        if (name.contains("GOLD") || name.contains("GOLDEN")) return 2.2;
        if (name.contains("IRON")) return 1.8;
        if (name.contains("COPPER")) return 1.2;
        if (name.contains("CHAINMAIL")) return 1.5;
        if (name.contains("LEATHER")) return 0.7;
        if (name.contains("WOOD") || name.contains("WOODEN")) return 0.55;
        if (name.contains("STONE") && !name.contains("REDSTONE")) return 0.85;

        if (name.contains("BEACON")) return 22.0;
        if (name.contains("SHULKER_BOX")) return 6.0;
        if (name.contains("CONCRETE") || name.contains("TERRACOTTA")) return 0.75;
        if (name.contains("GLASS")) return 0.6;

        if (name.contains("DIAMOND_ORE") || name.equals("DEEPSLATE_DIAMOND_ORE")) return 8.0;
        if (name.contains("EMERALD_ORE") || name.equals("DEEPSLATE_EMERALD_ORE")) return 7.0;
        if (name.contains("ANCIENT_DEBRIS")) return 10.0;
        if (name.contains("NETHERITE_SCRAP")) return 8.5;
        if (name.contains("_ORE") || name.contains("RAW_")) return 2.5;

        if (name.equals("DIRT") || name.equals("COBBLESTONE") || name.equals("COBBLED_DEEPSLATE")) return 0.25;
        if (name.equals("STONE") || name.equals("DEEPSLATE") || name.equals("NETHERRACK")) return 0.3;
        if (name.equals("SAND") || name.equals("GRAVEL")) return 0.25;
        if (name.equals("WHEAT") || name.equals("CARROT") || name.equals("POTATO")) return 0.35;

        if (material.isEdible()) return 0.55;
        if (material.isBlock()) return 0.5;
        return 1.0;
    }

    private double variantFactor(ItemStack stack, CatalogEntry entry) {
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

    private double potionFactor(PotionType type, Material bottle) {
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

    private double enchantBookFactor(Enchantment enchant, int level) {
        int max = Math.max(1, enchant.getMaxLevel());
        double levelScale = 0.85 + (level / (double) max) * 1.15;
        double rarity = enchant.isTreasure() ? 1.8 : 1.0;
        if (enchant.getKey().getKey().contains("mending")) rarity = 2.5;
        if (enchant.getKey().getKey().contains("unbreaking")) rarity = 1.2;
        return levelScale * rarity * 0.55;
    }

    private double clampAndRound(double price) {
        var min = plugin.getConfig().getDouble("shop.pricing.min-price", 0.5);
        var max = plugin.getConfig().getDouble("shop.pricing.max-price", 100000.0);
        var step = plugin.getConfig().getDouble("shop.pricing.round-to", 0.5);
        price = Math.max(min, Math.min(max, price));
        if (step <= 0) return price;
        return Math.round(price / step) * step;
    }
}
