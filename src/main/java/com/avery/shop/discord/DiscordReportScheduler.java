package com.avery.shop.discord;

import com.avery.shop.ShopPlugin;
import com.avery.shop.report.ReportService;
import com.avery.shop.report.ReportSummary;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Discord 每日/每週/每月報表定期推播排程器
 */
public final class DiscordReportScheduler {

    private final ShopPlugin plugin;
    private final ReportService reportService;
    private final DiscordReportBuilder reportBuilder;
    private BukkitTask task;

    private LocalDate lastDailyDate;
    private LocalDate lastWeeklyDate;
    private LocalDate lastMonthlyDate;

    public DiscordReportScheduler(ShopPlugin plugin, ReportService reportService, DiscordReportBuilder reportBuilder) {
        this.plugin = plugin;
        this.reportService = reportService;
        this.reportBuilder = reportBuilder;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("discord-report.enabled", true)) {
            return;
        }

        // 每 60 秒執行一次檢查 (1200 ticks)
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkAndSendReports, 100L, 1200L);
        plugin.getLogger().info("已啟動 Discord 營運報表定期自動推播排程器 (每分鐘檢查)");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void checkAndSendReports() {
        if (!plugin.getConfig().getBoolean("discord-report.enabled", true)) return;

        java.time.ZoneId zoneId = reportBuilder.getZoneId();
        java.time.ZonedDateTime zdt = java.time.ZonedDateTime.now(zoneId);
        LocalDate today = zdt.toLocalDate();
        LocalTime nowTime = zdt.toLocalTime();

        // 檢查每日報表
        if (plugin.getConfig().getBoolean("discord-report.scheduled.daily.enabled", true)) {
            String timeStr = plugin.getConfig().getString("discord-report.scheduled.daily.time", "00:00");
            if (shouldTrigger(today, nowTime, lastDailyDate, timeStr, null, null)) {
                lastDailyDate = today;
                sendReport(ReportSummary.ReportPeriod.DAILY, "overview");
            }
        }

        // 檢查每週報表
        if (plugin.getConfig().getBoolean("discord-report.scheduled.weekly.enabled", true)) {
            String timeStr = plugin.getConfig().getString("discord-report.scheduled.weekly.time", "00:00");
            String dayStr = plugin.getConfig().getString("discord-report.scheduled.weekly.day-of-week", "MONDAY");
            DayOfWeek targetDay;
            try {
                targetDay = DayOfWeek.valueOf(dayStr.toUpperCase().trim());
            } catch (Exception e) {
                targetDay = DayOfWeek.MONDAY;
            }

            if (today.getDayOfWeek() == targetDay && shouldTrigger(today, nowTime, lastWeeklyDate, timeStr, null, null)) {
                lastWeeklyDate = today;
                sendReport(ReportSummary.ReportPeriod.WEEKLY, "overview");
            }
        }

        // 檢查每月報表
        if (plugin.getConfig().getBoolean("discord-report.scheduled.monthly.enabled", true)) {
            String timeStr = plugin.getConfig().getString("discord-report.scheduled.monthly.time", "00:00");
            int targetDom = plugin.getConfig().getInt("discord-report.scheduled.monthly.day-of-month", 1);

            if (today.getDayOfMonth() == targetDom && shouldTrigger(today, nowTime, lastMonthlyDate, timeStr, null, null)) {
                lastMonthlyDate = today;
                sendReport(ReportSummary.ReportPeriod.MONTHLY, "overview");
            }
        }
    }

    private boolean shouldTrigger(LocalDate today, LocalTime nowTime, LocalDate lastTriggeredDate, String targetTimeStr, DayOfWeek targetDay, Integer targetDom) {
        if (lastTriggeredDate != null && lastTriggeredDate.equals(today)) {
            return false; // 今天已發送過
        }
        try {
            String[] parts = targetTimeStr.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());
            // 如果當前時間在觸發時間後 5 分鐘內
            LocalTime targetTime = LocalTime.of(hour, minute);
            return !nowTime.isBefore(targetTime) && nowTime.isBefore(targetTime.plusMinutes(5));
        } catch (Exception e) {
            return false;
        }
    }

    public void sendReport(ReportSummary.ReportPeriod period, String viewDetail) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ReportSummary summary = reportService.generateReport(period);

                // 1. 嘗試透過 JDA / DiscordSRV 發送至 Bot 頻道 (包含按鈕與下拉選單)
                String channelId = plugin.getConfig().getString("discord-report.channel-id", "").trim();
                boolean botSent = false;

                if (!channelId.isEmpty()) {
                    var discordService = plugin.getDiscordService();
                    if (discordService != null) {
                        botSent = discordService.sendReportToChannel(channelId, summary, viewDetail);
                    }
                }

                // 2. 嘗試透過 Webhook 發送 (純 Embed，無按鈕)
                String webhookUrl = plugin.getConfig().getString("discord-report.webhook-url", "").trim();
                if (!webhookUrl.isEmpty() && !webhookUrl.equalsIgnoreCase("YOUR_WEBHOOK_URL_HERE")) {
                    String payload = reportBuilder.buildWebhookPayload(summary);
                    plugin.getDiscordWebhookService().sendPayloadWithCustomUrl(webhookUrl, payload);
                    plugin.getLogger().info("已透過 Webhook 發送 [" + period.getDisplayNameZh() + "] 至指定 URL");
                } else if (!botSent && channelId.isEmpty()) {
                    plugin.getLogger().warning("欲發送 [" + period.getDisplayNameZh() + "] 但未設定 discord-report.channel-id 亦未設定 discord-report.webhook-url");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("發送 [" + period.getDisplayNameZh() + "] 時發生例外狀況: " + e.getMessage());
            }
        });
    }
}
