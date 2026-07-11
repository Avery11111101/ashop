package com.avery.shop.shop;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 批次賣給系統的結果
 */
public record SellBatchResult(
        double totalPaid,
        int soldCount,
        List<ItemStack> rejected
) {
    public boolean hasRejected() {
        return rejected != null && !rejected.isEmpty();
    }
}
