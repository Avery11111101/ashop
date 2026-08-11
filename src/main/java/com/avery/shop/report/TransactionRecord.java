package com.avery.shop.report;

/**
 * 單筆商店交易紀錄（買/賣）
 */
public final class TransactionRecord {

    private final long timestamp;
    private final String type; // BUY_SYSTEM, SELL_SYSTEM, BUY_PLAYER, SELL_PLAYER
    private final String playerUuid;
    private final String playerName;
    private final String catalogKey;
    private final int amount;
    private final double totalPrice;

    public TransactionRecord(long timestamp, String type, String playerUuid, String playerName,
                             String catalogKey, int amount, double totalPrice) {
        this.timestamp = timestamp;
        this.type = type;
        this.playerUuid = playerUuid != null ? playerUuid : "";
        this.playerName = playerName != null ? playerName : "Unknown";
        this.catalogKey = catalogKey != null ? catalogKey : "";
        this.amount = Math.max(1, amount);
        this.totalPrice = Math.max(0.0, totalPrice);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getCatalogKey() {
        return catalogKey;
    }

    public int getAmount() {
        return amount;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public boolean isBuy() {
        return type != null && type.startsWith("BUY");
    }

    public boolean isSell() {
        return type != null && type.startsWith("SELL");
    }
}
