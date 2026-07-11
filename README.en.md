<div align="right">

[![繁體中文](https://img.shields.io/badge/🌐-繁體中文-red?style=for-the-badge)](README.md)

</div>

# ashop

> **Dynamic Economy Shop** — prices shift with supply and demand. Buy↑ Sell↓ Scarcity matters.

A Paper **26.1.x ~ 26.2** plugin built around a **player-driven dynamic market**, with a full vanilla catalog, multi-language search, and NBT trading.

---

## Dynamic Economy (Core)

ashop is not a fixed-price shop. Every transaction moves the market:

| Player Action | Market Response |
|---------------|-----------------|
| **Buy** | Price rises ↑ |
| **List/Sell** | Price falls ↓ |
| **Low stock** | Extra premium ↑ (scarcity) |

Prices use **effective** buy/sell counts in `market-data.yml` (plus live stock).  
The GUI shows trends in real time (`↑+25%`); at min/max multiplier you see **At max cap** / **At min cap**.  
All rates are configurable in **percentages**.

```yaml
dynamic-pricing:
  enabled: true
  base-price: 10.0
  per-buy-increase: 2.0              # +2% per buy
  per-sell-decrease: 2.0             # -2% per sell to system (1:1 recovery by default)
  per-stock-shortage-increase: 3.0   # +3% per missing stock unit
  reference-stock: 5
  min-multiplier: 0.2
  max-multiplier: 5.0
```

Buys while already at max cap still increment `total-buys` but not effective `buys`; sell the effective amount that caused the cap to restore base price.

Use `/shop price` to check **system sell and buyback prices** (with trends).

---

## Other Features

- **Full vanilla catalog** — potions, enchanted books, NBT variants
- **12 categories** — blocks, tools, weapons, armor, and more
- **Multi-language search** — item ID + localized names
- **In-game language switch** — `/lang zh_tw` / `/lang en_us`
- **Full NBT support** — trade enchanted books, potions, custom NBT items
- **Vault economy** — optional Vault integration

---

## Installation

1. Place `ashop-1.2.2.jar` in the `plugins/` folder
2. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin (e.g. EssentialsX)
3. Restart the server

---

## Commands

| Command | Description |
|---------|-------------|
| `/shop` | Open dynamic market GUI |
| `/shop price` | Check live market price |
| `/shop search <keyword>` | Search items |
| `/shop sell [price]` | List item (omit price for suggested) |
| `/shop reload` | Reload (admin) |
| `/lang <code>` | Switch language |

**Aliases:** `/商店` `/vs` `/language` `/語言`

---

## Configuration

See `plugins/ashop/config.yml`.  
Shop categories: `plugins/ashop/shop/<category>/items.yml` (auto-generated, editable by admins).  
Market stats: `plugins/ashop/market-data.yml`

---

## Build

```bash
./gradlew build
```

Requires **Java 25**.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
