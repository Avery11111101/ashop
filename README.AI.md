# ashop - AI 上下文

## 專案核心用意

為 Paper 26.1.x~26.2 伺服器提供**原版全物品商店**，解決：
1. 附魔書、藥水等「同 Material 不同 NBT」物品難以區分上架的問題
2. 缺少以繁體中文或物品 ID 快速搜尋的需求
3. 需要預設提供全部原版物品供瀏覽/購買

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

## 待擴充

- 可匯入完整 Minecraft zh_tw.json 擴充翻譯覆蓋率
- 可加入更多語言（ja_jp 等）
- 可加入議價、限購、分頁效能優化（大量上架時）
