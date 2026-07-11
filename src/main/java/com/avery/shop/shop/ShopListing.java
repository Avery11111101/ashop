package com.avery.shop.shop;

import com.avery.shop.catalog.ItemStackUtil;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 玩家上架的商品
 */
public final class ShopListing {

    private final UUID id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;

    public ShopListing(UUID id, UUID sellerId, String sellerName, ItemStack item, double price, long createdAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String serializeItem() {
        return ItemStackUtil.serialize(item);
    }
}
