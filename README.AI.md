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

## 待擴充

- 可匯入完整 Minecraft zh_tw.json 擴充翻譯覆蓋率
- 可加入更多語言（ja_jp 等）
- 可加入議價、限購、分頁效能優化（大量上架時）
