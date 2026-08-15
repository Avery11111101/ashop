package com.avery.shop.discord;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.pricing.PriceQuote;
import com.avery.shop.report.ReportSummary;
import com.avery.shop.shop.ShopManager;
import com.avery.shop.util.InventorySpaceUtil;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DiscordShopListener extends ListenerAdapter {

    private final ShopPlugin plugin;
    private final DiscordPanelBuilder panelBuilder;
    private DiscordReportBuilder reportBuilder;

    public DiscordShopListener(ShopPlugin plugin, DiscordPanelBuilder panelBuilder) {
        this(plugin, panelBuilder, new DiscordReportBuilder(plugin));
    }

    public DiscordShopListener(ShopPlugin plugin, DiscordPanelBuilder panelBuilder, DiscordReportBuilder reportBuilder) {
        this.plugin = plugin;
        this.panelBuilder = panelBuilder;
        this.reportBuilder = reportBuilder;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = plugin.getConfig().getString("discord.command-name", "商店");
        String name = event.getName();

        if (name.equalsIgnoreCase(commandName) || name.equalsIgnoreCase("shop")) {
            event.reply(panelBuilder.buildMainMenuMessage()).setEphemeral(true).queue();
            return;
        }

        if (name.equalsIgnoreCase("report") || name.equalsIgnoreCase("報表")) {
            var option = event.getOption("type");
            String periodStr = option != null ? option.getAsString() : "daily";
            ReportSummary.ReportPeriod period = ReportSummary.ReportPeriod.fromString(periodStr);

            ReportSummary summary = plugin.getReportService().generateReport(period);
            var messageData = reportBuilder.buildReportMessage(summary, "overview");
            event.reply(messageData).setEphemeral(false).queue();
            return;
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String customId = event.getComponentId();

        if (customId.equals("shop:select_cat") || customId.equals("shop:select_subcat") || customId.equals("shop:select_rootcat")) {
            String categoryId = event.getValues().get(0);
            event.editMessage(panelBuilder.buildCategoryMessage(categoryId, 0)).queue();
            return;
        }

        if (customId.startsWith("shop:select_item:")) {
            String sub = customId.substring("shop:select_item:".length());
            int lastColon = sub.lastIndexOf(':');
            String categoryId = sub.substring(0, lastColon);
            int page = Integer.parseInt(sub.substring(lastColon + 1));
            String catalogKey = DiscordPanelBuilder.resolveFullKey(event.getValues().get(0));

            event.editMessage(panelBuilder.buildItemPanelMessage(catalogKey, categoryId, page, 1)).queue();
            return;
        }

        // 報表詳細項目下拉選單事件: report:select_detail:<period>
        if (customId.startsWith("report:select_detail:")) {
            String periodStr = customId.substring("report:select_detail:".length());
            ReportSummary.ReportPeriod period = ReportSummary.ReportPeriod.fromString(periodStr);
            String selectedView = event.getValues().get(0);

            ReportSummary summary = plugin.getReportService().generateReport(period);
            var editData = reportBuilder.buildReportEditMessage(summary, selectedView);
            event.editMessage(editData).queue();
            return;
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        // --- 報表按鈕事件 (report:btn:daily / report:btn:weekly / report:btn:monthly / report:btn:refresh:<period>) ---
        if (componentId.startsWith("report:btn:")) {
            String subKey = componentId.substring("report:btn:".length());
            String viewDetail = "overview";
            ReportSummary.ReportPeriod period;

            if (subKey.startsWith("refresh:")) {
                String pStr = subKey.substring("refresh:".length());
                period = ReportSummary.ReportPeriod.fromString(pStr);
            } else {
                period = ReportSummary.ReportPeriod.fromString(subKey);
            }

            ReportSummary summary = plugin.getReportService().generateReport(period);
            var editData = reportBuilder.buildReportEditMessage(summary, viewDetail);
            event.editMessage(editData).queue();
            return;
        }

        // --- 商店按鈕事件 ---
        if (componentId.equals("shop:nav:home")) {
            var mainMsg = panelBuilder.buildMainMenuMessage();
            event.editMessage(net.dv8tion.jda.api.utils.messages.MessageEditData.fromCreateData(mainMsg)).queue();
            return;
        }

        if (componentId.equals("shop:btn_search_modal")) {
            TextInput searchInput = TextInput.create("search_query", "搜尋關鍵字", TextInputStyle.SHORT)
                    .setPlaceholder("請輸入中文/英文名稱或物品ID (例如: 鑽石, diamond, 鎬)")
                    .setRequiredRange(1, 30)
                    .setRequired(true)
                    .build();

            Modal modal = Modal.create("shop:modal_search", "🔍 搜尋全伺服器商店商品")
                    .addActionRow(searchInput)
                    .build();

            event.replyModal(modal).queue();
            return;
        }

        if (componentId.startsWith("shop:qty_custom:")) {
            // shop:qty_custom:<shortKey>:<categoryId>:<page>
            String sub = componentId.substring("shop:qty_custom:".length());
            String[] parts = sub.split(":", 3);
            String shortKey = parts[0];
            String categoryId = parts[1];
            String pageStr = parts[2];

            TextInput qtyInput = TextInput.create("custom_amount", "購買數量", TextInputStyle.SHORT)
                    .setPlaceholder("請輸入欲購買之正整數數量 (例如: 16, 64, 128)")
                    .setRequiredRange(1, 6)
                    .setRequired(true)
                    .build();

            Modal modal = Modal.create("shop:modal_qty:" + shortKey + ":" + categoryId + ":" + pageStr, "✏️ 自訂購買數量")
                    .addActionRow(qtyInput)
                    .build();

            event.replyModal(modal).queue();
            return;
        }

        if (componentId.startsWith("shop:nav:search:")) {
            String[] parts = componentId.split(":");
            int page = Integer.parseInt(parts[parts.length - 1]);
            String query = joinParts(parts, 3, parts.length - 2);
            event.editMessage(panelBuilder.buildSearchResultsMessage(query, page)).queue();
            return;
        }

        if (componentId.startsWith("shop:nav:cat:")) {
            String[] parts = componentId.split(":");
            int page = Integer.parseInt(parts[parts.length - 1]);
            String categoryId = joinParts(parts, 3, parts.length - 2);
            event.editMessage(panelBuilder.buildCategoryMessage(categoryId, page)).queue();
            return;
        }

        if (componentId.startsWith("shop:qty:")) {
            // shop:qty:<shortKey>:<categoryId>:<page>:<amount>
            String sub = componentId.substring("shop:qty:".length());
            int lastColon = sub.lastIndexOf(':');
            int amount = Integer.parseInt(sub.substring(lastColon + 1));
            String rem = sub.substring(0, lastColon);
            int prevColon = rem.lastIndexOf(':');
            int page = Integer.parseInt(rem.substring(prevColon + 1));
            String catAndKey = rem.substring(0, prevColon);
            int firstColon = catAndKey.indexOf(':');
            String shortKey = catAndKey.substring(0, firstColon);
            String categoryId = catAndKey.substring(firstColon + 1);

            String catalogKey = DiscordPanelBuilder.resolveFullKey(shortKey);
            event.editMessage(panelBuilder.buildItemPanelMessage(catalogKey, categoryId, page, amount)).queue();
            return;
        }

        if (componentId.startsWith("shop:buy:") || componentId.startsWith("shop:btn_buy:")) {
            // shop:buy:<shortKey>:<amount>
            String prefix = componentId.startsWith("shop:buy:") ? "shop:buy:" : "shop:btn_buy:";
            String sub = componentId.substring(prefix.length());
            int colon = sub.indexOf(':');
            String shortKey = sub.substring(0, colon);
            int amount = Integer.parseInt(sub.substring(colon + 1));
            String catalogKey = DiscordPanelBuilder.resolveFullKey(shortKey);

            event.deferReply(true).queue(hook -> {
                handleDiscordBuy(event.getUser().getId(), catalogKey, amount, (msg) -> {
                    hook.sendMessage(msg).setEphemeral(true).queue();
                });
            });
            return;
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("shop:modal_search")) {
            String query = event.getValue("search_query").getAsString().trim();
            var editData = panelBuilder.buildSearchResultsMessage(query, 0);
            var createData = net.dv8tion.jda.api.utils.messages.MessageCreateData.fromEditData(editData);
            event.reply(createData).setEphemeral(true).queue();
            return;
        }

        if (event.getModalId().startsWith("shop:modal_qty:")) {
            String sub = event.getModalId().substring("shop:modal_qty:".length());
            String[] parts = sub.split(":", 3);
            String shortKey = parts[0];
            String categoryId = parts[1];
            int page = Integer.parseInt(parts[2]);
            String catalogKey = DiscordPanelBuilder.resolveFullKey(shortKey);

            String rawQty = event.getValue("custom_amount").getAsString().trim();
            int amount;
            try {
                amount = Integer.parseInt(rawQty);
                if (amount <= 0) {
                    event.reply("❌ 購買數量必須大於 0！").setEphemeral(true).queue();
                    return;
                }
            } catch (NumberFormatException e) {
                event.reply("❌ 請輸入有效的整數數量！").setEphemeral(true).queue();
                return;
            }

            int maxBuy = plugin.getConfig().getInt("gui.max-buy-amount", 2304);
            if (amount > maxBuy) {
                amount = maxBuy;
            }

            event.editMessage(panelBuilder.buildItemPanelMessage(catalogKey, categoryId, page, amount)).queue();
            return;
        }
    }

    private String joinParts(String[] parts, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (sb.length() > 0) sb.append(":");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    @FunctionalInterface
    interface ReplyCallback {
        void reply(String message);
    }

    private void handleDiscordBuy(String discordUserId, String catalogKey, int amount, ReplyCallback callback) {
        boolean requireLink = plugin.getConfig().getBoolean("discord.require-discordsrv-link", true);
        UUID playerUuid = null;

        if (requireLink) {
            if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
                playerUuid = DiscordService.fetchDiscordSRVLinkedUuid(discordUserId);
            }

            if (playerUuid == null) {
                callback.reply("❌ 購買失敗：您的 Discord 帳號尚未與 Minecraft 帳號繫結！請在遊戲內輸入 `/discord link` 完成繫結後再試。");
                return;
            }
        }

        final UUID targetUuid = playerUuid;

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Player player = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;

                if (player == null || !player.isOnline()) {
                    String name = targetUuid != null ? Bukkit.getOfflinePlayer(targetUuid).getName() : "玩家";
                    callback.reply("❌ 購買失敗：您的 Minecraft 帳號 (" + (name != null ? name : "未知名稱") + ") 目前不在線上！請先登入遊戲伺服器再進行購買。");
                    return;
                }

                CatalogEntry entry = plugin.getItemCatalog().getByKey(catalogKey);
                if (entry == null || !plugin.getShopConfigService().isItemPurchasable(entry)) {
                    callback.reply("❌ 購買失敗：該商品目前未開放購買。");
                    return;
                }

                PriceQuote buyQuote = plugin.getShopManager().getCatalogPriceQuote(catalogKey);
                double totalPrice = buyQuote.price() * amount;

                if (!plugin.getEconomyService().has(player, totalPrice)) {
                    double balance = plugin.getEconomyService().getBalance(player);
                    callback.reply(String.format("❌ 購買失敗：遊戲內金錢不足！購買需要 **$%.2f**，您目前餘額為 **$%.2f**。", totalPrice, balance));
                    return;
                }

                var deliveryStacks = ShopManager.buildDeliveryStacks(entry.getTemplate(), amount);
                if (!InventorySpaceUtil.canFitStorage(player, deliveryStacks)) {
                    callback.reply(String.format("❌ 購買失敗：您的遊戲背包空間不足！無法容納 %d 個物品，請先清空背包欄位後再試。", amount));
                    return;
                }

                ShopManager.BuyResult result = plugin.getShopManager().buyCatalogEntry(player, catalogKey, amount);
                if (result == ShopManager.BuyResult.SUCCESS) {
                    String itemName = plugin.getLocaleService().getFullDisplayName("zh_tw", entry);
                    callback.reply(String.format("✅ 購買成功！您花費了 **$%.2f** 成功購買 **%s** x %d 個，物品已直接傳送到您的遊戲背包中！",
                            totalPrice, itemName, amount));
                } else {
                    String reason = switch (result) {
                        case NO_MONEY -> "遊戲內金錢不足";
                        case NO_SPACE -> "遊戲背包空間不足";
                        case NOT_FOUND -> "商品不存在或未開放購買";
                        case ECONOMY_DISABLED -> "經濟系統未啟用";
                        default -> result.name();
                    };
                    callback.reply("❌ 購買失敗：系統處理交易時發生錯誤 (" + reason + ")。");
                }
            } catch (Exception e) {
                callback.reply("❌ 購買失敗：系統處理交易時發生異常 (" + e.getMessage() + ")。");
            }
        });
    }
}
