package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;

/**
 * 依伺服器經濟匯率調整基準價：最終價 = 基準價 × multiply + add
 */
public final class ServerPriceExchange {

    private final ShopPlugin plugin;

    public ServerPriceExchange(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public double apply(double basePrice) {
        if (basePrice < 0) return basePrice;
        double multiply = plugin.getConfig().getDouble("shop.pricing.exchange.multiply", 1.0);
        double add = plugin.getConfig().getDouble("shop.pricing.exchange.add", 0.0);
        if (multiply == 1.0 && add == 0.0) return basePrice;
        return basePrice * multiply + add;
    }

    public double getMultiply() {
        return plugin.getConfig().getDouble("shop.pricing.exchange.multiply", 1.0);
    }

    public double getAdd() {
        return plugin.getConfig().getDouble("shop.pricing.exchange.add", 0.0);
    }
}
