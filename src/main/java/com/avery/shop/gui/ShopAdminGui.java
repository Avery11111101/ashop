package com.avery.shop.gui;

import com.avery.shop.shop.ShopAdminService;
import com.avery.shop.shop.ShopManager;
import com.avery.shop.shop.TradeMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理員 GUI — 商品編輯與設定 UI
 */
public final class ShopAdminGui {

    public static final int ADMIN_SETTINGS_SLOT = 52;
    public static final int ADMIN_CATEGORY_SLOT = 48;

    public static final int CATEGORY_DISPLAY_SLOT = 13;
    public static final int CATEGORY_TOGGLE_BUY_SLOT = 22;
    public static final int CATEGORY_REMOVE_SLOT = 24;
    public static final int CATEGORY_BACK_SLOT = 49;

    public static final int ITEM_DISPLAY_SLOT = 13;
    public static final int ITEM_SET_PRICE_SLOT = 20;
    public static final int ITEM_TOGGLE_SLOT = 22;
    public static final int ITEM_REMOVE_SLOT = 24;
    public static final int ITEM_SHOP_SETTINGS_SLOT = 31;
    public static final int ITEM_BACK_SLOT = 49;

    private static final int ROWS = 6;

    private ShopAdminGui() {}

    public static void openAdminItemEdit(ShopManager manager, Player player, GuiSession session,
                                         String catalogKey) {
        var admin = manager.getAdminService();
        var resolved = admin.resolveForAdmin(catalogKey, manager.getCatalog()).orElse(null);
        if (resolved == null) {
            var locale = manager.getPlugin().getLocaleService();
            player.sendMessage("§c" + locale.msg(player, "msg.buy.not-found"));
            return;
        }

        var locale = manager.getPlugin().getLocaleService();
        var playerLocale = locale.getPlayerLocale(player);
        session.setViewType(GuiSession.ViewType.ADMIN_ITEM_EDIT);
        session.setPendingCatalogKey(catalogKey);
        session.clearSlotMap();

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.ADMIN_ITEM);
        var inv = ShopGui.createShopInventory(holder, ROWS * 9,
                Component.text(locale.msg(player, "msg.gui.admin.item.title"))
                        .color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                session);

        fillGray(inv, locale.msg(player, "msg.gui.admin.hint"));

        var setting = resolved.setting();
        var basePrice = setting.getPrice();
        var effectivePrice = manager.getShopConfig().applyServerPrice(basePrice);
        var effectiveTradeMode = manager.getShopConfig().getItemTradeMode(catalogKey);

        var display = resolved.entry().getTemplate().clone();
        display.setAmount(1);
        var meta = display.getItemMeta();
        if (meta != null) {
            var lore = new ArrayList<Component>();
            lore.add(Component.text(locale.msg(player, "msg.gui.admin.item.base-price",
                            manager.getEconomy().format(basePrice)))
                    .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(locale.msg(player, "msg.gui.admin.item.effective-price",
                            manager.getEconomy().format(effectivePrice)))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("交易模式: " + effectiveTradeMode.getDisplayName(playerLocale))
                    .color(tradeModeColor(effectiveTradeMode))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(ITEM_DISPLAY_SLOT, display);

        inv.setItem(ITEM_SET_PRICE_SLOT, ShopGui.button(
                Material.GOLD_INGOT,
                locale.msg(player, "msg.gui.admin.item.set-price"),
                locale.msg(player, "msg.gui.admin.item.set-price.lore")));

        var itemMode = setting.getTradeMode();
        inv.setItem(ITEM_TOGGLE_SLOT, ShopGui.button(
                tradeModeIcon(itemMode),
                "交易模式：" + itemMode.getDisplayName(playerLocale),
                "§7點擊切換模式 (買賣皆可 -> 只賣不收 -> 只收不賣 -> 禁用)"));

        inv.setItem(ITEM_REMOVE_SLOT, ShopGui.button(
                Material.BARRIER,
                locale.msg(player, "msg.gui.admin.item.remove"),
                locale.msg(player, "msg.gui.admin.item.remove.lore")));

        inv.setItem(ITEM_SHOP_SETTINGS_SLOT, ShopGui.button(
                Material.COMPARATOR,
                locale.msg(player, "msg.gui.admin.settings.title"),
                locale.msg(player, "msg.gui.admin.settings.open.lore")));

        inv.setItem(ITEM_BACK_SLOT, ShopGui.button(
                Material.ARROW,
                locale.msg(player, "msg.gui.back"),
                locale.msg(player, "msg.gui.admin.item.back.lore")));

        player.openInventory(inv);
    }

    public static void openAdminCategoryEdit(ShopManager manager, Player player, GuiSession session,
                                             String categoryId) {
        var config = manager.getShopConfig();
        var data = config.getCategory(categoryId).orElse(null);
        if (data == null) {
            player.sendMessage("§c" + manager.getPlugin().getLocaleService().msg(player, "msg.buy.not-found"));
            return;
        }

        var locale = manager.getPlugin().getLocaleService();
        var playerLocale = locale.getPlayerLocale(player);
        session.setViewType(GuiSession.ViewType.ADMIN_CATEGORY_EDIT);
        session.setCategoryId(categoryId);
        session.clearSlotMap();

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.ADMIN_CATEGORY);
        var inv = ShopGui.createShopInventory(holder, ROWS * 9,
                Component.text(locale.msg(player, "msg.gui.admin.category.title"))
                        .color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                session);

        fillGray(inv, locale.msg(player, "msg.gui.admin.hint"));

        var definition = data.getDefinition();
        var display = new ItemStack(definition.getIcon());
        display.setAmount(1);
        var meta = display.getItemMeta();
        if (meta != null) {
            var lore = new ArrayList<Component>();
            lore.add(Component.text(config.getCategoryDisplayName(player, categoryId))
                    .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            appendCategoryBuyLore(manager, player, locale, lore, categoryId);
            meta.lore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(CATEGORY_DISPLAY_SLOT, display);

        var currentMode = definition.getTradeMode();
        boolean hasDiffering = config.hasDifferingChildTradeModes(categoryId);

        var toggleLore = new ArrayList<String>();
        toggleLore.add("§7點擊切換分類交易模式 (買賣皆可 -> 只賣不收 -> 只收不賣 -> 禁用)");
        if (hasDiffering) {
            toggleLore.add("§e⚠️ 提示：下轄包含混合/自訂交易模式 (部分子分類或商品已被單獨調整)");
            toggleLore.add("§c (再次點擊調整父分類時，所有子分類與商品將強制統一為新模式)");
        } else {
            toggleLore.add("§7(點擊調整時，將同步將所有子分類與商品設為相同模式)");
        }

        inv.setItem(CATEGORY_TOGGLE_BUY_SLOT, ShopGui.button(
                tradeModeIcon(currentMode),
                "分類交易模式：" + currentMode.getDisplayName(playerLocale),
                toggleLore.toArray(String[]::new)));

        inv.setItem(CATEGORY_REMOVE_SLOT, ShopGui.button(
                Material.BARRIER,
                "§c刪除此分類",
                "§7點擊將完全刪除此分類及其設定檔"));

        inv.setItem(CATEGORY_BACK_SLOT, ShopGui.button(
                Material.ARROW,
                locale.msg(player, "msg.gui.back"),
                locale.msg(player, "msg.gui.admin.category.back.lore")));

        player.openInventory(inv);
    }

    static void appendCategoryBuyLore(ShopManager manager, Player player,
                                      com.avery.shop.locale.LocaleService locale,
                                      List<Component> lore, String categoryId) {
        var config = manager.getShopConfig();
        var playerLocale = locale.getPlayerLocale(player);
        var localMode = config.getCategoryTradeModeLocal(categoryId);
        var effectiveMode = config.getCategoryTradeMode(categoryId);

        lore.add(Component.text("此分類模式: " + localMode.getDisplayName(playerLocale))
                .color(tradeModeColor(localMode))
                .decoration(TextDecoration.ITALIC, false));

        lore.add(Component.text("繼承有效模式: " + effectiveMode.getDisplayName(playerLocale))
                .color(tradeModeColor(effectiveMode))
                .decoration(TextDecoration.ITALIC, false));

        if (config.hasDifferingChildTradeModes(categoryId)) {
            lore.add(Component.text("⚠️ 提示：包含子分類/商品自訂模式設定")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }

        config.findBuyBlockedByAncestor(categoryId).ifPresent(blocker -> {
            if (!blocker.equals(categoryId)) {
                lore.add(Component.text(locale.msg(player, "msg.gui.admin.category.inherited-from",
                                config.getCategoryDisplayName(player, blocker)))
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }
        });

        lore.add(Component.text(locale.msg(player, "msg.gui.admin.category.inherit-hint"))
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
    }

    private static Material tradeModeIcon(TradeMode mode) {
        return switch (mode) {
            case BOTH -> Material.LIME_DYE;
            case BUY_ONLY -> Material.GOLD_INGOT;
            case SELL_ONLY -> Material.HOPPER;
            case DISABLED -> Material.GRAY_DYE;
        };
    }

    private static NamedTextColor tradeModeColor(TradeMode mode) {
        return switch (mode) {
            case BOTH -> NamedTextColor.GREEN;
            case BUY_ONLY -> NamedTextColor.GOLD;
            case SELL_ONLY -> NamedTextColor.AQUA;
            case DISABLED -> NamedTextColor.RED;
        };
    }

    public static ItemStack adminCategoryButton(ShopManager manager, Player player) {
        return ShopGui.button(
                Material.COMPARATOR,
                manager.getPlugin().getLocaleService().msg(player, "msg.gui.admin.category.button"),
                manager.getPlugin().getLocaleService().msg(player, "msg.gui.admin.category.button.lore"));
    }

    public static void openAdminSettings(ShopManager manager, Player player, GuiSession session) {
        var locale = manager.getPlugin().getLocaleService();
        var admin = manager.getAdminService();

        session.setViewType(GuiSession.ViewType.ADMIN_SETTINGS);
        session.clearSlotMap();

        var holder = new ShopInventoryHolder(ShopInventoryHolder.Kind.ADMIN_SETTINGS);
        var inv = ShopGui.createShopInventory(holder, ROWS * 9,
                Component.text(locale.msg(player, "msg.gui.admin.settings.title"))
                        .color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                session);

        fillGray(inv, locale.msg(player, "msg.gui.admin.settings.hint"));

        int slot = 0;
        for (var field : admin.getConfigFields()) {
            if (slot >= 45) break;
            session.getSlotAdminConfigMap().put(slot, field.id());
            inv.setItem(slot++, configFieldButton(manager, player, field, admin));
        }

        inv.setItem(ShopGui.getBackSlot(), ShopGui.button(
                Material.ARROW,
                locale.msg(player, "msg.gui.back"),
                locale.msg(player, "msg.gui.admin.settings.back.lore")));

        player.openInventory(inv);
    }

    public static ItemStack configFieldButton(ShopManager manager, Player player,
                                              ShopAdminService.ConfigField field,
                                              ShopAdminService admin) {
        var locale = manager.getPlugin().getLocaleService();
        var value = admin.formatConfigValue(field);
        var nameKey = "msg.gui.admin.config." + field.id();
        var lore = new ArrayList<String>();
        lore.add(locale.msg(player, "msg.gui.admin.config.current", value));

        if (field.type() == ShopAdminService.ConfigValueType.BOOLEAN) {
            lore.add(locale.msg(player, "msg.gui.admin.config.toggle"));
        } else {
            lore.add(locale.msg(player, "msg.gui.admin.config.left", field.step()));
            lore.add(locale.msg(player, "msg.gui.admin.config.right", field.step()));
            lore.add(locale.msg(player, "msg.gui.admin.config.shift-left"));
        }

        return ShopGui.button(Material.PAPER, locale.msg(player, nameKey), lore.toArray(String[]::new));
    }

    public static ItemStack adminMainButton(ShopManager manager, Player player) {
        var locale = manager.getPlugin().getLocaleService();
        return ShopGui.button(
                Material.NETHER_STAR,
                locale.msg(player, "msg.gui.admin.settings.title"),
                locale.msg(player, "msg.gui.admin.settings.main.lore"));
    }

    private static void fillGray(org.bukkit.inventory.Inventory inv, String hint) {
        var filler = ShopGui.fillerPane(hint);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }
}
