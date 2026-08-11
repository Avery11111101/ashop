package com.avery.shop.report;

import java.util.List;

/**
 * 報表數據統計結果模型 (包含物品熱度指數與自動趨勢分析短評)
 */
public final class ReportSummary {

    public enum ReportPeriod {
        DAILY("每日報表", "Daily Report", 86400000L),
        WEEKLY("每週報表", "Weekly Report", 7 * 86400000L),
        MONTHLY("每月報表", "Monthly Report", 30 * 86400000L);

        private final String displayNameZh;
        private final String displayNameEn;
        private final long durationMillis;

        ReportPeriod(String displayNameZh, String displayNameEn, long durationMillis) {
            this.displayNameZh = displayNameZh;
            this.displayNameEn = displayNameEn;
            this.durationMillis = durationMillis;
        }

        public String getDisplayNameZh() {
            return displayNameZh;
        }

        public String getDisplayNameEn() {
            return displayNameEn;
        }

        public long getDurationMillis() {
            return durationMillis;
        }

        public static ReportPeriod fromString(String input) {
            if (input == null) return DAILY;
            String lower = input.toLowerCase().trim();
            if (lower.contains("week") || lower.contains("週") || lower.contains("周")) return WEEKLY;
            if (lower.contains("month") || lower.contains("月")) return MONTHLY;
            return DAILY;
        }
    }

    public record ItemStat(String catalogKey, String displayName, int totalQuantity, double totalAmount, int buyCount, int sellCount) {}
    public record PlayerStat(String playerName, int totalTransactions, double totalSpent, double totalEarned) {}

    /**
     * 物品交易熱度與自動分析短評數據
     */
    public record ItemTrendAnalysis(
            String catalogKey,
            String displayName,
            int totalQuantity,
            double totalAmount,
            int buyCount,
            int sellCount,
            double buyRatioPercent,
            double priceMultiplier,
            int popularityScore,
            String trendTag,
            String insightComment
    ) {}

    private final ReportPeriod period;
    private final long startTime;
    private final long endTime;

    private final double totalRevenue; // 總營業額 (金額)
    private final double totalSystemBuyRevenue; // 玩家買入總額
    private final double totalSystemSellPayout; // 系統收購總支出
    private final int totalItemsTraded; // 總件數
    private final int totalTransactionsCount; // 總筆數
    private final int activePlayersCount; // 參與玩家數

    private final List<ItemStat> topItems;
    private final List<PlayerStat> topPlayers;
    private final List<ItemTrendAnalysis> trendAnalyses;

    public ReportSummary(ReportPeriod period, long startTime, long endTime, double totalRevenue,
                         double totalSystemBuyRevenue, double totalSystemSellPayout,
                         int totalItemsTraded, int totalTransactionsCount, int activePlayersCount,
                         List<ItemStat> topItems, List<PlayerStat> topPlayers) {
        this(period, startTime, endTime, totalRevenue, totalSystemBuyRevenue, totalSystemSellPayout,
                totalItemsTraded, totalTransactionsCount, activePlayersCount, topItems, topPlayers, List.of());
    }

    public ReportSummary(ReportPeriod period, long startTime, long endTime, double totalRevenue,
                         double totalSystemBuyRevenue, double totalSystemSellPayout,
                         int totalItemsTraded, int totalTransactionsCount, int activePlayersCount,
                         List<ItemStat> topItems, List<PlayerStat> topPlayers,
                         List<ItemTrendAnalysis> trendAnalyses) {
        this.period = period;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalRevenue = totalRevenue;
        this.totalSystemBuyRevenue = totalSystemBuyRevenue;
        this.totalSystemSellPayout = totalSystemSellPayout;
        this.totalItemsTraded = totalItemsTraded;
        this.totalTransactionsCount = totalTransactionsCount;
        this.activePlayersCount = activePlayersCount;
        this.topItems = topItems != null ? topItems : List.of();
        this.topPlayers = topPlayers != null ? topPlayers : List.of();
        this.trendAnalyses = trendAnalyses != null ? trendAnalyses : List.of();
    }

    public ReportPeriod getPeriod() {
        return period;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public double getTotalSystemBuyRevenue() {
        return totalSystemBuyRevenue;
    }

    public double getTotalSystemSellPayout() {
        return totalSystemSellPayout;
    }

    public int getTotalItemsTraded() {
        return totalItemsTraded;
    }

    public int getTotalTransactionsCount() {
        return totalTransactionsCount;
    }

    public int getActivePlayersCount() {
        return activePlayersCount;
    }

    public List<ItemStat> getTopItems() {
        return topItems;
    }

    public List<PlayerStat> getTopPlayers() {
        return topPlayers;
    }

    public List<ItemTrendAnalysis> getTrendAnalyses() {
        return trendAnalyses;
    }
}
