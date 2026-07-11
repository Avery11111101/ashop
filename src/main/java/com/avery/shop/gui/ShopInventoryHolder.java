package com.avery.shop.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 標記 ashop GUI 容器，用於正確辨識是否仍在商店介面內
 */
public final class ShopInventoryHolder implements InventoryHolder {

    public enum Kind {
        MAIN, CATEGORY, SEARCH, LISTINGS, SELL, BUY_QUANTITY, ADMIN_ITEM, ADMIN_CATEGORY, ADMIN_SETTINGS
    }

    private final Kind kind;
    private Inventory inventory;

    public ShopInventoryHolder(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
