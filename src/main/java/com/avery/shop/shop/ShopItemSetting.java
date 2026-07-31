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
    /** 單一商品的交易模式（預設 BOTH） */
    private final TradeMode tradeMode;

    public ShopItemSetting(String catalogKey, String materialId, boolean enabled, double price) {
        this(catalogKey, materialId, enabled, price, null, TradeMode.BOTH);
    }

    public ShopItemSetting(String catalogKey, String materialId, boolean enabled, double price,
                           Double sellRatio) {
        this(catalogKey, materialId, enabled, price, sellRatio, TradeMode.BOTH);
    }

    public ShopItemSetting(String catalogKey, String materialId, boolean enabled, double price,
                           Double sellRatio, TradeMode tradeMode) {
        this.catalogKey = catalogKey;
        this.materialId = materialId;
        this.enabled = enabled;
        this.price = price;
        this.sellRatio = sellRatio;
        this.tradeMode = tradeMode != null ? tradeMode : TradeMode.BOTH;
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

    public TradeMode getTradeMode() {
        return tradeMode;
    }
}
