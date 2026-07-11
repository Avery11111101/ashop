package com.avery.shop.shop;

/**
 * 單一商品的商店設定（來自分類 items.yml）
 */
public final class ShopItemSetting {

    private final String catalogKey;
    private final String materialId;
    private final boolean enabled;
    private final double price;

    public ShopItemSetting(String catalogKey, String materialId, boolean enabled, double price) {
        this.catalogKey = catalogKey;
        this.materialId = materialId;
        this.enabled = enabled;
        this.price = price;
    }

    public String getCatalogKey() {
        return catalogKey;
    }

    public String getMaterialId() {
        return materialId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getPrice() {
        return price;
    }
}
