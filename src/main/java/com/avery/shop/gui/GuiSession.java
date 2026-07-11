package com.avery.shop.gui;

import com.avery.shop.catalog.ItemCategory;
import com.avery.shop.shop.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI 狀態管理
 */
public final class GuiSession {

    public enum ViewType {
        MAIN, CATEGORY, SEARCH, LISTINGS, BUY_CONFIRM
    }

    private final Player player;
    private ViewType viewType = ViewType.MAIN;
    private ItemCategory category;
    private String searchQuery;
    private int page;
    private UUID pendingListingId;
    private final Map<Integer, UUID> slotListingMap = new HashMap<>();

    public GuiSession(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public ViewType getViewType() {
        return viewType;
    }

    public void setViewType(ViewType viewType) {
        this.viewType = viewType;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public void setCategory(ItemCategory category) {
        this.category = category;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public UUID getPendingListingId() {
        return pendingListingId;
    }

    public void setPendingListingId(UUID pendingListingId) {
        this.pendingListingId = pendingListingId;
    }

    public Map<Integer, UUID> getSlotListingMap() {
        return slotListingMap;
    }

    public void clearSlotMap() {
        slotListingMap.clear();
    }
}
