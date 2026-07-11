# Changelog

All notable changes to ashop are documented here.  
ashop 的所有重要變更皆記錄於此。

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
