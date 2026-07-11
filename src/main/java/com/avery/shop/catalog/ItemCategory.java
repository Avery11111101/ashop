package com.avery.shop.catalog;

import org.bukkit.Material;

/**
 * 物品分類列舉（顯示名稱由語系檔 category.* 提供）
 */
public enum ItemCategory {
    BLOCKS("blocks", Material.GRASS_BLOCK),
    TOOLS("tools", Material.IRON_PICKAXE),
    WEAPONS("weapons", Material.IRON_SWORD),
    ARMOR("armor", Material.IRON_CHESTPLATE),
    FOOD("food", Material.COOKED_BEEF),
    POTIONS("potions", Material.POTION),
    ENCHANTED_BOOKS("enchanted_books", Material.ENCHANTED_BOOK),
    REDSTONE("redstone", Material.REDSTONE),
    TRANSPORT("transport", Material.MINECART),
    DECORATIONS("decorations", Material.FLOWER_POT),
    SPAWN_EGGS("spawn_eggs", Material.ZOMBIE_SPAWN_EGG),
    MISC("misc", Material.CHEST);

    private final String id;
    private final Material icon;

    ItemCategory(String id, Material icon) {
        this.id = id;
        this.icon = icon;
    }

    public String getId() {
        return id;
    }

    public Material getIcon() {
        return icon;
    }

    public static ItemCategory fromId(String id) {
        for (var cat : values()) {
            if (cat.id.equalsIgnoreCase(id)) {
                return cat;
            }
        }
        return null;
    }
}
