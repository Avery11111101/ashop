# Changelog

All notable changes to ashop are documented here.  
ashop 的所有重要變更皆記錄於此。

---

## [1.2.1] - 2026-07-11

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
