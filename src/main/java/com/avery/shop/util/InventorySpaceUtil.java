package com.avery.shop.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public final class InventorySpaceUtil {

    private InventorySpaceUtil() {}

    public static boolean canFitStorage(Player player, Collection<ItemStack> stacks) {
        var storage = player.getInventory().getStorageContents();
        var simulated = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null) {
                simulated[i] = storage[i].clone();
            }
        }

        for (var stack : stacks) {
            if (stack == null || stack.getType().isAir()) continue;
            if (!addToSimulated(simulated, stack.clone())) {
                return false;
            }
        }
        return true;
    }

    private static boolean addToSimulated(ItemStack[] storage, ItemStack incoming) {
        int maxStack = incoming.getMaxStackSize();
        int remaining = incoming.getAmount();

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            var slot = storage[i];
            if (slot == null || slot.getType().isAir()) continue;
            if (!slot.isSimilar(incoming)) continue;
            int space = maxStack - slot.getAmount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining);
            slot.setAmount(slot.getAmount() + move);
            remaining -= move;
        }

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            if (storage[i] != null && !storage[i].getType().isAir()) continue;
            int place = Math.min(maxStack, remaining);
            var placed = incoming.clone();
            placed.setAmount(place);
            storage[i] = placed;
            remaining -= place;
        }

        return remaining <= 0;
    }
}
