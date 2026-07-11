package com.avery.shop.shop;

import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCategory;
import org.bukkit.Material;

import java.util.Locale;

/**
 * 將目錄物品對應到 shop 子分類路徑（相對於頂層分類 id，如 blocks/building/wood）
 */
public final class ShopSubcategoryResolver {

    private ShopSubcategoryResolver() {}

    public static String resolve(ItemCategory topCategory, CatalogEntry entry) {
        var material = entry.getTemplate().getType();
        return switch (topCategory) {
            case BLOCKS -> resolveBlock(material);
            case TOOLS -> resolveTool(material);
            case WEAPONS -> resolveWeapon(material);
            case ARMOR -> resolveArmor(material);
            case FOOD -> resolveFood(material);
            case POTIONS -> resolvePotion(material);
            case ENCHANTED_BOOKS -> "all";
            case REDSTONE -> resolveRedstone(material);
            case TRANSPORT -> resolveTransport(material);
            case DECORATIONS -> resolveDecoration(material);
            case SPAWN_EGGS -> resolveSpawnEgg(material);
            case MISC -> resolveMisc(material);
        };
    }

    public static Material iconFor(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return Material.CHEST;
        }
        var parts = relativePath.split("/");
        var leaf = parts[parts.length - 1];
        return switch (leaf) {
            case "building" -> Material.BRICKS;
            case "wood" -> Material.OAK_PLANKS;
            case "stone" -> Material.STONE;
            case "copper" -> Material.COPPER_BLOCK;
            case "dyed" -> Material.WHITE_WOOL;
            case "wool" -> Material.WHITE_WOOL;
            case "carpet" -> Material.WHITE_CARPET;
            case "terracotta" -> Material.TERRACOTTA;
            case "concrete" -> Material.WHITE_CONCRETE;
            case "glazed_terracotta" -> Material.WHITE_GLAZED_TERRACOTTA;
            case "glass" -> Material.GLASS;
            case "natural" -> Material.GRASS_BLOCK;
            case "ores" -> Material.IRON_ORE;
            case "leaves" -> Material.OAK_LEAVES;
            case "saplings" -> Material.OAK_SAPLING;
            case "flowers" -> Material.POPPY;
            case "terrain" -> Material.DIRT;
            case "functional" -> Material.FURNACE;
            case "nether" -> Material.NETHERRACK;
            case "end" -> Material.END_STONE;
            case "other" -> Material.BEDROCK;
            case "pickaxes" -> Material.IRON_PICKAXE;
            case "axes" -> Material.IRON_AXE;
            case "shovels" -> Material.IRON_SHOVEL;
            case "hoes" -> Material.IRON_HOE;
            case "utility" -> Material.SHEARS;
            case "swords" -> Material.IRON_SWORD;
            case "ranged" -> Material.BOW;
            case "special" -> Material.TRIDENT;
            case "head" -> Material.IRON_HELMET;
            case "chest" -> Material.IRON_CHESTPLATE;
            case "legs" -> Material.IRON_LEGGINGS;
            case "feet" -> Material.IRON_BOOTS;
            case "shield_elytra" -> Material.SHIELD;
            case "raw" -> Material.BEEF;
            case "cooked" -> Material.COOKED_BEEF;
            case "snacks" -> Material.COOKIE;
            case "potion" -> Material.POTION;
            case "splash" -> Material.SPLASH_POTION;
            case "lingering" -> Material.LINGERING_POTION;
            case "all" -> Material.ENCHANTED_BOOK;
            case "components" -> Material.REDSTONE;
            case "mechanisms" -> Material.PISTON;
            case "rails" -> Material.RAIL;
            case "boats" -> Material.OAK_BOAT;
            case "minecarts" -> Material.MINECART;
            case "banners" -> Material.WHITE_BANNER;
            case "candles" -> Material.CANDLE;
            case "display" -> Material.ITEM_FRAME;
            case "passive" -> Material.PIG_SPAWN_EGG;
            case "hostile" -> Material.ZOMBIE_SPAWN_EGG;
            case "boss" -> Material.WITHER_SPAWN_EGG;
            case "materials" -> Material.IRON_INGOT;
            case "brewing" -> Material.BLAZE_POWDER;
            default -> Material.CHEST;
        };
    }

    public static int slotOrder(String relativePath) {
        if (relativePath == null) return 999;
        return switch (relativePath) {
            case "building" -> 0;
            case "building/wood" -> 0;
            case "building/stone" -> 1;
            case "building/copper" -> 2;
            case "dyed" -> 1;
            case "dyed/wool" -> 0;
            case "dyed/carpet" -> 1;
            case "dyed/terracotta" -> 2;
            case "dyed/concrete" -> 3;
            case "dyed/glazed_terracotta" -> 4;
            case "dyed/glass" -> 5;
            case "natural" -> 2;
            case "natural/ores" -> 0;
            case "natural/leaves" -> 1;
            case "natural/saplings" -> 2;
            case "natural/flowers" -> 3;
            case "natural/terrain" -> 4;
            case "functional" -> 3;
            case "nether" -> 4;
            case "end" -> 5;
            case "other" -> 6;
            case "pickaxes" -> 0;
            case "axes" -> 1;
            case "shovels" -> 2;
            case "hoes" -> 3;
            case "utility" -> 4;
            case "swords" -> 0;
            case "ranged" -> 1;
            case "special" -> 2;
            case "head" -> 0;
            case "chest" -> 1;
            case "legs" -> 2;
            case "feet" -> 3;
            case "shield_elytra" -> 4;
            case "raw" -> 0;
            case "cooked" -> 1;
            case "snacks" -> 2;
            case "potion" -> 0;
            case "splash" -> 1;
            case "lingering" -> 2;
            case "components" -> 0;
            case "mechanisms" -> 1;
            case "rails" -> 0;
            case "boats" -> 1;
            case "minecarts" -> 2;
            case "banners" -> 0;
            case "candles" -> 1;
            case "display" -> 2;
            case "passive" -> 0;
            case "hostile" -> 1;
            case "boss" -> 2;
            case "materials" -> 0;
            case "brewing" -> 1;
            default -> 50;
        };
    }

    private static String resolveBlock(Material material) {
        var name = material.name();

        if (isDyedWool(name)) return "dyed/wool";
        if (isDyedCarpet(name)) return "dyed/carpet";
        if (name.contains("TERRACOTTA") && !name.contains("GLAZED")) return "dyed/terracotta";
        if (name.contains("CONCRETE")) return "dyed/concrete";
        if (name.contains("GLAZED_TERRACOTTA")) return "dyed/glazed_terracotta";
        if (name.contains("GLASS")) return "dyed/glass";

        if (name.contains("COPPER") || name.equals("LIGHTNING_ROD") || name.equals("RAW_COPPER_BLOCK")) {
            return "building/copper";
        }

        if (isWoodBuilding(name, material)) return "building/wood";
        if (isStoneBuilding(name, material)) return "building/stone";

        if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS") || name.equals("NETHER_QUARTZ_ORE")) {
            return "natural/ores";
        }
        if (name.endsWith("_LEAVES")) return "natural/leaves";
        if (name.endsWith("_SAPLING") || name.equals("MANGROVE_PROPAGULE")) return "natural/saplings";
        if (isFlowerOrPlant(name, material)) return "natural/flowers";
        if (isTerrain(name, material)) return "natural/terrain";

        if (isFunctionalBlock(name, material)) return "functional";
        if (isNetherBlock(name, material)) return "nether";
        if (isEndBlock(name, material)) return "end";

        return "other";
    }

    private static boolean isDyedWool(String name) {
        return name.endsWith("_WOOL") || name.equals("WOOL");
    }

    private static boolean isDyedCarpet(String name) {
        return name.endsWith("_CARPET");
    }

    private static boolean isWoodBuilding(String name, Material material) {
        if (name.contains("BAMBOO")) return true;
        if (name.contains("_LOG") || name.contains("_WOOD") || name.contains("_PLANKS")) return true;
        if (name.contains("_DOOR") || name.contains("_TRAPDOOR") || name.contains("_FENCE")
                || name.contains("_GATE") || name.contains("_SIGN") || name.contains("_HANGING_SIGN")) {
            return name.contains("OAK") || name.contains("SPRUCE") || name.contains("BIRCH")
                    || name.contains("JUNGLE") || name.contains("ACACIA") || name.contains("DARK_OAK")
                    || name.contains("MANGROVE") || name.contains("CHERRY") || name.contains("PALE_OAK")
                    || name.contains("BAMBOO") || name.contains("CRIMSON") || name.contains("WARPED");
        }
        if (name.contains("_SLAB") || name.contains("_STAIRS")) {
            return name.contains("PLANKS") || name.contains("BAMBOO") || name.contains("CRIMSON")
                    || name.contains("WARPED");
        }
        return name.equals("COMPOSTER") || name.equals("BARREL") || name.equals("LECTERN")
                || name.equals("BOOKSHELF") || name.equals("CHISELED_BOOKSHELF")
                || name.equals("LADDER") || name.equals("SCAFFOLDING");
    }

    private static boolean isStoneBuilding(String name, Material material) {
        if (name.contains("COPPER") || name.contains("BAMBOO")) return false;
        if (name.contains("GLASS") || name.contains("WOOL") || name.contains("CARPET")) return false;
        if (name.endsWith("_ORE") || name.endsWith("_LEAVES")) return false;

        if (name.contains("STONE") || name.contains("COBBLE") || name.contains("BRICK")
                || name.contains("DEEPSLATE") || name.contains("ANDESITE") || name.contains("DIORITE")
                || name.contains("GRANITE") || name.contains("TUFF") || name.contains("CALCITE")
                || name.contains("DRIPSTONE") || name.contains("SANDSTONE") || name.contains("PRISMARINE")
                || name.contains("QUARTZ") || name.contains("PURPUR") || name.contains("BLACKSTONE")
                || name.contains("BASALT") || name.contains("MOSSY") || name.contains("POLISHED")
                || name.contains("CHISELED") || name.contains("SMOOTH") || name.contains("CUT_")
                || name.contains("TILE") || name.contains("SHULKER_BOX") || name.contains("INFESTED")) {
            return true;
        }
        return name.equals("GRAVEL") || name.equals("CLAY") || name.equals("BRICKS")
                || name.equals("OBSIDIAN") || name.equals("CRYING_OBSIDIAN")
                || name.equals("GLOWSTONE") || name.equals("SEA_LANTERN")
                || name.equals("MAGMA_BLOCK") || name.equals("AMETHYST_BLOCK")
                || name.equals("BUDDING_AMETHYST") || name.contains("AMETHYST");
    }

    private static boolean isFlowerOrPlant(String name, Material material) {
        if (name.contains("FLOWER") || name.contains("TULIP") || name.contains("ORCHID")
                || name.contains("ALLIUM") || name.contains("BLUET") || name.contains("POPPY")
                || name.contains("DAISY") || name.contains("DANDELION") || name.contains("LILY")
                || name.contains("ROSE") || name.contains("PEONY") || name.contains("SUNFLOWER")
                || name.contains("CORNFLOWER") || name.contains("TORCHFLOWER")
                || name.contains("PITCHER") || name.contains("AZALEA") || name.contains("MOSS")
                || name.contains("FERN") || name.contains("GRASS") || name.contains("VINE")
                || name.contains("HANGING_ROOTS") || name.contains("SPORE") || name.contains("FUNGUS")
                || name.contains("SHROOM") || name.contains("ROOTS") || name.contains("KELP")
                || name.contains("SEAGRASS") || name.contains("CORAL") || name.contains("BUSH")
                || name.contains("CACTUS") || name.contains("BAMBOO") && !isWoodBuilding(name, material)) {
            return true;
        }
        return name.equals("DEAD_BUSH") || name.equals("LILY_PAD") || name.equals("BIG_DRIPLEAF")
                || name.equals("SMALL_DRIPLEAF") || name.equals("SUGAR_CANE") || name.equals("BROWN_MUSHROOM")
                || name.equals("RED_MUSHROOM") || name.equals("CRIMSON_FUNGUS") || name.equals("WARPED_FUNGUS")
                || name.equals("NETHER_WART") || name.equals("CHORUS_PLANT") || name.equals("CHORUS_FLOWER");
    }

    private static boolean isTerrain(String name, Material material) {
        if (name.endsWith("_ORE") || name.endsWith("_LEAVES")) return false;
        return name.equals("DIRT") || name.equals("GRASS_BLOCK") || name.equals("COARSE_DIRT")
                || name.equals("ROOTED_DIRT") || name.equals("PODZOL") || name.equals("MYCELIUM")
                || name.equals("MUD") || name.equals("MUDDY_MANGROVE_ROOTS") || name.equals("CLAY")
                || name.equals("GRAVEL") || name.equals("SAND") || name.equals("RED_SAND")
                || name.equals("SOUL_SAND") || name.equals("SOUL_SOIL") || name.equals("SNOW_BLOCK")
                || name.equals("SNOW") || name.contains("ICE") || name.equals("PACKED_ICE")
                || name.equals("BLUE_ICE") || name.equals("FARMLAND") || name.equals("DIRT_PATH")
                || name.equals("SUSPICIOUS_SAND") || name.equals("SUSPICIOUS_GRAVEL");
    }

    private static boolean isFunctionalBlock(String name, Material material) {
        return name.contains("FURNACE") || name.contains("SMOKER") || name.contains("BLAST")
                || name.contains("BREWING") || name.contains("CRAFTING") || name.contains("ENCHANT")
                || name.contains("ANVIL") || name.contains("GRINDSTONE") || name.contains("SMITHING")
                || name.contains("STONECUTTER") || name.contains("CARTOGRAPHY") || name.contains("LOOM")
                || name.contains("FLETCHING") || name.contains("BEACON") || name.contains("CONDUIT")
                || name.contains("HOPPER") || name.contains("DROPPER") || name.contains("DISPENSER")
                || name.contains("OBSERVER") || name.contains("PISTON") || name.contains("REDSTONE")
                || name.contains("REPEATER") || name.contains("COMPARATOR") || name.contains("LEVER")
                || name.contains("BUTTON") || name.contains("PRESSURE_PLATE") || name.contains("TRIPWIRE")
                || name.contains("TARGET") || name.contains("NOTE_BLOCK") || name.contains("JUKEBOX")
                || name.contains("CHEST") || name.contains("SHULKER") || name.contains("BARREL")
                || name.contains("ENDER_CHEST") || name.contains("SPAWNER") || name.contains("BELL")
                || name.contains("CAULDRON") || name.contains("COMPOSTER") || name.contains("BEEHIVE")
                || name.contains("BEE_NEST") || name.contains("RESPAWN_ANCHOR") || name.contains("LODESTONE")
                || name.contains("CAMPFIRE") || name.contains("BED") || name.contains("SCULK")
                || name.contains("CALIBRATED") || name.contains("DAYLIGHT") || name.contains("DETECTOR")
                || name.contains("RAIL") || name.contains("COMMAND") || name.contains("STRUCTURE")
                || name.contains("JIGSAW") || name.equals("CRAFTING_TABLE") || name.equals("CHEST")
                || name.equals("TRAPPED_CHEST") || name.equals("CRAFTER");
    }

    private static boolean isNetherBlock(String name, Material material) {
        return name.contains("NETHER") || name.contains("CRIMSON") || name.contains("WARPED")
                || name.equals("MAGMA_BLOCK") || name.equals("GLOWSTONE") || name.equals("SOUL_SAND")
                || name.equals("SOUL_SOIL") || name.equals("BASALT") || name.equals("BLACKSTONE")
                || name.equals("ANCIENT_DEBRIS") || name.equals("QUARTZ_BLOCK");
    }

    private static boolean isEndBlock(String name, Material material) {
        return name.contains("END_") || name.contains("PURPUR") || name.contains("CHORUS")
                || name.equals("DRAGON_EGG") || name.equals("DRAGON_HEAD");
    }

    private static String resolveTool(Material material) {
        var name = material.name();
        if (name.contains("PICKAXE")) return "pickaxes";
        if (name.contains("_AXE") && !name.contains("PICKAXE")) return "axes";
        if (name.contains("SHOVEL")) return "shovels";
        if (name.contains("HOE")) return "hoes";
        return "utility";
    }

    private static String resolveWeapon(Material material) {
        var name = material.name();
        if (name.contains("SWORD")) return "swords";
        if (name.contains("BOW") || name.contains("CROSSBOW")) return "ranged";
        return "special";
    }

    private static String resolveArmor(Material material) {
        var name = material.name();
        if (name.contains("HELMET") || name.equals("TURTLE_HELMET") || name.contains("SKULL")
                || name.contains("HEAD")) {
            return "head";
        }
        if (name.contains("CHESTPLATE")) return "chest";
        if (name.contains("LEGGINGS")) return "legs";
        if (name.contains("BOOTS")) return "feet";
        return "shield_elytra";
    }

    private static String resolveFood(Material material) {
        var name = material.name();
        if (name.startsWith("COOKED_") || name.contains("BAKED") || name.equals("DRIED_KELP")
                || name.equals("POPPED_CHORUS_FRUIT")) {
            return "cooked";
        }
        if (name.contains("RAW_") || name.equals("BEEF") || name.equals("PORKCHOP")
                || name.equals("CHICKEN") || name.equals("MUTTON") || name.equals("RABBIT")
                || name.equals("COD") || name.equals("SALMON") || name.equals("TROPICAL_FISH")
                || name.equals("POTATO") || name.equals("BEETROOT")) {
            return "raw";
        }
        return "snacks";
    }

    private static String resolvePotion(Material material) {
        return switch (material) {
            case SPLASH_POTION -> "splash";
            case LINGERING_POTION -> "lingering";
            default -> "potion";
        };
    }

    private static String resolveRedstone(Material material) {
        var name = material.name();
        if (name.contains("PISTON") || name.contains("OBSERVER") || name.contains("HOPPER")
                || name.contains("DROPPER") || name.contains("DISPENSER") || name.contains("DOOR")
                || name.contains("GATE") || name.contains("TRAPDOOR") && name.contains("IRON")) {
            return "mechanisms";
        }
        return "components";
    }

    private static String resolveTransport(Material material) {
        var name = material.name();
        if (name.contains("RAIL")) return "rails";
        if (name.contains("BOAT") || name.contains("RAFT")) return "boats";
        if (name.contains("MINECART") || name.equals("SADDLE")) return "minecarts";
        return "minecarts";
    }

    private static String resolveDecoration(Material material) {
        var name = material.name();
        if (name.contains("BANNER")) return "banners";
        if (name.contains("CANDLE")) return "candles";
        if (name.contains("POT") || name.contains("PAINTING") || name.contains("ITEM_FRAME")
                || name.contains("ARMOR_STAND") || name.contains("DECORATED")) {
            return "display";
        }
        return "display";
    }

    private static String resolveSpawnEgg(Material material) {
        var name = material.name().replace("_SPAWN_EGG", "");
        if (isPassiveMob(name)) return "passive";
        if (isBossMob(name)) return "boss";
        return "hostile";
    }

    private static boolean isPassiveMob(String name) {
        return switch (name) {
            case "PIG", "COW", "SHEEP", "CHICKEN", "RABBIT", "HORSE", "DONKEY", "MULE",
                 "LLAMA", "TRADER_LLAMA", "CAT", "OCELOT", "WOLF", "FOX", "BEE", "TURTLE",
                 "DOLPHIN", "SQUID", "GLOW_SQUID", "AXOLOTL", "GOAT", "FROG", "TADPOLE",
                 "CAMEL", "SNIFFER", "ARMADILLO", "PARROT", "PANDA", "POLAR_BEAR", "COD",
                 "SALMON", "TROPICAL_FISH", "PUFFERFISH", "VILLAGER", "WANDERING_TRADER",
                 "IRON_GOLEM", "SNOW_GOLEM", "ALLAY", "BAT", "MOOSHROOM", "STRIDER" -> true;
            default -> false;
        };
    }

    private static boolean isBossMob(String name) {
        return name.equals("WITHER") || name.equals("ENDER_DRAGON") || name.equals("WARDEN")
                || name.equals("ELDER_GUARDIAN");
    }

    private static String resolveMisc(Material material) {
        var name = material.name();
        if (name.contains("INGOT") || name.contains("NUGGET") || name.contains("GEM")
                || name.contains("DUST") || name.contains("PEARL") || name.contains("ROD")
                || name.contains("SCRAP") || name.contains("SHARD") || name.equals("COAL")
                || name.equals("CHARCOAL") || name.equals("FLINT") || name.equals("FEATHER")
                || name.equals("LEATHER") || name.equals("STRING") || name.equals("BONE")
                || name.equals("SLIME_BALL") || name.equals("GUNPOWDER") || name.equals("PAPER")
                || name.equals("BOOK") || name.equals("EMERALD") || name.equals("DIAMOND")
                || name.equals("NETHERITE_INGOT") || name.equals("NETHERITE_SCRAP")) {
            return "materials";
        }
        if (name.contains("POTION") || name.contains("BREW") || name.contains("BLAZE")
                || name.contains("FERMENTED") || name.contains("GHAST") || name.contains("MAGMA")
                || name.contains("NETHER_WART") || name.contains("DRAGON_BREATH")
                || name.contains("TURTLE") || name.contains("PHANTOM")) {
            return "brewing";
        }
        return "other";
    }

    /** 將路徑轉為 locale key 後綴，如 blocks/building/wood → blocks.building.wood */
    public static String toLocaleSuffix(String fullCategoryId) {
        return fullCategoryId.replace('/', '.').toLowerCase(Locale.ROOT);
    }
}
