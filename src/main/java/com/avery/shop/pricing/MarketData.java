package com.avery.shop.pricing;

/**
 * 單一物品類型的市場統計
 * <p>
 * buys / sells：計入動態定價的有效次數（漲停後的購買、跌停後的出售不計入）<br>
 * allBuys / allSells：全部交易次數（含漲停／跌停期間）
 */
public final class MarketData {

    public static final MarketData EMPTY = new MarketData(0, 0, 0, 0);

    private double totalBuys;
    private double totalSells;
    private int allBuys;
    private int allSells;

    public MarketData(double totalBuys, double totalSells) {
        this(totalBuys, totalSells, (int) Math.round(totalBuys), (int) Math.round(totalSells));
    }

    public MarketData(double totalBuys, double totalSells, int allBuys, int allSells) {
        this.totalBuys = totalBuys;
        this.totalSells = totalSells;
        this.allBuys = allBuys;
        this.allSells = allSells;
    }

    public double getTotalBuys() {
        return totalBuys;
    }

    public double getTotalSells() {
        return totalSells;
    }

    public void setTotalBuys(double totalBuys) {
        this.totalBuys = Math.max(0.0, totalBuys);
    }

    public void setTotalSells(double totalSells) {
        this.totalSells = Math.max(0.0, totalSells);
    }

    public int getAllBuys() {
        return allBuys;
    }

    public int getAllSells() {
        return allSells;
    }

    /** 記錄一筆購買（有效計價） */
    public void recordBuy() {
        totalBuys += 1.0;
    }

    /** 記錄一筆出售（有效計價） */
    public void recordSell() {
        totalSells += 1.0;
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
