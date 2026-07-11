package com.avery.shop.catalog;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * 目錄項目 - 以唯一鍵區分同 Material 不同 NBT 的變體（藥水、附魔書等）
 */
public final class CatalogEntry {

    private final String key;
    private final ItemStack template;
    private final ItemCategory category;
    private final String materialId;
    private final String displayTag;

    public CatalogEntry(String key, ItemStack template, ItemCategory category, String materialId, String displayTag) {
        this.key = key;
        this.template = template.clone();
        this.category = category;
        this.materialId = materialId;
        this.displayTag = displayTag;
    }

    public String getKey() {
        return key;
    }

    public ItemStack getTemplate() {
        return template.clone();
    }

    public ItemCategory getCategory() {
        return category;
    }

    public String getMaterialId() {
        return materialId;
    }

    public String getDisplayTag() {
        return displayTag;
    }

    public boolean matches(ItemStack stack) {
        return ItemMatcher.matches(template, stack);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatalogEntry that)) return false;
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
