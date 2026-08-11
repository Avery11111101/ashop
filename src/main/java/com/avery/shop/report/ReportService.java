package com.avery.shop.report;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 商店交易紀錄與報表數據統計服務
 */
public final class ReportService {

    private final ShopPlugin plugin;
    private final File dataFile;
    private final List<TransactionRecord> history = new ArrayList<>();
    private final Queue<TransactionRecord> pendingBuffer = new ConcurrentLinkedQueue<>();
    private final Object lock = new Object();
    private boolean isDirty = false;

    public ReportService(ShopPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "transactions.yml");
    }

    public void load() {
        synchronized (lock) {
            history.clear();
            if (!dataFile.exists()) return;
            var config = YamlConfiguration.loadConfiguration(dataFile);
            var list = config.getMapList("transactions");
            for (var map : list) {
                try {
                    long timestamp = map.containsKey("timestamp") && map.get("timestamp") != null ? ((Number) map.get("timestamp")).longValue() : 0L;
                    String type = map.containsKey("type") && map.get("type") != null ? map.get("type").toString() : "BUY_SYSTEM";
                    String playerUuid = map.containsKey("playerUuid") && map.get("playerUuid") != null ? map.get("playerUuid").toString() : "";
                    String playerName = map.containsKey("playerName") && map.get("playerName") != null ? map.get("playerName").toString() : "Unknown";
                    String catalogKey = map.containsKey("catalogKey") && map.get("catalogKey") != null ? map.get("catalogKey").toString() : "";
                    int amount = map.containsKey("amount") && map.get("amount") != null ? ((Number) map.get("amount")).intValue() : 1;
                    double totalPrice = map.containsKey("totalPrice") && map.get("totalPrice") != null ? ((Number) map.get("totalPrice")).doubleValue() : 0.0;

                    history.add(new TransactionRecord(timestamp, type, playerUuid, playerName, catalogKey, amount, totalPrice));
                } catch (Exception ignored) {}
            }
            cleanOldRecords(90);
        }
    }

    public void save() {
        flushBuffer();
        synchronized (lock) {
            if (!isDirty) return;
            try {
                var config = new YamlConfiguration();
                List<Map<String, Object>> serializedList = new ArrayList<>(history.size());
                for (var rec : history) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("timestamp", rec.getTimestamp());
                    map.put("type", rec.getType());
                    map.put("playerUuid", rec.getPlayerUuid());
                    map.put("playerName", rec.getPlayerName());
                    map.put("catalogKey", rec.getCatalogKey());
                    map.put("amount", rec.getAmount());
                    map.put("totalPrice", rec.getTotalPrice());
                    serializedList.add(map);
                }
                config.set("transactions", serializedList);
                config.save(dataFile);
                isDirty = false;
            } catch (Exception e) {
                plugin.getLogger().warning("無法儲存交易紀錄檔 (transactions.yml): " + e.getMessage());
            }
        }
    }

    public void recordTransaction(String type, String playerUuid, String playerName,
                                  String catalogKey, int amount, double totalPrice) {
        TransactionRecord record = new TransactionRecord(
                System.currentTimeMillis(), type, playerUuid, playerName, catalogKey, amount, totalPrice
        );
        pendingBuffer.add(record);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::flushBufferAndSaveLater);
    }

    private void flushBuffer() {
        if (pendingBuffer.isEmpty()) return;
        synchronized (lock) {
            TransactionRecord item;
            while ((item = pendingBuffer.poll()) != null) {
                history.add(item);
                isDirty = true;
            }
        }
    }

    private void flushBufferAndSaveLater() {
        flushBuffer();
        save();
    }

    public void cleanOldRecords(int keepDays) {
        long cutoff = System.currentTimeMillis() - (keepDays * 86400000L);
        synchronized (lock) {
            int before = history.size();
            history.removeIf(rec -> rec.getTimestamp() < cutoff);
            if (history.size() != before) {
                isDirty = true;
            }
        }
    }

    public ReportSummary generateReport(ReportSummary.ReportPeriod period) {
        long now = System.currentTimeMillis();
        long startTime = now - period.getDurationMillis();
        return generateReport(period, startTime, now);
    }

    public ReportSummary generateReport(ReportSummary.ReportPeriod period, long startTime, long endTime) {
        flushBuffer();
        List<TransactionRecord> targetRecords;
        synchronized (lock) {
            targetRecords = history.stream()
                    .filter(r -> r.getTimestamp() >= startTime && r.getTimestamp() <= endTime)
                    .collect(Collectors.toList());
        }

        double totalBuyRev = 0.0;
        double totalSellPayout = 0.0;
        int totalItems = 0;
        Set<String> players = new HashSet<>();

        Map<String, ItemStatAccumulator> itemMap = new HashMap<>();
        Map<String, PlayerStatAccumulator> playerMap = new HashMap<>();

        for (var rec : targetRecords) {
            totalItems += rec.getAmount();
            if (rec.getPlayerName() != null && !rec.getPlayerName().isEmpty()) {
                players.add(rec.getPlayerName());
            }

            if (rec.isBuy()) {
                totalBuyRev += rec.getTotalPrice();
            } else if (rec.isSell()) {
                totalSellPayout += rec.getTotalPrice();
            }

            // 統計物品
            itemMap.computeIfAbsent(rec.getCatalogKey(), k -> new ItemStatAccumulator(k))
                    .add(rec.getAmount(), rec.getTotalPrice(), rec.isBuy());

            // 統計玩家
            playerMap.computeIfAbsent(rec.getPlayerName(), k -> new PlayerStatAccumulator(k))
                    .add(rec.getTotalPrice(), rec.isBuy());
        }

        double totalRevenue = totalBuyRev + totalSellPayout;

        // 計算熱門商品 (依總交易金額與數量排序)
        List<ReportSummary.ItemStat> topItems = itemMap.values().stream()
                .map(acc -> {
                    String displayName = getItemDisplayName(acc.key);
                    return new ReportSummary.ItemStat(acc.key, displayName, acc.qty, acc.amount, acc.buys, acc.sells);
                })
                .sorted((a, b) -> Double.compare(b.totalAmount(), a.totalAmount()))
                .limit(10)
                .collect(Collectors.toList());

        // 計算熱門玩家 (依總交易金額排序)
        List<ReportSummary.PlayerStat> topPlayers = playerMap.values().stream()
                .map(acc -> new ReportSummary.PlayerStat(acc.name, acc.count, acc.spent, acc.earned))
                .sorted((a, b) -> Double.compare((b.totalSpent() + b.totalEarned()), (a.totalSpent() + a.totalEarned())))
                .limit(10)
                .collect(Collectors.toList());

        // 計算物品交易熱度與趨勢分析
        List<ReportSummary.ItemTrendAnalysis> trendAnalyses = itemMap.values().stream()
                .map(acc -> {
                    String displayName = getItemDisplayName(acc.key);
                    int totalBuysSells = acc.buys + acc.sells;
                    double buyRatio = totalBuysSells > 0 ? (acc.buys * 100.0 / totalBuysSells) : 50.0;
                    double multiplier = 1.0;
                    if (plugin.getShopManager() != null) {
                        try {
                            multiplier = plugin.getShopManager().getCatalogPriceQuote(acc.key).multiplier();
                        } catch (Exception ignored) {}
                    }

                    // 計算熱度分數 (0~100)
                    int score = Math.min(100, Math.max(10, (int) Math.round((acc.qty * 0.4) + (totalBuysSells * 5) + (acc.amount * 0.1))));

                    String tag;
                    String insight;
                    if (buyRatio >= 75.0 && acc.buys >= 2) {
                        tag = "🔥 搶手爆款物資";
                        insight = "【" + displayName + "】極受玩家喜愛！玩家購買比例高達 " + String.format("%.0f", buyRatio) + "%，帶動動態物價至 " + String.format("%.2f", multiplier) + "x 倍率。";
                    } else if (buyRatio <= 25.0 && acc.sells >= 2) {
                        tag = "📥 玩家大量拋售";
                        insight = "【" + displayName + "】出現大量拋售！賣出比例高達 " + String.format("%.0f", 100.0 - buyRatio) + "%，物價回調至 " + String.format("%.2f", multiplier) + "x 倍率。";
                    } else if (buyRatio >= 55.0) {
                        tag = "📈 需求穩定上升";
                        insight = "【" + displayName + "】需求平穩增長，買賣次數比為 " + acc.buys + ":" + acc.sells + "，市場買氣良好。";
                    } else {
                        tag = "⚖️ 供需穩定平衡";
                        insight = "【" + displayName + "】供需維持良好平衡，物價穩定在 " + String.format("%.2f", multiplier) + "x 基準區間。";
                    }

                    return new ReportSummary.ItemTrendAnalysis(
                            acc.key, displayName, acc.qty, acc.amount, acc.buys, acc.sells,
                            buyRatio, multiplier, score, tag, insight
                    );
                })
                .sorted((a, b) -> Integer.compare(b.popularityScore(), a.popularityScore()))
                .limit(10)
                .collect(Collectors.toList());

        return new ReportSummary(
                period, startTime, endTime, totalRevenue, totalBuyRev, totalSellPayout,
                totalItems, targetRecords.size(), players.size(), topItems, topPlayers, trendAnalyses
        );
    }

    private String getItemDisplayName(String catalogKey) {
        if (plugin.getItemCatalog() == null || plugin.getLocaleService() == null) return catalogKey;
        CatalogEntry entry = plugin.getItemCatalog().getByKey(catalogKey);
        String locale = "zh_tw";
        if (entry != null) {
            return plugin.getLocaleService().getFullDisplayName(locale, entry);
        }
        return plugin.getLocaleService().getDisplayName(locale, catalogKey);
    }

    private static class ItemStatAccumulator {
        final String key;
        int qty = 0;
        double amount = 0.0;
        int buys = 0;
        int sells = 0;

        ItemStatAccumulator(String key) {
            this.key = key;
        }

        void add(int q, double a, boolean isBuy) {
            qty += q;
            amount += a;
            if (isBuy) buys++;
            else sells++;
        }
    }

    private static class PlayerStatAccumulator {
        final String name;
        int count = 0;
        double spent = 0.0;
        double earned = 0.0;

        PlayerStatAccumulator(String name) {
            this.name = name;
        }

        void add(double amount, boolean isBuy) {
            count++;
            if (isBuy) spent += amount;
            else earned += amount;
        }
    }
}
