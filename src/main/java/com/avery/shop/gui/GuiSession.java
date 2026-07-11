package com.avery.shop.gui;

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
        MAIN, CATEGORY, SEARCH, LISTINGS, SELL_TO_SYSTEM
    }

    private final Player player;
    private ViewType viewType = ViewType.MAIN;
    private ShopInventoryHolder shopHolder;
    private boolean sellConfirming;
    private String categoryId;
    private String searchQuery;
    private int page;
    private UUID pendingListingId;
    private boolean catalogBrowse;
    private final Map<Integer, UUID> slotListingMap = new HashMap<>();
    private final Map<Integer, String> slotCatalogMap = new HashMap<>();

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

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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
        slotCatalogMap.clear();
    }

    public boolean isCatalogBrowse() {
        return catalogBrowse;
    }

    public void setCatalogBrowse(boolean catalogBrowse) {
        this.catalogBrowse = catalogBrowse;
    }

    public Map<Integer, String> getSlotCatalogMap() {
        return slotCatalogMap;
    }

    public ShopInventoryHolder getShopHolder() {
        return shopHolder;
    }

    public void setShopHolder(ShopInventoryHolder shopHolder) {
        this.shopHolder = shopHolder;
    }

    public boolean isSellConfirming() {
        return sellConfirming;
    }

    public void setSellConfirming(boolean sellConfirming) {
        this.sellConfirming = sellConfirming;
    }

    public boolean isInShopGui() {
        return shopHolder != null
                && player.getOpenInventory().getTopInventory().getHolder() == shopHolder;
    }
}
