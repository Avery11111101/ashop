package com.avery.shop.discord;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.pricing.PriceQuote;
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

    public DiscordShopListener(ShopPlugin plugin, DiscordPanelBuilder panelBuilder) {
        this.plugin = plugin;
        this.panelBuilder = panelBuilder;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = plugin.getConfig().getString("discord.command-name", "商店");
        if (event.getName().equalsIgnoreCase(commandName) || event.getName().equalsIgnoreCase("shop")) {
            event.reply(panelBuilder.buildMainMenuMessage()).setEphemeral(true).queue();
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
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

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
            String query = parts[3];
            int page = Integer.parseInt(parts[4]);
            event.editMessage(panelBuilder.buildSearchResultsMessage(query, page)).queue();
            return;
        }

        if (componentId.startsWith("shop:nav:cat:")) {
            String[] parts = componentId.split(":");
            String categoryId = parts[3];
            int page = Integer.parseInt(parts[4]);
            event.editMessage(panelBuilder.buildCategoryMessage(categoryId, page)).queue();
            return;
        }

        if (componentId.startsWith("shop:qty:")) {
            String[] parts = componentId.split(":");
            int quantity = Integer.parseInt(parts[2]);
            String catalogKey = DiscordPanelBuilder.resolveFullKey(parts[3]);
            String categoryId = parts[4];
            int page = Integer.parseInt(parts[5]);

            event.editMessage(panelBuilder.buildItemPanelMessage(catalogKey, categoryId, page, quantity)).queue();
            return;
        }

        if (componentId.startsWith("shop:qty_custom:")) {
            String[] parts = componentId.split(":");
            String catalogKey = DiscordPanelBuilder.resolveFullKey(parts[2]);
            String categoryId = parts[3];
            int page = Integer.parseInt(parts[4]);

            TextInput qtyInput = TextInput.create("qty_input", "購買數量", TextInputStyle.SHORT)
                    .setPlaceholder("請輸入欲購買的數量 (例如: 16, 64)")
                    .setRequiredRange(1, 6)
                    .setRequired(true)
                    .build();

            Modal modal = Modal.create("shop:modal_qty:" + DiscordPanelBuilder.getShortKey(catalogKey) + ":" + categoryId + ":" + page, "自訂購買數量")
                    .addActionRow(qtyInput)
                    .build();

            event.replyModal(modal).queue();
            return;
        }

        if (componentId.startsWith("shop:buy:")) {
            String[] parts = componentId.split(":");
            int amount = Integer.parseInt(parts[2]);
            String catalogKey = DiscordPanelBuilder.resolveFullKey(parts[3]);

            handleDiscordPurchase(event.getUser().getId(), catalogKey, amount, (msg, ephemeral) -> {
                event.reply(msg).setEphemeral(ephemeral).queue();
            });
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("shop:modal_search")) {
            String query = event.getValue("search_query") != null ? event.getValue("search_query").getAsString().trim() : "";
            event.editMessage(panelBuilder.buildSearchResultsMessage(query, 0)).queue();
            return;
        }

        if (event.getModalId().startsWith("shop:modal_qty:")) {
            String[] parts = event.getModalId().split(":");
            String catalogKey = DiscordPanelBuilder.resolveFullKey(parts[2]);
            String categoryId = parts[3];
            int page = Integer.parseInt(parts[4]);

            String input = event.getValue("qty_input") != null ? event.getValue("qty_input").getAsString().trim() : "1";
            int quantity;
            try {
                quantity = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                quantity = 1;
            }

            event.editMessage(panelBuilder.buildItemPanelMessage(catalogKey, categoryId, page, quantity)).queue();
        }
    }

    @FunctionalInterface
    public interface ReplyCallback {
        void reply(String message, boolean ephemeral);
    }

    private void handleDiscordPurchase(String discordUserId, String catalogKey, int amount, ReplyCallback callback) {
        boolean requireLink = plugin.getConfig().getBoolean("discord.require-discordsrv-link", true);

        UUID playerUuid = null;

        if (requireLink) {
            if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
                try {
                    playerUuid = DiscordService.fetchDiscordSRVLinkedUuid(discordUserId);
                } catch (Throwable e) {
                    plugin.getLogger().warning("查詢 DiscordSRV 繫結帳號失敗: " + e.getMessage());
                }
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
