package com.avery.shop.gui;

import com.avery.shop.shop.ShopManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
    private final Map<UUID, Boolean> awaitingSearch = new HashMap<>();

    public GuiListener(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public GuiSession getOrCreateSession(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), id -> new GuiSession(player));
    }

    private GuiSession getActiveSession(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null) return null;
        if (!session.isInShopGui()) {
            if (!awaitingSearch.containsKey(player.getUniqueId())) {
                sessions.remove(player.getUniqueId());
            }
            return null;
        }
        return session;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var session = getActiveSession(player);
        if (session == null) return;

        if (session.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
            handleSellPanelClick(event, player, session);
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (session.getViewType()) {
            case MAIN -> handleMainClick(player, session, slot);
            case CATEGORY, SEARCH, LISTINGS -> handleListingClick(player, session, slot, event.getClick());
            default -> {}
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var session = getActiveSession(player);
        if (session == null || session.getViewType() != GuiSession.ViewType.SELL_TO_SYSTEM) return;

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot >= ShopGui.SELL_DEPOSIT_SIZE) {
                event.setCancelled(true);
                return;
            }
        }

        shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
            var active = getActiveSession(player);
            if (active != null && active.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
                ShopGui.refreshSellPanel(shopManager, player, player.getOpenInventory().getTopInventory());
            }
        });
    }

    private void handleSellPanelClick(InventoryClickEvent event, Player player, GuiSession session) {
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (rawSlot >= 0 && rawSlot < topSize) {
            if (rawSlot == ShopGui.SELL_CANCEL_SLOT) {
                event.setCancelled(true);
                returnDepositItems(player, event.getView().getTopInventory());
                player.closeInventory();
                ShopGui.openMain(shopManager, player, session);
                return;
            }
            if (rawSlot == ShopGui.SELL_CONFIRM_SLOT) {
                event.setCancelled(true);
                confirmSell(player, session, event.getView().getTopInventory());
                return;
            }
            if (rawSlot >= ShopGui.SELL_DEPOSIT_SIZE) {
                event.setCancelled(true);
                return;
            }
            // 投放區 0-44：允許放入/取出
            shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
                var active = getActiveSession(player);
                if (active != null && active.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
                    ShopGui.refreshSellPanel(shopManager, player, player.getOpenInventory().getTopInventory());
                }
            });
            return;
        }

        // 從玩家背包 shift+點擊放入
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
                var active = getActiveSession(player);
                if (active != null && active.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
                    ShopGui.refreshSellPanel(shopManager, player, player.getOpenInventory().getTopInventory());
                }
            });
        }
    }

    private void confirmSell(Player player, GuiSession session, org.bukkit.inventory.Inventory inv) {
        var locale = shopManager.getPlugin().getLocaleService();
        session.setSellConfirming(true);

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

        player.closeInventory();
        session.setSellConfirming(false);
        ShopGui.openMain(shopManager, player, session);
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

    private void handleMainClick(Player player, GuiSession session, int slot) {
        var locale = shopManager.getPlugin().getLocaleService();

        if (slot == ShopGui.getSearchSlot()) {
            awaitingSearch.put(player.getUniqueId(), true);
            session.setShopHolder(null);
            player.closeInventory();
            player.sendMessage("§e" + locale.msg(player, "msg.search.prompt"));
            player.sendMessage("§7" + locale.msg(player, "msg.search.example"));
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

        int catIndex = 0;
        for (var category : shopManager.getShopConfig().getCategories()) {
            if (!category.isEnabled()) continue;
            if (!shopManager.isCategoryVisible(category.getId())) continue;
            if (catIndex == slot) {
                session.setCategoryId(category.getId());
                session.setPage(0);
                ShopGui.openCategory(shopManager, player, session);
                return;
            }
            catIndex++;
        }
    }

    private void handleListingClick(Player player, GuiSession session, int slot, ClickType click) {
        var locale = shopManager.getPlugin().getLocaleService();

        if (slot == ShopGui.getBackSlot()) {
            ShopGui.openMain(shopManager, player, session);
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

        if (session.isCatalogBrowse() && catalogKey != null) {
            if (click != ClickType.LEFT) return;
            var result = shopManager.buyCatalogEntry(player, catalogKey);
            switch (result) {
                case SUCCESS -> {
                    player.sendMessage("§a" + locale.msg(player, "msg.buy.success"));
                    refreshListingView(player, session);
                }
                case NO_MONEY -> player.sendMessage("§c" + locale.msg(player, "msg.buy.no-money"));
                case NO_SPACE -> player.sendMessage("§c" + locale.msg(player, "msg.buy.no-space"));
                case ECONOMY_DISABLED -> player.sendMessage("§c" + locale.msg(player, "msg.buy.economy-disabled"));
                case NOT_FOUND -> player.sendMessage("§c" + locale.msg(player, "msg.buy.not-found"));
                default -> player.sendMessage("§c" + locale.msg(player, "msg.buy.failed"));
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

    private void refreshListingView(Player player, GuiSession session) {
        switch (session.getViewType()) {
            case CATEGORY -> ShopGui.openCategory(shopManager, player, session);
            case SEARCH -> ShopGui.openSearch(shopManager, player, session);
            case LISTINGS -> ShopGui.openMyListings(shopManager, player, session);
            default -> ShopGui.openMain(shopManager, player, session);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        if (!awaitingSearch.remove(player.getUniqueId())) return;

        event.setCancelled(true);
        var locale = shopManager.getPlugin().getLocaleService();
        var query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (query.isEmpty()) {
            player.sendMessage("§c" + locale.msg(player, "msg.search.empty"));
            return;
        }

        var minLen = shopManager.getPlugin().getConfig().getInt("search.min-length", 1);
        if (query.length() < minLen) {
            player.sendMessage("§c" + locale.msg(player, "msg.search.min-length", minLen));
            return;
        }

        shopManager.getPlugin().getServer().getScheduler().runTask(shopManager.getPlugin(), () -> {
            var session = getOrCreateSession(player);
            session.setSearchQuery(query);
            session.setPage(0);
            ShopGui.openSearch(shopManager, player, session);
            player.sendMessage("§a" + locale.msg(player, "msg.search.result", query));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var session = sessions.remove(player.getUniqueId());
        if (session != null && session.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM
                && session.getShopHolder() != null) {
            var inv = session.getShopHolder().getInventory();
            if (inv != null) {
                returnDepositItems(player, inv);
            }
        }
        awaitingSearch.remove(player.getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        var session = sessions.get(player.getUniqueId());
        if (session == null) return;

        if (event.getInventory().getHolder() != session.getShopHolder()) return;

        if (session.getViewType() == GuiSession.ViewType.SELL_TO_SYSTEM) {
            if (!session.isSellConfirming()) {
                returnDepositItems(player, event.getInventory());
            }
            session.setShopHolder(null);
            if (!awaitingSearch.containsKey(player.getUniqueId())) {
                sessions.remove(player.getUniqueId());
            }
            return;
        }

        session.setShopHolder(null);
        if (!awaitingSearch.containsKey(player.getUniqueId())) {
            sessions.remove(player.getUniqueId());
        }
    }
}
