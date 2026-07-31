package com.avery.shop.shop;

import java.util.Locale;

/**
 * 商店交易模式
 */
public enum TradeMode {
    /** 買賣皆可 (預設) */
    BOTH("買賣皆可", "Both"),
    /** 只賣不收（玩家僅可購買，商店不收購） */
    BUY_ONLY("只賣不收", "Buy Only"),
    /** 只收不賣（玩家僅可賣給商店，商店不售出） */
    SELL_ONLY("只收不賣", "Sell Only"),
    /** 禁用交易（顯示在商店，但暫不開放買賣） */
    DISABLED("禁用交易", "Disabled");

    private final String displayNameZh;
    private final String displayNameEn;

    TradeMode(String displayNameZh, String displayNameEn) {
        this.displayNameZh = displayNameZh;
        this.displayNameEn = displayNameEn;
    }

    public String getDisplayName(String locale) {
        if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en")) {
            return displayNameEn;
        }
        return displayNameZh;
    }

    public boolean allowsBuy() {
        return this == BOTH || this == BUY_ONLY;
    }

    public boolean allowsSell() {
        return this == BOTH || this == SELL_ONLY;
    }

    public TradeMode next() {
        return switch (this) {
            case BOTH -> BUY_ONLY;
            case BUY_ONLY -> SELL_ONLY;
            case SELL_ONLY -> DISABLED;
            case DISABLED -> BOTH;
        };
    }

    public static TradeMode parse(String raw, TradeMode fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return TradeMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
