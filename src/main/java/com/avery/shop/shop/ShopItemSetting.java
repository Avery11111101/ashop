package com.avery.shop.shop;

/**
 * 單一商品的商店設定（來自分類 items.yml）
 */
public final class ShopItemSetting {

    private final String catalogKey;
    private final String materialId;
    private final boolean enabled;
    private final double price;
  /** 覆寫全域收購比例；null 表示使用 config 預設 */
    private final Double sellRatio;

    public ShopItemSetting(String catalogKey, String materialId, boolean enabled, double price) {
        this(catalogKey, materialId, enabled, price, null);
    }

    public ShopItemSetting(String catalogKey, String materialId, boolean enabled, double price,
                           Double sellRatio) {
        this.catalogKey = catalogKey;
        this.materialId = materialId;
        this.enabled = enabled;
        this.price = price;
        this.sellRatio = sellRatio;
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

    public Double getSellRatio() {
        return sellRatio;
    }
}
