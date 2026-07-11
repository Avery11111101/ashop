package com.avery.shop.shop;

import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCatalog;
import com.avery.shop.catalog.ItemCategory;
import com.avery.shop.catalog.ItemMatcher;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * 從 shop 設定檔欄位解析 CatalogEntry
 */
public final class ShopItemResolver {

    private ShopItemResolver() {}

    public static Optional<CatalogEntry> resolve(String catalogKey, String materialId, ItemCatalog catalog) {
        if (catalogKey != null && !catalogKey.isBlank()) {
            var byKey = catalog.getByKey(catalogKey);
            if (byKey != null) return Optional.of(byKey);
        }

        var material = parseMaterial(materialId);
        if (material == null) return Optional.empty();

        var stack = new ItemStack(material);
        var matched = catalog.findMatching(stack);
        if (matched != null) return Optional.of(matched);

        if (catalogKey != null && !catalogKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new CatalogEntry(
                ItemMatcher.fingerprint(stack),
                stack,
                ItemCategory.MISC,
                material.name().toLowerCase(Locale.ROOT),
                null
        ));
    }

    public static Material parseMaterial(String materialId) {
        if (materialId == null || materialId.isBlank()) return null;
        var normalized = materialId.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        var material = Material.matchMaterial(normalized);
        if (material != null) return material;
        return Material.matchMaterial(materialId);
    }
}
