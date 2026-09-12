package com.avery.shop.catalog;

import com.avery.shop.ShopPlugin;
import com.avery.shop.locale.LocaleService;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 原版全物品目錄 - 含藥水、附魔書等變體
 */
public final class ItemCatalog {

    private final ShopPlugin plugin;
    private final LocaleService locale;
    private final Map<String, CatalogEntry> entries = new LinkedHashMap<>();
    private final Map<ItemCategory, List<CatalogEntry>> byCategory = new EnumMap<>(ItemCategory.class);

    public ItemCatalog(ShopPlugin plugin, LocaleService locale) {
        this.plugin = plugin;
        this.locale = locale;
        for (var cat : ItemCategory.values()) {
            byCategory.put(cat, new ArrayList<>());
        }
    }

    public void build() {
        entries.clear();
        for (var cat : ItemCategory.values()) {
            byCategory.get(cat).clear();
        }

        for (var material : Material.values()) {
            if (!material.isItem() || material.isLegacy()) continue;
            if (!isCategoryEnabled(material)) continue;

            var category = categorize(material);
            if (category == ItemCategory.POTIONS && isPotionMaterial(material)) {
                addPotionVariants(material, category);
            } else if (material == Material.ENCHANTED_BOOK) {
                addEnchantedBookVariants(category);
            } else {
                addEntry(material, category, null, null);
            }
        }

        plugin.getLogger().info("物品目錄建置完成：" + entries.size() + " 項");
    }

    private boolean isCategoryEnabled(Material material) {
        // 目錄僅供物品比對與搜尋，商店內容完全由 shop/ 資料夾決定
        return true;
    }

    private void addEntry(Material material, ItemCategory category, ItemStack customStack, String displayTag) {
        ItemStack stack = customStack != null ? customStack : new ItemStack(material);
        String key = ItemMatcher.fingerprint(stack);
        String materialId = locale.getMaterialId(material);

        var entry = new CatalogEntry(key, stack, category, materialId, displayTag);
        entries.putIfAbsent(key, entry);
        byCategory.get(category).add(entry);
    }

    private void addPotionVariants(Material material, ItemCategory category) {
        for (var potionType : PotionType.values()) {
            if (potionType == PotionType.WATER) continue;

            var stack = new ItemStack(material);
            var meta = (PotionMeta) stack.getItemMeta();
            meta.setBasePotionType(potionType);
            stack.setItemMeta(meta);

            String tag = potionType.name().toLowerCase(Locale.ROOT);
            addEntry(material, category, stack, tag);
        }
    }

    private void addEnchantedBookVariants(ItemCategory category) {
        var registry = plugin.getServer().getRegistry(Enchantment.class);
        for (var enchant : registry) {
            int maxLevel = enchant.getMaxLevel();
            for (int level = 1; level <= maxLevel; level++) {
                var stack = new ItemStack(Material.ENCHANTED_BOOK);
                var meta = (EnchantmentStorageMeta) stack.getItemMeta();
                meta.addStoredEnchant(enchant, level, true);
                stack.setItemMeta(meta);

                String tag = enchant.getKey().getKey() + ":" + level;
                addEntry(Material.ENCHANTED_BOOK, category, stack, tag);
            }
        }
    }

    private static boolean isPotionMaterial(Material material) {
        return material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION;
    }

    public ItemCategory categorize(Material material) {
        var name = material.name();

        if (name.endsWith("_SPAWN_EGG")) return ItemCategory.SPAWN_EGGS;
        if (isPotionMaterial(material)) return ItemCategory.POTIONS;
        if (material == Material.ENCHANTED_BOOK) return ItemCategory.ENCHANTED_BOOKS;

        // 1. 礦物 (粗礦物) — 純挖掘產物
        if (isMineral(name, material)) return ItemCategory.MINERALS;

        // 2. 原木 — 純原木林木
        if (isLog(name, material)) return ItemCategory.LOGS;

        // 3. 石頭 (變種方塊不要) — 純原石岩石
        if (isPureStone(name, material)) return ItemCategory.STONES;

        // 4. 生肉 — 生鮮肉類與魚產（在 isEdible 前判定，避免歸入熟食）
        if (isRawMeat(name, material)) return ItemCategory.RAW_MEAT;

        // 5. 農作物 — 耕種收成與種子（在 isEdible 前判定，避免胡蘿蔔/蘋果等歸入熟食）
        if (isCrop(name, material)) return ItemCategory.CROPS;

        // 食物（熟食、烹飪料理、點心等）
        if (isEdibleSafe(name, material)) return ItemCategory.FOOD;

        if (name.contains("SWORD") || name.contains("BOW") || name.contains("CROSSBOW")
                || name.contains("TRIDENT") || name.contains("MACE") || name.contains("ARROW")
                || name.equals("WIND_CHARGE")) return ItemCategory.WEAPONS;
        if (name.contains("HELMET") || name.contains("CHESTPLATE")
                || name.contains("LEGGINGS") || name.contains("BOOTS")
                || name.contains("SHIELD") || name.contains("ELYTRA")
                || name.contains("HORSE_ARMOR") || name.equals("WOLF_ARMOR")) return ItemCategory.ARMOR;
        if (name.contains("PICKAXE") || name.contains("AXE") || name.contains("SHOVEL")
                || name.contains("HOE") || name.contains("SHEARS")
                || name.contains("FISHING_ROD") || name.contains("FLINT_AND_STEEL")
                || name.equals("BRUSH") || name.equals("SPYGLASS") || name.equals("COMPASS")
                || name.equals("RECOVERY_COMPASS") || name.equals("CLOCK") || name.equals("LEAD")) return ItemCategory.TOOLS;
        if (name.contains("RAIL") || name.contains("MINECART") || name.contains("BOAT")
                || name.contains("CHEST_BOAT") || name.contains("RAFT") || material == Material.SADDLE) return ItemCategory.TRANSPORT;
        if (name.contains("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR")
                || name.contains("PISTON") || name.contains("OBSERVER")
                || name.contains("HOPPER") || name.contains("DROPPER") || name.contains("DISPENSER")
                || name.contains("LEVER") || name.contains("DAYLIGHT_DETECTOR") || name.contains("TRIPWIRE_HOOK")
                || name.contains("TARGET") || name.contains("LIGHTNING_ROD") || name.equals("CRAFTER")
                || name.contains("SCULK_SENSOR"))
            return ItemCategory.REDSTONE;
        if (name.contains("BANNER") || name.contains("CANDLE") || name.contains("FLOWER")
                || name.contains("POT") || name.contains("PAINTING") || name.contains("ITEM_FRAME")
                || name.contains("ARMOR_STAND") || name.contains("DECORATED_POT")
                || name.contains("HEAD") || name.contains("SKULL") || name.contains("TORCH")
                || name.contains("LANTERN") || name.contains("CAMPFIRE"))
            return ItemCategory.DECORATIONS;
        if (isBlockSafe(name, material)) return ItemCategory.BLOCKS;

        return ItemCategory.MISC;
    }

    private static boolean isEdibleSafe(String name, Material material) {
        try {
            return material.isEdible();
        } catch (Throwable ignored) {
            return name.startsWith("COOKED_") || name.contains("BAKED") || name.equals("BREAD")
                    || name.equals("COOKIE") || name.equals("CAKE") || name.equals("PUMPKIN_PIE")
                    || name.contains("STEW") || name.contains("SOUP") || name.equals("DRIED_KELP")
                    || name.equals("HONEY_BOTTLE") || name.equals("GOLDEN_CARROT")
                    || name.equals("ROTTEN_FLESH") || name.equals("SPIDER_EYE");
        }
    }

    private static boolean isBlockSafe(String name, Material material) {
        try {
            return material.isBlock();
        } catch (Throwable ignored) {
            return name.contains("PLANKS") || name.contains("STAIRS") || name.contains("SLAB")
                    || name.contains("WALL") || name.contains("BRICK") || name.contains("TERRACOTTA")
                    || name.contains("CONCRETE") || name.contains("WOOL") || name.contains("CARPET")
                    || name.contains("GLASS") || name.contains("COPPER") || name.endsWith("_BLOCK")
                    || name.endsWith("_LEAVES") || name.equals("DIRT") || name.equals("SAND")
                    || name.equals("GRAVEL");
        }
    }

    public static boolean isMineral(String name, Material material) {
        // 1. 絲綢鎬採集之原礦方塊與地質簇
        if (name.endsWith("_ORE") || name.contains("_ORE_")) {
            return true;
        }
        if (name.equals("ANCIENT_DEBRIS") || name.equals("GILDED_BLACKSTONE")
                || name.equals("AMETHYST_CLUSTER") || name.contains("AMETHYST_BUD")) {
            return true;
        }

        // 2. 無絲綢鎬單純挖掘掉落之粗礦（排除 RAW_*_BLOCK 合成方塊）
        if ((name.startsWith("RAW_") || name.contains("RAW_")) && !name.endsWith("_BLOCK")) {
            return true;
        }

        // 3. 無絲綢鎬單純挖掘掉落之寶石與礦產（排除木炭 CHARCOAL、獄髓碎屑 NETHERITE_SCRAP、方塊等加工品）
        return name.equals("COAL")
                || name.equals("DIAMOND")
                || name.equals("EMERALD")
                || name.equals("LAPIS_LAZULI")
                || name.equals("REDSTONE")
                || name.equals("QUARTZ")
                || name.equals("AMETHYST_SHARD")
                || name.equals("FLINT");
    }

    public static boolean isLog(String name, Material material) {
        return name.contains("_LOG") || name.contains("_STEM")
                || (name.endsWith("_WOOD") && !name.contains("PLANKS"))
                || (name.endsWith("_HYPHAE") && !name.contains("PLANKS"));
    }

    public static boolean isPureStone(String name, Material material) {
        if (name.contains("STAIR") || name.contains("SLAB") || name.contains("WALL")
                || name.contains("BRICK") || name.contains("PILLAR") || name.contains("MOSSY")
                || name.contains("CHISELED") || name.contains("POLISHED") || name.contains("CUT_")
                || name.contains("TILE") || name.contains("INFESTED") || name.contains("BUTTON")
                || name.contains("PRESSURE_PLATE")) {
            return false;
        }
        return name.equals("STONE") || name.equals("COBBLESTONE") || name.equals("SMOOTH_STONE")
                || name.equals("DEEPSLATE") || name.equals("COBBLED_DEEPSLATE")
                || name.equals("GRANITE") || name.equals("DIORITE") || name.equals("ANDESITE")
                || name.equals("BASALT") || name.equals("BLACKSTONE") || name.equals("END_STONE")
                || name.equals("NETHERRACK") || name.equals("SANDSTONE") || name.equals("RED_SANDSTONE")
                || name.equals("TUFF") || name.equals("CALCITE") || name.equals("DRIPSTONE_BLOCK")
                || name.equals("OBSIDIAN") || name.equals("CRYING_OBSIDIAN");
    }

    public static boolean isRawMeat(String name, Material material) {
        return material == Material.BEEF
                || material == Material.PORKCHOP
                || material == Material.CHICKEN
                || material == Material.MUTTON
                || material == Material.RABBIT
                || material == Material.COD
                || material == Material.SALMON
                || material == Material.TROPICAL_FISH
                || material == Material.PUFFERFISH;
    }

    public static boolean isCrop(String name, Material material) {
        return name.equals("WHEAT") || name.equals("CARROT") || name.equals("CARROTS")
                || name.equals("POTATO") || name.equals("POTATOES") || name.equals("BEETROOT")
                || name.equals("BEETROOTS") || name.equals("SUGAR_CANE") || name.equals("PUMPKIN")
                || name.equals("MELON_SLICE") || name.equals("MELON") || name.equals("COCOA_BEANS")
                || name.equals("NETHER_WART") || name.equals("SWEET_BERRIES") || name.equals("GLOW_BERRIES")
                || name.equals("CACTUS") || name.equals("BAMBOO") || name.equals("APPLE")
                || name.equals("GOLDEN_APPLE") || name.equals("ENCHANTED_GOLDEN_APPLE")
                || name.equals("CHORUS_FRUIT") || name.contains("SEEDS")
                || name.equals("PITCHER_POD") || name.equals("HAY_BLOCK")
                || name.equals("TORCHFLOWER_SEEDS");
    }

    public void registerCustomEntry(CatalogEntry entry) {
        if (entry == null) return;
        entries.put(entry.getKey(), entry);
        byCategory.computeIfAbsent(entry.getCategory(), k -> new ArrayList<>()).add(entry);
    }

    public List<CatalogEntry> getByCategory(ItemCategory category) {
        return Collections.unmodifiableList(byCategory.getOrDefault(category, List.of()));
    }

    public Collection<CatalogEntry> getAll() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public CatalogEntry getByKey(String key) {
        return entries.get(key);
    }

    public CatalogEntry findMatching(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        return entries.get(com.avery.shop.catalog.ItemMatcher.fingerprint(stack));
    }

    /**
     * 以物品 ID 或本地化名稱搜尋（依玩家語系）
     */
    public List<CatalogEntry> search(org.bukkit.entity.Player player, String query) {
        return search(locale.getPlayerLocale(player), query);
    }

    public List<CatalogEntry> search(String playerLocale, String query) {
        if (query == null || query.isBlank()) return List.of();

        var q = query.toLowerCase(Locale.ROOT).trim();
        int maxResults = plugin.getConfig().getInt("search.max-results", 100);

        return entries.values().stream()
                .filter(entry -> matchesSearch(playerLocale, entry, q))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private boolean matchesSearch(String playerLocale, CatalogEntry entry, String query) {
        var mat = entry.getTemplate().getType();
        var texts = locale.getSearchableTexts(playerLocale, mat, entry.getDisplayTag());

        for (var text : texts) {
            if (text != null && text.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }

        // 也搜尋 catalog key
        return entry.getKey().contains(query);
    }

    public int size() {
        return entries.size();
    }
}
