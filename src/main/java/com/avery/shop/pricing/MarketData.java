package com.avery.shop.pricing;

/**
 * 單一物品類型的市場統計
 */
public final class MarketData {

    public static final MarketData EMPTY = new MarketData(0, 0);

    private int totalBuys;
    private int totalSells;

    public MarketData(int totalBuys, int totalSells) {
        this.totalBuys = totalBuys;
        this.totalSells = totalSells;
    }

    public int getTotalBuys() {
        return totalBuys;
    }

    public int getTotalSells() {
        return totalSells;
    }

    public void recordBuy() {
        totalBuys++;
    }

    public void recordSell() {
        totalSells++;
    }
}
