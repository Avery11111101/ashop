package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.SurvivalPriceModel;

/**
 * 依生存取得難度計算獨立基準購買價
 */
public final class ItemPriceCalculator {

    private final ShopPlugin plugin;

    public ItemPriceCalculator(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public double calculate(CatalogEntry entry) {
        return clampAndRound(SurvivalPriceModel.calculateBuyPrice(entry));
    }

    private double clampAndRound(double price) {
        var min = plugin.getConfig().getDouble("shop.pricing.min-price", 0.5);
        var max = plugin.getConfig().getDouble("shop.pricing.max-price", 100000.0);
        var step = plugin.getConfig().getDouble("shop.pricing.round-to", 0.5);
        price = Math.max(min, Math.min(max, price));
        if (step <= 0) return price;
        return Math.round(price / step) * step;
    }
}
