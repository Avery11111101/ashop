package com.avery.shop.catalog;

import org.bukkit.Material;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;

import java.util.EnumSet;
import java.util.Set;

/**
 * 判斷物品是否可在原版生存模式合法取得（用於預設全物品商店）
 */
public final class SurvivalObtainability {

    private static final Set<Material> EXCLUDED = EnumSet.of(
            Material.BARRIER,
            Material.LIGHT,
            Material.BEDROCK,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.END_PORTAL_FRAME,
            Material.SPAWNER,
            Material.TRIAL_SPAWNER,
            Material.VAULT,
            Material.REINFORCED_DEEPSLATE,
            Material.DEBUG_STICK,
            Material.KNOWLEDGE_BOOK,
            Material.BUDDING_AMETHYST,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.FROSTED_ICE
    );

    private SurvivalObtainability() {}

    public static boolean isObtainableInSurvival(CatalogEntry entry) {
        if (entry == null) return false;
        return isObtainableInSurvival(entry.getTemplate().getType(), entry);
    }

    public static boolean isObtainableInSurvival(Material material) {
        return isObtainableInSurvival(material, null);
    }

    private static boolean isObtainableInSurvival(Material material, CatalogEntry entry) {
        if (material == null || material.isAir() || material.isLegacy()) {
            return false;
        }
        if (EXCLUDED.contains(material)) {
            return false;
        }

        var name = material.name();
        if (name.endsWith("_SPAWN_EGG")) {
            return false;
        }
        if (name.contains("COMMAND_BLOCK")) {
            return false;
        }
        if (name.equals("LIGHT")) {
            return false;
        }
        if (name.contains("STRUCTURE_VOID") || name.equals("STRUCTURE_BLOCK") || name.equals("JIGSAW")) {
            return false;
        }

        // 生怪蛋變體（部分版本 meta 標記）
        if (entry != null && entry.getTemplate().hasItemMeta()) {
            var meta = entry.getTemplate().getItemMeta();
            if (meta instanceof SpawnEggMeta) {
                return false;
            }
            if (meta instanceof PotionMeta potionMeta) {
                var base = potionMeta.getBasePotionType();
                if (base == null) {
                    return false;
                }
            }
        }

        return true;
    }
}
