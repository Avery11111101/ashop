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

## 待擴充

- 可匯入完整 Minecraft zh_tw.json 擴充翻譯覆蓋率
- 可加入更多語言（ja_jp 等）
- 可加入議價、限購、分頁效能優化（大量上架時）
