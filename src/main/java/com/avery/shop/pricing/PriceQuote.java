package com.avery.shop.pricing;

/**
 * 動態報價結果
 */
public record PriceQuote(double price, double multiplier, double changePercent, PriceCap cap) {

    public enum PriceCap {
        NONE, MAX, MIN
    }

    private static final PriceQuote UNAVAILABLE = new PriceQuote(-1, 0, 0, PriceCap.NONE);

    public PriceQuote(double price, double multiplier, double changePercent) {
        this(price, multiplier, changePercent, PriceCap.NONE);
    }

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

    /** 趨勢文字，例如 ↑+20% 或 ↑+400% · 已達上限 */
    public String formatTrend(com.avery.shop.locale.LocaleService locale,
                              org.bukkit.entity.Player player) {
        var trend = trendSymbol() + String.format("%+.0f", changePercent()) + "%";
        return switch (cap) {
            case MAX -> trend + " · " + locale.msg(player, "msg.price.cap-max");
            case MIN -> trend + " · " + locale.msg(player, "msg.price.cap-min");
            case NONE -> trend;
        };
    }
}
