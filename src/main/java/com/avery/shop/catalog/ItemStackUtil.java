package com.avery.shop.catalog;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * ItemStack 完整序列化（保留 NBT / Data Components）
 */
public final class ItemStackUtil {

    private ItemStackUtil() {}

    public static String serialize(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        byte[] bytes = stack.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ItemStack deserialize(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(data);
        return ItemStack.deserializeBytes(bytes);
    }

    public static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
