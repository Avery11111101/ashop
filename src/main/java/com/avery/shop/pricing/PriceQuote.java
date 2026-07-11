package com.avery.shop.pricing;

/**
 * 動態報價結果
 */
public record PriceQuote(double price, double multiplier, double changePercent) {

    private static final PriceQuote UNAVAILABLE = new PriceQuote(-1, 0, 0);

    public static PriceQuote unavailable() {
        return UNAVAILABLE;
    }

    public boolean available() {
        return price >= 0;
    }

    public String trendSymbol() {
        if (changePercent > 0.5) return "↑";
        if (changePercent < -0.5) return "↓";
        return "→";
    }
}
