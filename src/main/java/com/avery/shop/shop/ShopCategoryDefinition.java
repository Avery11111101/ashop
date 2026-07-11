package com.avery.shop.shop;

import org.bukkit.Material;

/**
 * 由 shop/&lt;id&gt;/items.yml 定義的動態商店分類
 */
public final class ShopCategoryDefinition {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final boolean enabled;
    private final int slot;

    public ShopCategoryDefinition(String id, String displayName, Material icon, boolean enabled, int slot) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.enabled = enabled;
        this.slot = slot;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSlot() {
        return slot;
    }
}
