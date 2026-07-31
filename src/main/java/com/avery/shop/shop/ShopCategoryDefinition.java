package com.avery.shop.shop;

import org.bukkit.Material;

/**
 * 由 shop/&lt;路徑&gt;/items.yml 定義的動態商店分類（支援巢狀子分類）
 */
public final class ShopCategoryDefinition {

    private final String id;
    private final String parentId;
    private final String displayName;
    private final Material icon;
    private final boolean enabled;
    private final TradeMode tradeMode;
    private final int slot;

    public ShopCategoryDefinition(String id, String parentId, String displayName,
                                  Material icon, boolean enabled, TradeMode tradeMode, int slot) {
        this.id = id;
        this.parentId = parentId;
        this.displayName = displayName;
        this.icon = icon;
        this.enabled = enabled;
        this.tradeMode = tradeMode != null ? tradeMode : TradeMode.BOTH;
        this.slot = slot;
    }

    public ShopCategoryDefinition(String id, String parentId, String displayName,
                                  Material icon, boolean enabled, boolean allowBuy, int slot) {
        this(id, parentId, displayName, icon, enabled, allowBuy ? TradeMode.BOTH : TradeMode.SELL_ONLY, slot);
    }

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public boolean isRoot() {
        return parentId == null;
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

    /** 此節點自身交易模式設定 */
    public TradeMode getTradeMode() {
        return tradeMode;
    }

    /** 相容舊版判定：此節點自身是否允許購買 */
    public boolean isAllowBuy() {
        return tradeMode.allowsBuy();
    }

    public int getSlot() {
        return slot;
    }
}
