package com.avery.shop.gui;

import com.avery.shop.catalog.ItemCategory;
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
 * 商店 GUI 建構器（多語系）
 */
public final class ShopGui {

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 49;
    private static final int SEARCH_SLOT = 48;
    private static final int SELL_SLOT = 50;

    private ShopGui() {}

    public static void openMain(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.MAIN);
        session.setPage(0);
        session.clearSlotMap();

        var inv = Bukkit.createInventory(null, ROWS * 9,
                Component.text(locale.msg(player, "msg.shop.title"))
                        .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        int slot = 0;
        for (var category : ItemCategory.values()) {
            if (!manager.getPlugin().getConfig().getBoolean("categories." + category.getId(), true)) continue;

            var icon = new ItemStack(category.getIcon());
            var meta = icon.getItemMeta();
            meta.displayName(Component.text(locale.getCategoryName(player, category.getId()))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            int count = manager.getListingsByCategory(category).size();
            meta.lore(List.of(
                    Component.text(locale.msg(player, "msg.gui.category.count", count))
                            .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text(locale.msg(player, "msg.gui.category.click"))
                            .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
            ));
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }

        inv.setItem(SEARCH_SLOT, button(
                Material.COMPASS,
                locale.msg(player, "msg.gui.search.title"),
                locale.msg(player, "msg.gui.search.lore1"),
                locale.msg(player, "msg.gui.search.lore2")));

        inv.setItem(SELL_SLOT, button(
                Material.EMERALD,
                locale.msg(player, "msg.gui.sell.title"),
                locale.msg(player, "msg.gui.sell.lore1"),
                locale.msg(player, "msg.gui.sell.lore2")));

        inv.setItem(47, button(
                Material.BOOK,
                locale.msg(player, "msg.gui.my-listings"),
                locale.msg(player, "msg.gui.my-listings.lore")));

        player.openInventory(inv);
    }

    public static void openCategory(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.CATEGORY);
        session.clearSlotMap();

        var category = session.getCategory();
        var listings = manager.getListingsByCategory(category);
        var title = locale.getCategoryName(player, category.getId())
                + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);
        openListingPage(manager, player, session, listings, title);
    }

    public static void openSearch(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.SEARCH);
        session.clearSlotMap();

        var listings = manager.searchListings(player, session.getSearchQuery());
        var title = locale.msg(player, "msg.gui.search-result", session.getSearchQuery())
                + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);
        openListingPage(manager, player, session, listings, title);
    }

    public static void openMyListings(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        session.setViewType(GuiSession.ViewType.LISTINGS);
        session.clearSlotMap();

        var listings = manager.getPlayerListings(player.getUniqueId());
        var title = locale.msg(player, "msg.gui.my-listings-title")
                + " " + locale.msg(player, "msg.gui.page", session.getPage() + 1);
        openListingPage(manager, player, session, listings, title);
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

        var inv = Bukkit.createInventory(null, ROWS * 9, Component.text(title));

        for (int i = start; i < end; i++) {
            var listing = allListings.get(i);
            int slot = i - start;
            session.getSlotListingMap().put(slot, listing.getId());

            var display = listing.getItem().clone();
            var meta = display.getItemMeta();
            var lore = new ArrayList<Component>();

            var quote = manager.getPriceQuote(listing);
            var priceStr = manager.getEconomy().format(quote.price());
            if (manager.getPricing().isEnabled() && manager.getPricing().useDynamicForListing(listing)) {
                var trend = quote.trendSymbol();
                var change = String.format("%+.0f", quote.changePercent());
                lore.add(Component.text(locale.msg(player, "msg.gui.price-dynamic", priceStr, trend, change))
                        .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(locale.msg(player, "msg.gui.price", priceStr))
                        .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            }
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

        player.openInventory(inv);
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
