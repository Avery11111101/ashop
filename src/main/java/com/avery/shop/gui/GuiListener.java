package com.avery.shop.gui;

import com.avery.shop.shop.ShopManager;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI 互動監聽器（多語系）
 */
public final class GuiListener implements Listener {

    private final ShopManager shopManager;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();

    public GuiListener(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public GuiSession getOrCreateSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new GuiSession(player));
    }

    /** 清除聊天輸入與子流程殘留狀態 */
    public void resetFlowState(Player player) {
        var id = player.getUniqueId();

        var session = sessions.get(id);
        if (session != null) {
            session.setPendingCatalogKey(null);
            session.setReturnViewType(null);
        }
    }

    private GuiSession getActiveSession(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null) return null;
        if (!session.isInShopGui()) {
            if (!player.isConversing()) {
                sessions.remove(player.getUniqueId());
            }
            return null;
        }
        return session;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // PDC 游標與殘留物品極限清理防呆
        if (ShopGui.isShopGuiItem(event.getCursor())) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
        }
        if (ShopGui.isShopGuiItem(event.getCurrentItem()) && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
            event.setCurrentItem(null);
        }

        if (shopManager.getPlugin().getConfig().getBoolean("bedrock.block-gui", false)
                && com.avery.shop.util.BedrockUtil.isBedrockPlayer(player)) {
            if (event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder || getActiveSession(player) != null) {
                event.setCancelled(true);
                player.closeInventory();
                var locale = shopManager.getPlugin().getLocaleService();
                player.sendMessage(locale.msg(player, "msg.cmd.bedrock-blocked"));
                return;
            }
        }


        var session = getActiveSession(player);
        if (session == null) return;


        if (session.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
            handleSellPanelClick(event, player, session);
            return;
        }

        if (session.getViewType() == GuiSession.ViewType.BUY_QUANTITY) {
            handleBuyQuantityClick(event, player, session);
            return;
        }

        if (session.getViewType() == GuiSession.ViewType.ADMIN_ITEM_EDIT) {
            handleAdminItemEditClick(event, player, session);
            return;
        }

        if (session.getViewType() == GuiSession.ViewType.ADMIN_CATEGORY_EDIT) {
            handleAdminCategoryEditClick(event, player, session);
            return;
        }

        if (session.getViewType() == GuiSession.ViewType.ADMIN_SETTINGS) {
            handleAdminSettingsClick(event, player, session, event.getClick());
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (session.getViewType()) {
            case MAIN -> handleMainClick(player, session, slot, event);
            case CATEGORY, SEARCH, LISTINGS, SELLABLE_ITEMS -> handleListingClick(player, session, slot, event);
            default -> {}
        }
    }

    /** 管理員 Shift+右鍵 或 滾輪中鍵 編輯 */
    private static boolean isAdminEditClick(InventoryClickEvent event) {
        var click = event.getClick();
        if (click == ClickType.SHIFT_RIGHT || click == ClickType.MIDDLE) {
            return true;
        }
        return event.isShiftClick() && click == ClickType.RIGHT;
    }

    private static boolean isPlainLeftClick(InventoryClickEvent event) {
        return event.getClick() == ClickType.LEFT && !event.isShiftClick();
    }

    private static boolean isShiftLeftClick(InventoryClickEvent event) {
        var click = event.getClick();
        if (click == ClickType.SHIFT_LEFT) {
            return true;
        }
        return event.isShiftClick() && click == ClickType.LEFT;
    }

    private static boolean isPlainRightClick(InventoryClickEvent event) {
        return event.getClick() == ClickType.RIGHT && !event.isShiftClick();
    }

    private static boolean isCatalogItemView(GuiSession session, String catalogKey) {
        if (catalogKey == null) {
            return false;
        }
        var view = session.getViewType();
        return (view == GuiSession.ViewType.SEARCH || view == GuiSession.ViewType.CATEGORY || view == GuiSession.ViewType.SELLABLE_ITEMS)
                && session.isCatalogBrowse();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (shopManager.getPlugin().getConfig().getBoolean("bedrock.block-gui", true)
                && com.avery.shop.util.BedrockUtil.isBedrockPlayer(player)) {
            if (event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder || getActiveSession(player) != null) {
                event.setCancelled(true);
                player.closeInventory();
                return;
            }
        }

        var session = getActiveSession(player);

        if (session == null || session.getViewType() != GuiSession.ViewType.SELL_TO_SYSTEM) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot >= ShopGui.SELL_DEPOSIT_SIZE) {
                event.setCancelled(true);
                return;
            }
        }

        scheduleSellPanelRefresh(player);
    }

    private void scheduleSellPanelRefresh(Player player) {
        shopManager.getPlugin().getServer().getScheduler().runTaskLater(shopManager.getPlugin(), () -> {
            if (!player.isOnline()) return;
            var view = player.getOpenInventory();
            if (!(view.getTopInventory().getHolder() instanceof ShopInventoryHolder holder)) return;
            if (holder.getKind() != ShopInventoryHolder.Kind.SELL) return;

            var session = sessions.get(player.getUniqueId());
            if (session == null || session.getViewType() != GuiSession.ViewType.SELL_TO_SYSTEM) return;

            ShopGui.refreshSellPanel(shopManager, player, view.getTopInventory());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSellPanelClickMonitor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.getViewType() != GuiSession.ViewType.SELL_TO_SYSTEM) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder holder)
                || holder.getKind() != ShopInventoryHolder.Kind.SELL) {
            return;
        }

        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot == ShopGui.SELL_CANCEL_SLOT || rawSlot == ShopGui.SELL_CONFIRM_SLOT) return;
        if (rawSlot >= 0 && rawSlot < topSize && rawSlot >= ShopGui.SELL_DEPOSIT_SIZE) return;

        scheduleSellPanelRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSellPanelDragMonitor(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.getViewType() != GuiSession.ViewType.SELL_TO_SYSTEM) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder holder)
                || holder.getKind() != ShopInventoryHolder.Kind.SELL) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot < ShopGui.SELL_DEPOSIT_SIZE) {
                scheduleSellPanelRefresh(player);
                return;
            }
        }
    }

    private void handleSellPanelClick(InventoryClickEvent event, Player player, GuiSession session) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (rawSlot >= 0 && rawSlot < topSize) {
            if (rawSlot == ShopGui.SELL_CANCEL_SLOT) {
                event.setCancelled(true);
                session.setPendingShopNavigation(true);
                returnDepositItems(player, event.getView().getTopInventory());
                player.closeInventory();
                ShopGui.openMain(shopManager, player, session);
                return;
            }
            if (rawSlot == ShopGui.SELL_FILL_ALL_SLOT) {
                event.setCancelled(true);
                handleFillAllSellable(player, event.getView().getTopInventory());
                return;
            }
            if (rawSlot == ShopGui.SELL_VIEW_SELLABLE_SLOT) {
                event.setCancelled(true);
                ShopGui.openSellableCatalog(shopManager, player, session, 0);
                return;
            }
            if (rawSlot == ShopGui.SELL_CONFIRM_SLOT) {
                event.setCancelled(true);
                if (session.isSellConfirming()) return;
                confirmSell(player, session, event.getView().getTopInventory());
                return;
            }
            if (rawSlot >= ShopGui.SELL_DEPOSIT_SIZE) {
                event.setCancelled(true);
                return;
            }
            // 投放區 0-44：允許放入/取出（價格由 MONITOR 事件刷新）
            return;
        }

        // 從玩家背包 shift+點擊放入
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            scheduleSellPanelRefresh(player);
        }
    }

    private void handleFillAllSellable(Player player, org.bukkit.inventory.Inventory topInv) {
        var locale = shopManager.getPlugin().getLocaleService();
        var pInv = player.getInventory();
        int movedStacks = 0;
        boolean full = false;

        for (int i = 0; i < 36; i++) {
            var item = pInv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            if (shopManager.canSellToSystem(item)) {
                var quote = shopManager.getSellToSystemQuote(item);
                if (!quote.available()) continue;

                int targetSlot = findFirstAvailableSlot(topInv, item);
                if (targetSlot == -1) {
                    full = true;
                    break;
                }

                var existing = topInv.getItem(targetSlot);
                if (existing == null || existing.getType().isAir()) {
                    topInv.setItem(targetSlot, item.clone());
                    pInv.setItem(i, null);
                } else {
                    int maxStack = existing.getMaxStackSize();
                    int space = maxStack - existing.getAmount();
                    if (space > 0) {
                        int transfer = Math.min(space, item.getAmount());
                        existing.setAmount(existing.getAmount() + transfer);
                        item.setAmount(item.getAmount() - transfer);
                        if (item.getAmount() <= 0) {
                            pInv.setItem(i, null);
                        }
                    }
                }
                movedStacks++;
            }
        }

        ShopGui.refreshSellPanel(shopManager, player, topInv);

        if (movedStacks > 0) {
            if (full) {
                player.sendMessage("§e" + locale.msg(player, "msg.gui.sell.fill-all.full"));
            } else {
                player.sendMessage("§a" + locale.msg(player, "msg.gui.sell.fill-all.success", movedStacks));
            }
        } else {
            player.sendMessage("§c" + locale.msg(player, "msg.gui.sell.fill-all.none"));
        }
    }

    private int findFirstAvailableSlot(org.bukkit.inventory.Inventory inv, ItemStack item) {
        for (int slot = 0; slot < ShopGui.SELL_DEPOSIT_SIZE; slot++) {
            var current = inv.getItem(slot);
            if (current == null || current.getType().isAir()) {
                return slot;
            }
            if (current.isSimilar(item) && current.getAmount() < current.getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    private void confirmSell(Player player, GuiSession session, org.bukkit.inventory.Inventory inv) {
        var locale = shopManager.getPlugin().getLocaleService();
        session.setSellConfirming(true);
        try {
            var result = shopManager.sellDepositToSystem(
                    player, inv, 0, ShopGui.SELL_DEPOSIT_SIZE - 1);

            if (result.rejected() != null) {
                for (var rejected : result.rejected()) {
                    giveItemBack(player, rejected);
                }
            }

            if (result.soldCount() > 0) {
                player.sendMessage("§a" + locale.msg(player, "msg.gui.sell.confirm-success",
                        shopManager.getEconomy().format(result.totalPaid()),
                        result.soldCount()));
                if (result.hasRejected()) {
                    player.sendMessage("§c" + locale.msg(player, "msg.gui.sell.rejected-return"));
                }
                if (shopManager.getPricing().isEnabled()) {
                    player.sendMessage("§7" + locale.msg(player, "msg.sell.price-hint"));
                }
            } else if (result.hasRejected()) {
                player.sendMessage("§c" + locale.msg(player, "msg.gui.sell.all-rejected"));
            } else {
                player.sendMessage("§c" + locale.msg(player, "msg.gui.sell.empty"));
            }

            session.setPendingShopNavigation(true);
            player.closeInventory();
            ShopGui.openMain(shopManager, player, session);
        } finally {
            session.setSellConfirming(false);
        }
    }

    private void returnDepositItems(Player player, org.bukkit.inventory.Inventory inv) {
        for (int slot = 0; slot < ShopGui.SELL_DEPOSIT_SIZE; slot++) {
            var stack = inv.getItem(slot);
            if (stack == null || stack.getType().isAir()) continue;
            giveItemBack(player, ShopManager.stripSellGuiLore(stack.clone()));
            inv.setItem(slot, null);
        }
    }

    private void giveItemBack(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void handleMainClick(Player player, GuiSession session, int slot, InventoryClickEvent event) {
        var locale = shopManager.getPlugin().getLocaleService();

        if (slot == ShopGui.getSearchSlot()) {
            session.setShopHolder(null);
            player.closeInventory();
            String promptText = "§e" + locale.msg(player, "msg.search.prompt") + "\n§e(英文或物品ID比較容易搜尋到，中文查不到不妨用物品ID)\n§7" + locale.msg(player, "msg.search.example");
            ChatPrompt.start(shopManager.getPlugin(), player, promptText, (query) -> {
                if (query.isEmpty()) {
                    player.sendMessage("§c" + locale.msg(player, "msg.search.empty"));
                    return;
                }
                var minLen = shopManager.getPlugin().getConfig().getInt("search.min-length", 1);
                if (query.length() < minLen) {
                    player.sendMessage("§c" + locale.msg(player, "msg.search.min-length", minLen));
                    return;
                }
                var activeSession = getOrCreateSession(player);
                activeSession.setSearchQuery(query);
                activeSession.setPage(0);
                ShopGui.openSearch(shopManager, player, activeSession);
                player.sendMessage("§a" + locale.msg(player, "msg.search.result", query));
            }, () -> {
                var activeSession = getOrCreateSession(player);
                ShopGui.openMain(shopManager, player, activeSession);
            });
            return;
        }

        if (slot == ShopGui.getSellSlot()) {
            if (!shopManager.isSellToSystemEnabled()) {
                player.sendMessage("§c" + locale.msg(player, "msg.sell.disabled"));
                return;
            }
            if (!player.hasPermission("shop.sell")) {
                player.sendMessage("§c" + locale.msg(player, "msg.sell.no-permission"));
                return;
            }
            ShopGui.openSellToSystem(shopManager, player, session);
            return;
        }

        if (slot == ShopAdminGui.ADMIN_SETTINGS_SLOT && player.hasPermission("shop.admin")) {
            session.setReturnViewType(GuiSession.ViewType.MAIN);
            ShopAdminGui.openAdminSettings(shopManager, player, session);
            return;
        }

        var categoryId = session.getSlotSubcategoryMap().get(slot);
        if (categoryId != null) {
            if (player.hasPermission("shop.admin") && isAdminEditClick(event)) {
                session.setReturnViewType(session.getViewType());
                ShopAdminGui.openAdminCategoryEdit(shopManager, player, session, categoryId);
                return;
            }
            session.setCategoryId(categoryId);
            session.setPage(0);
            ShopGui.openCategory(shopManager, player, session);
            return;
        }
    }

    private void navigateCategoryBack(Player player, GuiSession session) {
        if (session.getViewType() == GuiSession.ViewType.SELLABLE_ITEMS) {
            ShopGui.openSellToSystem(shopManager, player, session);
            return;
        }
        if (session.getViewType() == GuiSession.ViewType.CATEGORY && session.getCategoryId() != null) {
            var parent = shopManager.getShopConfig().getParentCategoryId(session.getCategoryId());
            if (parent != null) {
                session.setCategoryId(parent);
                session.setPage(0);
                ShopGui.openCategory(shopManager, player, session);
                return;
            }
        }
        ShopGui.openMain(shopManager, player, session);
    }

    private void handleListingClick(Player player, GuiSession session, int slot, InventoryClickEvent event) {
        var locale = shopManager.getPlugin().getLocaleService();
        var click = event.getClick();

        if (slot == ShopGui.getBackSlot()) {
            if (session.getViewType() == GuiSession.ViewType.SEARCH) {
                ShopGui.openMain(shopManager, player, session);
            } else {
                navigateCategoryBack(player, session);
            }
            return;
        }
        if (slot == ShopAdminGui.ADMIN_CATEGORY_SLOT && player.hasPermission("shop.admin")
                && session.getCategoryId() != null) {
            session.setReturnViewType(session.getViewType());
            ShopAdminGui.openAdminCategoryEdit(shopManager, player, session, session.getCategoryId());
            return;
        }
        if (slot == ShopGui.getPrevSlot()) {
            session.setPage(Math.max(0, session.getPage() - 1));
            refreshListingView(player, session);
            return;
        }
        if (slot == ShopGui.getNextSlot()) {
            session.setPage(session.getPage() + 1);
            refreshListingView(player, session);
            return;
        }

        var listingId = session.getSlotListingMap().get(slot);
        var catalogKey = session.getSlotCatalogMap().get(slot);
        var subcategoryId = session.getSlotSubcategoryMap().get(slot);

        if (subcategoryId != null) {
            if (player.hasPermission("shop.admin") && isAdminEditClick(event)) {
                session.setReturnViewType(session.getViewType());
                ShopAdminGui.openAdminCategoryEdit(shopManager, player, session, subcategoryId);
                return;
            }
            session.setCategoryId(subcategoryId);
            session.setPage(0);
            ShopGui.openCategory(shopManager, player, session);
            return;
        }

        if (isCatalogItemView(session, catalogKey)) {
            if (player.hasPermission("shop.admin") && isAdminEditClick(event)) {
                session.setReturnViewType(session.getViewType());
                ShopAdminGui.openAdminItemEdit(shopManager, player, session, catalogKey);
                return;
            }

            var entry = shopManager.getCatalog().getByKey(catalogKey);
            if (entry != null && !shopManager.getShopConfig().isItemPurchasable(entry)) {
                player.sendMessage("§c" + locale.msg(player, "msg.buy.category-disabled"));
                return;
            }

            session.setReturnViewType(session.getViewType());

            if (isShiftLeftClick(event)) {
                if (entry == null) return;
                executeCatalogBuy(player, session, catalogKey, entry.getTemplate().getMaxStackSize());
                return;
            }
            if (isPlainRightClick(event)) {
                ShopGui.openBuyQuantity(shopManager, player, session, catalogKey);
                return;
            }
            if (isPlainLeftClick(event)) {
                executeCatalogBuy(player, session, catalogKey, 1);
                return;
            }
            return;
        }

        if (!shopManager.isPlayerListingsEnabled()) return;
        if (listingId == null) return;

        if (click == ClickType.RIGHT) {
            if (shopManager.removeListing(player, listingId)) {
                player.sendMessage("§a" + locale.msg(player, "msg.remove.success"));
                refreshListingView(player, session);
            } else {
                player.sendMessage("§c" + locale.msg(player, "msg.remove.failed"));
            }
            return;
        }

        var result = shopManager.buyListing(player, listingId);
        switch (result) {
            case SUCCESS -> {
                player.sendMessage("§a" + locale.msg(player, "msg.buy.success"));
                refreshListingView(player, session);
            }
            case NO_MONEY -> player.sendMessage("§c" + locale.msg(player, "msg.buy.no-money"));
            case NO_SPACE -> player.sendMessage("§c" + locale.msg(player, "msg.buy.no-space"));
            case OWN_ITEM -> player.sendMessage("§c" + locale.msg(player, "msg.buy.own-item"));
            case ECONOMY_DISABLED -> player.sendMessage("§c" + locale.msg(player, "msg.buy.economy-disabled"));
            case NOT_FOUND -> player.sendMessage("§c" + locale.msg(player, "msg.buy.not-found"));
            default -> player.sendMessage("§c" + locale.msg(player, "msg.buy.failed"));
        }
    }

    private void handleAdminCategoryEditClick(InventoryClickEvent event, Player player, GuiSession session) {
        event.setCancelled(true);
        if (!player.hasPermission("shop.admin")) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        var categoryId = session.getCategoryId();
        if (categoryId == null) {
            returnFromAdminCategory(player, session);
            return;
        }

        var locale = shopManager.getPlugin().getLocaleService();
        var admin = shopManager.getAdminService();

        if (slot == ShopAdminGui.CATEGORY_BACK_SLOT) {
            returnFromAdminCategory(player, session);
            return;
        }
        if (slot == ShopAdminGui.CATEGORY_TOGGLE_BUY_SLOT) {
            var currentMode = shopManager.getShopConfig().getCategoryTradeModeLocal(categoryId);
            var nextMode = currentMode.next();
            if (admin.setCategoryTradeMode(shopManager.getCatalog(), categoryId, nextMode)) {
                var playerLocale = locale.getPlayerLocale(player);
                player.sendMessage("§a分類交易模式已更新為：" + nextMode.getDisplayName(playerLocale));
                ShopAdminGui.openAdminCategoryEdit(shopManager, player, session, categoryId);
            } else {
                player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.failed"));
            }
            return;
        }
        if (slot == ShopAdminGui.CATEGORY_REMOVE_SLOT) {
            if (admin.removeCategory(shopManager.getCatalog(), categoryId)) {
                player.sendMessage("§a已成功刪除分類 " + categoryId);
                session.setCategoryId(null);
                ShopGui.openMain(shopManager, player, session);
            } else {
                player.sendMessage("§c刪除分類失敗");
            }
            return;
        }
    }

    private void returnFromAdminCategory(Player player, GuiSession session) {
        var returnTo = session.getReturnViewType();
        session.setReturnViewType(null);
        if (returnTo == GuiSession.ViewType.CATEGORY && session.getCategoryId() != null) {
            session.setViewType(GuiSession.ViewType.CATEGORY);
            ShopGui.openCategory(shopManager, player, session);
        } else {
            ShopGui.openMain(shopManager, player, session);
        }
    }

    private void handleAdminItemEditClick(InventoryClickEvent event, Player player, GuiSession session) {
        event.setCancelled(true);
        if (!player.hasPermission("shop.admin")) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        var catalogKey = session.getPendingCatalogKey();
        if (catalogKey == null) {
            returnFromAdmin(player, session);
            return;
        }

        var locale = shopManager.getPlugin().getLocaleService();
        var admin = shopManager.getAdminService();

        if (slot == ShopAdminGui.ITEM_BACK_SLOT) {
            returnFromAdmin(player, session);
            return;
        }
        if (slot == ShopAdminGui.ITEM_SHOP_SETTINGS_SLOT) {
            session.setReturnViewType(GuiSession.ViewType.ADMIN_ITEM_EDIT);
            ShopAdminGui.openAdminSettings(shopManager, player, session);
            return;
        }
        if (slot == ShopAdminGui.ITEM_SET_PRICE_SLOT) {
            session.setShopHolder(null);
            player.closeInventory();
            String promptText = "§e" + locale.msg(player, "msg.gui.admin.item.price-prompt");
            ChatPrompt.start(shopManager.getPlugin(), player, promptText, (input) -> {
                handleAdminChat(player, locale, input, "ITEM_PRICE", catalogKey, null);
            }, () -> {
                var activeSession = getOrCreateSession(player);
                ShopAdminGui.openAdminItemEdit(shopManager, player, activeSession, catalogKey);
            });
            return;
        }
        if (slot == ShopAdminGui.ITEM_TOGGLE_SLOT) {
            var setting = shopManager.getShopConfig().findItemSetting(catalogKey).orElse(null);
            if (setting == null) return;
            var nextMode = setting.getTradeMode().next();
            if (admin.setItemTradeMode(shopManager.getCatalog(), catalogKey, nextMode)) {
                var playerLocale = locale.getPlayerLocale(player);
                player.sendMessage("§a商品交易模式已更新為：" + nextMode.getDisplayName(playerLocale));
                ShopAdminGui.openAdminItemEdit(shopManager, player, session, catalogKey);
            } else {
                player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.failed"));
            }
            return;
        }
        if (slot == ShopAdminGui.ITEM_REMOVE_SLOT) {
            if (admin.removeItem(shopManager.getCatalog(), catalogKey)) {
                player.sendMessage("§a" + locale.msg(player, "msg.gui.admin.item.remove-success"));
                session.setPendingCatalogKey(null);
                returnFromAdmin(player, session);
            } else {
                player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.failed"));
            }
        }
    }

    private void handleAdminSettingsClick(InventoryClickEvent event, Player player, GuiSession session,
                                          ClickType click) {
        event.setCancelled(true);
        if (!player.hasPermission("shop.admin")) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (slot == ShopGui.getBackSlot()) {
            returnFromAdminSettings(player, session);
            return;
        }

        var fieldId = session.getSlotAdminConfigMap().get(slot);
        if (fieldId == null) return;

        var admin = shopManager.getAdminService();
        var field = admin.getConfigField(fieldId).orElse(null);
        if (field == null) return;

        var locale = shopManager.getPlugin().getLocaleService();

        if (field.type() == com.avery.shop.shop.ShopAdminService.ConfigValueType.BOOLEAN) {
            if (admin.toggleConfigBoolean(fieldId)) {
                player.sendMessage("§a" + locale.msg(player, "msg.gui.admin.config.updated"));
                ShopAdminGui.openAdminSettings(shopManager, player, session);
            }
            return;
        }

        if (click == ClickType.SHIFT_LEFT) {
            session.setShopHolder(null);
            player.closeInventory();
            String promptText = "§e" + locale.msg(player, "msg.gui.admin.config.prompt",
                    locale.msg(player, "msg.gui.admin.config." + fieldId));
            ChatPrompt.start(shopManager.getPlugin(), player, promptText, (input) -> {
                handleAdminChat(player, locale, input, "CONFIG_VALUE", null, fieldId);
            }, () -> {
                var activeSession = getOrCreateSession(player);
                ShopAdminGui.openAdminSettings(shopManager, player, activeSession);
            });
            return;
        }

        boolean increase = click != ClickType.RIGHT;
        if (admin.adjustConfigNumber(fieldId, increase)) {
            player.sendMessage("§a" + locale.msg(player, "msg.gui.admin.config.updated"));
            ShopAdminGui.openAdminSettings(shopManager, player, session);
        }
    }

    private void returnFromAdmin(Player player, GuiSession session) {
        session.setPendingCatalogKey(null);
        var returnTo = session.getReturnViewType();
        session.setReturnViewType(null);
        if (returnTo == GuiSession.ViewType.SEARCH) {
            session.setViewType(GuiSession.ViewType.SEARCH);
            ShopGui.openSearch(shopManager, player, session);
        } else if (returnTo == GuiSession.ViewType.CATEGORY) {
            session.setViewType(GuiSession.ViewType.CATEGORY);
            ShopGui.openCategory(shopManager, player, session);
        } else {
            ShopGui.openMain(shopManager, player, session);
        }
    }

    private void returnFromAdminSettings(Player player, GuiSession session) {
        var returnTo = session.getReturnViewType();
        session.setReturnViewType(null);
        if (returnTo == GuiSession.ViewType.ADMIN_ITEM_EDIT && session.getPendingCatalogKey() != null) {
            ShopAdminGui.openAdminItemEdit(shopManager, player, session, session.getPendingCatalogKey());
        } else if (returnTo == GuiSession.ViewType.MAIN) {
            ShopGui.openMain(shopManager, player, session);
        } else {
            ShopGui.openMain(shopManager, player, session);
        }
    }

    private void handleBuyQuantityClick(InventoryClickEvent event, Player player, GuiSession session) {
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        var catalogKey = session.getPendingCatalogKey();
        if (catalogKey == null) {
            returnFromBuyQuantity(player, session);
            return;
        }

        var entry = shopManager.getCatalog().getByKey(catalogKey);
        if (entry == null) {
            player.sendMessage("§c" + shopManager.getPlugin().getLocaleService()
                    .msg(player, "msg.buy.not-found"));
            returnFromBuyQuantity(player, session);
            return;
        }

        if (slot == ShopGui.BUY_QTY_BACK_SLOT) {
            returnFromBuyQuantity(player, session);
            return;
        }
        if (slot == ShopGui.BUY_QTY_ONE_SLOT) {
            executeCatalogBuy(player, session, catalogKey, 1);
            return;
        }
        if (slot == ShopGui.BUY_QTY_STACK_SLOT) {
            executeCatalogBuy(player, session, catalogKey, entry.getTemplate().getMaxStackSize());
            return;
        }
        if (slot == ShopGui.BUY_QTY_CUSTOM_SLOT) {
            var locale = shopManager.getPlugin().getLocaleService();
            session.setShopHolder(null);
            player.closeInventory();
            String promptText = "§e" + locale.msg(player, "msg.buy-qty.prompt") + "\n§7" + locale.msg(player, "msg.buy-qty.prompt.hint", shopManager.getMaxBuyAmount());
            ChatPrompt.start(shopManager.getPlugin(), player, promptText, (input) -> {
                handleBuyQuantityChat(player, locale, input);
            }, () -> {
                var activeSession = getOrCreateSession(player);
                returnFromBuyQuantity(player, activeSession);
            });
        }
    }

    private void executeCatalogBuy(Player player, GuiSession session, String catalogKey, int amount) {
        var locale = shopManager.getPlugin().getLocaleService();
        var result = shopManager.buyCatalogEntry(player, catalogKey, amount);
        switch (result) {
            case SUCCESS -> {
                if (amount > 1) {
                    player.sendMessage("§a" + locale.msg(player, "msg.buy.success-qty", amount));
                } else {
                    player.sendMessage("§a" + locale.msg(player, "msg.buy.success"));
                }
                if (session.getViewType() == GuiSession.ViewType.BUY_QUANTITY) {
                    returnFromBuyQuantity(player, session);
                } else {
                    refreshListingView(player, session);
                }
            }
            case NO_MONEY -> player.sendMessage("§c" + locale.msg(player, "msg.buy.no-money"));
            case NO_SPACE -> player.sendMessage("§c" + locale.msg(player, "msg.buy.no-space"));
            case ECONOMY_DISABLED -> player.sendMessage("§c" + locale.msg(player, "msg.buy.economy-disabled"));
            case NOT_FOUND -> {
                player.sendMessage("§c" + locale.msg(player, "msg.buy.not-found"));
                if (session.getViewType() == GuiSession.ViewType.BUY_QUANTITY) {
                    returnFromBuyQuantity(player, session);
                }
            }
            default -> player.sendMessage("§c" + locale.msg(player, "msg.buy.failed"));
        }
    }

    private void returnFromBuyQuantity(Player player, GuiSession session) {
        session.setPendingCatalogKey(null);
        var returnTo = session.getReturnViewType();
        session.setReturnViewType(null);

        if (returnTo == GuiSession.ViewType.SEARCH) {
            session.setViewType(GuiSession.ViewType.SEARCH);
            ShopGui.openSearch(shopManager, player, session);
        } else if (returnTo == GuiSession.ViewType.CATEGORY) {
            session.setViewType(GuiSession.ViewType.CATEGORY);
            ShopGui.openCategory(shopManager, player, session);
        } else if (returnTo == GuiSession.ViewType.SELLABLE_ITEMS) {
            session.setViewType(GuiSession.ViewType.SELLABLE_ITEMS);
            ShopGui.openSellableCatalog(shopManager, player, session, session.getPage());
        } else {
            ShopGui.openMain(shopManager, player, session);
        }
    }

    private void refreshListingView(Player player, GuiSession session) {
        switch (session.getViewType()) {
            case CATEGORY -> ShopGui.openCategory(shopManager, player, session);
            case SEARCH -> ShopGui.openSearch(shopManager, player, session);
            case LISTINGS -> ShopGui.openMyListings(shopManager, player, session);
            case SELLABLE_ITEMS -> ShopGui.openSellableCatalog(shopManager, player, session, session.getPage());
            default -> ShopGui.openMain(shopManager, player, session);
        }
    }


    private void handleBuyQuantityChat(Player player,
                                       com.avery.shop.locale.LocaleService locale,
                                       String input) {
        int amount;
        try {
            amount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            player.sendMessage("§c" + locale.msg(player, "msg.buy-qty.invalid"));
            reopenBuyQuantityOrReturn(player);
            return;
        }

        if (amount < 1) {
            player.sendMessage("§c" + locale.msg(player, "msg.buy-qty.too-small"));
            reopenBuyQuantityOrReturn(player);
            return;
        }

        int maxBuy = shopManager.getMaxBuyAmount();
        if (amount > maxBuy) {
            player.sendMessage("§c" + locale.msg(player, "msg.buy-qty.too-large", maxBuy));
            reopenBuyQuantityOrReturn(player);
            return;
        }

        shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
            var session = sessions.get(player.getUniqueId());
            if (session == null) {
                session = getOrCreateSession(player);
            }
            var catalogKey = session.getPendingCatalogKey();
            if (catalogKey == null) {
                player.sendMessage("§c" + locale.msg(player, "msg.buy.not-found"));
                return;
            }
            executeCatalogBuy(player, session, catalogKey, amount);
        });
    }

    private void reopenBuyQuantityOrReturn(Player player) {
        shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
            var session = sessions.get(player.getUniqueId());
            if (session == null || session.getPendingCatalogKey() == null) {
                if (session != null) {
                    returnFromBuyQuantity(player, session);
                }
                return;
            }
            ShopGui.openBuyQuantity(shopManager, player, session, session.getPendingCatalogKey());
        });
    }

    private void handleAdminChat(Player player,
                                 com.avery.shop.locale.LocaleService locale,
                                 String input,
                                 String type,
                                 String catalogKey,
                                 String configFieldId) {
        shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
            var session = sessions.computeIfAbsent(player.getUniqueId(), id -> new GuiSession(player));
            var admin = shopManager.getAdminService();

            if ("ITEM_PRICE".equals(type)) {
                double price;
                try {
                    price = Double.parseDouble(input.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.item.price-invalid"));
                    ShopAdminGui.openAdminItemEdit(shopManager, player, session, catalogKey);
                    return;
                }
                if (price < 0) {
                    player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.item.price-invalid"));
                    ShopAdminGui.openAdminItemEdit(shopManager, player, session, catalogKey);
                    return;
                }
                if (admin.updateItemPrice(shopManager.getCatalog(), catalogKey, price)) {
                    player.sendMessage("§a" + locale.msg(player, "msg.gui.admin.item.price-success",
                            shopManager.getEconomy().format(price)));
                    ShopAdminGui.openAdminItemEdit(shopManager, player, session, catalogKey);
                } else {
                    player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.failed"));
                    returnFromAdmin(player, session);
                }
                return;
            }

            if ("CONFIG_VALUE".equals(type)) {
                if (admin.setConfigValue(configFieldId, input)) {
                    player.sendMessage("§a" + locale.msg(player, "msg.gui.admin.config.updated"));
                    ShopAdminGui.openAdminSettings(shopManager, player, session);
                } else {
                    player.sendMessage("§c" + locale.msg(player, "msg.gui.admin.config.invalid"));
                    ShopAdminGui.openAdminSettings(shopManager, player, session);
                }
            }
        });
    }

    public static void purgeGuiItemsFromPlayer(Player player) {
        if (player == null) return;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            var item = inv.getItem(i);
            if (ShopGui.isShopGuiItem(item)) {
                inv.setItem(i, null);
            }
        }
        if (ShopGui.isShopGuiItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var session = sessions.remove(player.getUniqueId());

        if (session == null || session.getViewType() != GuiSession.ViewType.SELL_TO_SYSTEM) {
            if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
                if (ShopGui.isShopGuiItem(player.getItemOnCursor()) || session != null) {
                    player.setItemOnCursor(null);
                }
            }
        }

        purgeGuiItemsFromPlayer(player);

        if (session != null && session.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM
                && session.getShopHolder() != null) {
            var inv = session.getShopHolder().getInventory();
            if (inv != null) {
                returnDepositItems(player, inv);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof ShopInventoryHolder holder) {
            if (holder.getKind() != ShopInventoryHolder.Kind.SELL) {
                if (player.getItemOnCursor() != null && !player.getItemOnCursor().getType().isAir()) {
                    player.setItemOnCursor(null);
                }
            }
        }

        purgeGuiItemsFromPlayer(player);

        var session = sessions.get(player.getUniqueId());
        if (session == null) return;

        if (event.getInventory().getHolder() != session.getShopHolder()) return;

        if (session.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
            if (session.isPendingShopNavigation()) {
                session.setPendingShopNavigation(false);
                session.setSellConfirming(false);
                session.setShopHolder(null);
                return;
            }
            if (!session.isSellConfirming()) {
                returnDepositItems(player, event.getInventory());
            }
            session.setShopHolder(null);
            if (!player.isConversing()) {
                sessions.remove(player.getUniqueId());
            }
            return;
        }

        if (session.getViewType() == GuiSession.ViewType.BUY_QUANTITY
                || session.getViewType() == GuiSession.ViewType.ADMIN_ITEM_EDIT
                || session.getViewType() == GuiSession.ViewType.ADMIN_CATEGORY_EDIT
                || session.getViewType() == GuiSession.ViewType.ADMIN_SETTINGS) {
            session.setShopHolder(null);
            if (!player.isConversing()) {
                session.setPendingCatalogKey(null);
                session.setReturnViewType(null);
                sessions.remove(player.getUniqueId());
            }
            return;
        }

        session.setShopHolder(null);
        if (!player.isConversing()) {
            sessions.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        purgeGuiItemsFromPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        var item = event.getItemInHand();
        if (ShopGui.isShopGuiItem(item)) {
            event.setCancelled(true);
            event.getPlayer().getInventory().setItemInMainHand(null);
            event.getPlayer().getInventory().setItemInOffHand(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        var item = event.getItemDrop().getItemStack();
        if (ShopGui.isShopGuiItem(item)) {
            event.setCancelled(true);
            event.getItemDrop().remove();
        }
    }
}
