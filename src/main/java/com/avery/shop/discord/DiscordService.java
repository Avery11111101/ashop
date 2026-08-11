package com.avery.shop.discord;

import com.avery.shop.ShopPlugin;
import com.avery.shop.report.ReportSummary;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class DiscordService {

    private final ShopPlugin plugin;
    private JDA standaloneJda;
    private Object discordSrvJda;
    private DiscordShopListener shopListener;
    private DiscordReportBuilder reportBuilder;

    public DiscordService(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) {
            plugin.getLogger().info("Discord 線上商店功能已關閉 (discord.enabled = false)");
            return;
        }

        String token = plugin.getConfig().getString("discord.bot-token", "").trim();
        String commandName = plugin.getConfig().getString("discord.command-name", "商店").trim();

        DiscordPanelBuilder panelBuilder = new DiscordPanelBuilder(plugin);
        DiscordPanelBuilder.preloadCatalogKeys(plugin.getItemCatalog());
        reportBuilder = new DiscordReportBuilder(plugin);
        shopListener = new DiscordShopListener(plugin, panelBuilder, reportBuilder);

        if (!token.isEmpty()) {
            try {
                plugin.getLogger().info("正在啟動獨立 Discord Bot (Token模式)...");
                standaloneJda = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES)
                        .build();
                standaloneJda.awaitReady();
                standaloneJda.addEventListener(shopListener);
                registerSlashCommands(standaloneJda, commandName);
                plugin.getLogger().info("獨立 Discord Bot 連線成功！");
            } catch (Exception e) {
                plugin.getLogger().severe("無法啟動獨立 Discord Bot: " + e.getMessage());
            }
        } else if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            try {
                discordSrvJda = fetchDiscordSRVJda();
                if (discordSrvJda == null) {
                    plugin.getLogger().warning("DiscordSRV 已啟動但 JDA 實體尚未準備就緒，將於 3 秒後重試。");
                    Bukkit.getScheduler().runTaskLater(plugin, this::start, 60L);
                    return;
                }
                registerListenerToObject(discordSrvJda, shopListener);
                registerSlashCommandsToObject(discordSrvJda, commandName);
                plugin.getLogger().info("已成功連結 DiscordSRV 之 JDA 實體與註冊商店斜線指令！");
            } catch (Exception e) {
                plugin.getLogger().warning("連結 DiscordSRV JDA 失敗: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("未設定 discord.bot-token 且伺服器未安裝/啟用 DiscordSRV，Discord 線上商店與 Bot 報表功能暫停。");
        }
    }

    public DiscordReportBuilder getReportBuilder() {
        return reportBuilder;
    }

    public boolean sendReportToChannel(String channelId, ReportSummary summary, String viewDetail) {
        if (reportBuilder == null || summary == null) return false;
        var messageData = reportBuilder.buildReportMessage(summary, viewDetail);

        if (standaloneJda != null) {
            TextChannel channel = standaloneJda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage(messageData).queue();
                return true;
            }
        }

        if (discordSrvJda instanceof JDA nativeJda) {
            TextChannel channel = nativeJda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage(messageData).queue();
                return true;
            }
        } else if (discordSrvJda != null) {
            try {
                Method getTextChannelById = discordSrvJda.getClass().getMethod("getTextChannelById", String.class);
                Object channelObj = getTextChannelById.invoke(discordSrvJda, channelId);
                if (channelObj != null) {
                    Method sendMessage = channelObj.getClass().getMethod("sendMessage", net.dv8tion.jda.api.utils.messages.MessageCreateData.class);
                    Object action = sendMessage.invoke(channelObj, messageData);
                    if (action != null) {
                        Method queue = action.getClass().getMethod("queue");
                        queue.invoke(action);
                        return true;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("透過反射發送報表訊息至 DiscordSRV Channel 失敗: " + e.getMessage());
            }
        }
        return false;
    }

    public static Object fetchDiscordSRVPlugin() {
        try {
            Class<?> clazz = Class.forName("github.scarsz.discordsrv.DiscordSRV");
            Method method = clazz.getMethod("getPlugin");
            return method.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object fetchDiscordSRVJda() {
        Object pluginInstance = fetchDiscordSRVPlugin();
        if (pluginInstance == null) return null;
        try {
            Method method = pluginInstance.getClass().getMethod("getJda");
            return method.invoke(pluginInstance);
        } catch (Throwable t) {
            return null;
        }
    }

    public static UUID fetchDiscordSRVLinkedUuid(String discordUserId) {
        Object pluginInstance = fetchDiscordSRVPlugin();
        if (pluginInstance == null) return null;
        try {
            Method getLinkManager = pluginInstance.getClass().getMethod("getAccountLinkManager");
            Object linkManager = getLinkManager.invoke(pluginInstance);
            if (linkManager != null) {
                Method getUuid = linkManager.getClass().getMethod("getUuid", String.class);
                return (UUID) getUuid.invoke(linkManager, discordUserId);
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    private void registerListenerToObject(Object jdaObj, Object listener) {
        try {
            Method addListenerMethod = jdaObj.getClass().getMethod("addEventListener", Object[].class);
            addListenerMethod.invoke(jdaObj, (Object) new Object[]{ listener });
        } catch (Exception e) {
            try {
                Method addListenerMethod = jdaObj.getClass().getMethod("addEventListener", Object.class);
                addListenerMethod.invoke(jdaObj, listener);
            } catch (Exception ex) {
                plugin.getLogger().warning("無法向 DiscordSRV JDA 註冊監聽器: " + ex.getMessage());
            }
        }
    }

    private void registerSlashCommands(JDA jda, String commandName) {
        var cmd1 = Commands.slash(commandName, "線上預覽與購買伺服器商店商品");
        var cmd2 = Commands.slash("shop", "Preview and buy items from server shop");
        var cmdReport = Commands.slash("report", "查詢與生成伺服器商店營運報表 (每日/每週/每月)")
                .addOption(OptionType.STRING, "type", "報表類型: daily (每日), weekly (每週), monthly (每月)", false);
        var cmdReportZh = Commands.slash("報表", "查詢與生成伺服器商店營運報表 (每日/每週/每月)")
                .addOption(OptionType.STRING, "type", "報表類型: daily (每日), weekly (每週), monthly (每月)", false);

        List<String> guildIds = plugin.getConfig().getStringList("discord.guild-ids");
        if (!guildIds.isEmpty()) {
            for (String guildId : guildIds) {
                var guild = jda.getGuildById(guildId);
                if (guild != null) {
                    guild.updateCommands().addCommands(cmd1, cmd2, cmdReport, cmdReportZh).queue();
                }
            }
        } else {
            jda.updateCommands().addCommands(cmd1, cmd2, cmdReport, cmdReportZh).queue();
        }
    }

    private void registerSlashCommandsToObject(Object jdaObj, String commandName) {
        if (jdaObj instanceof JDA nativeJda) {
            registerSlashCommands(nativeJda, commandName);
            return;
        }
        try {
            var cmd1 = Commands.slash(commandName, "線上預覽與購買伺服器商店商品");
            var cmd2 = Commands.slash("shop", "Preview and buy items from server shop");
            var cmdReport = Commands.slash("report", "查詢與生成伺服器商店營運報表 (每日/每週/每月)")
                    .addOption(OptionType.STRING, "type", "報表類型: daily (每日), weekly (每週), monthly (每月)", false);
            var cmdReportZh = Commands.slash("報表", "查詢與生成伺服器商店營運報表 (每日/每週/每月)")
                    .addOption(OptionType.STRING, "type", "報表類型: daily (每日), weekly (每週), monthly (每月)", false);

            Method updateCommandsMethod = jdaObj.getClass().getMethod("updateCommands");
            Object action = updateCommandsMethod.invoke(jdaObj);
            if (action != null) {
                Method addCommandsMethod = action.getClass().getMethod("addCommands", Object[].class);
                Object queued = addCommandsMethod.invoke(action, (Object) new Object[]{ cmd1, cmd2, cmdReport, cmdReportZh });
                if (queued != null) {
                    try {
                        Method queueMethod = queued.getClass().getMethod("queue");
                        queueMethod.invoke(queued);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("透過反射向 DiscordSRV 註冊斜線指令時提示: " + e.getMessage());
        }
    }

    public void stop() {
        if (standaloneJda != null) {
            try {
                standaloneJda.shutdown();
                plugin.getLogger().info("獨立 Discord Bot 已停止。");
            } catch (Exception ignored) {}
            standaloneJda = null;
        }
        if (discordSrvJda != null && shopListener != null) {
            try {
                Method removeListenerMethod = discordSrvJda.getClass().getMethod("removeEventListener", Object[].class);
                removeListenerMethod.invoke(discordSrvJda, (Object) new Object[]{ shopListener });
            } catch (Exception ignored) {}
            discordSrvJda = null;
        }
    }
}
