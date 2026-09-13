# Changelog

All notable changes to ashop are documented here.  
ashop 的所有重要變更皆記錄於此。

## [1.8.0-beta.8] - 2026-09-13

### Fixed / 修復
- **Admin Add Item Custom Price Adjustment Vanishing Fix (修復管理員上架自訂物品調整售價時物品消失問題)** — Resolved GitHub Issue #1. In `GuiSession`, added session-scoped caching for the pending custom item (`pendingCustomItem`). When clicking the price setting button (`ADD_ITEM_PRICE_DISPLAY_SLOT`) to initiate the chat prompt, the item in the workbench slot 13 is safely preserved and restored upon reopen. Closing or canceling properly refunds the item to the player's inventory without item duplication or loss.  
  **修復管理員上架自訂物品調整售價時物品消失問題** — 修復 GitHub Issue #1。於 `GuiSession` 新增暫存自訂商品欄位（`pendingCustomItem`）。當管理員在自訂上架介面放入物品並點擊設定價格（Slot 22）彈出聊天欄時，系統安全暫存第 13 格物品並於價格設定完成重新開啟 GUI 時自動還原。退回或非對話關閉介面時亦完善執行背包退還與掉落防呆，徹底防止自訂物品遺失。

## [1.8.0-beta.7] - 2026-09-13

### Added / 新增
- **Main Menu Create Category Button (商店主選單新增頂層分類按鈕)** — Added an admin button (`ADMIN_CREATE_CATEGORY_MAIN_SLOT = 45`, Material: `CHEST`) to the bottom-left corner of the main shop menu. Admins with `shop.admin` permission can click to input a category ID and display name via chat prompt (supports `id:Display Name` or plain `Category Name`), instantly generating a new `shop/<categoryId>/items.yml` category and redirecting directly into it.  
  **商店主選單新增頂層分類按鈕** — 主畫面面板左下角（Slot 45，箱子圖示）新增「新增頂層分類」管理員按鈕。具備 `shop.admin` 權限之管理員點擊後，可於聊天欄輸入新分類 ID 與顯示名稱（支援 `分類ID:顯示名稱` 或純輸入 `分類名稱`），即時生成對應 `shop/<categoryId>/items.yml` 並無縫開啟該分類目錄。
- **In-Category Create Subcategory Button (分類頁面建立全新子分類按鈕)** — Added an admin button (`ADMIN_CREATE_SUBCATEGORY_SLOT = 46`, Material: `CHEST_MINECART`) across all category listing and subcategory navigation pages. Allows admins to dynamically create nested subcategories under the current parent category (e.g. `weapons/magic` or `minerals/rare`) via interactive chat prompt without manually creating folders in the file system.  
  **分類頁面建立全新子分類按鈕** — 在所有分類導航與商品瀏覽頁面的管理員控制列（Slot 46，儲物車圖示）新增「建立新子分類」按鈕。管理員可直接在目前所在的分類下即時建立子資料夾結構與 `items.yml`（例如在 `weapons` 下新增 `magic` 形成 `weapons/magic`），省去手動開啟伺服器後台建檔繁瑣流程。
- **Admin Custom Item Listing Flow Enhancement (管理員自訂物品上架機制無縫整合)** — Strengthened custom item listing workflow through GUI and backend services. Automatically generates category definitions if not pre-existing, normalizes stack items, preserves complete custom names, Lore, enchantments, and NBT attributes, and automatically navigates the admin to the target page upon listing.  
  **管理員自訂物品上架機制無縫整合** — 全面升級自訂物品上架流程與底層 API。支援放置任何自訂名稱、Lore、附魔與 NBT 神裝，若該分類尚未建立會自動補全基礎 YAML 結構，上架成功後立即重載記憶體快取並定位至該商品頁數。

## [1.8.0-beta.6] - 2026-09-13

### Added / 新增
- **Dual-Track Update Pipeline - Scheme C (GitHub Releases 雙重推播更新機制 - 方案 C)** — Upgraded `UpdateService` to fetch all releases via the GitHub API and parse both the latest Official Release (`latestOfficial`) and the latest Beta Pre-release (`latestBeta`). Admins running `/shop update [check]` receive dual-track notifications with release titles, formatted Markdown changelog summaries, and direct options to download either channel (`/shop update download release` or `/shop update download beta`).  
  **GitHub Releases 雙重推播更新機制 (方案 C)** — 全面重構 `UpdateService`，對接 GitHub Releases 清單 API，雙軌分辨並同時呈現最新「正式穩定版」與「搶先測試版」之版本資訊、更新日誌精選摘要與獨立下載指令（`/shop update download release` 與 `/shop update download beta`）。管理員進服時亦會顯示對應版本類型標記。
- **Dynamic In-Game Version & GitHub Release Notes Command (`/shop version`)** — Added `/shop version` (aliases: `/shop ver`, `/shop 查看版本`, `/shop 版本`) which dynamically fetches and displays the current plugin version's release title, publish timestamp, and full release notes directly from GitHub Releases, formatted with a custom Markdown-to-Minecraft chat color engine. Eliminates outdated hardcoded version strings.  
  **動態版本說明與更新日誌指令 (`/shop version`)** — 新增 `/shop version`（別名 `/shop ver`、`/shop 查看版本`、`/shop 版本`）。外掛非同步連線 GitHub Releases API 即時抓取對應版本之發布標題、時間與 Markdown 更新日誌，並以 Minecraft 專屬色彩排版輸出，徹底解決傳統外掛版本說明寫死容易過期的問題。
- **Physical Plugin Jar Replacement & Windows Lock Safe Handling (外掛 Jar 實體安全替換機制)** — When executing update download commands, the updater directly downloads the asset into `plugins/`. For differing filenames, the new jar is written immediately and the old file is scheduled for automatic unlinked deletion on JVM shutdown (`deleteOnExit()`). For identical filenames, atomic replacement or Bukkit `plugins/update/` staging is used to safely handle Windows file locking without crashing.  
  **外掛 Jar 實體安全替換機制** — 執行下載指令時直接將新版本寫入伺服器 `plugins/` 目錄。若檔名不同，新檔直接落地生效並標記舊檔於伺服器關機瞬間自動清除；若檔名相同且遭遇 Windows 檔案鎖定，自動轉存至 `plugins/update/` 於下次重啟時原生覆蓋，杜絕多版本 Jar 重疊衝突。
- **Config Migration Version 3 (設定檔無痛升級至 v3)** — Bumped `config-version` to `3` and added `updater.channel: RELEASE` (options: `RELEASE` or `BETA`). Existing server configs are safely backed up and seamlessly merged with new fields and comments.  
  **設定檔無痛升級至 v3** — 設定檔版本升級至 `3`，新增 `updater.channel` 更新通道偏好設定。既有服主升級時自動備份並保留所有舊數值。

## [1.8.0-beta.5] - 2026-09-12

### Added / 新增
- **Main Menu "All Items" Catalog View (主選單全部商品快捷瀏覽與管理)** — Added an "All Items" button (`ALL_ITEMS_SLOT = 49`, Material: `KNOWLEDGE_BOOK`) on the main shop menu. Players and admins can browse all available items across all enabled categories in a single unified paginated interface, supporting full purchasing, selling, price trend display, and admin direct item modification.  
  **主選單全部商品快捷瀏覽與管理** — 主選單第 5 行中間（Slot 49，知識之書圖示）新增「全部商品」快捷瀏覽功能。玩家與管理員可在此一站式分頁查看所有已啟用的商品，完整支援動態定價、趨勢顯示、購買、收購以及管理員快捷編輯。

### Fixed / 修復
- **Admin Custom Item Display on Container Categories (修復管理員自訂物品在上層分類頁面隱形未顯示的問題)** — Fixed an issue where items added to container categories (e.g. `weapons`, `tools`, `minerals`, `armor`, `blocks`, `food`) through the admin GUI workbench (Slot 47) or `/shop add` were not rendered in `openSubcategoryPage`. Container category pages now dynamically render subcategory folders in top rows and display direct custom items in remaining slots with full pricing lore and interactions.  
  **修復管理員自訂物品在上層分類頁面隱形未顯示問題** — 修復管理員透過 GUI 工作台（Slot 47）或 `/shop add` 上架商品至具有子分類的目錄（如武器、工具、礦產、防具、方塊等）後，`openSubcategoryPage` 僅顯示子分類資料夾而未渲染自訂物品的缺陷。現在上層分類介面會在頂行排列子分類資料夾，並於下方直接展示該分類之自訂商品，支援完整價格標籤、購買與管理操作。
- **Item Amount Normalization on Custom Item Listing & Fingerprinting (自訂物品堆疊數量規格化與防指紋雜湊偏移)** — Fixed item stack amounts > 1 affecting `ItemMatcher.fingerprint(ItemStack)` byte serialization and custom item registration in `ShopConfigService`. Stacks are now normalized to 1 count during fingerprinting and saving, and GUI automatically redirects admins to the target page upon listing.  
  **自訂物品堆疊數量規格化與防指紋雜湊偏移** — 修復管理員放置超過 1 個數量之物品至工作台時，導致 `ItemMatcher` 位元組雜湊偏移及上架範本數量異常的問題。上架與雜湊計算時均統一規格化為 1 個，且管理員上架完成後介面會自動跳轉至商品所在之目標頁數。
- **Automatic Leaf Subcategory Resolution in `/shop add` (指令上架自動解析最深層葉子子分類)** — When the category argument is omitted in `/shop add <price>`, the system now automatically resolves the leaf subcategory path via `ShopSubcategoryResolver` (e.g. Diamond Sword -> `weapons/swords`) instead of placing it into the top-level container.  
  **指令上架自動解析最深層葉子子分類** — 當使用 `/shop add <價格>` 省略分類參數時，系統會透過 `ShopSubcategoryResolver` 自動判定並導向至最底層子分類（如鑽石劍自動分類至 `weapons/swords`），避免直接寫入頂層容器。

## [1.8.0-beta.4] - 2026-09-12

### Fixed / 修復
- **Survival Price Model Netherite Recursion Fix (修復定價模型獄髓物品無限遞迴 StackOverflowError)** — Fixed infinite recursion in `SurvivalPriceModel` caused by matching on `NETHERITE_` prefix before validating tool/armor suffixes (`_HELMET`, `_SWORD`, etc.). Non-equipment netherite materials (`NETHERITE_INGOT`, `NETHERITE_SCRAP`, `NETHERITE_BLOCK`, `NETHERITE_UPGRADE_SMITHING_TEMPLATE`) now cleanly return base resource or pattern pricing, and `calculateBuyPrice(Material, ItemCategory)` overload was introduced for modular pricing.  
  **修復定價模型獄髓物品無限遞迴錯誤** — 修復 `SurvivalPriceModel` 中因在驗證工具/裝備後綴前過早匹配 `NETHERITE_` 前綴（如 `NETHERITE_INGOT` 獄髓錠），導致在計算獄髓鍛造升級配方時與 `res()` 方法相互呼叫引發 `StackOverflowError` 的問題。非裝備類獄髓物品現在能精準回傳基礎資源單價，並新增 `calculateBuyPrice(Material, ItemCategory)` 解耦多載。

## [1.8.0-beta.3] - 2026-09-12

### Added / 新增
- **GitHub Releases Auto-Updater & Safe Update Pipeline (GitHub Releases 自動更新系統與安全管道)** — Integrated asynchronous GitHub Releases checking and asset downloading (`/shop update` & `/shop update download`). Downloaded files are placed in `plugins/update/` to prevent Windows file locking issues during runtime and safely replace the plugin jar on server restart. Includes startup auto-check/auto-download toggles (disabled by default) and admin join notifications.  
  **GitHub Releases 自動更新系統與安全管道** — 整合 GitHub REST API 異步查詢與安全下載機制（支援 `/shop update` 檢查與 `/shop update download` 下載）。下載檔案存放於 `plugins/update/` 資料夾，解決 Windows 系統下 JVM 檔案鎖定無法覆蓋的難題，並於伺服器重啟時由伺服器核心原生安全替換。包含開機自動檢查／自動下載設定項（預設關閉）與管理員進服更新提示。
- **Seamless Config Migration & Comment Preservation Engine (設定檔無痛銜接升級與註解保留引擎)** — Created `ConfigMigrationService` with automatic version tracking (`config-version: 2`). Upon upgrading, existing user configurations (prices, multipliers, tokens, options) are 100% preserved while newly introduced configuration sections, default values, and full Traditional Chinese comments are seamlessly injected without comment stripping. Includes automatic backup generation (`config.backup-v1.yml`).  
  **設定檔無痛銜接升級與註解保留引擎** — 建立 `ConfigMigrationService` 與版本追蹤機制（`config-version: 2`）。外掛升級時 100% 保留服主原先自訂的數值（貨幣符號、價格倍率、Discord Token 等），並將新版本新增的設定項目與完整繁中註解自動注入合併，徹底解決 SnakeYAML 清除註解的通病，並自動產生備份檔案。

## [1.8.0-beta.2] - 2026-09-12

### Added / 新增
- **Dedicated Top-Level Resource Categories & Strict Filtering (頂層獨立五大基礎資源分類與精準過濾)** — Elevated 5 primary gatherable survival resources into independent top-level categories: Logs (`LOGS`), Pure Stones (`STONES`), Farm Crops (`CROPS`), Raw Meat (`RAW_MEAT`), and Minerals (`MINERALS`). Strict filtering ensures only direct mining/harvesting products are included, excluding processed stairs, slabs, ingots, compacted blocks, and cooked foods.  
  **頂層獨立五大基礎資源分類與精準過濾** — 將原木木材 (`LOGS`)、天然石材 (`STONES`)、農耕作物 (`CROPS`)、生鮮肉品 (`RAW_MEAT`) 與礦產原礦 (`MINERALS`) 提升為頂層主分類（Slot 0~4）。嚴密過濾機制確保僅納入純採集收穫物，排除階梯、半磚、石磚、金屬錠與合成方塊。
- **Selective Default Trade Mode Control (預設收購模式精確管控)** — Implemented selective default trade modes: only the 5 primary gatherable resource categories default to `TradeMode.BOTH` (buy & sell enabled). All remaining 12 categories (building blocks, tools, weapons, armor, cooked food, potions, books, redstone, transport, decorations, misc) default to `TradeMode.BUY_ONLY` to prevent economy collapse from mass selling non-resource or manufactured items.  
  **預設收購模式精確管控** — 僅 5 大基礎資源分類預設開啟系統收購 (`TradeMode.BOTH`)，其餘 12 大加工、裝備與衍生分類預設為只賣不收 (`TradeMode.BUY_ONLY`)，杜絕非採集商品無限傾銷沖垮伺服器經濟。
- **Documentation & Category Matrix (分類體系表與文檔更新)** — Added comprehensive default category matrix and hierarchy specifications to `README.md`.  
  **分類體系說明更新** — 於 `README.md` 新增完整預設 17 大分類表格與收購模式說明。

## [1.8.0-beta.1] - 2026-09-12

### Added / 新增
- **Dedicated Mineral Top-Level Category & Silk Touch Subcategories (頂層獨立礦物分類與絲綢鎬子分類)** — Added a dedicated top-level `MINERALS` category (Slot 0), cleanly split into `silk_touch` (ore blocks requiring silk touch to drop as blocks) and `non_silk_touch` (raw ores, ingots, gems, nuggets, and compressed blocks). Block category is now cleanly purged of mineral clutter.  
  **頂層獨立礦物分類與子分類** — 新增頂層獨立主分類「礦物」（Slot 0），並細分為「方塊類礦物（絲綢鎬採集）」與「無絲綢鎬採集礦物（粗礦、寶石、金屬錠等）」。原有方塊分類全面移出礦物，回歸乾淨建材。
- **Fortune-Proof Pricing Model & Rarity Rebalancing (防時運鎬套利定價模型與稀有度重構)** — Adjusted raw ore buying/selling price formulas and survival price anchor baselines to mathematically prevent infinite money exploits using Fortune III picks, while giving deepslate variants and rare ores (like deepslate emerald ore) authentic survival rarity value.  
  **防時運鎬套利定價模型與稀有度重構** — 嚴格調整原礦售價與收購比率，確保原礦售價高於時運 III 期望掉落價值，杜絕透過絲綢鎬速挖、時運鎬暴擊再賣回商店的洗錢漏洞；同時賦予深板岩綠寶石等罕見原礦應有的稀有度價值。
- **Admin Custom Item Selling Workbench & Quick Command (管理員自訂物品上架工作台與指令)** — Admins can now list and sell custom items (custom name, lore, enchants, custom model data, and NBT) via an intuitive 6-row GUI workbench (Slot 47) or quick command `/shop add <price> [category] [mode]`. Items are safely refunded on inventory close or disconnect to prevent loss.  
  **管理員自訂物品上架販售系統** — 提供管理員專屬 6 行 GUI 工作台（分類頁面 Slot 47）與 `/shop add <價格> [分類] [模式]` 指令，完整保留自訂物品之客製化名稱、Lore、附魔與 NBT 數據（以 Base64 持久化），並具備返回、關閉與斷線安全退還防吃裝機制。

## [1.7.4] - 2026-09-12

### Fixed / 修復
- **Sell Chest Item Lore Extraction Prevention (修復物品放進收購箱拿出來標籤殘留問題)** — Implemented PersistentDataContainer (PDC) tracking for sell preview lore, preserving original item lore without overwrite, and intercepting manual item takeout, cursor drags, inventory close, and hotbar swaps to ensure items returned to players are completely clean vanilla items.  
  **修復物品放進收購箱拿出來標籤殘留問題** — 採用 PDC 精確追蹤收購箱價格預覽 Lore 行數，完整保留原物品原有的自訂 Lore，並於點擊拿取、Shift 快速移動、快捷鍵交換、游標拖曳與介面關閉等所有途徑即時還原純淨原物，徹底杜絕單價/小計/───────── 標籤殘留於玩家物品上的問題。

## [1.7.3] - 2026-09-12

### Fixed / 修復
- **Repository Cleanup (儲存庫清理)** — Removed mistakenly tracked `ashop-*.jar` build artifacts from the repository root directory and added `/*.jar` to `.gitignore` to prevent future clutter.  
  **儲存庫清理** — 移除專案根目錄下誤追蹤的編譯產物 `ashop-*.jar`，並於 `.gitignore` 補上 `/*.jar` 規則，避免未來編譯結果再次意外推入儲存庫。

## [1.7.2] - 2026-08-15

### Fixed / 修復
- **Discord Shop Button Interaction Timeout & Parameter Inversion (修復 Discord 商店購買與數量按鈕無回應、超時未及時回應 Bug)** — Fixed mismatched parameter order in `shop:qty:` component ID between builder and listener, fixed missing custom quantity modal handler (`shop:qty_custom:`), aligned purchase button prefix (`shop:buy:`), and wrapped purchase processing with JDA `deferReply(true)` to guarantee instant gateway acknowledgment within milliseconds, completely eliminating the "未及時回應" (Interaction Failed) timeout.  
  **修復 Discord 商店購買與數量按鈕無回應及超時問題** — 修正 `DiscordPanelBuilder` 與 `DiscordShopListener` 在商品數量按鈕上的參數順序不一致問題、補齊自訂數量彈窗 (`shop:qty_custom:`) 互動邏輯、對齊確認購買按鈕 ID 前綴，並在購買交易處理時全面採用 `deferReply(true)` 非同步即時響應機制，徹底解決 Discord 提示「未及時回應」與點擊無效的異常。

## [1.7.1] - 2026-08-11

### Fixed / 修復
- **Bedrock Touch GUI Item Extraction Prevention (徹底修復觸控版/基岩版玩家拖曳 GUI 方塊離線帶走 Bug)** — Implemented PersistentDataContainer (PDC) item tagging (`ashop:gui_item`) on all shop GUI display items, auto-clearing cursor items on inventory close and player disconnect, and cancelling block placement and drop events if any GUI item ever reaches a player's inventory  
  **徹底修復觸控版/基岩版玩家拖曳 GUI 方塊離線帶走 Bug** — 為所有商店 GUI 面板顯示圖示注入 `ashop:gui_item` PDC 標籤，並於介面關閉、玩家離線與重新登入時自動抹除游標與背包內殘留的 GUI 物品；同時監聽放置方塊與丟棄物品事件徹底進行防呆攔截。

### Added / 新增
- **Bedrock Edition GUI Blocking Option (基岩版玩家 GUI 商店限制選項)** — Added `bedrock.block-gui` option in `config.yml` (default `false`), allowing server admins to optionally restrict Bedrock players to Discord bot purchasing if desired  
  **基岩版玩家 GUI 限制設定** — `config.yml` 新增 `bedrock.block-gui` 開關（預設為 `false`，開放全平台使用；管理員可視需要設為 `true` 限制基岩版僅能用 Discord 機器人購買）


---

## [1.7.0] - 2026-08-10


### Fixed / 修復
- **Discord Item Purchasing with Colons (修復 Discord 購買含冒號標籤商品失敗 Bug)** — Fixed an issue where clicking the purchase or quantity buttons on Discord for items containing colons (`:`) in their `catalogKey` (such as enchanted books `enchanted_book:ench:minecraft:luck_of_the_sea:3` or potions) split the key on colons, causing purchase validation to fail with "該商品目前未開放購買". Catalog keys containing colons are now shortened to clean hash keys (`k_...`), and component ID parsing handles colon-delimited parameters safely.  
  **修復 Discord 含冒號商品購買失敗** — 修復 Discord 點擊附魔書（如 `海洋的祝福 III`）、藥水等 `catalogKey` 包含冒號（`:`）之商品時，`componentId.split(":")` 會將 key 切碎導致傳入不完整 ID 報錯「該商品目前未開放購買」。現已強化短 Key 雜湊對照表機制並對冒號參數進行安全防呆組合。

### Added / 新增
- **Discord Online Shop Preview & Purchase (Discord 線上商店預覽與購買)** — Chinese slash command `/商店` (`/shop`), StringSelectMenu category & item browser with real-time dynamic pricing, DiscordSRV account binding verification, and inventory space pre-check protection  
  **Discord 線上商店面板與斜線指令** — 中文斜線指令 `/商店`，支援選單下拉切換 12 大分類與商品、預覽即時動態價格趨勢、DiscordSRV 帳號繫結與遊戲內背包空間防呆預檢直接發貨
- **Backward Compatible Config Auto-Migration (設定檔平滑無縫升級)** — Automatically populates missing `discord:` config fields on plugin enable without requiring user re-configuration  
  **舊設定檔向下相容** — 啟動時自動補充新增之 `discord:` 設定區塊，無需重新設定舊有 `config.yml`

---

### Added / 新增
- **Trade Mode Control (交易模式控制)** — Support `BOTH` (買賣皆可), `BUY_ONLY` (只賣不收), `SELL_ONLY` (只收不賣), `DISABLED` (禁用交易) per item & per category with tree inheritance  
  **商品與分類交易模式** — 支援單一商品與整個分類獨立設定「買賣皆可、只賣不收、只收不賣、禁用交易」，包含層級繼承與收購箱連動
- **Disabled Item Display (禁用交易顯示)** — Items in `DISABLED` mode remain visible in shop GUI, but purchase and sell actions are blocked with status hint  
  **禁用交易仍可瀏覽** — 設定為禁用交易之商品依然會顯示於商店 GUI 中供玩家查看，但禁止買賣並提示「暫不開放交易」
- **Middle Click Admin Edit (滾輪中鍵編輯)** — Admins can use Middle Click (mouse wheel) or Shift+Right Click on items/categories to open Edit GUI  
  **滾輪中鍵編輯** — 管理員除 Shift+右鍵外，亦可使用「滾輪中鍵」點擊物品或分類直接進入編輯選單
- **Independent Category Delete (獨立刪除分類選項)** — Added explicit Delete Category button in Category Admin GUI  
  **獨立刪除分類** — 分類編輯面板提供獨立刪除按鈕，可直接從 GUI 刪除整個分類資料夾

---

## [1.6.6] - 2026-07-11

### Added / 新增
- **Price cap indicators** — GUI and `/shop price` show `已達上限` / `已達下限` when min/max multiplier is hit  
  **漲跌停提示** — 觸及價格上下限時，GUI 與 `/shop price` 顯示「已達上限／下限」
- **Sell price trend** — sell panel and `/shop price` show buyback trend (e.g. `↓-12%`)  
  **收購價趨勢** — 收購面板與查價指令顯示收購價漲跌幅
- **Dual market counters** in `market-data.yml`: `buys`/`sells` (effective) and `total-buys`/`total-sells` (all trades)  
  **雙軌市場統計** — 有效計價次數與全部交易次數分開記錄

### Changed / 變更
- **Effective buy/sell at cap** — purchases while at max cap (or sales at min cap) no longer inflate effective counters; restoring base price only requires selling the effective amount that caused the cap (1:1 by default)  
  **漲停有效計次** — 漲停後多買、跌停後多賣不計入有效次數；賣出漲停前的有效數量即可恢復原價
- Default `per-sell-decrease` changed to `2.0` (matches `per-buy-increase` for 1:1 recovery)  
  預設 `per-sell-decrease` 改為 `2.0`，與 `per-buy-increase` 對齊（1:1 恢復）

---

## [1.6.5] - 2026-07-11

### Changed / 變更
- **Survival-based pricing** — prices derived from obtain method, crafting cost, and loot rarity  
  **生存定價** — 依取得方式、配方成本、戰利品稀有度計算基準價
- Calibrated anchors: Diorite 9/6, Diamond Pickaxe 1000/700, Mace 180000/120000, Elytra 130000/9500  
  校準基準：閃長岩、鑽石鎬、重錘、鞘翅
- Default `sell-ratio` changed to 2/3 (0.667); Elytra uses per-item sell-ratio override  
  預設收購比例改為 2/3；鞘翅另設低收購比例
- Admin: `/ashop resync-prices` recalculates existing `shop/` prices without wiping categories  
  管理員可用 `/ashop resync-prices` 重算現有商品價格

---

## [1.6.0] - 2026-07-11

### Added / 新增
- **Nested shop categories** — folders like `shop/blocks/building/wood/items.yml`; GUI browses subcategories like Creative mode  
  **巢狀子分類** — 支援 `shop/方塊/建築/木材/items.yml` 多層結構，GUI 像創造模式一樣逐層瀏覽
- Blocks split into building (wood/stone/copper), dyed (wool/carpet/concrete/glass…), natural (ores/leaves/terrain…), etc.  
  方塊細分為建築、染色、自然、功能性等子分類
- `/shop reset` generates full nested default tree for all 12 top categories  
  `/shop reset` 會產生完整巢狀預設分類樹

### Fixed / 修復
- Large categories (e.g. blocks) no longer freeze GUI on click — async open with loading hint  
  大型分類（如方塊）點擊不再卡住 — 非同步開啟並顯示載入提示

---

## [1.6.3] - 2026-07-11

### Changed / 變更
- Default shop (`/shop reset` & first-run seed) now only includes survival-obtainable items  
  預設商店僅含生存可取得物品，排除指令方塊、光源方塊、生怪蛋等
- Config: `shop.survival-only-defaults: true` (set false to restore old all-items behavior)  
  設定檔可關閉此過濾

### Fixed / 修復
- Shift+Right-click admin edit works in search results GUI  
  搜尋結果頁 Shift+右鍵管理員編輯

---

## [1.6.2] - 2026-07-11

### Added / 新增
- `/ashop help` full command & GUI tutorial (`/shop help` also works)  
  `/ashop help` 完整指令與 GUI 教學
- Auto-seed default full-item nested shop on first install when shop/ is empty  
  首次安裝且 shop/ 為空時，自動建立預設全物品商店

---

## [1.6.1] - 2026-07-11

### Added / 新增
- **Category purchase toggle** — `allow-buy: false` on any category blocks player purchases for that branch (inherited by subcategories)  
  **分類購買開關** — 在 `items.yml` 設 `allow-buy: false` 可禁止該分類（含所有子分類）的購買
- Admin category settings GUI (slot 48 or Shift+Right-click subcategory)  
  管理員分類設定 GUI（slot 48 或 Shift+右鍵子分類）

---

## [1.5.1] - 2026-07-11

### Fixed / 修復
- Unified price lookup and sell acceptance logic (no more “price shows OK but sell rejected”)  
  統一查價與收購判定，避免查價可收購但實際被拒
- GUI flow fixes: chat await cleanup, ESC no longer reopens sub-menus, sell confirm anti double-click  
  GUI 流程修復：清除聊天等待狀態、ESC 不再拉回子介面、收購確認防連點
- Admin item editor shows effective price after server exchange rate  
  管理員商品編輯顯示匯率換算後的實際單價
- Offline seller payments via Vault offline deposit  
  賣家離線時款項仍會入帳

---

## [1.2.6] - 2026-07-11

### Changed / 變更
- **System shop only** — players buy from / sell to system; player listings disabled by default  
  **純系統商店** — 玩家只能跟系統買賣，預設禁止玩家上架
- `/shop sell` now sells held item to system at dynamic buy price × `sell-ratio`  
  `/shop sell` 改為賣給系統，收購價 = 動態購買價 × 比例
- `/shop price` shows both system sell and buy prices  
  `/shop price` 同時顯示系統售價與收購價
- Removed「我的上架」from GUI  
  GUI 移除「我的上架」

---

## [1.2.5] - 2026-07-11

### Added / 新增
- **Shop category configs** — `plugins/ashop/shop/<category>/items.yml` auto-generated on first run  
  **分類商店設定** — 首次啟動自動建立 `shop/<分類>/items.yml`，管理員可直接編輯上下架與單項基準價
- `/shop reload` reloads shop category files  
  `/shop reload` 會重新載入 shop 分類設定

---

## [1.2.4] - 2026-07-11

### Added / 新增
- Custom language files in `plugins/ashop/locales/` with config-driven locale registration  
  支援 data 資料夾自訂語系檔，config 可登記任意語言代碼
- Auto-extract `_template.properties` for new custom languages  
  自訂語言首次載入自動產生翻譯範本

---

### Fixed / 修復
- **Major lag fix** — catalog browse mode, indexed lookups, debounced async saves  
  **大幅修復卡頓** — 目錄即時瀏覽、索引快取、延遲非同步存檔

### Changed / 變更
- Default `default-prices.mode: catalog` (no thousands of YAML listings)  
  預設改為 `catalog` 模式，不再建立數千筆系統上架
- Item matching O(1) via fingerprint index  
  物品比對改為 O(1) 指紋索引

---

### Changed / 變更
- Repositioned branding around **dynamic economy** as the core feature  
  品牌定位調整為以**動態經濟**為核心賣點
- README, plugin description, and GUI title updated  
  更新 README、插件描述與 GUI 標題

---

### Changed / 變更
- README defaults to Traditional Chinese; English version at `README.en.md` with switch button  
  README 預設繁體中文，英文版見 `README.en.md`（頂部按鈕切換）

---

## [1.2.0] - 2026-07-11

### Added / 新增
- **Dynamic pricing** — buy increases price, sell/list decreases, scarcity raises price  
  **動態定價** — 越買越貴、越賣越便宜、物以稀為貴
- Configurable float rates in `dynamic-pricing.*` (% per buy/sell/stock shortage)  
  `dynamic-pricing.*` 可設定浮動幅度（%）
- `/shop price` — check market price for held item  
  `/shop price` 查詢手持物品市價
- GUI shows price trend (↑/↓ +%)  
  GUI 顯示價格趨勢（↑/↓ +%）
- Market stats persisted in `market-data.yml`  
  市場統計持久化至 `market-data.yml`

### Changed / 變更
- `/shop sell` without price uses suggested dynamic price (if enabled)  
  `/shop sell` 省略價格時使用建議動態價

---

## [1.1.0] - 2026-07-11

### Added / 新增
- **Multi-language support** — UI messages, categories, and commands are localized  
  **多語言支援** — 介面訊息、分類、指令皆已本地化
- **`/lang` command** — switch language in-game (`zh_tw`, `en_us`)  
  **`/lang` 指令** — 遊戲內切換語言（`zh_tw`、`en_us`）
- Per-player language preference persisted in `player-locales.yml  
  玩家語言偏好持久化至 `player-locales.yml`
- Cross-locale item search (match names in any loaded language)  
  跨語系物品搜尋（任一已載入語言名稱皆可命中）
- Bilingual README and separate CHANGELOG  
  雙語 README 與獨立 CHANGELOG

### Changed / 變更
- `config.yml`: `locale` replaced by `languages.default` + `languages.available`  
  `config.yml`：`locale` 改為 `languages.default` + `languages.available`
- Category display names moved to locale files (`category.*`)  
  分類顯示名稱移至語系檔（`category.*`）

---

## [1.0.0] - 2026-07-11

### Added / 新增
- Initial release: full vanilla catalog, categories, search, NBT listing/buying  
  初版發布：全物品目錄、分類、搜尋、NBT 上架購買
- Enchanted book and potion variant support  
  附魔書與藥水變體支援
- Vault economy integration  
  Vault 經濟整合
