package com.avery.shop.discord;

import com.avery.shop.ShopPlugin;
import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.pricing.PriceQuote;
import com.avery.shop.shop.ShopCategoryDefinition;
import com.avery.shop.shop.TradeMode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DiscordPanelBuilder {

    private final ShopPlugin plugin;
    private static final int ITEMS_PER_PAGE = 10;

    private static final Map<String, String> shortToFullKeyMap = new ConcurrentHashMap<>();
    private static final Map<String, String> fullToShortKeyMap = new ConcurrentHashMap<>();

    public DiscordPanelBuilder(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    public static String getShortKey(String fullKey) {
        if (fullKey == null) return "";
        if (fullKey.length() <= 60 && !fullKey.contains(":")) {
            return fullKey;
        }
        return fullToShortKeyMap.computeIfAbsent(fullKey, k -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(k.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder("k_");
                for (int i = 0; i < 8; i++) {
                    sb.append(String.format("%02x", hash[i]));
                }
                String shortKey = sb.toString();
                shortToFullKeyMap.put(shortKey, k);
                return shortKey;
            } catch (Exception e) {
                String shortKey = "k_" + Math.abs(k.hashCode());
                shortToFullKeyMap.put(shortKey, k);
                return shortKey;
            }
        });
    }

    public static void preloadCatalogKeys(com.avery.shop.catalog.ItemCatalog catalog) {
        if (catalog == null) return;
        for (var entry : catalog.getAll()) {
            getShortKey(entry.getKey());
        }
    }

    public static String resolveFullKey(String key) {
        if (key == null) return "";
        return shortToFullKeyMap.getOrDefault(key, key);
    }

    private static String clampString(String input, int maxLen) {
        if (input == null) return "";
        if (input.length() <= maxLen) return input;
        return input.substring(0, maxLen - 3) + "...";
    }

    public StringSelectMenu buildRootCategorySelectMenu(String currentCatId) {
        StringSelectMenu.Builder selectMenu = StringSelectMenu.create("shop:select_rootcat")
                .setPlaceholder("📂 請選擇商店分類...");

        var categories = plugin.getShopConfigService().getRootCategories();
        int count = 0;
        for (var cat : categories) {
            if (!cat.isEnabled()) continue;
            String emoji = getCategoryEmoji(cat.getId());
            String displayName = plugin.getLocaleService().getCategoryDisplayName("zh_tw", cat.getId());
            boolean isSelected = currentCatId != null && cat.getId().equalsIgnoreCase(currentCatId);
            selectMenu.addOption(
                    clampString((isSelected ? "▶ " : "") + emoji + " " + displayName, 95),
                    cat.getId(),
                    clampString("點擊查看 " + displayName + " 分類商品", 95)
            );
            count++;
        }

        if (count == 0) return null;
        return selectMenu.build();
    }

    public MessageCreateData buildMainMenuMessage() {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛒 伺服器線上商店 (Online Shop)")
                .setDescription("歡迎使用 Discord 線上商店！您可以在此處預覽與查詢伺服器所有商店商品。\n\n👉 **請選擇下方選單瀏覽分類，或點擊「🔍 搜尋商品」按鈕**")
                .setColor(new Color(46, 204, 113))
                .setFooter("ashop 伺服器商店 • 所有人皆可線上預覽與查詢", null);

        List<LayoutComponent> layoutRows = new ArrayList<>();

        var rootMenu = buildRootCategorySelectMenu(null);
        if (rootMenu != null) {
            layoutRows.add(ActionRow.of(rootMenu));
        }

        List<ItemComponent> navBtns = new ArrayList<>();
        navBtns.add(Button.secondary("shop:btn_search_modal", "🔍 搜尋商品"));
        layoutRows.add(ActionRow.of(navBtns));

        return new MessageCreateBuilder()
                .setEmbeds(embed.build())
                .setComponents(layoutRows)
                .build();
    }

    public MessageEditData buildCategoryMessage(String categoryId, int page) {
        var catDef = plugin.getShopConfigService().getCategory(categoryId);
        String catName = catDef.map(d -> d.getDefinition().getDisplayName())
                .orElseGet(() -> plugin.getLocaleService().getCategoryDisplayName("zh_tw", categoryId));

        var subcategories = plugin.getShopConfigService().getChildCategories(categoryId);
        var entries = plugin.getShopConfigService().getEnabledEntries(categoryId);
        String parentId = plugin.getShopConfigService().getParentCategoryId(categoryId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📂 商店分類：" + catName)
                .setColor(new Color(52, 152, 219));

        List<LayoutComponent> layoutRows = new ArrayList<>();

        if (!subcategories.isEmpty()) {
            StringBuilder desc = new StringBuilder("此分類包含子分類，請從下方選單選擇：\n\n");
            StringSelectMenu.Builder subMenu = StringSelectMenu.create("shop:select_subcat")
                    .setPlaceholder("📂 請選擇子分類...");

            int subCount = 0;
            for (var sub : subcategories) {
                if (!sub.isEnabled()) continue;
                String displayName = sub.getDisplayName();
                desc.append("• ").append(displayName).append("\n");
                subMenu.addOption(clampString("📂 " + displayName, 95), sub.getId());
                subCount++;
            }

            if (subCount > 0) {
                layoutRows.add(ActionRow.of(subMenu.build()));
            }

            if (entries.isEmpty()) {
                embed.setDescription(desc.toString());
            }
        }

        int totalItems = entries.size();
        if (totalItems > 0) {
            int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
            page = Math.max(0, Math.min(page, totalPages - 1));

            int start = page * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

            StringBuilder desc = new StringBuilder();
            if (!subcategories.isEmpty()) {
                desc.append("📂 **子分類列表**：\n");
                for (var sub : subcategories) {
                    if (sub.isEnabled()) desc.append("• ").append(sub.getDisplayName()).append(" ");
                }
                desc.append("\n\n");
            }

            desc.append(String.format("📦 **商品列表 (第 %d / %d 頁，共 %d 項商品)**：\n\n", page + 1, totalPages, totalItems));

            StringSelectMenu.Builder itemSelect = StringSelectMenu.create("shop:select_item:" + categoryId + ":" + page)
                    .setPlaceholder("📦 請選擇要查看/購買的商品...");

            for (int i = start; i < end; i++) {
                CatalogEntry entry = entries.get(i);
                String catalogKey = entry.getKey();
                String shortKey = getShortKey(catalogKey);
                String itemName = getItemChineseName(entry);
                PriceQuote buyQuote = plugin.getShopManager().getCatalogPriceQuote(catalogKey);
                double sellPrice = plugin.getShopManager().getSellToSystemQuote(entry.getTemplate()).price();
                TradeMode tradeMode = plugin.getShopConfigService().getItemTradeMode(catalogKey);

                String priceStr = tradeMode.allowsBuy() ? String.format("$%.1f", buyQuote.price()) : "不可購買";
                String sellStr = tradeMode.allowsSell() ? String.format("$%.1f", sellPrice) : "不可收購";
                String trendStr = buyQuote.formatTrend();

                desc.append(String.format("• **%s** — 買: `%s` | 賣: `%s` %s\n",
                        itemName, priceStr, sellStr, trendStr.isEmpty() ? "" : "(" + trendStr + ")"));

                itemSelect.addOption(clampString(itemName + " - 買: " + priceStr, 95), shortKey, "點擊查看詳情與購買");
            }

            embed.setDescription(desc.toString());
            embed.setFooter(String.format("頁數: %d/%d • 所有價格為即時動態價格", page + 1, totalPages), null);

            layoutRows.add(ActionRow.of(itemSelect.build()));
        }

        if (subcategories.isEmpty() && totalItems == 0) {
            embed.setDescription("*(此分類目前沒有商品與子分類)*");
        }

        var rootMenu = buildRootCategorySelectMenu(categoryId);
        if (rootMenu != null) {
            layoutRows.add(ActionRow.of(rootMenu));
        }

        List<ItemComponent> navBtns = new ArrayList<>();
        if (totalItems > 0) {
            int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
            navBtns.add(Button.secondary("shop:nav:cat:" + categoryId + ":" + (page - 1), "◀️ 上一頁").withDisabled(page <= 0));
            navBtns.add(Button.secondary("shop:nav:cat:" + categoryId + ":" + (page + 1), "▶️ 下一頁").withDisabled(page >= totalPages - 1));
        }
        if (parentId != null) {
            navBtns.add(Button.primary("shop:nav:cat:" + parentId + ":0", "↩️ 上一層"));
        }
        navBtns.add(Button.secondary("shop:btn_search_modal", "🔍 搜尋"));
        navBtns.add(Button.primary("shop:nav:home", "🏠 主選單"));

        layoutRows.add(ActionRow.of(navBtns));

        return new MessageEditBuilder()
                .setEmbeds(embed.build())
                .setComponents(layoutRows)
                .build();
    }

    public MessageEditData buildSearchResultsMessage(String query, int page) {
        var catalogResults = plugin.getItemCatalog().search("zh_tw", query);
        var filteredEntries = catalogResults.stream()
                .filter(entry -> plugin.getShopConfigService().isItemInShop(entry))
                .toList();

        int totalItems = filteredEntries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔍 商店搜尋結果：" + query)
                .setColor(new Color(155, 89, 182));

        List<LayoutComponent> layoutRows = new ArrayList<>();

        if (totalItems == 0) {
            embed.setDescription("*(未找到任何符合「" + query + "」且已上架的商店商品)*\n\n提示：嘗試輸入物品中文名稱、英文 ID（例如 `diamond`）或通用關鍵字再試一次。");
            var rootMenu = buildRootCategorySelectMenu(null);
            if (rootMenu != null) layoutRows.add(ActionRow.of(rootMenu));

            List<ItemComponent> navBtns = new ArrayList<>();
            navBtns.add(Button.secondary("shop:btn_search_modal", "🔍 重新搜尋"));
            navBtns.add(Button.primary("shop:nav:home", "🏠 回主選單"));
            layoutRows.add(ActionRow.of(navBtns));
            return new MessageEditBuilder()
                    .setEmbeds(embed.build())
                    .setComponents(layoutRows)
                    .build();
        }

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalItems);

        StringBuilder desc = new StringBuilder();
        desc.append(String.format("找到 **%d** 項商品 (第 **%d / %d** 頁)：\n\n", totalItems, page + 1, totalPages));

        StringSelectMenu.Builder itemSelect = StringSelectMenu.create("shop:select_item:search@" + query + ":" + page)
                .setPlaceholder("📦 請選擇要查看/購買的商品...");

        for (int i = start; i < end; i++) {
            CatalogEntry entry = filteredEntries.get(i);
            String catalogKey = entry.getKey();
            String shortKey = getShortKey(catalogKey);
            String itemName = getItemChineseName(entry);
            PriceQuote buyQuote = plugin.getShopManager().getCatalogPriceQuote(catalogKey);
            double sellPrice = plugin.getShopManager().getSellToSystemQuote(entry.getTemplate()).price();
            TradeMode tradeMode = plugin.getShopConfigService().getItemTradeMode(catalogKey);

            String priceStr = tradeMode.allowsBuy() ? String.format("$%.1f", buyQuote.price()) : "不可購買";
            String sellStr = tradeMode.allowsSell() ? String.format("$%.1f", sellPrice) : "不可收購";
            String trendStr = buyQuote.formatTrend();

            desc.append(String.format("• **%s** — 買: `%s` | 賣: `%s` %s\n",
                    itemName, priceStr, sellStr, trendStr.isEmpty() ? "" : "(" + trendStr + ")"));

            itemSelect.addOption(clampString(itemName + " - 買: " + priceStr, 95), shortKey, "點擊查看詳情與購買");
        }

        embed.setDescription(desc.toString());
        embed.setFooter(String.format("搜尋結果: %d 項 • 頁數 %d/%d", totalItems, page + 1, totalPages), null);

        layoutRows.add(ActionRow.of(itemSelect.build()));

        var rootMenu = buildRootCategorySelectMenu(null);
        if (rootMenu != null) layoutRows.add(ActionRow.of(rootMenu));

        List<ItemComponent> navBtns = new ArrayList<>();
        navBtns.add(Button.secondary("shop:nav:search:" + query + ":" + (page - 1), "◀️ 上一頁").withDisabled(page <= 0));
        navBtns.add(Button.secondary("shop:nav:search:" + query + ":" + (page + 1), "▶️ 下一頁").withDisabled(page >= totalPages - 1));
        navBtns.add(Button.secondary("shop:btn_search_modal", "🔍 重新搜尋"));
        navBtns.add(Button.primary("shop:nav:home", "🏠 主選單"));

        layoutRows.add(ActionRow.of(navBtns));

        return new MessageEditBuilder()
                .setEmbeds(embed.build())
                .setComponents(layoutRows)
                .build();
    }

    public MessageEditData buildItemPanelMessage(String rawKey, String categoryId, int page, int quantity) {
        String catalogKey = resolveFullKey(rawKey);
        String shortKey = getShortKey(catalogKey);

        var entry = plugin.getItemCatalog().getByKey(catalogKey);
        if (entry == null) {
            if (categoryId.startsWith("search@")) {
                String query = categoryId.substring("search@".length());
                return buildSearchResultsMessage(query, page);
            }
            return buildCategoryMessage(categoryId, page);
        }

        String itemName = getItemChineseName(entry);
        PriceQuote buyQuote = plugin.getShopManager().getCatalogPriceQuote(catalogKey);
        PriceQuote sellQuote = plugin.getShopManager().getSellToSystemQuote(entry.getTemplate());
        TradeMode tradeMode = plugin.getShopConfigService().getItemTradeMode(catalogKey);

        ItemStack template = entry.getTemplate();
        int maxStack = template != null ? template.getMaxStackSize() : 64;
        quantity = Math.max(1, Math.min(quantity, plugin.getShopManager().getMaxBuyAmount()));

        double unitPrice = buyQuote.price();
        double totalPrice = unitPrice * quantity;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📦 商品詳情：" + itemName)
                .setColor(new Color(241, 196, 15))
                .addField("標籤 ID", "`" + clampString(catalogKey, 60) + "`", true)
                .addField("交易模式", getTradeModeText(tradeMode), true)
                .addField("價格動態趨勢", buyQuote.formatTrend().isEmpty() ? "穩定" : buyQuote.formatTrend(), true)
                .addField("單價", tradeMode.allowsBuy() ? String.format("**$%.2f**", unitPrice) : "暫不開放購買", true)
                .addField("收購單價", tradeMode.allowsSell() ? String.format("**$%.2f**", sellQuote.price()) : "暫不開放收購", true)
                .addField("單組上限", maxStack + " 個", true);

        if (tradeMode.allowsBuy()) {
            embed.addField("預計購買數量", "**" + quantity + "** 個", true);
            embed.addField("總計金額", "**$" + String.format("%.2f", totalPrice) + "**", true);
        }

        embed.setFooter("請點擊下方按鈕選擇數量或確認購買 • 購買物品將直接傳至遊戲背包", null);

        List<LayoutComponent> layoutRows = new ArrayList<>();

        if (tradeMode.allowsBuy()) {
            List<ItemComponent> qtyRow = new ArrayList<>();
            if (maxStack > 1) {
                qtyRow.add(Button.secondary("shop:qty:" + shortKey + ":" + categoryId + ":" + page + ":1", "1個"));
                qtyRow.add(Button.secondary("shop:qty:" + shortKey + ":" + categoryId + ":" + page + ":" + maxStack, "1組 (" + maxStack + "個)"));
                qtyRow.add(Button.secondary("shop:qty:" + shortKey + ":" + categoryId + ":" + page + ":" + (maxStack * 4), "4組 (" + (maxStack * 4) + "個)"));
            } else {
                qtyRow.add(Button.secondary("shop:qty:" + shortKey + ":" + categoryId + ":" + page + ":1", "1個"));
                qtyRow.add(Button.secondary("shop:qty:" + shortKey + ":" + categoryId + ":" + page + ":2", "2個"));
                qtyRow.add(Button.secondary("shop:qty:" + shortKey + ":" + categoryId + ":" + page + ":5", "5個"));
            }
            qtyRow.add(Button.secondary("shop:qty_custom:" + shortKey + ":" + categoryId + ":" + page, "✏️ 自訂數量"));
            layoutRows.add(ActionRow.of(qtyRow));
        }

        var rootMenu = buildRootCategorySelectMenu(categoryId.startsWith("search@") ? null : categoryId);
        if (rootMenu != null) layoutRows.add(ActionRow.of(rootMenu));

        List<ItemComponent> actionRow = new ArrayList<>();
        Button backBtn;
        if (categoryId.startsWith("search@")) {
            String query = categoryId.substring("search@".length());
            backBtn = Button.secondary("shop:nav:search:" + query + ":" + page, "↩️ 返回搜尋結果");
        } else {
            backBtn = Button.secondary("shop:nav:cat:" + categoryId + ":" + page, "↩️ 返回列表");
        }
        actionRow.add(backBtn);

        if (tradeMode.allowsBuy()) {
            Button buyBtn = Button.success("shop:buy:" + shortKey + ":" + quantity,
                    String.format("🛒 確認購買 (%d個 · $%.2f)", quantity, totalPrice));
            actionRow.add(buyBtn);
        }

        layoutRows.add(ActionRow.of(actionRow));

        return new MessageEditBuilder()
                .setEmbeds(embed.build())
                .setComponents(layoutRows)
                .build();
    }

    private String getItemChineseName(CatalogEntry entry) {
        if (entry == null) return "未知物品";
        return plugin.getLocaleService().getFullDisplayName("zh_tw", entry);
    }

    private String getTradeModeText(TradeMode mode) {
        return switch (mode) {
            case BOTH -> "🛒 允許買賣";
            case BUY_ONLY -> "🛍️ 只賣不收 (玩家僅可購買)";
            case SELL_ONLY -> "🛑 只收不賣 (玩家僅可出售)";
            case DISABLED -> "❌ 暫停交易";
        };
    }

    private String getCategoryEmoji(String categoryId) {
        if (categoryId.contains("blocks")) return "🧱";
        if (categoryId.contains("tools")) return "🛠️";
        if (categoryId.contains("weapons")) return "⚔️";
        if (categoryId.contains("armor")) return "🛡️";
        if (categoryId.contains("food")) return "🍎";
        if (categoryId.contains("potions")) return "🧪";
        if (categoryId.contains("enchanted_books")) return "📚";
        if (categoryId.contains("redstone")) return "🔴";
        if (categoryId.contains("transport")) return "🛒";
        if (categoryId.contains("decorations")) return "🎨";
        if (categoryId.contains("spawn_eggs")) return "🥚";
        return "📦";
    }
}
