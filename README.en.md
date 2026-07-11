<div align="right">

[![繁體中文](https://img.shields.io/badge/🌐-繁體中文-red?style=for-the-badge)](README.md)

</div>

# ashop

Paper **26.1.x ~ 26.2** vanilla item shop plugin.

---

## Features

- **Full vanilla catalog** — potions, enchanted books, and NBT variants
- **12 categories** — blocks, tools, weapons, armor, and more
- **Multi-language search** — item ID + localized names (zh_tw / en_us)
- **In-game language switch** — `/lang zh_tw` / `/lang en_us`
- **Full NBT support** — list and buy enchanted books, potions, custom NBT items
- **Dynamic pricing** — buy↑ sell↓ scarcity-based pricing
- **Vault economy** — optional Vault integration

---

## Installation

1. Place `ashop-1.2.1.jar` in the `plugins/` folder
2. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin (e.g. EssentialsX)
3. Restart the server

---

## Commands

| Command | Description |
|---------|-------------|
| `/shop` | Open shop GUI |
| `/shop search <keyword>` | Search items |
| `/shop sell [price]` | List held item (omit price for suggested market price) |
| `/shop price` | Check dynamic market price for held item |
| `/shop reload` | Reload plugin (admin) |
| `/lang` | Show available languages |
| `/lang <code>` | Switch language |
| `/lang list` | Same as `/lang` |

**Aliases:** `/商店` `/vs` `/language` `/語言`

**Supported languages:** `zh_tw` `en_us`

---

## Configuration

```yaml
languages:
  default: zh_tw
  available: [zh_tw, en_us]

dynamic-pricing:
  enabled: true
  base-price: 10.0
  per-buy-increase: 2.0          # +2% per buy
  per-sell-decrease: 1.5         # -1.5% per list
  per-stock-shortage-increase: 3.0  # +3% per missing stock unit
  reference-stock: 5             # scarcity baseline
  min-multiplier: 0.2            # floor 20%
  max-multiplier: 5.0            # cap 500%
  system-shop: true
  player-listings: false
  auto-suggest-price: true

default-prices:
  enabled: true
  base-price: 10.0
```

- Language files: `plugins/ashop/locales/` (bundled in JAR)
- Player language prefs: `plugins/ashop/player-locales.yml`
- Market stats: `plugins/ashop/market-data.yml`

---

## Build

```bash
./gradlew build
```

Requires **Java 25**.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
