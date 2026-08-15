# ashop - AI 上下文

## 專案核心用意

為 Paper 26.1.x~26.2 伺服器提供**以動態經濟為核心的玩家驅動市場**：
1. 價格隨買賣與庫存即時浮動（越買越貴、越賣越便宜、物以稀為貴）
2. 附魔書、藥水等 NBT 變體可納入市場交易
3. 原版全物品目錄 + 多語搜尋作為交易基礎設施

## 使用者決策動機

Avery 要求建立從零開始的商店插件，核心需求：
- Paper 26.1.x~26.2 相容
- 預設含全部原版物品
- 分類 + 搜尋（物品 ID + 語言如繁中）
- NBT 物品可上架購買
- 附魔書、藥水等變體需獨立識別

## 架構

```
ShopPlugin
├── LocaleService      → locales/zh_tw.properties 多語搜尋
├── ItemCatalog        → 全 Material + 藥水/附魔書變體展開
├── ItemMatcher        → NBT/Meta 精確比對 + serializeAsBytes
├── ShopManager        → 上架/購買/搜尋邏輯
├── ShopStorage        → listings.yml 持久化（Base64 ItemStack）
├── ShopGui + GuiListener → 6 行 GUI，分類/搜尋/分頁
└── EconomyService     → Vault 可選整合
```

## 歷史變更軌跡

### 2026-07-11 初版建置

1. 建立 Gradle 專案，target `paper-api:26.2.build.+`，Java 25
2. `ItemCatalog.build()` 遍歷 Material enum，藥水展開 PotionType，附魔書展開 Enchantment×level
3. `ItemMatcher` 用 PotionMeta/EnchantmentStorageMeta 專用比對 + serializeAsBytes 通用比對
4. `LocaleService` 載入 zh_tw.properties，搜尋支援 enum名、minecraft:id、中英文名
5. GUI：主選單分類 → 商品列表分頁 → 左鍵購買/右鍵下架
6. 搜尋：GUI 指南針按鈕觸發聊天輸入，或 `/shop search <關鍵字>`
7. 首次啟動 `seedDefaultListings()` 為所有目錄物品建立系統商店上架
8. 上架 `/shop sell <價格>` 從主手取物，完整 NBT 序列化存入 listings.yml

### 2026-07-11 多語言支援

1. 重構 `LocaleService`：載入多語系檔、玩家偏好持久化（`player-locales.yml`）
2. 新增 `/lang` 指令（`LangCommand`），支援 `zh_tw` / `en_us` 切換
3. 語系檔結構：`locales/{locale}.properties` 含 `msg.*` 訊息、`category.*` 分類、物品名稱
4. `ShopGui`、`GuiListener`、`ShopCommand` 全部改用 `locale.msg(player, key)`
5. 搜尋支援跨語系命中（任一已載入語言名稱）
6. README 改雙語、CHANGELOG 獨立為 `CHANGELOG.md`
7. `config.yml`：`languages.default` + `languages.available` 取代單一 `locale`

### 2026-07-11 動態定價

1. 新增 `DynamicPricingService`：買入漲價、上架跌價、庫存稀缺加價
2. 公式：`倍率 = 1 + 買入次數×per-buy - 上架次數×per-sell + 缺貨量×per-shortage`，clamp 至 min/max multiplier
3. 設定項：`dynamic-pricing.*` 全部以 % 表示，使用者易於調整
4. 市場統計存 `market-data.yml`（buys/sells per catalog key）
5. GUI 顯示 `價格：$12 (↑+20%)`；新增 `/shop price` 查詢市價
6. `/shop sell` 可省略價格，自動使用建議動態價

### 2026-07-11 插件更名 ashop

1. `plugin.yml` name → `ashop`
2. JAR 輸出 `ashop-{version}.jar`
3. 資料夾 `plugins/ashop/`
4. GUI 標題、README、CHANGELOG 同步更新

### 2026-07-11 README 中英文分離

1. `README.md` 預設繁體中文，頂部 badge 按鈕切換至 `README.en.md`
2. `README.en.md` 英文版，頂部按鈕切回中文 README

### 2026-07-11 主打動態經濟定位

1. README 重寫：動態經濟置頂為核心章節
2. plugin.yml description、GUI 標題改為「動態市場」
3. 指令表 `/shop price` 提升優先順序

### 2026-07-11 效能優化（卡頓修復）

1. **根因**：數千筆系統上架 + 每次點擊同步寫 YAML + O(n²) 物品比對
2. 新增 `catalog` 模式（預設）：直接瀏覽 ItemCatalog，不建立實體 listing
3. `ListingIndex` 快取庫存/分類計數
4. `AsyncSaveService` 延遲 2 秒非同步存檔
5. `findMatching` 改 O(1) fingerprint 查表
6. 啟動時自動略過/清理舊系統上架

### 2026-07-11 自訂語系檔

1. `languages.locales` map：代碼 + 顯示名稱，可新增插件未內建語言
2. 語系檔路徑 `plugins/ashop/locales/<code>.properties`（覆蓋 JAR 內建）
3. 首次啟動釋出 zh_tw、en_us、_template、README.txt
4. 自訂語言缺少檔案時自動從 template + fallback 產生

### 2026-07-11 /shop reset 還原預設全物品

1. 管理員 `/shop reset`（別名：還原、restore）
2. 刪除 shop/ 內自訂分類（保留 _template）
3. 重建 12 分類 + 全原版物品 + 獨立 price

### 2026-07-11 商店改為 shop 資料夾驅動

1. 移除寫死的 12 分類 enum 與自動灌入全物品
2. 掃描 `shop/*/items.yml` 動態載入分類與商品
3. 僅釋出 `shop/_template/items.yml` 範例，管理員自行建立分類
4. 移除 config.yml `categories.*` 開關，改由 items.yml 的 enabled 控制

### 2026-07-11 每物品獨立定價

1. 新增 `ItemPriceCalculator`：依分類、材質、藥水/附魔書變體自動計算基準價
2. seed/sync/backfill 皆寫入每項 `price` 至 items.yml
3. 動態定價以各物品獨立 price 為基準浮動，不再共用單一 base-price

### 2026-07-11 賣給系統收購箱 GUI

1. 主選單「賣給系統」開啟 5 行投放區 + 取消/總計/確認列
2. 每項顯示單價與小計，底部金錠顯示本批總計
3. 確認後批次結算並提示「共獲得 $X（N 個物品）」
4. 修復關閉 /shop 後 session 仍攔截背包點擊（ShopInventoryHolder + 關閉清 session）

### 2026-07-11 純系統商店模式

1. 新增 `system-shop.*` 設定，預設 `player-listings: false`
2. `/shop sell` 改為賣給系統（收購價 = 動態購買價 × sell-ratio）
3. 啟動時清除玩家上架資料
4. GUI 移除「我的上架」，綠寶石按鈕改為賣給系統
5. `/shop price` 顯示系統售價 + 收購價

### 2026-07-11 分類商店設定檔（shop/）

1. 新增 `ShopConfigService`：管理 `plugins/ashop/shop/<分類>/items.yml`
2. 首次啟動依 ItemCatalog 自動建立 12 分類資料夾與 items.yml
3. 管理員可編輯：分類/單項 `enabled`、單項 `price` 覆寫基準價
4. `sync-new-items`：reload 時自動補上原版目錄新增物品
5. catalog 模式改讀 shop 設定，不再直接暴露完整目錄
6. `/shop reload` 重新載入 shop 分類設定

### 2026-07-11 移除本地共同編輯者

1. 使用 `git filter-branch` 移除了最近 Git 提交歷史中的 `Co-authored-by: Cursor` 標籤，保留使用者自身為唯一作者。

### 2026-07-11 查價可收購但實際拒絕（v1.3.2）

**使用者回報**：`/shop price` 顯示可收購，但收購箱確認出售時被拒。

**根因分析（子代理 + 程式碼審查）**：
1. 查價路徑未統一檢查物品是否在 `shop/` 設定中，fallback 計價仍顯示價格
2. `ItemCatalog.findMatching` 用完整 fingerprint（含耐久），損耗工具無法對上 shop 模板
3. **關鍵**：`ShopGui.refreshSellPanel` 將單價 lore 寫回真實 ItemStack，確認出售時 fingerprint 改變 → `canSellToSystem` 失敗

**修復步驟**：
1. 新增 `ResolvedShopItem`、`ShopConfigService.resolvePlayerItem()`（含 `normalizeForLookup` + `ItemMatcher.matchesForTrade`）
2. `canSellToSystem` / `getItemPriceQuote` / `getSellToSystemQuote` 全部走 `resolvePlayerItem`
3. 新增 `ShopManager.tradeItem()` → 交易前 `stripSellGuiLore`，避免 GUI lore 污染比對
4. `/shop price` UX：區分「可購買 / 可收購 / 不在商店 / 不收購」
5. `PriceQuote.unavailable()` 表示無法報價
6. `sellDepositToSystem`：`economy.deposit` 失敗時退還 `pendingSold`
7. `buyCatalogEntry` / `buyListing`：先 `withdraw` 再 `addItem`，背包滿則 `deposit` 退款
8. 收購箱 UX：`msg.gui.sell.all-rejected` 全數被拒時明確提示

**驗證**：第三、四輪獨立子代理連續 PASS（查價/收購一致、lore 修復、經濟回滾）

### 2026-07-11 收購箱即時報價與伺服器匯率（v1.3.3）

**使用者需求**：
1. 收購箱放入方塊後單價應立即顯示，不要等很久
2. 預設系統價格可依伺服器經濟匯率調整（×倍率 + 固定加值）

**修復/新增**：
1. `GuiListener` 新增 MONITOR 優先級監聽，任何放入/拖曳/Shift 點擊後 1 tick 刷新
2. `ShopGui.refreshSellPanel` 改用 clone 顯示、`inv.setItem` + `player.updateInventory()` 強制同步
3. 新增 `ServerPriceExchange`：`實際價 = 基準價 × multiply + add`
4. `config.yml` → `shop.pricing.exchange.multiply` / `add`，`/shop reload` 即生效（不需重設 shop）

### 2026-07-11 出售完成後主頁無法點擊

**根因**：`confirmSell` / 取消收購時 `closeInventory()` 觸發 `onClose`，session 被從 map 移除，但 `openMain` 仍用同一 session 物件，導致 `getActiveSession` 回傳 null、主頁按鈕無效。

**修復**：`GuiSession.pendingShopNavigation` 標記「即將返回主頁」，`onClose` 在此狀態下保留 session 不清除。

### 2026-07-11 購買數量選擇 GUI（v1.4.0）

**需求**：點商品後可選購買 1 個、一組（max stack）、或聊天輸入自訂數量。

**實作**：
1. 新增 `BUY_QUANTITY` 介面與 `ShopGui.openBuyQuantity`
2. `ShopManager.buyCatalogEntry(player, key, amount)` 批量購買 + 背包空間預檢
3. 自訂數量：關閉 GUI → 聊天輸入（可輸入 cancel/取消 返回）
4. `config.yml` → `gui.max-buy-amount` 單次上限（預設 2304）

### 2026-07-11 管理員 GUI 編輯（v1.5.0）

**需求**：Shift+右鍵快速編輯商品單價/移除；config 設定 UI 化。

**實作**：
1. `ShopAdminService` — 寫入 items.yml 價格/啟用/移除；config 欄位讀寫
2. Shift+右鍵商品 → `ShopAdminGui.openAdminItemEdit`（設單價、啟停、移除）
3. 主選單 slot 52 地獄星 → 全域商店設定 GUI（收購比例、匯率、動態定價等）
4. 數值：左鍵 +step、右鍵 -step、Shift+左鍵聊天自訂；布林：左鍵切換

### 2026-07-11 邏輯審查全面修復（v1.5.1）

**背景**：子代理審查發現查價/收購/GUI 流程存在多處邏輯不一致與競態風險，使用者要求一次修完。

**修復清單**：

| 編號 | 問題 | 修復 |
|------|------|------|
| H1 | `buyListing` 競態導致重複購買 | 整段交易包在 `synchronized (listingLock)` |
| H2 | 收購判定與報價路徑不一致 | `canPlayerSell` / `getSellToSystemQuote` 一律走 `resolvePlayerItem` |
| H2/M6 | 無有效 quote 仍吃掉物品 | `sellDepositToSystem` 檢查 `sellQuote.available()` |
| H3 | 聊天 await 殘留導致誤觸發 | `resetFlowState()` + `openMain` 時清除 awaiting 旗標 |
| M1 | catalog 模式庫存虛高 | `quoteStock()` 目錄模式用 `reference-stock` |
| M3 | 管理員 GUI 只顯示基準價 | 新增「實際單價」顯示 `applyServerPrice` |
| M4 | 搜尋驗證失敗仍清除 await | 驗證通過後才 `awaitingSearch.remove` |
| M5 | ESC 關子 GUI 又被拉回 | 移除 `onClose` 延遲 `returnFromBuyQuantity/Admin` |
| M7 | 賣家離線款項消失 | `EconomyService.deposit(UUID, amount)` |
| M12 | SELL onClose 漏檢 admin chat | onClose 加入 `awaitingAdminChat` 判斷 |
| L2 | 分類頁動態價格 lore 硬編碼 | 改為 `pricing.isEnabled()` |
| — | 確認收購連點 | `sellConfirming` 防護 + try/finally |
| — | 收購面板顯示與實際不符 | `refreshSellPanel` 檢查 `sellQuote.available()` |

**涉及檔案**：`ShopManager`、`GuiListener`、`ShopGui`、`ShopAdminGui`、`EconomyService`、`ShopPlugin`、語系檔

### 2026-07-11 巢狀子分類（v1.6.0）

**需求**：方塊分類點擊無反應（600+ 項卡頓）；希望像創造模式一樣多層子分類（建築→木材/石頭/銅、染色→羊毛/地毯/混凝土/玻璃…、自然→原礦/樹葉/挖礦體…）；其他分類也要細分；預設值與使用者自訂編輯皆支援。

**根因**：舊版 `blocks/items.yml` 單檔含 600+ 商品，主執行緒同步建 GUI 造成明顯卡頓；扁平結構無子分類。

**實作**：
1. `ShopSubcategoryResolver` — 物品→子分類路徑規則（方塊/工具/武器等）
2. `shop/<path>/items.yml` 遞迴載入，category id 為路徑（如 `blocks/building/wood`）
3. `ShopGui.openSubcategoryPage` — 有子節點時顯示子分類；葉節點才顯示商品
4. 返回鍵沿 parent 回上一層；`openCategory` 改下一 tick 非同步開啟 + 載入提示
5. `/shop reset` 產生完整巢狀預設樹；README 更新巢狀資料夾說明

### 2026-07-11 分類購買開關（v1.6.1）

**需求**：整個分類可設定禁止玩家購買，並繼承到所有子分類。

**實作**：
1. `items.yml` 新增 `allow-buy: true/false`（預設 true）
2. `isCategoryAllowBuy()` 沿 parent 鏈檢查；上層關閉則整棵子樹不可買
3. 收購不受影響（仍可賣給系統）
4. 管理員：分類頁 slot 48 或 Shift+右鍵子分類 → 分類設定 GUI

### 2026-07-11 /ashop help 與首次自動載入（v1.6.2）

**需求**：要有 `/ashop help` 教學；首次安裝無 shop 設定時預設載入全物品。

**實作**：
1. `shop` 指令新增別名 `ashop`；`help` 子指令顯示玩家/管理員/GUI/設定檔說明
2. `shop.auto-seed-on-first-run: true`（config 預設）— shop/ 無分類或零商品時自動 `restoreDefaults`
3. `/shop reload` 若偵測空 shop 也會補建預設

### 2026-07-11 預設商店僅生存可取得物品（v1.6.3）

**需求**：全物品預設商店不應含光源方塊、指令方塊等生存無法取得的物品。

**實作**：
1. 新增 `SurvivalObtainability` 過濾器
2. `/shop reset` 與首次 auto-seed 套用過濾
3. `shop.survival-only-defaults: true`（config 可關閉恢復舊行為）
4. 排除：指令方塊、光源、屏障、結構方塊、生怪蛋、試煉生怪籠、寶庫、強化深板岩、除錯棒等

### 2026-07-11 生存定價模型（v1.6.5）

**需求**：依純生存取得方式與機率重算全物品價格，並以使用者基準校準：
- 閃長岩 購9/售6、鑽石鎬 購1000/售700、重錘 購180000/售120000、鞘翅 購130000/售9500

**實作**：
1. 新增 `SurvivalPriceModel`：基礎資源價、配方拆解、戰利品稀有度、四項錨點精確對齊
2. `ItemPriceCalculator` 改委派至生存定價模型
3. `system-shop.sell-ratio` 預設改為 2/3（0.667）
4. 鞘翅等特殊物品支援 `items.yml` 的 `sell-ratio` 覆寫（預設 ~0.073）
5. 管理員 `/ashop resync-prices` 重算現有 shop/ 價格（不刪分類結構）

### 2026-07-11 動態定價漲停有效計次（v1.6.6）

**需求（Avery）**：
1. GUI / `/shop price` 顯示「已達上限／下限」
2. 後台仍累加全部交易次數（total-buys / total-sells）
3. 收購價也顯示趨勢 %
4. 漲停後多買的不計入有效 buys；恢復原價只需賣出「漲停前有效次數」等量（1:1 預設）

**實作**：
1. `MarketData` 拆分：`buys`/`sells`（有效計價）與 `total-buys`/`total-sells`（全部交易）
2. `DynamicPricingService.recordBuy/Sell` 僅在未達漲停／跌停時增加有效次數
3. `PriceQuote` 新增 `PriceCap`（MAX/MIN/NONE）與 `formatTrend()`
4. GUI 商品價、收購面板單價、`/shop price` 顯示趨勢與上限提示
5. `config.yml` 預設 `per-sell-decrease: 2.0`（與 per-buy-increase 1:1）

**涉及檔案**：`DynamicPricingService`、`MarketData`、`MarketStorage`、`PriceQuote`、`ShopManager`、`ShopGui`、`ShopCommand`、語系檔、`config.yml`

### 2026-07-15 分類置中排版 (v1.6.7)

**需求 (Avery)**：
主選單與子分類選單中的分類圖示需置中排版（依據提供之圖片，將分類排列在中間的 5x4 區塊）。

**實作**：
1. `ShopGui.java`：在 `openMain` 和 `openSubcategoryPage` 中，將原先依序遞增的 `slot` 演算法，改為以置中 5 行 (columns 2~6) 的公式：`int slot = (1 + slotIndex / 5) * 9 + 2 + (slotIndex % 5)`。
2. 透過 `slotSubcategoryMap` 來建立自訂 slot 與類別 ID 的對應。
3. `GuiListener.java`：在 `handleMainClick` 內改用 `slotSubcategoryMap` 解析玩家點擊的類別，並同時補上支援管理員 Shift+右鍵 編輯主分類的邏輯，修正主選單原本不支援分類設定 GUI 的問題。

### 2026-07-15 搜尋加入提示與防 Discord 同步 (v1.6.7)

**需求 (Avery)**：
1. 移除先前鐵砧介面的作法，改回原版的聊天室搜尋輸入。
2. 加入提示字樣，提醒使用者「英文或物品ID比較容易搜尋到，中文查不到不妨用物品ID」。
3. 確保玩家在聊天室的搜尋文字不會被其他玩家看到，也不會同步到 Discord。
4. 版本號保持不變。

**實作**：
1. `build.gradle.kts`：移除 `com.gradleup.shadow` 插件與 `net.wesjd:anvilgui` 依賴，還原預設編譯設定，解決 Java 25 造成的 build 錯誤。
2. `GuiListener.java`：還原 `awaitingSearch` 聊天室攔截邏輯，並在點擊搜尋按鈕時加入黃色字體的提示：「(英文或物品ID比較容易搜尋到，中文查不到不妨用物品ID)」。
3. `ShopCommand.java`：當輸入 `/shop search` 缺少參數時，也補充上述的提示文字。
4. **防外流機制修復**：原本使用 Paper 的 `AsyncChatEvent` 可能因為相容性原因被其他舊版插件 (如 DiscordSRV) 讀取。為了確保 100% 攔截，已改為監聽 Bukkit 的 `AsyncPlayerChatEvent`，除了呼叫 `event.setCancelled(true)` 外，還強制執行 `event.getRecipients().clear()` 清空所有接收者。這能徹底防止搜尋字串在伺服器聊天室出現，以及防止被跨服/Discord 插件同步出去。

### 2026-07-15 實裝 Discord Webhook 交易紀錄推播 (v1.6.7)

**需求 (Avery)**：
1. 有玩家販賣或買入時，都要可以將紀錄傳送到設定的 Discord webhook。

**實作**：
1. 新增 `DiscordWebhookService`，處理非同步 POST 請求以發送 JSON payload 到 Discord webhook URL。
2. 在 `config.yml` 中新增 `discord-webhook.enabled` 和 `discord-webhook.url` 設定項。
3. 於 `ShopPlugin.java` 內初始化 `DiscordWebhookService` 並透過 getter 開放存取。
4. 在 `ShopManager.java` 內各交易邏輯節點（`sellDepositToSystem`、`sellToSystem`、`buyCatalogEntry`、`buyListing`）觸發推播，以粗體 Markdown 語法記錄玩家 ID、物品資訊與交易金額。
5. 在 `ShopPlugin.java` 的 `onEnable()` 中加入 `getConfig().options().copyDefaults(true); saveConfig();`，確保舊版設定檔能自動補齊這個新區塊。

### 2026-07-15 修復預設商店生存過濾與耐久度收購價 (v1.6.8)

**需求 (Avery)**：
1. 預設的商店為何可以取得生存不能獲得的兩種測試方塊（如 Jigsaw/Structure Block），儘管已開啟生存避開設定。
2. 賣物品的時候，耐久度有減少就該依原本收購價的一半，剩下的一半再根據使用的剩餘耐久度去給予金錢。

**根因分析與實作**：
1. **生存過濾漏套用**：`ShopManager.seedDefaultListings()` 在非 catalog 模式生成預設商店上架清單時，迴圈遍歷 `catalog.getAll()` 卻遺漏了 `SurvivalObtainability.isObtainableInSurvival` 檢查。
   - **修復**：在迴圈內新增判斷，若 `shop.survival-only-defaults` 為 true 則跳過非生存可取得物品。
2. **耐久度收購價未遞減**：原本的 `getSellToSystemQuote()` 計算收購價時，直接回傳 `buyQuote.price() * ratio`，未將工具損耗納入計算。
   - **修復**：檢查物品的 `ItemMeta` 是否實作 `Damageable` 且包含耐久度損耗。計算公式修改為：`基礎收購價 / 2.0 + (基礎收購價 / 2.0) * (剩餘耐久 / 最大耐久)`，確保最低有 50% 殘值並依比例浮動。

### 2026-07-16 優化 Discord Webhook 交易紀錄為 Embed 崁入訊息 (v1.6.9)

**需求 (Avery)**：
1. 傳送到 Discord 的 log 要更好閱讀，並且改為使用 Embed 崁入訊息格式。
2. 物品名稱需顯示為中文。
3. 原本的原始資訊需保留並標記在訊息最底下。

**實作**：
1. `ShopManager.java` 新增 `getChineseItemName()` 輔助方法：統一呼叫 `plugin.getLocaleService().getDisplayName("zh_tw", ...)` 取得物品之中文名稱與變體標籤。
2. `DiscordWebhookService.java` 新增 `sendPayload()` 方法，支援直接發送建構好的 JSON Payload，不再強制包裝為單純的 text content。
3. `ShopManager.java` 新增 `sendDiscordEmbed()` 輔助方法：產生包含標題、顏色、欄位（玩家、金額、物品明細）的 Embed JSON 結構，並將傳入的原始資訊放入最底部的 field 內。
4. 將原本的文字推播改為發送帶有顏色與欄位排版的 Embed 訊息。
5. 新增長文字防護機制：若玩家單次交易超過 950 字元（例如大量不同的物品），程式會自動將「物品明細」拆分為多個獨立的 Embed 欄位（如明細 (1)、明細 (2)）。原始 JSON 若超過 900 字元則會在結尾進行截斷，並透過 `multipart/form-data` 以 `raw_data.txt` 附檔的形式完整上傳至 Discord。

### 2026-07-16 過濾 Paper 1.21.x 新增的測試方塊 (v1.6.9)

**需求 (Avery)**：
1. `minecraft:test_block` 和 `minecraft:test_instance_block` 等開發測試方塊仍然出現在商店中，這些在生存模式中無法取得，應該刪除。

**根因分析與實作**：
1. **Material Enum 變更**：Paper 1.21+ 將 Mojang 內部的測試用方塊（如 `TEST_BLOCK`, `TEST_INSTANCE_BLOCK` 等）暴露在 API 的 `Material` enum 中，導致原本的黑名單無法攔截。
   - **修復**：在 `SurvivalObtainability.java` 的 `isObtainableInSurvival` 方法中新增 `name.contains("TEST")` 的判斷條件，攔截所有這類內部測試用的方塊，避免它們進入商店商品名單。

### 2026-07-20 修復聊天輸入廣播漏洞 (v1.6.10)

**需求 (Avery)**：
玩家在商店插件輸入文字時（例如購買數量、自訂價格等），雖然有被處理，但仍然會被廣播到伺服器上，沒被正確隱藏。

**根因分析與實作**：
1. **問題原因**：原先使用 `AsyncPlayerChatEvent` 且設定 `EventPriority.LOWEST` 來攔截訊息，雖然呼叫了 `event.setCancelled(true)` 與 `event.getRecipients().clear()`，但其他聊天管理插件或格式化插件可能會在較高優先級強制處理或廣播，導致攔截失效。
2. **修復**：徹底移除 `AsyncPlayerChatEvent` 監聽器，改用 Bukkit 內建的 `Conversation API` (`ConversationFactory`) 來實作聊天輸入。
3. **實作細節**：
   - 建立 `ChatPrompt.java` 工具類，封裝 `ConversationFactory`，設定 `withModality(true)` 獨占焦點，並使用 `withLocalEcho(false)` 確保玩家輸入的文字不會回顯。
   - `GuiListener` 移除所有 `awaitingSearch`, `awaitingBuyQuantity`, `awaitingAdminChat` 等 Map 與相關狀態變數，移除原本的 `onChat` 監聽。
   - 購買數量、搜尋、管理員設定的對話流程，全面改為呼叫 `ChatPrompt.start()` 接收輸入。
   - 清理 Session 時 (`onClose` / `getActiveSession`) 透過 `player.isConversing()` 確認玩家是否在對話中，以防 Session 提前被清空。

### 2026-07-31 新增交易模式（TradeMode）與滾輪中鍵編輯 (v1.7.0)

**使用者決策動機 (Avery)**：
1. 需求單一商品或整筆分類可以調整為「只收購不賣」或「只賣不收」，以及「禁用交易」。
2. 設定為禁用交易的商品/分類仍需要顯示在商店 GUI 中供玩家瀏覽，僅禁止進行買賣交易。
3. 原有的管理員 Shift+右鍵編輯，要求同時支援使用「滾輪中鍵 (Middle Click)」觸發開啟編輯面板。
4. 管理員 GUI 設定面板（分類編輯與商品編輯）必須包含獨立的刪除選項（刪除分類 / 刪除商品）。

**實作與架構變更**：
1. **TradeMode Enum** (`TradeMode.java`)：
   - 定義 `BOTH` (買賣皆可), `BUY_ONLY` (只賣不收), `SELL_ONLY` (只收不賣), `DISABLED` (禁用交易)。
   - 提供 `allowsBuy()`, `allowsSell()`, `next()`（循環切換）等方法。
2. **多層級與樹狀繼承** (`ShopConfigService.java`)：
   - 分類檔 `items.yml` 支援 `trade-mode` 屬性（相容舊版 `allow-buy`）。
   - 商品層級支援獨立 `trade-mode`。
   - `getCategoryTradeMode(categoryId)` 與 `getItemTradeMode(catalogKey)` 實現樹狀組合權限計算 (`combineTradeModes`)：父分類設為 `BUY_ONLY` 時，子項目自動繼承限制。
3. **禁用交易仍保留 GUI 顯示**：
   - `isItemInShop(entry)` 保持有效，`ShopGui` 中正常呈現所有 enabled 商品。
   - `ShopGui` 根據 `getItemTradeMode` 動態顯示 Lore 提示（「只收不賣 (至 /shop sell 出售)」、「暫不開放交易」）。
   - `canPlayerBuy` 與 `canPlayerSell` 嚴格檢驗 `allowsBuy()` 與 `allowsSell()`，不符權限時拒絕交易或自動剔除收購箱物品。
4. **滾輪中鍵編輯** (`GuiListener.java`)：
   - `isAdminEditClick` 加入 `ClickType.MIDDLE`（滾輪中鍵）判定，管理者中鍵點擊分類或物品圖示即可直接開啟管理選單。
5. **管理員 GUI 獨立刪除選項** (`ShopAdminGui.java` & `ShopAdminService.java`)：
   - 商品編輯面板：提供獨立「刪除商品」按鈕 (`removeItem`)。
   - 分類編輯面板：提供獨立「刪除分類」按鈕 (`removeCategory`)，點擊刪除整筆分類檔案與子目錄並傳回主選單。
6. **舊版設定檔自動平滑升級** (`ShopConfigService.java`)：
   - 啟動與載入時透過 `migrateConfigsRecursively` 自動掃描 `shop/` 下現有舊版 `items.yml`。
   - 若檔案中欠缺 `trade-mode` 屬性，會依據舊版 `allow-buy` 自動升級移轉寫入 `trade-mode`（如 `BOTH` 或 `SELL_ONLY`），無需人工手動修改舊設定。
7. **分類/商品 TradeMode 獨立調整與父分類重置級聯** (`ShopConfigService.java` & `ShopAdminGui.java`)：
   - 子分類單獨設定完全生效：修復 `getCategoryTradeMode` 移除舊有父分類強加交集覆蓋限制。當單獨將子分類（如「食物/生食」）調為 `BOTH`（買賣皆可）時，該子分類即刻完全以 `BOTH` 生效，允許買賣。
   - 父分類混合模式提示：當父分類下有子分類或商品做了單獨調整時，父分類編輯選單會顯式提示（`⚠️ 提示：下轄包含混合/自訂交易模式 (部分子分類或商品已被單獨調整)`）。
   - 父分類再次切換重置級聯：當管理員再次點擊調整父分類時，系統會觸發級聯（Cascade）機制，將該分類及其下轄所有子分類與商品的 `trade-mode` 強制統一重置為父分類的新模式。
   - 動態繼承與收購箱阻擋：商品為預設 `BOTH` 時動態繼承分類之 `TradeMode`；若分類設為 `BUY_ONLY`（只賣不收），`/shop sell` 收購箱精準阻擋並退回物品。
8. **新增獨立專屬分類與單純挖掘掉落物過濾** (`ShopSubcategoryResolver.java`, `ItemCatalog.java` & `zh_tw.properties`)：
   - 建立 4 個專屬獨立分類歸類解析：
     - **原木 (`logs`)**：專門歸類原木/原木變種/菌柄，木板、階梯、門板留在木製品。
     - **礦物(粗礦物) (`ores`)**：修正預設歸類邏輯使非方塊物品（粗鐵/粗金/粗銅、金屬錠與粒、煤炭/木炭/鑽石/綠寶石/青金石/紅石/石英/Netherite 碎屑/紫水晶/燧石）全數納入 `礦物(粗礦物)`，並嚴格過濾需要絲綢觸摸的 `*_ORE` 方塊。
     - **石頭(變種方塊不要) (`stones`)**：專門歸類純原石/石頭/深片岩/黑石/終界石/地獄石，嚴格排除階梯/半磚/牆/磚塊變種。
     - **農作物 (`crops`)**：專門歸類小麥/胡蘿蔔/馬鈴薯/甜菜根/甘蔗/南瓜/西瓜/種子等農作物。
9. **分類名稱全面繁體中文原生顯示與自動修正** (`ShopConfigService.java` & `LocaleService.java`)：
   - 強制預設 `zh_tw` 產生 default `items.yml` 分類名稱。
   - 啟動與熱載入時，自動掃描並修復舊 `items.yml` 中殘留的英文目錄路徑 `display-name`（如 `building/logs` -> `原木`）。
   - GUI 渲染優先呈現語系檔中的標準繁體中文名稱，確保視覺 100% 中文化。

### 2026-07-31 收購箱角落一鍵放入與查看可收購物品 (v1.7.1)

**使用者決策動機 (Avery)**：
1. 希望在收購箱（`/shop sell`）中能更快速方便地處置物品，不必手動逐一 Shift+點擊 背包物品。
2. 要求將「所有可以收購的東西放在收購箱角落」，即在收購箱底欄角落提供「一鍵填入」功能按鈕。
3. 要求提供「查看可收購的物品」功能，使玩家能直接瀏覽目前伺服器開放系統收購的所有商品與即時單價。

**實作與變更細節**：
1. **收購箱角落控制列按鈕排版** (`ShopGui.java`)：
   - `Slot 45`: 取消返還 (紅)
   - `Slot 46`: **一鍵放入可收購物品** (`SELL_FILL_ALL_SLOT` / Hopper 漏斗)
   - `Slot 49`: 本批總計收益 (金錠)
   - `Slot 52`: **查看可收購物品** (`SELL_VIEW_SELLABLE_SLOT` / Book 書本)
   - `Slot 53`: 確認出售 (綠)
2. **一鍵自動填入邏輯** (`GuiListener.java` -> `handleFillAllSellable`)：
   - 遍歷玩家主背包 36 個欄位，自動檢查 `shopManager.canSellToSystem(item)` 與收購報價可用性。
   - 自動尋找收購箱 (Slots 0~44) 之空位或可堆疊欄位進行搬移，並自動觸發 `refreshSellPanel` 即時顯示總金額。
   - 提示轉移成功的組數，若收購箱空間已滿則彈出相應提示。
3. **查看可收購物品 GUI 與指令** (`ShopGui.java`, `ShopManager.java` & `ShopCommand.java`)：
   - `ShopManager.getSellableCatalogEntries(Player)`: 篩選所有 `TradeMode` 包含 `allowsSell()` 且上架啟用的商品。
   - `ShopGui.openSellableCatalog`: 以分頁 GUI 展示全可收購商品，包含即時系統收購單價、漲跌趨勢與出售操作提示。
   - `GuiSession.ViewType.SELLABLE_ITEMS`: 支援完整的上一頁/下一頁分頁導覽與返回上一層機制。
   - 指令新增 `/shop sellable` (別名 `/shop sell-list`, `/shop list-sellable`, `/shop 可收購`)，方便玩家指令開啟。
4. **多語言支援** (`locales/zh_tw.properties`)：
   - 補齊一鍵放入、查看可收購物品、全收購商品 GUI 標題與說明字串。

### 2026-08-10 Discord 線上商店預覽與購買面板 (v1.8.0)

**使用者決策動機 (Avery)**：
1. 舊版設定檔必須相容，新增 `discord:` 設定欄位時要能自動補齊，不需要玩家重新設定 `config.yml`。
2. 伺服器內所有商店與分類都可以在 Discord 進行線上預覽與購買。
3. 購買時必須透過 DiscordSRV 的繫結 Discord 帳號才可以進行購買。
4. 購買的物品直接放入玩家遊戲背包，若玩家背包沒有相對應足夠空間，必須有防呆機制防止購買與扣款。
5. 任何玩家/Discord 成員皆可使用中文斜線指令（`/商店`）進行商店與商品動態價格預覽。
6. 介面需包含選單下拉選單 (StringSelectMenu)、分頁按鈕與商品細節面板。

**實作與架構變更**：
1. **設定檔無縫平滑升級 (`ShopPlugin.java` & `config.yml`)**：
   - 啟動時呼叫 `getConfig().options().copyDefaults(true)` 並 `saveConfig()`。
   - 自動補充 `discord.enabled` (預設 true)、`discord.bot-token`、`discord.require-discordsrv-link` (預設 true)、`discord.command-name` ("商店") 與 `discord.guild-ids`。
2. **Discord 服務管理 (`DiscordService.java`)**：
   - 雙軌相容機制：若設定 `bot-token` 則啟動獨立 JDA Bot；若未設定則自動連接 `DiscordSRV.getPlugin().getJda()`。
   - 註冊斜線指令 `/商店` 與 `/shop`，並綁定至 `DiscordShopListener`。
3. **Discord 面板建構器 (`DiscordPanelBuilder.java`)**：
   - **主選單**：`buildMainMenuMessage` 建構 12 大分類選單，支援下拉選擇。
   - **分類與商品分頁**：`buildCategoryMessage` 展示分類物品、即時動態價格 (買價/賣價/漲跌幅)，提供下拉選單選擇商品及 `◀️ 上一頁` / `▶️ 下一頁` / `🏠 回主選單` 按鈕。
   - **單項商品購買面板**：`buildItemPanelMessage` 展示商品資訊、數量選擇按鈕 (`1個` / `1組` / `4組` / `自訂數量`) 及 `🛒 確定購買` 按鈕。
4. **互動與購買防呆交割 (`DiscordShopListener.java`)**：
   - 處理斜線指令、下拉選單、按鈕點擊與 Modal 彈窗輸入。
   - **DiscordSRV 繫結驗證**：呼叫 `DiscordSRV.getPlugin().getAccountLinkManager().getUuid(discordUserId)`，未繫結回傳 Ephemeral 錯誤提示。
   - **玩家線上狀態檢查**：驗證 `Bukkit.getPlayer(uuid)` 是否線上。
   - **金錢與背包防呆預檢**：計算交易總價與 deliveries 堆疊，呼叫 `InventorySpaceUtil.canFitStorage(player, deliveries)`。背包空間不足時直接攔截交易。
   - **主執行緒交割**：主執行緒執行 `ShopManager.buyCatalogEntry` 完成交易並發送物品至背包。
5. **多語系檔 (`locales/zh_tw.properties`)**：
   - 補充 Discord 介面標題、按鈕文字、未繫結 / 離線 / 金額不足 / 背包不足防呆訊息與購買成功提示。

### 2026-08-10 修復未安裝 DiscordSRV 時觸發 NoClassDefFoundError 導致插件停用 (v1.7.0)

**使用者回報**：執行 `/ashop` 提示 `Cannot execute command 'ashop' in plugin ashop v1.7.0 - plugin is disabled.`。

**根因分析**：
`DiscordService` 與 `DiscordShopListener` 在類別頂端直接 import 了 `github.scarsz.discordsrv.DiscordSRV`。當伺服器未安裝 DiscordSRV 時，JVM ClassLoader 載入類別即拋出 `NoClassDefFoundError`。由於 Error 未被一般的 Exception 擷取，導緻 `onEnable()` 中斷，Paper 自動停用 ashop 插件。

**修復步驟**：
1. **徹底移除硬編碼 import**：移除全專案所有 `import github.scarsz.discordsrv.DiscordSRV;`。
2. **全動態反射存取 (`DiscordService.java`)**：新增 `fetchDiscordSRVPlugin()`、`fetchDiscordSRVJda()` 與 `fetchDiscordSRVLinkedUuid()` 輔助方法，改用 `Class.forName("github.scarsz.discordsrv.DiscordSRV")` 動態載入，未安裝時安全返回 `null`。
3. **JDA 依賴打包至 JAR (`build.gradle.kts`)**：將 `net.dv8tion:JDA` 由 `compileOnly` 改為 `implementation`，並配置 `tasks.jar` 進行 Fat-Jar 依賴打包（產出 15MB 完整 JAR），確保任何伺服器執行環境皆內建 `net.dv8tion.jda.api` 所有 Class，徹底消除 `NoClassDefFoundError: ListenerAdapter`。
4. **onEnable / onDisable 隔離防護 (`ShopPlugin.java`)**：`discordService.start()` 採用 `try-catch (Throwable t)` 包覆，確保即使 Discord 服務初始化失敗，遊戲內商店主體 100% 正常載入運作。
5. **修復 Discord 多層選單與購買按鈕覆寫 (`DiscordPanelBuilder.java`)**：修復原先連續呼叫 `setActionRow()` 導致前一個下拉選單（子分類選單 / 商品選單）被後方按鈕列覆寫蓋掉的問題。改用 `setComponents` 與 `ActionRow.of(...)` 多層 ActionRow 組合，恢復子分類選單、商品選擇選單、數量切換與 `🛒 確定購買` 按鈕。
6. **修復 Discord 下拉選單 Option Value 超長引發之 Exception (`DiscordPanelBuilder.java` & `DiscordShopListener.java`)**：針對帶有複雜 NBT Base64 數據之商品（長度達 120+ 字元），新增 `getShortKey()` 與 `resolveFullKey()` 雙向對照表機制。將超長 Key 自動轉為 18 字元短 Key（`k_8a1b2c3d`），並對選單 Label 進行 `clampString(95)` 邊界截斷，徹底消除 Discord API `Value may not be longer than 100 characters!` 限制導致之點擊未響應與內部錯誤。
7. **修復不可堆疊物品產生重複 custom_id 錯誤與新增全商店搜尋功能 (`DiscordPanelBuilder.java` & `DiscordShopListener.java`)**：
   - **重複 custom_id 修復**：針對 `maxStack = 1` 之不可堆疊物品（如劍、工具、鞍、鞘翅），原先生成 `1個` 與 `1組 (1個)` 導致 `shop:qty:1:...` 的按鈕 ID 重複並觸發 Discord `COMPONENT_CUSTOM_ID_DUPLICATED` 錯誤。修復後針對不可堆疊物品改為生成 `1個` / `2個` / `5個` 獨一無二的 Custom ID 按鈕。
   - **全伺服器商店商品即時查詢 (`buildSearchResultsMessage`)**：實裝「`🔍 搜尋商品`」Modal 彈窗與全伺服器跨分類商品查詢結果面板，玩家可在 Discord 輸入中文/英文/物品 ID 即時查詢所有已上架商品價格與趨勢，並可直接選取進行線上購買。
8. **實裝全自動附魔書與藥水繁體中文詳細名稱解析 (`LocaleService.java` & `DiscordPanelBuilder.java`)**：
   - 全自動讀取 `ItemStackMeta`（`EnchantmentStorageMeta` 與 `PotionMeta`）以及 `CatalogEntry` 之變體標籤。
   - 將原先一律只顯示「附魔書」或「藥水」的無差異列表，全自動精準解析為繁體中文名稱與羅馬數字等級（例如：`附魔書 (保護 IV)`、`附魔書 (鋒利 V)`、`附魔書 (耐久 III)`、`附魔書 (修復 I)`、`藥水 (迅捷 II)` 等），完全不需手動建立字典檔。
**根因分析**：直接 import `DiscordSRV` 類別導致在缺少該插件時拋出 `NoClassDefFoundError`。
**修復步驟：**
1. **移除硬編碼 import**：改用反射動態存取。
2. **Fat-Jar 打包**：將 JDA 依賴打包進插件中，確保執行環境完整性。
3. **生命週期隔離**：`onEnable` 與 Discord 服務啟動邏輯加入 `try-catch` 防護。
4. **Discord 互動修復**：修正選單覆寫、超長 Custom ID 與不可堆疊物品處理問題。
5. ** Mojang 方塊前綴修正**：自動解析繁體中文映射，確保 Discord 上物品顯示 100% 正確的官方繁體名稱。

### 2026-08-10 商品物價自動回歸基準值機制 (v1.8.0)

**使用者決策動機**：
- Avery 要求針對商品基準值動態調整機制進行擴充。
- 功能預設關閉 (`enabled: false`)。若為舊版設定檔無此欄位，系統必須自動相容並自動新增預設值欄位。
- 每個商品有基準值物價。需要能設定時間週期（如每 1 小時）物價向基準值靠攏的上漲幅與下跌幅（漲幅與跌幅需能獨立設定）。
- 當物價偏離基準價時（例如 10 塊上漲至 11 塊），依設定之跌幅（如每小時 -1.0%）慢慢回歸基準值，但不會越過基準值；反之低於基準價時依漲幅慢慢回升。
- 調整時必須於控制台印出 log 記錄變更資訊。

**實作與變更細節：**
1. **`config.yml` 擴充與向下相容**：
   - 新增 `dynamic-pricing.auto-reversion`（含 `enabled`, `interval-minutes`, `increase-rate-percent`, `decrease-rate-percent`）。
   - `DynamicPricingService.checkAndMigrateConfig()` 在啟動時自動檢測舊設定檔並自動寫入補全。
2. **`MarketData.java` & `MarketStorage.java` 精準度升級**：
   - 將有效買賣計數 `totalBuys` 與 `totalSells` 升級為 `double` 浮點數，支援時間週期衰減時的小數位精度，YAML 讀寫自動轉接。
3. **`DynamicPricingService.java` 物價回歸核心演算法與排程任務**：
   - 實作 `processAutoReversion()`：依偏離方向分別套用 `decrease-rate-percent` 或 `increase-rate-percent` 微調買賣權重，並輸出控制台日誌：
     `[物價調整] 物品 apple 價格從 $11.00 (偏離 +10.0%) 依跌幅 -1.0% 回歸至 $10.90 (基準價: $10.00)`
   - 實作 `startReversionTask()` / `stopReversionTask()`：使用 Bukkit Scheduler 根據 `interval-minutes` 進行週期排程，支援 `/shop reload` 動態重設。
4. **`ShopPlugin.java` 生命週期維護**：
   - 於 `onDisable()` 中呼叫 `pricingService.stopReversionTask()` 正確釋放任務資源。
5. **管理員 GUI 設定面板整合 (`ShopAdminService.java`, `ShopAdminGui.java`, 語系檔)**：
   - 將 `auto-reversion` 四個設定項（開關、週期、漲幅、跌幅）納入管理員 GUI 設定清單，提供左鍵/右鍵/Shift點擊即時編輯。
   - GUI 變更數值時自動呼叫 `startReversionTask()` 重設排程任務。

### 2026-08-10 針對 Paper 26.2 開發與捨棄 Gradle Wrapper (gradlew)

**使用者決策動機**：
- Avery 要求針對 Paper 26.2 進行專案開發，並完全捨棄 `gradlew` 包裹器腳本。
- 專案改為完全使用系統安裝的 `gradle` 進行日常編譯、測試與發布構建，簡化專案檔案結構。

**實作與變更細節：**
1. **移除 Gradle Wrapper 檔案**：
   - 刪除根目錄下的 `gradlew` 腳本與 `gradle/` 目錄（包含 `gradle-wrapper.jar` 與 `gradle-wrapper.properties`）。
2. **更新目標版本與構建說明**：
   - 更新 `build.gradle.kts` 與 `plugin.yml` 中的標註與建置設定，確保持續針對 Paper 26.2 環境開發。
   - 更新 `README.md` 與 `README.en.md` 的編譯說明，將 `./gradlew build` 替換為 `gradle build`。
3. **驗證**：
   - 使用系統 Gradle（`gradle build`）驗證，專案成功編譯打包出 `build/libs/ashop-1.7.0.jar`。

### 2026-08-10 原生 Paper 26.2 封裝與遠端 Git 推送

**使用者決策動機**：
- Avery 要求將目前的變更與編譯完成的 `ashop-1.7.0.jar` 插件檔案提交並推送到遠端 Git 儲存庫。
- 需要打好預編譯 JAR 檔案的完整 Release 文字敘述與發布說明，便於社群與伺服器管理員了解新版本亮點與安裝方式。

**實作與變更細節：**
1. **補齊編譯成品 (`ashop-1.7.0.jar`)**：
   - 將原生 Paper 26.2 環境下編譯出之 `build/libs/ashop-1.7.0.jar` (15.4MB Fat-Jar) 複製至專案根目錄。
2. **Git Commit & Push**：
   - 提交包含 `api-version: 26.2` 標註調整與原生 Paper 26.2 建置檔。
   - 執行 `git push origin main` 將本機 5 個 Commit 安全同步至遠端 GitHub 儲存庫。
3. **撰寫 Release 描述與說明文件**：
   - 彙整 v1.7.0 ~ v1.8.0 核心更新亮點（動態定價自動回歸、Discord 線上預覽/購買、一鍵收購箱、Paper 26.2 原生支援、Fat-Jar JDA 打包等），產出完整發布文字說明。

### 2026-08-11 修復 Discord 點擊含冒號商品（如附魔書/藥水）誤報「該商品目前未開放購買」 (v1.7.0 Fix)

**使用者回報與問題**：
- Avery 指出在遊戲內明明可以購買的商品（例如附魔書 `海洋的祝福 III`），但在 Discord 上點擊「`確定購買`」或數量切換按鈕時，卻顯示 `❌ 購買失敗：該商品目前未開放購買。`

**根因分析**：
1. **CatalogKey 包含冒號（`:`）**：
   - 附魔書、藥水與特定物品的 `catalogKey` 格式為 `enchanted_book:ench:minecraft:luck_of_the_sea:3`，長度約 46 字元。
   - 原 `DiscordPanelBuilder.getShortKey(fullKey)` 僅在長度 > 60 時才進行 Hash，因此 46 字元長度的 Key 被原樣輸出到 Discord 按鈕 Custom ID（如 `shop:buy:1:enchanted_book:ench:minecraft:luck_of_the_sea:3`）。
2. **`componentId.split(":")` 參數分割破壞**：
   - 當玩家在 Discord 點擊購買按鈕時，`DiscordShopListener` 呼叫 `componentId.split(":")`。
   - 由於 Key 本身包含 4 個冒號，`split(":")` 將 ID 拆碎，導致 `parts[3]` 只拿到 `"enchanted_book"` 而非完整 Key `"enchanted_book:ench:minecraft:luck_of_the_sea:3"`。
   - `handleDiscordPurchase` 拿著無效的 `"enchanted_book"` 去查詢 catalog，`isItemPurchasable("enchanted_book")` 判定失敗，進而回傳「該商品目前未開放購買」。

**修復步驟與細節**：
1. **短 Key 雜湊延伸機制 (`DiscordPanelBuilder.java`)**：
   - 修改 `getShortKey(fullKey)` 判定邏輯：只要 `fullKey` **包含冒號（`:`）** 或 **長度 > 60**，一律經由 SHA-256 轉為乾淨無冒號之短 Key（如 `k_a3f89b12c4d567e8`），確保所有按鈕 ID 結構絕不含多餘冒號。
   - 新增 `preloadCatalogKeys(ItemCatalog catalog)` 方法，在 Discord 服務啟動與 Catalog 載入時自動將 Catalog 內所有商品 Key 預先對照登記進 `shortToFullKeyMap` 與 `fullToShortKeyMap`。
2. **冒號防呆與向下相容組合演算法 (`DiscordShopListener.java`)**：
   - 新增 `joinParts(parts, start, end)` 工具方法。
   - 針對所有按鈕與 Modal 監聽器（`shop:buy:`, `shop:qty:`, `shop:qty_custom:`, `shop:modal_qty:`, `shop:nav:search:`, `shop:nav:cat:`），改以倒數索引（`parts[parts.length - 1]`、`parts[parts.length - 2]`）抓取固定位置之 `page` 與 `categoryId`，並將中間的 `start` 到 `end` 區間無損以 `:` 重新拼接。
   - 此舉不僅確保新生成的無冒號 Key 運作完美，更能完全向下相容目前已存在於 Discord 頻道歷史訊息中、舊版帶有冒號的購買按鈕！

### 2026-08-11 Discord 每日/每週/每月商店營運報表系統與定期自動推播 (v1.8.0)

**使用者決策動機**：
- Avery 要求新增 Discord 每日報表、每週報表與每月報表功能，支援手動指令查詢以及在設定的頻道定期自動發送。
- **發送管道差異化需求**：
  - **Discord Bot (頻道發送)**：需附加**永久互動按鈕** (`[📅 每日]` `[📆 每週]` `[📊 每月]` `[🔄 重新整理]`) 以及**永久下拉選單** (選擇詳細檢視項目：`🔥 熱門商品 Top 10`、`🏆 活躍玩家榜`、`📦 系統收購與售出明細` 等)。
  - **Discord Webhook (Webhook 發送)**：因 Webhook 無法接收互動 Event，發送純 Embed 報表訊息。

**實作與變更細節**：
1. **交易紀錄與報表數據服務 (`com.avery.shop.report`)**：
   - `TransactionRecord`: 封裝時間戳記、交易類型 (`BUY_SYSTEM`/`SELL_SYSTEM`/`BUY_PLAYER`/`SELL_PLAYER`)、玩家 UUID/名稱、CatalogKey、數量與總金額。
   - `ReportSummary`: 封裝時間區間、總營業額、銷售額、收購支出、總件數、總筆數、活躍玩家數、熱門商品 Top 10 與熱門玩家 Top 10 模型。
   - `ReportService`: 負責非同步讀寫與 `transactions.yml` 持久化，提供 `generateReport(ReportPeriod)` 統計計算以及 `cleanOldRecords(keepDays)` 過期清理。
2. **交易觸發掛鉤 (`ShopManager.java`)**：
   - 在 `buyCatalogEntry` (系統購買)、`sellHandToSystem` (單件販售)、`sellBatch` (整箱收購) 以及 `buyListing` (玩家拍賣) 完成時，同步呼叫 `ReportService.recordTransaction(...)` 寫入交易日誌。
3. **Discord 報表渲染與互動元件 (`DiscordReportBuilder.java`)**：
   - 生成配色 Embed (每日藍色、每週金色、每月紫色)。
   - 提供 `buildReportActionRows` 生成 JDA 永久按鈕列與 4 選項下拉選單。
   - 提供 `buildWebhookPayload` 產生適用於 Webhook 的純 JSON Payload。
4. **定期自動推播排程器 (`DiscordReportScheduler.java`)**：
   - Bukkit 定時任務（每 60 秒檢查）：比對當前本地時間與 `config.yml` 設定（每日 00:00、每週一 00:00、每月 1 號 00:00）。
   - 透過 JDA 發送至 `discord-report.channel-id` 頻道（帶 Component），或透過 `DiscordWebhookService` 發送至 `discord-report.webhook-url`（純 Embed）。
5. **Discord 指令與監聽器整合 (`DiscordService.java`, `DiscordShopListener.java`)**：
   - 註冊斜線指令 `/report` 與 `/報表` (可附帶 `type: daily/weekly/monthly` 參數)。
   - 監聽按鈕點擊 (`report:btn:*`) 與下拉選單選擇 (`report:select_detail:*`)，即時以 `editMessage` 更新報表與細節內容。
6. **遊戲內指令與設定檔更新 (`ShopCommand.java`, `config.yml`)**：
   - 新增 `/shop report <daily|weekly|monthly> [send]` 指令，支援管理員遊戲內/主控台查詢與手動推播測試。
   - `config.yml` 新增 `discord-report` 完整設定區塊，並加入 `timezone` 設定與備註說明。
7. **時區解析與標註 (Timezone Annotation & Resolution)**：
   - `discord-report.timezone`: 預設 `system` (使用伺服器主機系統時區)。若主機位於國外 VPS，可手動設定為 `"Asia/Taipei"`、`"UTC"` 或指定時區。
   - `DiscordReportBuilder` 與 `DiscordReportScheduler` 動態解析 `ZoneId` 並於 Embed 描述與頁尾標註時區備註 (例如 `Asia/Taipei` 或 `Asia/Taipei (伺服器系統時區)`).
8. **物品交易趨勢與熱度智慧分析 (Item Trend & Popularity Analytics)**：
   - `ReportSummary.ItemTrendAnalysis`: 自動計算物品熱度指數 (0~100)、買賣比例與動態物價漲跌倍率。
   - 自動生成標籤 (`🔥 搶手爆款物資` / `📈 需求持續上升` / `⚖️ 供需穩定平衡` / `📥 玩家大量拋售`) 與智慧分析評語 (如 `"【鑽石】極受玩家喜愛！購買比例達 85%..."`)。
   - 在 Discord 下拉選單中新增 `📈 物品交易趨勢與熱度智慧分析` 選項，在遊戲內可透過 `/shop report trend` 進行即時查詢。
9. **修復 PlugManX 熱重載/重啟時 JDA 拋出 IllegalStateException: zip file closed 錯誤**：
   - **根因**：舊版在 `DiscordService.stop()` 中呼叫非同步 `standaloneJda.shutdown()` 後立即回傳，PlugManX 隨即關閉舊插件的 ClassLoader（ZipFile）。當背景 JDA WebSocket 清理執行緒運作時，因 JarFile 已被關閉而拋出 `IllegalStateException: zip file closed`。
   - **修復**：改用 `standaloneJda.shutdownNow()` 搭配 `standaloneJda.awaitShutdown(Duration.ofSeconds(3))`，確保 JDA 所有背景執行緒與連線在 ClassLoader 被關閉前同步安全清理釋放。

### 2026-08-11 禁止基岩版 (Bedrock) 玩家使用遊戲內商店 GUI

1. **動機考量**：
   - 基岩版 (Bedrock / Geyser / Floodgate) 玩家使用手機觸控螢幕操作時，因圖形面板點擊封包與虛擬 UI 互動特性，可能出現抓取/拿取系統商店面板中裝飾物品（方塊）至手上的異常問題。
   - 由於不針對基岩版觸控 UI 進行個別底層適應，決策為將遊戲內 GUI 完全專屬於 Java 版玩家使用，並引導基岩版玩家至伺服器 Discord 透過 Discord 機器人（/商店 指令與下拉面板）進行物品購買與交易。
2. **基岩版識別工具 (`BedrockUtil.java`)**：
   - 建立 `BedrockUtil.isBedrockPlayer(Player)` 靜態檢測方法。
   - 採用動態反射安全檢測 `FloodgateApi.getInstance().isFloodgatePlayer(...)` 與 `GeyserApi.api().isBedrockPlayer(...)`。
   - 加入 Floodgate UUID 預設前綴 (`00000000-0000-0000-0009-...`) 與玩家名稱前綴 (`.` / `*`) 備援檢測，避免反射失敗時遺漏。
3. **指令與 GUI 雙層阻擋 (`ShopCommand.java`, `GuiListener.java`)**：
   - `ShopCommand`: 在 `/shop` 及開啟 GUI 的子指令 (`search`, `sell`, `sellable`) 中加入 `checkBedrockBlocked(player)` 攔截。
   - `GuiListener`: 在 `onInventoryClick` 與 `onInventoryDrag` 增加二次防護，若基岩版玩家嘗試點擊或拖曳商店 UI 容器，立即取消事件、關閉介面並發送 `msg.cmd.bedrock-blocked` 訊息。
4. **設定檔與多語系檔 (`config.yml`, `zh_tw.properties`, `en_us.properties`, `_template.properties`)**：
   - `config.yml` 新增 `bedrock.block-gui` 設定項（預設 `true`）。
   - 各語系檔新增 `msg.cmd.bedrock-blocked` 提示文字。

### 2026-08-15 修復 Discord 機器人購買物品無回應與「未及時回應」問題 (v1.7.2)

1. **核心痛點與問題現象**：
   - 使用者反映在 Discord 機器人上點擊「🛒 確認購買」按鈕時，機器人完全沒有回應，最後 Discord 提示「Johnny私人伺服器 未及時回應」。
   - 點擊商品數量的預設按鈕 (`1個`、`2個`、`5個` 或 `1組`、`4組`) 時，因為 `DiscordPanelBuilder` 與 `DiscordShopListener` 之間的 customId 參數順序不一致（Panel 生成 `shop:qty:<amount>:<shortKey>:<cat>:<page>`，而 Listener 卻以 `parts[2]` 讀取 shortKey、`parts[4]` 讀取 page，導致 `Integer.parseInt(categoryId)` 拋出 `NumberFormatException`），導致數量切換直接崩潰無回應。
   - 點擊「✏️ 自訂數量」按鈕時，監聽器根本未實作 `shop:qty_custom:` 的按鈕點擊事件與 Modal 彈窗輸入處理，造成點擊後完全無反應並超時。
   - 點擊「🛒 確認購買」按鈕時，Panel 生成的 customId 為 `shop:buy:<shortKey>:<amount>`，但 Listener 卻檢查 `componentId.startsWith("shop:btn_buy:")`，且未立即透過 `event.deferReply(true)` 進行閘道 ACK，一旦主執行緒排程稍有延遲即觸發 Discord 3 秒超時（顯示「未及時回應」）。

2. **具體修復步驟與架構調整**：
   - **標準化 Button customId 規範 (`DiscordPanelBuilder.java`)**：
     - 商品數量切換按鈕統一格式：`shop:qty:<shortKey>:<categoryId>:<page>:<amount>`。
     - 自訂數量按鈕統一格式：`shop:qty_custom:<shortKey>:<categoryId>:<page>`。
     - 確認購買按鈕統一格式：`shop:buy:<shortKey>:<amount>`。
     - 智慧返回按鈕：若來自搜尋（`categoryId` 為 `search@<query>`），返回按鈕指向 `shop:nav:search:<query>:<page>` 並顯示「↩️ 返回搜尋結果」；若來自一般分類，則指向 `shop:nav:cat:<categoryId>:<page>`。
   - **強健的事件監聽與 Modal 彈窗 (`DiscordShopListener.java`)**：
     - 修復 `shop:qty:` 解析邏輯，依序正確提取 `shortKey`、`categoryId`、`page` 與 `amount`，並即時更新面板。
     - 新增 `shop:qty_custom:` 點擊事件：跳出 `shop:modal_qty:...` Modal 彈窗要求玩家輸入自訂數量（1 ~ 2304）。
     - 新增 `shop:modal_qty:` Modal 提交事件：驗證數量合法性後自動切換至指定數量的商品面板。
     - **非同步即時 ACK 機制 (`deferReply`)**：在收到購買請求時立即調用 `event.deferReply(true)`，在幾毫秒內向 Discord 閘道完成回應確認（顯示「機器人正在思考...」），隨後交由 Bukkit 主執行緒檢查玩家在線狀態、餘額、背包空間並執行發貨，最後透過 `hook.sendMessage(...)` 回傳購買結果，徹底杜絕「未及時回應」超時錯誤。
   - **強化防呆與例外捕捉**：
     - 在 `handleDiscordBuy` 中加入完整的 try-catch 防護，若交易處理發生異常會直接回覆清晰的繁體中文錯誤提示（包含金錢不足、背包空間不足、商品未開放購買等）。

## 待擴充

- 可匯入完整 Minecraft zh_tw.json 擴充翻譯覆蓋率
- 可加入更多語言（ja_jp 等）
- 可加入議價、限購、分頁效能優化（大量上架時）




