package com.avery.shop.shop;

import com.avery.shop.ShopPlugin;
import com.avery.shop.pricing.MarketStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 延遲非同步存檔 — 避免每次交易阻塞主線程
 */
public final class AsyncSaveService {

    private final ShopPlugin plugin;
    private final ShopManager shopManager;
    private volatile boolean dirty;
    private int taskId = -1;

    public AsyncSaveService(ShopPlugin plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public void markDirty() {
        dirty = true;
        scheduleSave();
    }

    private void scheduleSave() {
        if (taskId != -1) return;
        int delay = plugin.getConfig().getInt("performance.save-delay-ticks", 40);
        taskId = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            taskId = -1;
            if (!dirty) return;
            flushAsync();
            if (dirty) {
                scheduleSave();
            }
        }, delay).getTaskId();
    }

    private void flushAsync() {
        dirty = false;
        List<ShopListing> snapshot;
        Map<String, com.avery.shop.pricing.MarketData> marketSnapshot;
        synchronized (shopManager.getListingLock()) {
            snapshot = new ArrayList<>(shopManager.getListingsInternal());
            marketSnapshot = shopManager.getPricing().getMarketSnapshot();
        }
        ShopStorage.save(plugin, snapshot);
        MarketStorage.save(plugin, marketSnapshot);
    }

    /** 關服時同步寫入 */
    public void flushSync() {
        dirty = false;
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        synchronized (shopManager.getListingLock()) {
            ShopStorage.save(plugin, shopManager.getListingsInternal());
            shopManager.getPricing().save();
        }
    }
}
