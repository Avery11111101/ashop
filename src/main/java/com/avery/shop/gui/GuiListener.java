package com.avery.shop.gui;

import com.avery.shop.catalog.ItemCategory;
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
import org.bukkit.event.player.PlayerQuitEvent;

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var session = sessions.get(player.getUniqueId());
        if (session == null) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        switch (session.getViewType()) {
            case MAIN -> handleMainClick(player, session, slot);
            case CATEGORY, SEARCH, LISTINGS -> handleListingClick(player, session, slot, event.getClick());
            default -> {}
        }
    }

    private void handleMainClick(Player player, GuiSession session, int slot) {
        var locale = shopManager.getPlugin().getLocaleService();

        if (slot == ShopGui.getSearchSlot()) {
            awaitingSearch.put(player.getUniqueId(), true);
            player.closeInventory();
            player.sendMessage("§e" + locale.msg(player, "msg.search.prompt"));
            player.sendMessage("§7" + locale.msg(player, "msg.search.example"));
            return;
        }

        if (slot == ShopGui.getSellSlot()) {
            player.closeInventory();
            player.sendMessage("§e" + locale.msg(player, "msg.sell.hint"));
            return;
        }

        if (slot == 47) {
            session.setPage(0);
            ShopGui.openMyListings(shopManager, player, session);
            return;
        }

        var categories = ItemCategory.values();
        int catIndex = 0;
        for (var category : categories) {
            if (!shopManager.getPlugin().getConfig().getBoolean("categories." + category.getId(), true)) continue;
            if (catIndex == slot) {
                session.setCategory(category);
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
        var id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        awaitingSearch.remove(id);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // 保留 session 供搜尋後重新開啟
    }
}
