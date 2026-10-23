package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.catalog.ItemCategory;
import com.avery.shop.catalog.SurvivalPriceModel;
import org.bukkit.Material;

/**
 * 依生存取得難度與標準工時價值計算商品出售價與收購價
 */
public final class ItemPriceCalculator {

    private final ShopPlugin plugin;

    public ItemPriceCalculator(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public double getHourlyWage() {
        return plugin.getConfig().getDouble("shop.pricing.labor-economy.hourly-wage", 1000.0);
    }

    public boolean isVeinMiner() {
        return plugin.getConfig().getBoolean("shop.pricing.labor-economy.vein-miner", false);
    }

    public SurvivalPriceModel.ItemPriceResult calculateResult(CatalogEntry entry) {
        var res = SurvivalPriceModel.calculatePrice(entry, getHourlyWage(), isVeinMiner());
        return new SurvivalPriceModel.ItemPriceResult(
                clampAndRound(res.systemSellPrice()),
                clampAndRound(res.systemBuyPrice()),
                res.sellRatio()
        );
    }

    public SurvivalPriceModel.ItemPriceResult calculateResult(Material material, ItemCategory category) {
        var res = SurvivalPriceModel.calculatePrice(material, category, getHourlyWage(), isVeinMiner());
        return new SurvivalPriceModel.ItemPriceResult(
                clampAndRound(res.systemSellPrice()),
                clampAndRound(res.systemBuyPrice()),
                res.sellRatio()
        );
    }

    public double calculate(CatalogEntry entry) {
        return calculateResult(entry).systemSellPrice();
    }

    public double calculateSellPrice(CatalogEntry entry) {
        return calculateResult(entry).systemBuyPrice();
    }

    private double clampAndRound(double price) {
        var min = plugin.getConfig().getDouble("shop.pricing.min-price", 0.01);
        var max = plugin.getConfig().getDouble("shop.pricing.max-price", 100000.0);
        var step = plugin.getConfig().getDouble("shop.pricing.round-to", 0.01);
        price = Math.max(min, Math.min(max, price));
        if (step <= 0) return price;
        return Math.round(price / step) * step;
    }
}
