package com.avery.shop.gui;

import com.avery.shop.catalog.CatalogEntry;
import com.avery.shop.shop.ShopListing;
import com.avery.shop.shop.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店 GUI 建構器（多語系、效能優化）
 */
public final class ShopGui {

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 49;
    private static final int SEARCH_SLOT = 48;
    private static final int SELL_SLOT = 50;

    public static final int SELL_DEPOSIT_SIZE = 45;
    public static final int SELL_CANCEL_SLOT = 45;
    public static final int SELL_TOTAL_SLOT = 49;
    public static final int SELL_CONFIRM_SLOT = 53;

    public static final int BUY_QTY_ONE_SLOT = 11;
    public static final int BUY_QTY_DISPLAY_SLOT = 13;
    public static final int BUY_QTY_STACK_SLOT = 15;
    public static final int BUY_QTY_CUSTOM_SLOT = 22;
    public static final int BUY_QTY_BACK_SLOT = 49;

    private ShopGui() {}

    static org.bukkit.inventory.Inventory createShopInventory(
            ShopInventoryHolder holder, int size, Component title, GuiSession session) {
        var inv = Bukkit.createInventory(holder, size, title);
        holder.bind(inv);
        session.setShopHolder(holder);
        return inv;
    }

    public static void openMain(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.MAIN);
        session.setCatalogBrowse(false);
        session.setPage(0);
        session.setPendingCatalogKey(null);
        session.setReturnViewType(null);
        session.clearSlotMap();
        manager.resetPlayerGuiFlow(player);

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.MAIN);
        var inv = createShopInventory(holder, ROWS * 9,
                Component.text(locale.msg(player, "msg.shop.title"))
                        .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                session);

        int slotIndex = 0;
        for (var category : manager.getShopConfig().getCategories()) {
            if (!category.isEnabled()) continue;
            if (!manager.isCategoryVisible(category.getId())) continue;

            int row = 1 + slotIndex / 5;
            int col = 2 + (slotIndex % 5);
            int slot = row * 9 + col;
            
            session.getSlotSubcategoryMap().put(slot, category.getId());

            var icon = new ItemStack(category.getIcon());
            var meta = icon.getItemMeta();
            meta.displayName(Component.text(manager.getShopConfig().getCategoryDisplayName(player, category.getId()))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            int count = manager.getCategoryDisplayCount(category.getId());
            meta.lore(buildSubcategoryLore(manager, player, locale, category.getId()));
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slotIndex++;
        }

        inv.setItem(SEARCH_SLOT, button(
                Material.COMPASS,
                locale.msg(player, "msg.gui.search.title"),
                locale.msg(player, "msg.gui.search.lore1"),
                locale.msg(player, "msg.gui.search.lore2")));

        inv.setItem(SELL_SLOT, button(
                Material.CHEST,
                locale.msg(player, "msg.gui.sell.title"),
                locale.msg(player, "msg.gui.sell.lore1"),
                locale.msg(player, "msg.gui.sell.lore2")));

        if (player.hasPermission("shop.admin")) {
            inv.setItem(ShopAdminGui.ADMIN_SETTINGS_SLOT, ShopAdminGui.adminMainButton(manager, player));
        }

        player.openInventory(inv);
    }

    public static void openSellToSystem(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.SELL_TO_SYSTEM);
        session.setCatalogBrowse(false);
        session.setSellConfirming(false);
        session.clearSlotMap();

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.SELL);
        var inv = createShopInventory(holder, ROWS * 9,
                Component.text(locale.msg(player, "msg.gui.sell-panel.title"))
                        .color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                session);

        var filler = fillerPane(locale.msg(player, "msg.gui.sell.deposit-hint"));
        for (int slot = SELL_CANCEL_SLOT; slot < ROWS * 9; slot++) {
            if (slot == SELL_CANCEL_SLOT || slot == SELL_TOTAL_SLOT || slot == SELL_CONFIRM_SLOT) {
                continue;
            }
            inv.setItem(slot, filler);
        }

        inv.setItem(SELL_CANCEL_SLOT, button(
                Material.RED_STAINED_GLASS_PANE,
                locale.msg(player, "msg.gui.sell.cancel"),
                locale.msg(player, "msg.gui.sell.cancel.lore")));

        inv.setItem(SELL_CONFIRM_SLOT, button(
                Material.LIME_STAINED_GLASS_PANE,
                locale.msg(player, "msg.gui.sell.confirm"),
                locale.msg(player, "msg.gui.sell.confirm.lore")));

        refreshSellPanel(manager, player, inv);
        player.openInventory(inv);
    }

    public static void refreshSellPanel(ShopManager manager, Player player, org.bukkit.inventory.Inventory inv) {
        var locale = manager.getPlugin().getLocaleService();
        double total = 0;
        int stackCount = 0;

        for (int slot = 0; slot < SELL_DEPOSIT_SIZE; slot++) {
            var stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            stackCount++;

            var display = stack.clone();
            var meta = display.getItemMeta();
            if (meta == null) {
                meta = Bukkit.getItemFactory().getItemMeta(display.getType());
                if (meta == null) continue;
            }

            var lore = new ArrayList<Component>();
            lore.add(Component.text("─────────")
                    .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));

            if (manager.canSellToSystem(display)) {
                var sellQuote = manager.getSellToSystemQuote(display);
                if (sellQuote.available()) {
                    var unit = sellQuote.price();
                    var subtotal = unit * display.getAmount();
                    total += subtotal;
                    if (manager.getPricing().isEnabled()) {
                        lore.add(Component.text(locale.msg(player, "msg.gui.sell.unit-price-dynamic",
                                        manager.getEconomy().format(unit),
                                        sellQuote.formatTrend(locale, player)))
                                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                    } else {
                        lore.add(Component.text(locale.msg(player, "msg.gui.sell.unit-price",
                                        manager.getEconomy().format(unit)))
                                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                    }
                    lore.add(Component.text(locale.msg(player, "msg.gui.sell.subtotal",
                                    manager.getEconomy().format(subtotal)))
                            .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text(locale.msg(player, "msg.gui.sell.rejected"))
                            .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
                }
            } else {
                lore.add(Component.text(locale.msg(player, "msg.gui.sell.rejected"))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot, display);
        }

        var totalItem = new ItemStack(Material.GOLD_INGOT);
        var totalMeta = totalItem.getItemMeta();
        totalMeta.displayName(Component.text(locale.msg(player, "msg.gui.sell.total"))
                .color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, false));

        var totalLore = new ArrayList<Component>();
        if (stackCount == 0) {
            totalLore.add(Component.text(locale.msg(player, "msg.gui.sell.empty"))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            totalLore.add(Component.text(locale.msg(player, "msg.gui.sell.total-amount",
                            manager.getEconomy().format(total)))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            totalLore.add(Component.text(locale.msg(player, "msg.gui.sell.stack-count", stackCount))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        totalMeta.lore(totalLore);
        totalItem.setItemMeta(totalMeta);
        inv.setItem(SELL_TOTAL_SLOT, totalItem);
        player.updateInventory();
    }

    static ItemStack fillerPane(String label) {
        var item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(" ")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(label)
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    public static void openCategory(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        player.sendActionBar(Component.text(locale.msg(player, "msg.gui.loading"))
                .color(NamedTextColor.GRAY));
        manager.getPlugin().getServer().getScheduler().runTask(manager.getPlugin(),
                () -> openCategorySync(manager, player, session));
    }

    private static void openCategorySync(ShopManager manager, Player player, GuiSession session) {
        session.setViewType(GuiSession.ViewType.CATEGORY);
        session.clearSlotMap();

        var categoryId = session.getCategoryId();
        var locale = manager.getPlugin().getLocaleService();
        var config = manager.getShopConfig();

        var children = config.getChildCategories(categoryId);
        if (!children.isEmpty()) {
            var title = config.getCategoryDisplayName(player, categoryId);
            openSubcategoryPage(manager, player, session, children, title);
            return;
        }

        if (manager.usesCatalogBrowse()) {
            session.setCatalogBrowse(true);
            var entries = manager.getCatalogByCategory(categoryId);
            var title = config.getCategoryDisplayName(player, categoryId)
                    + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);
            openCatalogPage(manager, player, session, entries, title);
        } else {
            session.setCatalogBrowse(false);
            var listings = manager.getListingsByCategory(categoryId);
            var title = config.getCategoryDisplayName(player, categoryId)
                    + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);
            openListingPage(manager, player, session, listings, title);
        }
    }

    private static void openSubcategoryPage(ShopManager manager, Player player, GuiSession session,
                                            List<com.avery.shop.shop.ShopCategoryDefinition> children,
                                            String title) {
        var locale = manager.getPlugin().getLocaleService();
        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.CATEGORY);
        var inv = createShopInventory(holder, ROWS * 9,
                Component.text(title).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                session);

        int slotIndex = 0;
        for (var category : children) {
            if (!category.isEnabled()) continue;
            if (!manager.isCategoryVisible(category.getId())) continue;

            int row = 1 + slotIndex / 5;
            int col = 2 + (slotIndex % 5);
            int slot = row * 9 + col;

            session.getSlotSubcategoryMap().put(slot, category.getId());

            var icon = new ItemStack(category.getIcon());
            var meta = icon.getItemMeta();
            meta.displayName(Component.text(manager.getShopConfig().getCategoryDisplayName(player, category.getId()))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            int count = manager.getCategoryDisplayCount(category.getId());
            meta.lore(buildSubcategoryLore(manager, player, locale, category.getId()));
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slotIndex++;
        }

        inv.setItem(BACK_SLOT, button(Material.BARRIER, locale.msg(player, "msg.gui.back")));
        if (player.hasPermission("shop.admin") && session.getCategoryId() != null) {
            inv.setItem(ShopAdminGui.ADMIN_CATEGORY_SLOT, ShopAdminGui.adminCategoryButton(manager, player));
        }
        player.openInventory(inv);
    }

    public static void openSearch(ShopManager manager, Player player, GuiSession session) {
        session.setViewType(GuiSession.ViewType.SEARCH);
        session.clearSlotMap();

        var locale = manager.getPlugin().getLocaleService();
        var title = locale.msg(player, "msg.gui.search-result", session.getSearchQuery())
                + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);

        if (manager.usesCatalogBrowse()) {
            session.setCatalogBrowse(true);
            openCatalogPage(manager, player, session,
                    manager.searchCatalog(player, session.getSearchQuery()), title);
        } else {
            session.setCatalogBrowse(false);
            openListingPage(manager, player, session,
                    manager.searchListings(player, session.getSearchQuery()), title);
        }
    }

    public static void openMyListings(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.LISTINGS);
        session.setCatalogBrowse(false);
        session.clearSlotMap();

        var listings = manager.getPlayerListings(player.getUniqueId());
        var title = locale.msg(player, "msg.gui.my-listings-title")
                + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);
        openListingPage(manager, player, session, listings, title);
    }

    private static void openCatalogPage(ShopManager manager, Player player, GuiSession session,
                                        List<CatalogEntry> allEntries, String title) {
        var locale = manager.getPlugin().getLocaleService();
        int page = session.getPage();
        int totalPages = Math.max(1, (int) Math.ceil(allEntries.size() / (double) PAGE_SIZE));
        page = Math.min(page, totalPages - 1);
        session.setPage(page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allEntries.size());

        var holderKind = session.getViewType() == GuiSession.ViewType.SEARCH
                ? ShopInventoryHolder.Kind.SEARCH
                : ShopInventoryHolder.Kind.CATEGORY;
        var holder = new ShopInventoryHolder(holderKind);
        var inv = createShopInventory(holder, ROWS * 9, Component.text(title), session);
        var systemName = locale.msg(player, "msg.system.shop-name");

        for (int i = start; i < end; i++) {
            var entry = allEntries.get(i);
            int slot = i - start;
            session.getSlotCatalogMap().put(slot, entry.getKey());

            var display = entry.getTemplate().clone();
            var meta = display.getItemMeta();
            var lore = new ArrayList<Component>();

            var quote = manager.getCatalogPriceQuote(entry.getKey());
            appendPriceLore(manager, player, locale, lore, quote,
                    manager.getPricing().isEnabled());

            lore.add(Component.text(locale.msg(player, "msg.gui.seller", systemName))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            var effectiveMode = manager.getShopConfig().getItemTradeMode(entry.getKey());
            if (effectiveMode.allowsBuy()) {
                lore.add(Component.text(locale.msg(player, "msg.gui.buy-one"))
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(locale.msg(player, "msg.gui.buy-stack"))
                        .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(locale.msg(player, "msg.gui.buy-custom"))
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            } else if (effectiveMode == com.avery.shop.shop.TradeMode.SELL_ONLY) {
                lore.add(Component.text("§e交易模式：只收不賣 (至 /shop sell 出售)")
                        .decoration(TextDecoration.ITALIC, false));
            } else if (effectiveMode == com.avery.shop.shop.TradeMode.DISABLED) {
                lore.add(Component.text("§c交易模式：暫不開放交易")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(locale.msg(player, "msg.gui.buy-disabled"))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            if (player.hasPermission("shop.admin")) {
                lore.add(Component.text(locale.msg(player, "msg.gui.admin.item.hint"))
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot, display);
        }

        addNavButtons(manager, player, session, inv, page, totalPages);
        if (session.getViewType() == GuiSession.ViewType.CATEGORY
                && player.hasPermission("shop.admin")
                && session.getCategoryId() != null) {
            inv.setItem(ShopAdminGui.ADMIN_CATEGORY_SLOT, ShopAdminGui.adminCategoryButton(manager, player));
        }
        player.openInventory(inv);
    }

    public static void openBuyQuantity(ShopManager manager, Player player, GuiSession session,
                                       String catalogKey) {
        var entry = manager.getCatalog().getByKey(catalogKey);
        if (entry == null) return;
        if (!manager.getShopConfig().isItemPurchasable(entry)) return;

        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.BUY_QUANTITY);
        session.setPendingCatalogKey(catalogKey);
        session.clearSlotMap();

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.BUY_QUANTITY);
        var inv = createShopInventory(holder, ROWS * 9,
                Component.text(locale.msg(player, "msg.gui.buy-qty.title"))
                        .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                session);

        var filler = fillerPane(" ");
        for (int slot = 0; slot < ROWS * 9; slot++) {
            inv.setItem(slot, filler);
        }

        var quote = manager.getCatalogPriceQuote(catalogKey);
        var unitStr = manager.getEconomy().format(quote.price());
        int maxStack = entry.getTemplate().getMaxStackSize();
        var stackTotal = manager.getEconomy().format(quote.price() * maxStack);

        var display = entry.getTemplate().clone();
        display.setAmount(1);
        var displayMeta = display.getItemMeta();
        var displayLore = new ArrayList<Component>();
        appendPriceLore(manager, player, locale, displayLore, quote,
                manager.getPricing().isEnabled());
        displayMeta.lore(displayLore);
        display.setItemMeta(displayMeta);
        inv.setItem(BUY_QTY_DISPLAY_SLOT, display);

        inv.setItem(BUY_QTY_ONE_SLOT, button(
                Material.LIME_STAINED_GLASS_PANE,
                locale.msg(player, "msg.gui.buy-qty.one"),
                locale.msg(player, "msg.gui.buy-qty.one.lore", unitStr)));

        inv.setItem(BUY_QTY_STACK_SLOT, button(
                Material.CHEST,
                locale.msg(player, "msg.gui.buy-qty.stack", maxStack),
                locale.msg(player, "msg.gui.buy-qty.stack.lore", maxStack, stackTotal)));

        inv.setItem(BUY_QTY_CUSTOM_SLOT, button(
                Material.WRITABLE_BOOK,
                locale.msg(player, "msg.gui.buy-qty.custom"),
                locale.msg(player, "msg.gui.buy-qty.custom.lore")));

        inv.setItem(BUY_QTY_BACK_SLOT, button(
                Material.ARROW,
                locale.msg(player, "msg.gui.back"),
                locale.msg(player, "msg.gui.buy-qty.back.lore")));

        player.openInventory(inv);
    }

    private static void openListingPage(ShopManager manager, Player player, GuiSession session,
                                         List<ShopListing> allListings, String title) {
        var locale = manager.getPlugin().getLocaleService();
        int page = session.getPage();
        int totalPages = Math.max(1, (int) Math.ceil(allListings.size() / (double) PAGE_SIZE));
        page = Math.min(page, totalPages - 1);
        session.setPage(page);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allListings.size());

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.LISTINGS);
        var inv = createShopInventory(holder, ROWS * 9, Component.text(title), session);

        for (int i = start; i < end; i++) {
            var listing = allListings.get(i);
            int slot = i - start;
            session.getSlotListingMap().put(slot, listing.getId());

            var display = listing.getItem().clone();
            var meta = display.getItemMeta();
            var lore = new ArrayList<Component>();

            var quote = manager.getPriceQuote(listing);
            appendPriceLore(manager, player, locale, lore, quote,
                    manager.getPricing().isEnabled() && manager.getPricing().useDynamicForListing(listing));

            lore.add(Component.text(locale.msg(player, "msg.gui.seller", listing.getSellerName()))
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(locale.msg(player, "msg.gui.buy"))
                    .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(locale.msg(player, "msg.gui.remove"))
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(slot, display);
        }

        addNavButtons(manager, player, session, inv, page, totalPages);
        player.openInventory(inv);
    }

    private static List<Component> buildSubcategoryLore(ShopManager manager, Player player,
                                                        com.avery.shop.locale.LocaleService locale,
                                                        String categoryId) {
        var lore = new ArrayList<Component>();
        int count = manager.getCategoryDisplayCount(categoryId);
        lore.add(Component.text(locale.msg(player, "msg.gui.category.count", count))
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        var effectiveMode = manager.getShopConfig().getCategoryTradeMode(categoryId);
        if (effectiveMode == com.avery.shop.shop.TradeMode.BUY_ONLY) {
            lore.add(Component.text("§e交易模式：只賣不收 (玩家僅可購買)")
                    .decoration(TextDecoration.ITALIC, false));
        } else if (effectiveMode == com.avery.shop.shop.TradeMode.SELL_ONLY) {
            lore.add(Component.text("§e交易模式：只收不賣 (至 /shop sell 出售)")
                    .decoration(TextDecoration.ITALIC, false));
        } else if (effectiveMode == com.avery.shop.shop.TradeMode.DISABLED) {
            lore.add(Component.text("§c交易模式：暫不開放交易")
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.text(locale.msg(player, "msg.gui.category.click"))
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        if (player.hasPermission("shop.admin")) {
            lore.add(Component.text(locale.msg(player, "msg.gui.admin.category.hint"))
                    .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    private static void appendPriceLore(ShopManager manager, Player player,
                                        com.avery.shop.locale.LocaleService locale,
                                        List<Component> lore,
                                        com.avery.shop.pricing.PriceQuote quote,
                                        boolean dynamic) {
        var priceStr = manager.getEconomy().format(quote.price());
        if (dynamic) {
            lore.add(Component.text(locale.msg(player, "msg.gui.price-dynamic",
                            priceStr, quote.formatTrend(locale, player)))
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(locale.msg(player, "msg.gui.price", priceStr))
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
    }

    private static void addNavButtons(ShopManager manager, Player player, GuiSession session,
                                      org.bukkit.inventory.Inventory inv, int page, int totalPages) {
        var locale = manager.getPlugin().getLocaleService();
        if (page > 0) {
            inv.setItem(PREV_SLOT, button(Material.ARROW,
                    locale.msg(player, "msg.gui.prev"),
                    locale.msg(player, "msg.gui.prev.lore", page)));
        }
        if (page < totalPages - 1) {
            inv.setItem(NEXT_SLOT, button(Material.ARROW,
                    locale.msg(player, "msg.gui.next"),
                    locale.msg(player, "msg.gui.next.lore", page + 2)));
        }
        inv.setItem(BACK_SLOT, button(Material.BARRIER, locale.msg(player, "msg.gui.back")));
    }

    public static ItemStack button(Material material, String name, String... lore) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name)
                .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            var loreComponents = new ArrayList<Component>();
            for (var line : lore) {
                loreComponents.add(Component.text(line)
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static int getPrevSlot() { return PREV_SLOT; }
    public static int getNextSlot() { return NEXT_SLOT; }
    public static int getBackSlot() { return BACK_SLOT; }
    public static int getSearchSlot() { return SEARCH_SLOT; }
    public static int getSellSlot() { return SELL_SLOT; }
    public static int getPageSize() { return PAGE_SIZE; }
}
