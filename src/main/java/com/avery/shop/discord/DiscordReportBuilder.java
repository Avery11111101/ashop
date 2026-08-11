package com.avery.shop.discord;

import com.avery.shop.ShopPlugin;
import com.avery.shop.report.ReportSummary;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.Color;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Discord 商店交易與玩家服務統計 Embed 訊息與元件建構器
 */
public final class DiscordReportBuilder {

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_FMT = new DecimalFormat("#,##0");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ShopPlugin plugin;

    public DiscordReportBuilder(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public ZoneId getZoneId() {
        String tzStr = plugin.getConfig().getString("discord-report.timezone", "system").trim();
        if (tzStr.equalsIgnoreCase("system") || tzStr.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tzStr);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public String formatDate(long millis) {
        return Instant.ofEpochMilli(millis).atZone(getZoneId()).format(DATE_FMT);
    }

    public String getTimezoneAnnotation() {
        String tzStr = plugin.getConfig().getString("discord-report.timezone", "system").trim();
        ZoneId zoneId = getZoneId();
        if (tzStr.equalsIgnoreCase("system") || tzStr.isEmpty()) {
            return zoneId.getId() + " (系統時區)";
        }
        return zoneId.getId();
    }

    public MessageCreateData buildReportMessage(ReportSummary summary, String viewDetail) {
        MessageEmbed embed = buildEmbed(summary, viewDetail);
        List<ActionRow> rows = buildActionRows(summary.getPeriod(), viewDetail);
        return new MessageCreateBuilder()
                .setEmbeds(embed)
                .setComponents(rows)
                .build();
    }

    public MessageEditData buildReportEditMessage(ReportSummary summary, String viewDetail) {
        MessageEmbed embed = buildEmbed(summary, viewDetail);
        List<ActionRow> rows = buildActionRows(summary.getPeriod(), viewDetail);
        return new MessageEditBuilder()
                .setEmbeds(embed)
                .setComponents(rows)
                .build();
    }

    public MessageEmbed buildEmbed(ReportSummary summary, String viewDetail) {
        if (viewDetail == null) viewDetail = "overview";
        EmbedBuilder eb = new EmbedBuilder();

        String periodName = summary.getPeriod().getDisplayNameZh();
        String startDateStr = formatDate(summary.getStartTime());
        String endDateStr = formatDate(summary.getEndTime());
        String tzText = getTimezoneAnnotation();

        // 根據週期選擇主題顏色
        Color color = switch (summary.getPeriod()) {
            case DAILY -> new Color(52, 152, 219);   // 藍色
            case WEEKLY -> new Color(241, 196, 15);  // 金色
            case MONTHLY -> new Color(155, 89, 182); // 紫色
        };
        eb.setColor(color);

        String currency = plugin.getConfig().getString("economy.currency-symbol", "$");

        switch (viewDetail.toLowerCase()) {
            case "top_items" -> {
                eb.setTitle("🔥 [" + periodName + "] 熱門物資交易榜 Top 10");
                eb.setDescription("統計區間 (" + tzText + "): `" + startDateStr + "` 至 `" + endDateStr + "`");

                if (summary.getTopItems().isEmpty()) {
                    eb.addField("暫無數據", "在此期間內尚無玩家交易紀錄。", false);
                } else {
                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (var item : summary.getTopItems()) {
                        String rankEmoji = switch (rank) {
                            case 1 -> "🥇";
                            case 2 -> "🥈";
                            case 3 -> "🥉";
                            default -> "`#" + rank + "`";
                        };
                        sb.append(rankEmoji).append(" **").append(item.displayName()).append("**\n")
                          .append("└ 總流通: `").append(INT_FMT.format(item.totalQuantity())).append(" 件` | 金額: `")
                          .append(currency).append(MONEY_FMT.format(item.totalAmount())).append("` (買 ").append(item.buyCount()).append(" 次 / 賣 ").append(item.sellCount()).append(" 次)\n");
                        rank++;
                    }
                    eb.addField("📊 熱門買賣物資清單", sb.toString(), false);
                }
            }
            case "top_players" -> {
                eb.setTitle("🏆 [" + periodName + "] 活躍交易玩家榜 Top 10");
                eb.setDescription("統計區間 (" + tzText + "): `" + startDateStr + "` 至 `" + endDateStr + "`");

                if (summary.getTopPlayers().isEmpty()) {
                    eb.addField("暫無數據", "在此期間內尚無玩家進行交易。", false);
                } else {
                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (var p : summary.getTopPlayers()) {
                        String rankEmoji = switch (rank) {
                            case 1 -> "🥇";
                            case 2 -> "🥈";
                            case 3 -> "🥉";
                            default -> "`#" + rank + "`";
                        };
                        double total = p.totalSpent() + p.totalEarned();
                        sb.append(rankEmoji).append(" **").append(p.playerName()).append("**\n")
                          .append("└ 交易: `").append(p.totalTransactions()).append(" 筆` | 總額: `")
                          .append(currency).append(MONEY_FMT.format(total)).append("` (購買消費 ")
                          .append(currency).append(MONEY_FMT.format(p.totalSpent())).append(" / 賣出獲得 ")
                          .append(currency).append(MONEY_FMT.format(p.totalEarned())).append(")\n");
                        rank++;
                    }
                    eb.addField("👥 玩家服務使用率排行", sb.toString(), false);
                }
            }
            case "details" -> {
                eb.setTitle("📦 [" + periodName + "] 玩家買賣金額與金錢流向明細");
                eb.setDescription("統計區間 (" + tzText + "): `" + startDateStr + "` 至 `" + endDateStr + "`");

                eb.addField("🛒 玩家向系統購買 (購買消費總額)",
                        "```yaml\n" +
                        "總金額: " + currency + MONEY_FMT.format(summary.getTotalSystemBuyRevenue()) + "\n" +
                        "```", true);

                eb.addField("📥 玩家向系統售出 (賣出獲得總額)",
                        "```yaml\n" +
                        "總金額: " + currency + MONEY_FMT.format(summary.getTotalSystemSellPayout()) + "\n" +
                        "```", true);

                double netFlow = summary.getTotalSystemBuyRevenue() - summary.getTotalSystemSellPayout();
                String netStatus = netFlow >= 0 ? "⚖️ 經濟流向: 玩家淨消費 (商店回收金錢)" : "⚖️ 經濟流向: 玩家淨獲利 (商店發放金錢)";

                eb.addField(netStatus,
                        "```md\n# " + currency + MONEY_FMT.format(Math.abs(netFlow)) + "\n```", false);
            }
            default -> { // overview
                eb.setTitle("📊 [" + periodName + "] 玩家交易與系統商店服務統計");
                eb.setDescription("統計區間 (" + tzText + "): `" + startDateStr + "` 至 `" + endDateStr + "`");

                eb.addField("💰 總交易流通額",
                        "**" + currency + MONEY_FMT.format(summary.getTotalRevenue()) + "**", true);

                eb.addField("📦 總物資流通量",
                        "**" + INT_FMT.format(summary.getTotalItemsTraded()) + "** 件 (`" + summary.getTotalTransactionsCount() + "` 筆交易)", true);

                eb.addField("👥 活躍服務玩家",
                        "**" + summary.getActivePlayersCount() + "** 位玩家使用商店", true);

                // 熱門商品 Top 3 預覽
                if (!summary.getTopItems().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int limit = Math.min(3, summary.getTopItems().size());
                    for (int i = 0; i < limit; i++) {
                        var item = summary.getTopItems().get(i);
                        sb.append(i + 1).append(". **").append(item.displayName()).append("** - ")
                          .append(INT_FMT.format(item.totalQuantity())).append(" 件 (").append(currency).append(MONEY_FMT.format(item.totalAmount())).append(")\n");
                    }
                    eb.addField("🔥 熱門買賣物資 Top 3", sb.toString(), false);
                }

                // 熱門玩家 Top 3 預覽
                if (!summary.getTopPlayers().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    int limit = Math.min(3, summary.getTopPlayers().size());
                    for (int i = 0; i < limit; i++) {
                        var p = summary.getTopPlayers().get(i);
                        double total = p.totalSpent() + p.totalEarned();
                        sb.append(i + 1).append(". **").append(p.playerName()).append("** - ")
                          .append(currency).append(MONEY_FMT.format(total)).append(" (").append(p.totalTransactions()).append(" 筆)\n");
                    }
                    eb.addField("🏆 活躍玩家 Top 3", sb.toString(), false);
                }
            }
        }

        eb.setFooter("ashop 系統商店服務 • 時區: " + tzText + " • " + formatDate(System.currentTimeMillis()));
        return eb.build();
    }

    public List<ActionRow> buildActionRows(ReportSummary.ReportPeriod period, String currentDetail) {
        String periodKey = period.name().toLowerCase();
        if (currentDetail == null) currentDetail = "overview";

        // 按鈕列 (永久按鈕)
        Button btnDaily = Button.primary("report:btn:daily", "📅 每日統計");
        Button btnWeekly = Button.primary("report:btn:weekly", "📆 每週統計");
        Button btnMonthly = Button.primary("report:btn:monthly", "📊 每月統計");
        Button btnRefresh = Button.secondary("report:btn:refresh:" + periodKey, "🔄 重新整理");

        switch (period) {
            case DAILY -> btnDaily = btnDaily.asDisabled();
            case WEEKLY -> btnWeekly = btnWeekly.asDisabled();
            case MONTHLY -> btnMonthly = btnMonthly.asDisabled();
        }

        ActionRow buttonsRow = ActionRow.of(btnDaily, btnWeekly, btnMonthly, btnRefresh);

        // 下拉選單列 (永久下拉選單)
        StringSelectMenu selectMenu = StringSelectMenu.create("report:select_detail:" + periodKey)
                .setPlaceholder("👇 選擇欲檢視的玩家買賣與服務統計項目...")
                .addOption("📊 玩家交易數據總覽", "overview", "查看整體交易流通額、流通量與玩家參與度", currentDetail.equals("overview") ? null : null)
                .addOption("🔥 熱門物資交易榜 Top 10", "top_items", "查看玩家買賣最熱門前 10 名物品物資", currentDetail.equals("top_items") ? null : null)
                .addOption("🏆 活躍交易玩家榜 Top 10", "top_players", "查看使用商店服務交易金額最高的前 10 名玩家", currentDetail.equals("top_players") ? null : null)
                .addOption("📦 玩家買賣金額與金錢流向明細", "details", "查看玩家購買消費、售出獲得金額與金錢流向分析", currentDetail.equals("details") ? null : null)
                .build();

        ActionRow selectRow = ActionRow.of(selectMenu);

        return List.of(buttonsRow, selectRow);
    }

    /**
     * 為 Discord Webhook 生成純 JSON Payload (不附帶互動按鈕/下拉選單)
     */
    public String buildWebhookPayload(ReportSummary summary) {
        MessageEmbed embed = buildEmbed(summary, "overview");

        String title = escapeJson(embed.getTitle());
        String description = escapeJson(embed.getDescription());
        int color = embed.getColor() != null ? embed.getColor().getRGB() & 0xFFFFFF : 3447003;

        StringBuilder fieldsJson = new StringBuilder("[");
        List<MessageEmbed.Field> fields = embed.getFields();
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            fieldsJson.append("{")
                    .append("\"name\": \"").append(escapeJson(f.getName())).append("\",")
                    .append("\"value\": \"").append(escapeJson(f.getValue())).append("\",")
                    .append("\"inline\": ").append(f.isInline())
                    .append("}");
            if (i < fields.size() - 1) fieldsJson.append(",");
        }
        fieldsJson.append("]");

        String footerText = embed.getFooter() != null ? escapeJson(embed.getFooter().getText()) : "";

        return "{"
                + "\"username\": \"ashop 商店服務數據\","
                + "\"embeds\": [{"
                + "\"title\": \"" + title + "\","
                + "\"description\": \"" + description + "\","
                + "\"color\": " + color + ","
                + "\"fields\": " + fieldsJson + ","
                + "\"footer\": {\"text\": \"" + footerText + "\"}"
                + "}]"
                + "}";
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
