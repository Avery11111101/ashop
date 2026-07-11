package com.avery.shop.catalog;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.Locale;

/**
 * 比對兩個 ItemStack 是否為同一商品（含 NBT / Meta 變體）
 */
public final class ItemMatcher {

    private ItemMatcher() {}

    public static boolean matches(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (a.getAmount() != b.getAmount()) {
            // 上架比對忽略數量，僅比對物品本體
        }

        var metaA = a.getItemMeta();
        var metaB = b.getItemMeta();
        if (metaA == null && metaB == null) return true;
        if (metaA == null || metaB == null) return false;

        // 藥水：比對藥水類型
        if (metaA instanceof PotionMeta pmA && metaB instanceof PotionMeta pmB) {
            return pmA.getBasePotionType() == pmB.getBasePotionType()
                    && pmA.hasCustomEffects() == pmB.hasCustomEffects()
                    && (!pmA.hasCustomEffects() || pmA.getCustomEffects().equals(pmB.getCustomEffects()));
        }

        // 附魔書：比對儲存附魔
        if (metaA instanceof EnchantmentStorageMeta esmA && metaB instanceof EnchantmentStorageMeta esmB) {
            return esmA.getStoredEnchants().equals(esmB.getStoredEnchants());
        }

        // 一般物品：使用 Paper 位元組序列化比對（保留完整 NBT）
        return ItemStackUtil.serialize(a).equals(ItemStackUtil.serialize(b));
    }

    public static String fingerprint(ItemStack stack) {
        if (stack == null) return "air";
        var sb = new StringBuilder(stack.getType().name());

        var meta = stack.getItemMeta();
        if (meta instanceof PotionMeta pm && pm.getBasePotionType() != null) {
            sb.append(":potion:").append(pm.getBasePotionType().name());
        } else if (meta instanceof EnchantmentStorageMeta esm && !esm.getStoredEnchants().isEmpty()) {
            esm.getStoredEnchants().entrySet().stream()
                    .sorted((e1, e2) -> e1.getKey().getKey().compareTo(e2.getKey().getKey()))
                    .forEach(e -> sb.append(":ench:").append(e.getKey().getKey()).append(":").append(e.getValue()));
        } else {
            sb.append(":bytes:").append(ItemStackUtil.serialize(stack));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
