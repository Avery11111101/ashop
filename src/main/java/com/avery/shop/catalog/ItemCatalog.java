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

        if (material.isEdible()) return ItemCategory.FOOD;
        if (name.contains("SWORD") || name.contains("BOW") || name.contains("CROSSBOW")
                || name.contains("TRIDENT") || name.contains("MACE")) return ItemCategory.WEAPONS;
        if (name.contains("HELMET") || name.contains("CHESTPLATE")
                || name.contains("LEGGINGS") || name.contains("BOOTS")
                || name.contains("SHIELD") || name.contains("ELYTRA")) return ItemCategory.ARMOR;
        if (name.contains("PICKAXE") || name.contains("AXE") || name.contains("SHOVEL")
                || name.contains("HOE") || name.contains("SHEARS")
                || name.contains("FISHING_ROD") || name.contains("FLINT_AND_STEEL")) return ItemCategory.TOOLS;
        if (name.contains("RAIL") || name.contains("MINECART") || name.contains("BOAT")
                || name.contains("CHEST_BOAT") || material == Material.SADDLE) return ItemCategory.TRANSPORT;
        if (name.contains("REDSTONE") || name.contains("REPEATER") || name.contains("COMPARATOR")
                || name.contains("PISTON") || name.contains("OBSERVER")
                || name.contains("HOPPER") || name.contains("DROPPER") || name.contains("DISPENSER"))
            return ItemCategory.REDSTONE;
        if (name.contains("BANNER") || name.contains("CANDLE") || name.contains("FLOWER")
                || name.contains("POT") || name.contains("PAINTING") || name.contains("ITEM_FRAME")
                || name.contains("ARMOR_STAND") || name.contains("DECORATED_POT"))
            return ItemCategory.DECORATIONS;
        if (isMineralOrOre(name, material)) return ItemCategory.BLOCKS;
        if (material.isBlock()) return ItemCategory.BLOCKS;

        return ItemCategory.MISC;
    }

    private static boolean isMineralOrOre(String name, Material material) {
        if (name.endsWith("_ORE") || name.contains("_ORE_")) return false;

        return name.startsWith("RAW_") || name.contains("RAW_")
                || name.contains("INGOT") || name.contains("NUGGET")
                || name.equals("COAL") || name.equals("CHARCOAL")
                || name.equals("DIAMOND") || name.equals("EMERALD")
                || name.equals("LAPIS_LAZULI") || name.equals("REDSTONE")
                || name.equals("QUARTZ") || name.equals("NETHERITE_SCRAP")
                || name.equals("ANCIENT_DEBRIS") || name.equals("AMETHYST_SHARD")
                || name.equals("AMETHYST_BLOCK") || name.equals("BUDDING_AMETHYST")
                || name.equals("FLINT");
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
