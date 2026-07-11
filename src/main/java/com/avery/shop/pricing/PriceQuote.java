package com.avery.shop.pricing;

/**
 * 動態報價結果
 */
public record PriceQuote(double price, double multiplier, double changePercent) {

    public String trendSymbol() {
        if (changePercent > 0.5) return "↑";
        if (changePercent < -0.5) return "↓";
        return "→";
    }
}
