package com.avery.shop.shop;

import com.avery.shop.catalog.CatalogEntry;

/**
 * 玩家手持物品對應的商店設定
 */
public record ResolvedShopItem(
        CatalogEntry entry,
        ShopItemSetting setting,
        String categoryId
) {}
