package com.avery.shop.pricing;

/**
 * 單一物品類型的市場統計
 * <p>
 * buys / sells：計入動態定價的有效次數（漲停後的購買、跌停後的出售不計入）<br>
 * allBuys / allSells：全部交易次數（含漲停／跌停期間）
 */
public final class MarketData {

    public static final MarketData EMPTY = new MarketData(0, 0, 0, 0);

    private int totalBuys;
    private int totalSells;
    private int allBuys;
    private int allSells;

    public MarketData(int totalBuys, int totalSells) {
        this(totalBuys, totalSells, totalBuys, totalSells);
    }

    public MarketData(int totalBuys, int totalSells, int allBuys, int allSells) {
        this.totalBuys = totalBuys;
        this.totalSells = totalSells;
        this.allBuys = allBuys;
        this.allSells = allSells;
    }

    public int getTotalBuys() {
        return totalBuys;
    }

    public int getTotalSells() {
        return totalSells;
    }

    public int getAllBuys() {
        return allBuys;
    }

    public int getAllSells() {
        return allSells;
    }

    /** 記錄一筆購買（有效計價） */
    public void recordBuy() {
        totalBuys++;
    }

    /** 記錄一筆出售（有效計價） */
    public void recordSell() {
        totalSells++;
    }

    /** 記錄一筆購買（全部交易，含漲停期間） */
    public void recordAllBuy() {
        allBuys++;
    }

    /** 記錄一筆出售（全部交易，含跌停期間） */
    public void recordAllSell() {
        allSells++;
    }
}
