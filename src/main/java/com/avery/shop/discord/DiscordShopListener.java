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
            String[] parts = customId.split(":");
            String categoryId = parts[2];
            int page = Integer.parseInt(parts[3]);
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
            String[] parts = componentId.split(":");
            String catalogKey = DiscordPanelBuilder.resolveFullKey(parts[2]);
            String categoryId = parts[3];
            int page = Integer.parseInt(parts[4]);
            int amount = Integer.parseInt(parts[5]);
            event.editMessage(panelBuilder.buildItemPanelMessage(catalogKey, categoryId, page, amount)).queue();
            return;
        }

        if (componentId.startsWith("shop:btn_buy:")) {
            String[] parts = componentId.split(":");
            String catalogKey = DiscordPanelBuilder.resolveFullKey(parts[2]);
            int amount = Integer.parseInt(parts[3]);
            handleDiscordBuy(event.getUser().getId(), catalogKey, amount, (msg, ephemeral) ->
                    event.reply(msg).setEphemeral(ephemeral).queue());
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
        void reply(String message, boolean ephemeral);
    }

    private void handleDiscordBuy(String discordUserId, String catalogKey, int amount, ReplyCallback callback) {
        boolean requireLink = plugin.getConfig().getBoolean("discord.require-discordsrv-link", true);
        UUID playerUuid = null;

        if (requireLink) {
            if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
                playerUuid = DiscordService.fetchDiscordSRVLinkedUuid(discordUserId);
            }

            if (playerUuid == null) {
                callback.reply("❌ 購買失敗：您的 Discord 帳號尚未與 Minecraft 帳號繫結！請在遊戲內輸入 `/discord link` 完成繫結後再試。", true);
                return;
            }
        }

        final UUID targetUuid = playerUuid;

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;

            if (player == null || !player.isOnline()) {
                String name = targetUuid != null ? Bukkit.getOfflinePlayer(targetUuid).getName() : "玩家";
                callback.reply("❌ 購買失敗：您的 Minecraft 帳號 (" + name + ") 目前不在線上！請先登入遊戲伺服器再進行購買。", true);
                return;
            }

            CatalogEntry entry = plugin.getItemCatalog().getByKey(catalogKey);
            if (entry == null || !plugin.getShopConfigService().isItemPurchasable(entry)) {
                callback.reply("❌ 購買失敗：該商品目前未開放購買。", true);
                return;
            }

            PriceQuote buyQuote = plugin.getShopManager().getCatalogPriceQuote(catalogKey);
            double totalPrice = buyQuote.price() * amount;

            if (!plugin.getEconomyService().has(player, totalPrice)) {
                double balance = plugin.getEconomyService().getBalance(player);
                callback.reply(String.format("❌ 購買失敗：遊戲內金錢不足！購買需要 $%.2f，您目前餘額為 $%.2f。", totalPrice, balance), true);
                return;
            }

            var deliveryStacks = ShopManager.buildDeliveryStacks(entry.getTemplate(), amount);
            if (!InventorySpaceUtil.canFitStorage(player, deliveryStacks)) {
                callback.reply(String.format("❌ 購買失敗：您的遊戲背包空間不足！無法容納 %d 個物品，請先清空背包欄位後再試。", amount), true);
                return;
            }

            ShopManager.BuyResult result = plugin.getShopManager().buyCatalogEntry(player, catalogKey, amount);
            if (result == ShopManager.BuyResult.SUCCESS) {
                String itemName = plugin.getLocaleService().getDisplayName("zh_tw", entry.getTemplate().getType());
                callback.reply(String.format("✅ 購買成功！您花費了 **$%.2f** 成功購買 **%s** x %d 個，物品已直接傳送到您的遊戲背包中！",
                        totalPrice, itemName, amount), true);
            } else {
                callback.reply("❌ 購買失敗：系統處理交易時發生錯誤 (" + result + ")。", true);
            }
        });
    }
}
