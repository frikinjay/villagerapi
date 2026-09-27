# VillagerAPI Villager Pack Documentation

VillagerAPI lets you add villager professions, workstations, villager types, trades, explorer maps, hero gifts and biome mappings using nothing but JSON files and textures. You put them in a villager pack, drop the pack into your instance, and the mod handles the registration and datapack side for you.

This page covers the pack format, how packs are loaded, and the parts that tend to trip people up.

---

## Contents

- [Quick Start](#quick-start)
- [Where Packs Are Loaded From](#where-packs-are-loaded-from)
- [Pack Layout](#pack-layout)
- [villagerapi_config.json](#villagerapi_configjson)
- [pack.mcmeta](#packmcmeta)
- [How Packs Are Loaded](#how-packs-are-loaded)
- [Pack Order and villagerpacks_order.json](#pack-order-and-villagerpacks_orderjson)
- [Workstations](#workstations)
- [POI Types](#poi-types)
- [Villager Types](#villager-types)
- [Professions](#professions)
- [Structure Tags, Explorer Maps and Map Decorations](#structure-tags-explorer-maps-and-map-decorations)
- [Trades](#trades)
- [Overriding Vanilla and Other Mods' Trades](#overriding-vanilla-and-other-mods-trades)
- [Biome Trades](#biome-trades)
- [Hero Gifts](#hero-gifts)
- [Biome Mappings](#biome-mappings)
- [Village Types](#village-types)
- [Village Structures](#village-structures)
- [Repurposed Structures Compatibility](#repurposed-structures-compatibility)
- [Generated Village Packs](#generated-village-packs)
- [Structure Palette Tools](#structure-palette-tools)
- [Translations and Textures](#translations-and-textures)
- [Datapack Content You Still Need](#datapack-content-you-still-need)
- [Reference Lists](#reference-lists)
- [Validation and Logs](#validation-and-logs)
- [Troubleshooting](#troubleshooting)
- [For Mod Developers](#for-mod-developers)

---

## Quick Start

1. Open your Minecraft instance folder. Start the game once with VillagerAPI installed and a `villagerpacks` folder will be created for you.
2. Inside `villagerpacks`, make a folder for your pack, for example `my_pack`.
3. Add a `villagerapi_config.json` to the root of that folder. A pack without this file is ignored.
4. Add a `pack.mcmeta` so the pack's data and assets can be loaded.
5. Add your content under `villagers/`, `assets/` and `data/` as described below.
6. Launch the game and check the log. VillagerAPI prints every pack it found and anything wrong with it.

---

## Where Packs Are Loaded From

VillagerAPI looks for packs in two places:

- **The `villagerpacks` folder** in your instance directory. A pack there can be a plain folder, a `.zip` or a `.jar`.
- The `resourcepacks` folder in your instance directory. Any folder, `.zip` or `.jar` there that has a `villagerapi_config.json` at its root is loaded as a villager pack, exactly like one in `villagerpacks`. Ordinary resource packs in the same folder are ignored. The pack will also appear in the Resource Packs screen, but it doesn't need to be enabled there to work.
- **Inside mod jars.** Any installed mod that has a `villagerapi_config.json` at the root of its jar is treated as a villager pack. This is how a mod like More Villagers ships its content.

Both kinds work the same way once they are found.

---

## Pack Layout

```
villagerpacks/my_pack/
├── villagerapi_config.json        required
├── pack.mcmeta                    needed for data and assets to load
├── pack.png                       optional icon
├── villagers/
│   ├── workstations/              workstation blocks
│   ├── poi_types/                 job site POIs
│   ├── types/                     villager types (biome looks)
│   ├── professions/               professions
│   ├── trades/                    trades per profession
│   ├── biome_trades/              trades limited to certain villager types
│   ├── gifts/                     hero of the village gifts
│   ├── structure_tags/            explorer map definitions
│   ├── biome_mappings/            biome to villager type mappings
│   ├── village_types/             new villages copied from existing ones
│   └── village_structures/        houses added to villages
├── assets/{namespace}/
│   ├── lang/en_us.json
│   ├── blockstates/
│   ├── models/block/
│   ├── models/item/
│   └── textures/
│       ├── block/
│       ├── entity/villager/profession/
│       ├── entity/villager/type/
│       ├── entity/zombie_villager/profession/
│       ├── entity/zombie_villager/type/
│       └── map/decorations/
└── data/
    ├── {namespace}/
    │   ├── loot_table/
    │   └── tags/worldgen/structure/
    └── minecraft/tags/
        ├── block/mineable/
        └── point_of_interest_type/
```

You only need the folders you actually use. A pack that only changes trades can be just `villagerapi_config.json`, `pack.mcmeta` and `villagers/trades/`.

---

## villagerapi_config.json

Every pack needs this file in its root.

```json
{
  "namespace": "morevillagers",
  "display_name": "More Villagers",
  "description": "Adds new villager professions and trades",
  "version": "1.0.0",
  "author": "Your Name",
  "creative_tab_icon": "minecraft:emerald"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `namespace` | Strongly recommended | Cleaned-up folder, zip or mod name | Identifies the pack. Lowercase letters, numbers, `_`, `.` and `-` only. |
| `display_name` | No | Folder, zip or mod name | Shown in logs, the creative tab and the pack list. |
| `description` | No | Empty | |
| `version` | No | `1.0.0` | |
| `author` | No | `Unknown` | |
| `creative_tab_icon` | No | `minecraft:emerald` | Item shown on the pack's creative tab. Falls back to an emerald if the item doesn't exist. |

### The namespace is the pack's identity

The namespace is used as the pack's name everywhere VillagerAPI needs one. It is what shows up in `villagerpacks_order.json`, it decides where a pack's trades land when several packs change the same profession, and it names the creative tab. Set it explicitly.

If you leave it out, VillagerAPI builds one from the folder, zip or mod name. It lowercases it, swaps invalid characters for underscores, and strips version and loader suffixes such as `-1.0.0`, `_v2.1`, `-mc1.21.1`, `+fabric`, `-fabric`, `-forge` and `-neoforge`. That works, but renaming the folder later will then change your pack's identity.

### Namespaces must be unique

Two packs cannot share a namespace. If they do, VillagerAPI keeps one and ignores the other completely. The ignored pack loses its registrations, trades, creative tab, datapack and resource pack.

The pack that is kept is picked the same way every time. Packs in `villagerpacks` win over packs in `resourcepacks`, which win over packs inside mods. Within each group, packs are compared in alphabetical order of their file or folder name (mod ID for mod packs). The log shows an error naming both packs, for example:

```
Villagerpack 'MoreVillagersAddon.zip' in villagerpacks/ is ignored: its namespace 'morevillagers' is already used by mod 'morevillagers'. Each villagerpack must use a unique namespace in its villagerapi_config.json
```

### Default namespace for your content

Workstations, POI types, villager types, professions and structure tags each take an optional `namespace` field. If you leave it out, the pack's namespace is used. You only need the field if you want to register something under a different namespace than the rest of your pack.

### Creative tab

Each pack gets its own creative tab containing its workstation blocks. Name it with the translation key `itemGroup.{namespace}.villagerpack_tab`.

---

## pack.mcmeta

VillagerAPI registers your pack as a datapack and a resource pack behind the scenes, and Minecraft needs a `pack.mcmeta` to do that. Without one, anything under `data/` and `assets/` won't load, and neither will trades. Registration of workstations, professions and so on still happens, but that's rarely useful on its own.

```json
{
  "pack": {
    "description": "My Villager Pack",
    "pack_format": 48,
    "min_format": 38,
    "max_format": 1000,
    "supported_formats": [38, 1000]
  }
}
```

Use the pack format for the Minecraft version you're targeting. Current numbers are listed at https://minecraft.wiki/w/Pack_format. Declaring a wide supported range, like the example above, avoids the "made for an older version" warning.

---

## How Packs Are Loaded

You don't need to install your villager pack as a separate datapack or resource pack. VillagerAPI does it for you.

- **Datapacks.** Every pack with a `data/` or `villagers/` folder is added to the world as a datapack. The datapack is always enabled and sits above vanilla and mod data.
- **Resource packs.** Every pack with an `assets/` folder is added as a resource pack. It shows up in the resource pack screen as `Villager Pack: {display_name}` and you can move it up or down there like any other resource pack.
- **Generated files.** On top of your own files, VillagerAPI generates files in memory when the packs load. You won't see them on disk. Your trade files become proper `villager_trade`, trade set and tag files. Each explorer map gets an item texture, model and item definition.

The pack ID is `villagerpack:` followed by your pack's namespace, for example `villagerpack:morevillagers`. The name shown in the pack screens uses your display name, so you can change the display name freely without affecting existing worlds.

---

## Pack Order and villagerpacks_order.json

When two packs touch the same thing, the pack higher in the order wins. By default the order is alphabetical by namespace.

VillagerAPI keeps a file at `villagerpacks/villagerpacks_order.json` and updates it every time the game starts:

```json
{
  "manualOrderingEnabled": false,
  "packOrder": [
    "morevillagers",
    "my_pack"
  ]
}
```

- While `manualOrderingEnabled` is `false`, `packOrder` just shows the automatic order and is rewritten on each launch.
- Set `manualOrderingEnabled` to `true` to use your own order. The first entry is the highest priority and the last entry is the lowest.
- With manual ordering on, your order is kept. New packs are added to the end, and packs that are no longer installed are removed from the list.
- If the file contains broken JSON, VillagerAPI logs an error and falls back to the automatic order for that session. It won't overwrite your file, so fix the typo and restart.

Order matters for three things:

- **Trades.** When several packs add trades to the same profession level, they are combined from the top of the order down, and a pack that replaces the level cuts off everything below it. See [Overriding Vanilla and Other Mods' Trades](#overriding-vanilla-and-other-mods-trades).
- **Registry content with the same file name.** If two packs both have, for example, `villagers/professions/miner.json`, only the higher pack's file is used. A warning is logged for the one that was skipped.
- **Biome mappings.** Every pack's mappings for a biome are combined from the top of the order down, and a pack with `"replace": true` for a biome cuts off the packs below it. See [Biome Mappings](#biome-mappings).
- **Village types with the same ID.** Only the highest pack's definition is used, and the others are skipped with a warning.

Plain files in your `data/` folder are not reordered by this file. Those follow Minecraft's normal datapack order, which you can change in game with `/datapack`.

---

## Workstations

**Path:** `villagers/workstations/{name}.json`

Registers a new block and its item. The file name is the block ID. The block behaves like a cartography table: wood, broken fastest with an axe, same hardness.

```json
{
  "name": "purpur_altar",
  "namespace": "morevillagers"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `name` | Yes | - | Keep it the same as the file name. The file name is what's used for the ID. |
| `namespace` | No | Pack namespace | Namespace the block is registered under. |

The example above registers `morevillagers:purpur_altar`. The block is added to your pack's creative tab automatically.

The block itself has no look or drops until you give it some:

- Blockstate: `assets/{namespace}/blockstates/{name}.json`
- Block model: `assets/{namespace}/models/block/{name}.json`
- Item model: `assets/{namespace}/models/item/{name}.json`
- Item definition: `assets/{namespace}/items/{name}.json`
- Textures: `assets/{namespace}/textures/block/...` (16x16)
- Loot table so it drops itself: `data/{namespace}/loot_table/blocks/{name}.json`
- Mining tag, usually `data/minecraft/tags/block/mineable/axe.json`

You don't need a custom workstation for every profession. A POI type can point at any block, including vanilla ones.

---

## POI Types

**Path:** `villagers/poi_types/{name}.json`

A POI type is what makes a block a job site. The file name is the POI ID and is also the name professions use to refer to it.

```json
{
  "block": "morevillagers:purpur_altar",
  "namespace": "morevillagers"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `block` | Yes | - | Any block ID, from your pack, vanilla or another mod. For example, More Villagers uses `minecraft:decorated_pot` for its explorer. |
| `namespace` | No | Pack namespace | Namespace the POI is registered under. |

All block states of the block count as the job site. If the block doesn't exist, a warning is logged on server start and the profession using it won't work.

Blocks from [workstation files](#workstations) are always registered before POI types, in every pack, so a POI can point at any villager pack's workstation. It can also point at a block another mod registers in its own code. On Fabric that mod may not have registered its blocks yet when VillagerAPI registers the POI. VillagerAPI registers the POI anyway, and links it to its block the moment the block is registered, logging `Linked POI type ... to block ...`. Mods don't need to do anything for this. On NeoForge blocks are always registered first.

Unemployed villagers only look for POIs listed in the `acquirable_job_site` tag. You have to add yours there, see [Datapack Content You Still Need](#datapack-content-you-still-need).

---

## Villager Types

**Path:** `villagers/types/{name}.json`

A villager type is the biome look of a villager, like vanilla's desert or taiga villagers. The file name is the type ID.

```json
{
  "name": "jungle_dweller",
  "namespace": "morevillagers"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `name` | Yes | - | Must be present. Keep it the same as the file name. |
| `namespace` | No | Pack namespace | Namespace the type is registered under. |

Registering a type on its own does nothing visible. Villagers only spawn with it once you map biomes to it with [Biome Mappings](#biome-mappings).

Textures:

- `assets/{namespace}/textures/entity/villager/type/{name}.png`
- `assets/{namespace}/textures/entity/zombie_villager/type/{name}.png`
- `assets/{namespace}/textures/entity/villager/baby/{name}.png`
- `assets/{namespace}/textures/entity/zombie_villager/baby/{name}.png`

Since Minecraft 26.1, baby villagers have their own textures, so each type needs the two `baby/` textures as well. Without them, baby villagers of the type show the missing texture.

Each can have a `.png.mcmeta` next to it to control how the hat layer is drawn:

```json
{
  "villager": {
    "hat": "full"
  }
}
```

---

## Professions

**Path:** `villagers/professions/{name}.json`

The file name is the profession ID.

```json
{
  "poi_type": "enderian",
  "work_sound": "minecraft:entity.villager.work_cleric",
  "namespace": "morevillagers"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `poi_type` | Yes | - | The file name of a POI type in `villagers/poi_types/`, without `.json`. Not a full ID. It can be a POI from any loaded villager pack. |
| `work_sound` | Yes | - | A sound event ID. If the sound doesn't exist, the armorer work sound is used. |
| `namespace` | No | Pack namespace | Namespace the profession is registered under. |

The example registers `morevillagers:enderian`. If the POI type can't be found, the profession is still registered, so nothing crashes. But no villager will ever take the job, and a warning is logged.

Each profession gets trade levels 1 to 5, novice to master. Its trades come from `villagers/trades/`, see [Trades](#trades).

The profession's display name uses the translation key `entity.minecraft.villager.{namespace}.{name}`.

Textures:

- `assets/{namespace}/textures/entity/villager/profession/{name}.png`
- `assets/{namespace}/textures/entity/zombie_villager/profession/{name}.png`

Optional `.png.mcmeta` for the hat:

```json
{
  "villager": {
    "hat": "partial"
  }
}
```

Vanilla work sounds you can reuse, all starting with `minecraft:entity.villager.work_`: armorer, butcher, cartographer, cleric, farmer, fisherman, fletcher, leatherworker, librarian, mason, shepherd, toolsmith, weaponsmith.

---

## Structure Tags, Explorer Maps and Map Decorations

**Path:** `villagers/structure_tags/{name}.json`

Each file here sets up one kind of explorer map. From a single file VillagerAPI will:

1. Register a map item for it.
2. Register the map decoration, which is the icon marking the structure on the map, unless that decoration already exists.
3. Generate the map item's texture, model and item definition, so you don't have to draw an item icon for every map.

```json
{
  "tag": "on_end_city_explorer_maps",
  "map_decoration": "end_city_decoration",
  "map_color": "#ac7bac",
  "namespace": "morevillagers"
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `tag` | Yes | - | Name of the structure tag the map searches for. |
| `map_decoration` | Yes | - | Decoration ID. Without a namespace it uses `namespace`. With one, like `minecraft:red_x`, that decoration is used as is. |
| `map_color` | Yes | - | Hex color, `#RRGGBB`. Used to tint the generated item icon. |
| `namespace` | No | Pack namespace | Namespace for the map item, the decoration and the tag. |

### The map item

The item ID comes from the tag name. A leading `on_` is removed. Then a trailing `_explorer_maps` or `_maps` is removed, and `_map` is added.

| Tag | Map item |
|-----|----------|
| `on_end_city_explorer_maps` | `{namespace}:end_city_map` |
| `on_shipwreck_maps` | `{namespace}:shipwreck_map` |
| `on_trial_chambers_maps` | `{namespace}:trial_chambers_map` |

The item is a real map item, so once a trade fills it in, it works exactly like a vanilla explorer map.

### The generated icon

The inventory icon is built from two images:

- VillagerAPI's own blank map (16x16), tinted 10% toward your `map_color`.
- Your decoration texture (8x8), drawn on top of it 5 pixels in from the left and 5 pixels down from the top.

VillagerAPI looks for the decoration texture at `assets/{decoration namespace}/textures/map/decorations/{decoration name}.png`. It checks your own pack first, then vanilla and other mods. If it can't find the texture at all, you get the tinted blank map without an icon, and a warning in the log.

If you'd rather draw the icon yourself, put your own texture at `assets/{namespace}/textures/item/{item name}.png`. Then give your pack's resource pack a higher position so it takes priority.

### Map decorations

A new decoration is registered with the same behaviour as vanilla structure markers: it shows on item frames and counts toward the map's decoration limit. Its texture is `assets/{namespace}/textures/map/decorations/{decoration name}.png` and should be 8x8.

If `map_decoration` points at a decoration that already exists, such as `minecraft:red_x`, it is reused instead of being registered again.

### The actual structure tag

The file above only tells VillagerAPI about the map. You still have to create the real structure tag the map searches with, in your pack's `data/` folder:

`data/{namespace}/tags/worldgen/structure/{tag}.json`

```json
{
  "replace": false,
  "values": [
    "minecraft:end_city"
  ]
}
```

Keep the two files straight. They often share a file name, but they live in different folders and have different contents. If the real tag is missing, the world fails to load with an "Unbound tags" error pointing at your tag.

To use the map in a trade, see [Explorer map trades](#explorer-map-trades).

---

## Trades

**Path:** `villagers/trades/{any name}.json`

Trades are written per profession and per level. The file name doesn't matter. The `profession` field does.

On 26.1 and newer, trades must use the format shown here, with `wants` and `gives`. The older `buy_a` / `sell` format from the 1.21.1 version of VillagerAPI isn't supported. Minecraft rejects those trades, and the world won't load.

```json
{
  "profession": "morevillagers:oceanographer",
  "levels": {
    "1": [
      {
        "wants": { "id": "minecraft:prismarine", "count": 14 },
        "gives": { "id": "minecraft:emerald" },
        "max_uses": 16,
        "xp": 2
      },
      {
        "wants": { "id": "minecraft:emerald", "count": 6 },
        "gives": { "id": "minecraft:sea_lantern", "count": 1 },
        "max_uses": 16,
        "xp": 2
      }
    ],
    "2": [
      {
        "wants": { "id": "minecraft:emerald", "count": 8 },
        "additional_wants": { "id": "minecraft:compass", "count": 1 },
        "gives": { "id": "minecraft:recovery_compass" },
        "max_uses": 1,
        "xp": 10,
        "reputation_discount": 0.2
      }
    ]
  }
}
```

### Top level

| Field | Notes |
|-------|-------|
| `profession` | Profession ID. Without a namespace, your pack's namespace is assumed. It can be one of your professions, a vanilla one, or another mod's. |
| `levels` | Keys `"1"` to `"5"`, novice to master. Each value is either a list of trades, or an object with a `trades` list and the level options below. |

### Level options

Writing a level as an object lets you control how it's combined with other trades and how many trades a villager ends up with:

```json
"3": {
  "replace": false,
  "trade_list_amount": 2,
  "trade_list_max": { "size": 4, "replace": true },
  "trades": [ ... ]
}
```

| Field | Default | Notes |
|-------|---------|-------|
| `trades` | none | The trades for this level. |
| `replace` | `false` | When `false`, your trades are added to whatever the level already has. When `true`, they replace it. See [Overriding Vanilla and Other Mods' Trades](#overriding-vanilla-and-other-mods-trades). |
| `trade_list_amount` | every trade | How many of your trades a villager gets at this level. Picked by `trade_list_weight`. Zero or a negative number means none of your trades are offered at this level. |
| `trade_list_max` | none | The most trades a villager can have at this level, counting everyone's trades. See [Limiting the total](#limiting-the-total). |

A level written as a plain list is the same as an object with only `trades`.

### Trade fields

Each trade uses Minecraft's own villager trade format, so any field vanilla supports works here too.

| Field | Required | Notes |
|-------|----------|-------|
| `wants` | Yes | Item the player pays with. `id`, optional `count`, optional `components`. |
| `additional_wants` | No | Second payment item. |
| `gives` | Yes | Item the player gets. `id`, optional `count`, optional `components`. |
| `max_uses` | No | Uses before the villager needs to restock. |
| `xp` | No | Villager experience gained per trade. |
| `reputation_discount` | No | How much the player's reputation lowers the price. |
| `merchant_predicate` | No | A condition the villager must meet to offer the trade. Kept as is, and combined with any biome restriction. |
| `given_item_modifiers` | No | Item functions applied to the item the player gets. See below. |
| `trade_list_weight` | No | How likely this trade is to be picked when the level uses `trade_list_amount` or `trade_list_max`. Defaults to 1. Zero or a negative number means it's never picked. |

Counts and other numbers can be plain numbers or number providers, the same as vanilla. For example:

```json
"count": { "type": "minecraft:uniform", "min": 5, "max": 10 }
```

If you write a `merchant_predicate` in the older style, with `"condition"` instead of `"type"` or `"predicates"` instead of `"minecraft:predicates"`, it's converted to the current format for you.

Items with components:

```json
"gives": {
  "id": "minecraft:diamond_boots",
  "count": 1,
  "components": {
    "minecraft:enchantments": {
      "minecraft:feather_falling": 4,
      "minecraft:depth_strider": 3
    }
  }
}
```

### How many trades a villager gets

Trades are picked when a villager reaches a level, and each source of trades is picked on its own:

- **Your pack's trades.** Without `trade_list_amount`, a villager gets every trade you list for the level. With it, the villager gets that many, picked at random by `trade_list_weight`. The same trade is never picked twice.
- **The original trades**, if you're adding to an existing profession. These are picked the way the game or the original mod set them up. Vanilla farmers, for example, get two of their four novice trades.
- **Other packs' trades** at the same level, each with their own `trade_list_amount`.

A trade with a biome restriction that doesn't match the villager is skipped and another one is picked in its place, so restricted trades don't use up picks for villagers that can't have them.

A weight is relative to the other trades in the same list. A trade with weight 5 is five times as likely to be picked as one with weight 1. If every trade in the list has the same weight, the picks are plain random.

```json
"2": {
  "trade_list_amount": 1,
  "trades": [
    { "wants": { "id": "minecraft:emerald", "count": 1 }, "gives": { "id": "minecraft:coal", "count": 4 }, "trade_list_weight": 5 },
    { "wants": { "id": "minecraft:emerald", "count": 6 }, "gives": { "id": "minecraft:diamond" }, "trade_list_weight": 1 }
  ]
}
```

Here a villager gets one trade at level 2. Most of the time it's the coal trade, and about one villager in six gets the diamond trade.

### Limiting the total

`trade_list_max` caps how many trades a villager can have at a level once everything has been picked. That includes the original trades and every pack's trades.

| Field | Notes |
|-------|-------|
| `size` | The cap. Must be a positive whole number. |
| `replace` | When `true`, this pack's `size` is the cap and caps from packs below it are ignored. When `false`, this pack's `size` is added to the cap from the packs below it. If nothing below sets a cap, it's the cap on its own. |

If the villager ends up with more trades than the cap, VillagerAPI keeps that many of them, picked by weight. Original trades count as weight 1. The trades that are kept stay in their usual order: the original trades first, then each pack's trades from the lowest pack to the highest.

Say vanilla farmers get two novice trades, and your pack adds three with `trade_list_max` set to `{ "size": 3, "replace": true }`. A novice farmer ends up with three of those five, favouring your higher weighted trades.

### Item modifiers

Write item functions under `given_item_modifiers` and name each one with `function`. VillagerAPI converts them to the format the game expects. Using `given_item_modifiers` and `function` is what triggers the explorer map handling below, so stick to them.

```json
"given_item_modifiers": [
  {
    "function": "minecraft:set_name",
    "name": { "translate": "filled_map.shipwreck" },
    "target": "item_name"
  }
]
```

### Explorer map trades

To sell an explorer map, use `minecraft:exploration_map` with `destination` set to one of your structure tags:

```json
{
  "wants": { "id": "minecraft:emerald", "count": 13 },
  "gives": { "id": "minecraft:map" },
  "given_item_modifiers": [
    {
      "function": "minecraft:exploration_map",
      "destination": "morevillagers:on_shipwreck_maps",
      "decoration": "morevillagers:red_x_decoration",
      "search_radius": 100
    },
    {
      "function": "minecraft:set_name",
      "name": { "translate": "filled_map.shipwreck" },
      "target": "item_name"
    },
    {
      "function": "minecraft:filtered",
      "item_filter": {
        "items": "minecraft:filled_map",
        "predicates": { "minecraft:map_id": {} }
      },
      "on_fail": { "function": "minecraft:discard" }
    }
  ],
  "max_uses": 12,
  "xp": 10
}
```

A few things happen to this trade automatically:

- The item in `gives` is replaced with the map item for that destination, `morevillagers:shipwreck_map` here. It doesn't matter what you put in `gives`.
- The `destination` gets a `#` added, because it's a tag.
- The destination's namespace is resolved like this:
  - If one of your `structure_tags` files defines that tag, its `namespace` is used.
  - Otherwise, a namespace written in the destination is used, unless it's `minecraft`.
  - Otherwise, your pack's namespace is used.

  The result is that maps always belong to your pack, even when they point at a vanilla-style tag name.
- A `decoration` without a namespace gets the destination's namespace.
- `items` inside `item_filter` is removed. The `filtered` / `discard` step in the example just drops the map if no structure was found nearby. Keep it, so villagers don't sell blank maps.

The map's name comes from `set_name`. Any translation key works, as long as it's in your lang file.

A map trade only shows up if a matching structure exists within the search radius of the villager. End City maps sold in the Overworld will never find anything.

### Trade IDs

Each trade becomes its own `villager_trade` entry under your pack's namespace:

- `{namespace}:{profession}/{level}/{profession}_trade_{level}_{n}` for your own professions.
- `{namespace}:{profession namespace}/{profession}/{level}/...` when targeting a profession from another namespace.

You'll see these IDs in the log if a trade fails to parse. Trades for the same profession in several files are numbered one after another, so they never overwrite each other. Trades with a `trade_list_weight` of zero or less can never be picked, so they aren't generated at all.

---

## Overriding Vanilla and Other Mods' Trades

There's no separate override format. A trade file's `profession` can point at any profession, including ones your pack didn't create, like `minecraft:farmer` or `othermod:blacksmith`. What happens to that profession's existing trades is decided per level.

### Adding to a level

By default your trades are added to what the level already has. This leaves farmers with all their usual trades, and adds a wheat trade that every novice farmer gets:

```json
{
  "profession": "minecraft:farmer",
  "levels": {
    "1": [
      {
        "wants": { "id": "minecraft:wheat", "count": 30 },
        "gives": { "id": "minecraft:emerald" },
        "max_uses": 16,
        "xp": 2
      }
    ]
  }
}
```

The original trades are still picked the way they were before, so a novice farmer gets their usual two vanilla trades plus yours. Use `trade_list_amount` if you'd rather have your trades rolled at random too, and `trade_list_max` if you want to limit the total.

### Replacing a level

Set `"replace": true` on a level to throw away what it had and use only your trades:

```json
{
  "profession": "minecraft:farmer",
  "levels": {
    "1": {
      "replace": true,
      "trades": [
        {
          "wants": { "id": "minecraft:wheat", "count": 30 },
          "gives": { "id": "minecraft:emerald" },
          "max_uses": 16,
          "xp": 2
        }
      ]
    }
  }
}
```

Now novice farmers only have the wheat trade. To empty a level completely, use `"replace": true` with an empty `trades` list.

Levels you don't mention are left alone. The examples above only touch level 1, so farmers keep their normal trades at levels 2 to 5.

### Several packs on the same profession

Packs are combined in [pack order](#pack-order-and-villagerpacks_orderjson), working from the top down:

- Packs that add to a level all get their trades in, each picked with its own `trade_list_amount`.
- The first pack, going down, that replaces the level is the last one included. Its trades and the trades of every pack above it are kept. Packs below it, and the original trades, are dropped for that level.
- If no pack replaces the level, the original trades are kept as well.

This works the same for professions from villager packs, so an addon pack can add to or replace trades of a More Villagers profession.

### Where the original trades come from

To add to a level, VillagerAPI needs to find the profession's original trade set file. It looks in villager packs' `data/` folders, then in mod jars, then in the game itself. If it can't find the file for a vanilla or modded profession, it leaves that level untouched and logs an error, rather than risk wiping out trades it couldn't see. Using `"replace": true` on that level works either way, since nothing needs to be read.

Anything other datapacks or mods add to the original trade list is kept when you add to a level, because VillagerAPI refers to the original list instead of copying it.

### Limitations

- This only works on trades defined as data, which is how vanilla does it. If a mod adds trades purely through code, VillagerAPI can't touch those.
- If a regular datapack (not a villager pack) replaces a vanilla trade set file, VillagerAPI still reads the vanilla one when adding to that level.

---

## Biome Trades

**Path:** `villagers/biome_trades/{any name}.json`

Biome trades are trades that only villagers of certain types will offer. By default they are added on top of the profession's normal trades for that level. With `replace`, they take the place of the normal trades for those villager types instead.

```json
{
  "profession": "morevillagers:merchant",
  "biome_overrides": {
    "morevillagers:jungle_dweller": {
      "levels": {
        "5": [
          {
            "wants": { "id": "minecraft:pufferfish", "count": 4 },
            "gives": { "id": "minecraft:emerald" },
            "max_uses": 12,
            "xp": 30
          }
        ]
      }
    },
    "minecraft:desert,minecraft:savanna": {
      "levels": {
        "3": {
          "replace": true,
          "trades": [
            {
              "wants": { "id": "minecraft:emerald", "count": 4 },
              "gives": { "id": "minecraft:gold_ingot" },
              "max_uses": 12,
              "xp": 30
            }
          ]
        }
      }
    }
  }
}
```

### Villager type keys

- The keys under `biome_overrides` are **villager type IDs**, not biome IDs. They can be vanilla types like `minecraft:desert` or your own.
- A key without a namespace, like `jungle_dweller`, uses your pack's namespace.
- Put several types in one key, separated by commas, to share the same trades between them.

### Levels

Levels use the same format as normal trades: either a list of trades, or an object with a `trades` list. The object form also takes `replace` and `trade_list_amount`.

| Field | Default | Notes |
|-------|---------|-------|
| `trades` | none | The trades these villager types get at this level. |
| `replace` | `false` | When `true`, villagers of these types stop getting the profession's normal trades at this level and only get the biome trades. |
| `trade_list_amount` | every trade | Works the same as on normal trades. Your biome trades and your normal trades for a level share one list, so if both set it, the normal trades file wins. |

Each biome trade can also have a `trade_list_weight`, the same as a normal trade.

Note that `replace` here means something narrower than `replace` on a normal trade level. On a normal level it replaces the whole level for everyone. Here it only removes the normal trades for the listed villager types.

In the example above, desert and savanna merchants get the gold ingot trade at level 3 instead of the normal level 3 trades. Every other merchant keeps the normal level 3 trades and never sees the gold ingot trade. Jungle dwellers get the pufferfish trade in addition to the normal level 5 trades.

A few details about `replace`:

- It only removes the profession's normal trades. Other biome trades that apply to the same types are kept, whether or not they use `replace` themselves.
- `"replace": true` with an empty `trades` list is allowed. It removes that level's normal trades for those types and adds nothing.
- It removes the normal trades from every villager pack that contributes to that level. It can't remove the original trades of a level you're adding to, because those aren't copied into your pack. A warning is logged when that happens. If you need those gone as well, replace the whole level in your normal trades file.

### How the restriction is written

Each biome trade gets a `merchant_predicate` that checks the villager's type. When `replace` is used, each normal trade at that level gets the opposite check, excluding those types. If a trade already has its own `merchant_predicate`, VillagerAPI doesn't overwrite it. It combines the two, so the villager has to pass both.

### Across packs

Biome trades are combined with other packs the same way as normal trades, level by level. A pack that only has biome trades for a profession adds them on top of what's already there. It doesn't replace anything unless it uses `replace`.

---

## Hero Gifts

**Path:** `villagers/gifts/{any name}.json`

Sets the loot table a profession uses when throwing gifts at a Hero of the Village.

```json
{
  "profession": "morevillagers:alchemist",
  "loot_table": "morevillagers:gameplay/hero_of_the_village/alchemist_gift"
}
```

The profession can be yours or any other registered profession. Gifts are applied when the server starts. If the profession can't be found, a warning is logged and that gift is skipped.

Make the loot table itself at `data/morevillagers/loot_table/gameplay/hero_of_the_village/alchemist_gift.json`:

```json
{
  "type": "minecraft:gift",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:potion", "weight": 2 },
        { "type": "minecraft:item", "name": "minecraft:glowstone_dust", "weight": 1 }
      ]
    }
  ]
}
```

On NeoForge, also add the gift to NeoForge's data map, because NeoForge reads hero gifts from there. The path is `data/neoforge/data_maps/villager_profession/raid_hero_gifts.json`:

```json
{
  "values": {
    "morevillagers:alchemist": {
      "loot_table": "morevillagers:gameplay/hero_of_the_village/alchemist_gift"
    }
  }
}
```

It's harmless to include this file on Fabric too, so one pack can serve both loaders.

---

## Biome Mappings

**Path:** `villagers/biome_mappings/{any name}.json`

Decides which villager types spawn in which biomes. A biome can have several types, each with a weight, and a villager spawning there picks one at random.

```json
{
  "biomes": [
    { "biome": "minecraft:badlands", "villager_type": "morevillagers:badlands", "replace": true },
    { "biome": "minecraft:wooded_badlands", "villager_type": "morevillagers:badlands", "weight": 3 },
    { "biome": "#minecraft:is_jungle", "villager_type": "morevillagers:jungle_dweller", "weight": 6 }
  ]
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `biome` | Yes | - | A biome ID, or a biome tag starting with `#`. Tags are resolved when a world starts, so they include biomes other mods add to them. |
| `villager_type` | Yes | - | The villager type. Without a namespace it uses your pack's namespace. `type` works as well. |
| `weight` | No | 1 | How likely this type is compared to the other types for the same biome. |
| `replace` | No | `false` | Stops villager packs further down the [pack order](#pack-order-and-villagerpacks_orderjson) from adding types for this biome. |

### How the types for a biome are picked

- Every villager pack that maps a biome adds its types to that biome's pool, with their weights. Packs are combined from the top of the order down, and a pack with `"replace": true` for a biome is the last one included for it.
- Vanilla's own type for the biome is never part of the pool once any pack maps the biome. So desert villagers don't appear in a biome your packs map, unless you map `minecraft:desert` yourself.
- A type that another mod puts on the biome, through its own code or through a data map, stays in the pool with weight 1. `"replace": true` removes it as well.
- Biomes that no pack maps are left completely alone.
- Two packs mapping the same type to the same biome add their weights together.

### Villages and other structures

Villagers placed by structures, such as villages, carry a villager type in the structure file. In a biome that a villager pack maps, VillagerAPI replaces that stored type with one picked from the biome's pool. This is what makes villagers in a [generated badlands village](#village-types) badlands villagers instead of desert ones. In biomes no pack maps, structure villagers keep their stored type, the same as vanilla.

### Older format

The older format, with biomes as keys, still works. Every entry in it counts as `"replace": true`, which is how it behaved before:

```json
{
  "biomes": {
    "minecraft:badlands": "morevillagers:badlands",
    "minecraft:eroded_badlands": "morevillagers:badlands"
  }
}
```

### Notes

- Any biome works, including modded ones. Any villager type works, including vanilla and other mods' types.
- A villager type or biome that doesn't exist is skipped with a warning.
- Mappings are applied when a world starts. Villagers that already exist keep their type.
- The same random pick is used when villagers breed, since vanilla gives half of all babies the biome's type.

---

## Village Types

**Path:** `villagers/village_types/{folder}/{any name}.json`

Adds a new kind of village, made by copying an existing one and optionally changing some of its structures. For example, a badlands village built from the desert village with red sandstone instead of sandstone.

```json
{
  "namespace": "morevillagers",
  "name": "village_badlands",
  "copy_from": "minecraft:village_desert",
  "biomes": ["#minecraft:is_badlands"],
  "weight": 1
}
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `name` | Yes | - | The village's structure ID, together with `namespace`. |
| `namespace` | No | Pack namespace | |
| `copy_from` | Yes | - | The village to copy. Vanilla's are `minecraft:village_plains`, `village_desert`, `village_savanna`, `village_snowy` and `village_taiga`. Other mods' jigsaw villages work too. |
| `biomes` | Yes | - | Biome IDs and `#tags` the village generates in. Biomes from mods that aren't installed are skipped. |
| `weight` | No | 1 | The village's weight in `minecraft:villages`. Each vanilla village has weight 1. |

### Changing the copied structures

Next to the JSON, make a folder with the same name as `name`. Any `.nbt` file in it replaces the copied structure with the same relative path. The paths are the same as in the source village's structure folder, which for the desert village is `data/minecraft/structure/village/desert/`:

```
villagers/village_types/badlands/
├── village_badlands.json
└── village_badlands/
    ├── houses/desert_small_house_1.nbt
    ├── streets/straight_01.nbt
    └── zombie/houses/desert_small_house_1.nbt
```

- Structures you don't replace are copied from the source village unchanged.
- The abandoned "zombie" variants live in `zombie/`, so replacing `houses/x.nbt` doesn't change `zombie/houses/x.nbt`.
- A replacement that doesn't match any structure in the source village is listed in the log.
- The [structure palette tools](#structure-palette-tools) can make block-swapped copies of a whole village folder for you.

### How copying works

The village's pieces are joined by jigsaw blocks, and every jigsaw block names the pool it connects to, for example `minecraft:village/desert/houses`. VillagerAPI copies every pool and structure that belongs to the source village, and rewrites every one of those names, including inside your replacement structures. So `minecraft:village/desert/houses` becomes `morevillagers:village/badlands/houses`. Pools shared between villages, like `minecraft:village/common/cats`, stay shared.

The new village uses the folder `village/<name without "village_">/`. For `village_badlands` that's `morevillagers:village/badlands/...`.

### Where it generates

- The village is added to vanilla's `minecraft:villages` structure set when a world starts. Villages in that set share the same spacing, and in each spot a village could generate, one of the village types whose biome matches is picked by weight. So your village generates as often as vanilla villages do in its biomes.
- It is also added to the `#minecraft:village` structure tag, so `/locate` and other village checks find it.
- It only generates in new chunks.

Villagers in the village get their type from the biome, through [biome mappings](#biome-mappings). Map the village's biomes to your villager type, or the villagers keep the type stored in the copied structures.

---

## Village Structures

**Path:** `villagers/village_structures/{folder}/{any name}.json`

Adds houses to a village: a vanilla village, another mod's village, or a village from [Village Types](#village-types).

```json
{
  "namespace": "morevillagers",
  "village_type": "minecraft:village_plains",
  "houses": {
    "plains_woodworker": { "weight": 10 },
    "plains_florist": { "weight": 10, "processors": "minecraft:mossify_10_percent" }
  },
  "zombie_houses": {
    "plains_woodworker": { "weight": 2 }
  }
}
```

The structure files go next to the JSON:

```
villagers/village_structures/plains/
├── plains_structures.json
├── houses/plains_woodworker.nbt
└── zombie_houses/plains_woodworker.nbt
```

| Field | Required | Default | Notes |
|-------|----------|---------|-------|
| `village_type` | Yes | - | The village to add to. Without a namespace it uses your pack's namespace. |
| `namespace` | No | Pack namespace | Namespace the house structures are saved under. |
| `houses` | No | - | Houses for the normal village, by file name without `.nbt`. |
| `zombie_houses` | No | - | Houses for the abandoned zombie variant. |

Each house takes:

| Field | Default | Notes |
|-------|---------|-------|
| `weight` | 1 | How often the house is picked, compared to the other houses in the village. Between 1 and 150. Vanilla houses mostly use 1 to 3. |
| `processors` | The first house's processors in that pool | A processor list ID, for example `minecraft:mossify_10_percent` or `minecraft:zombie_desert`. |
| `element_type` | `legacy` | `legacy` skips the structure's air blocks, like vanilla village houses. `single` also places air, which clears terrain inside the house. |
| `location` | - | Use an existing structure instead of a file, for example `morevillagers:village/plains/plains_woodworker`. No `.nbt` file is needed then. |
| `max_count` | - | Only for [Repurposed Structures](#repurposed-structures-compatibility) villages: the most of this house one village can have. |

- Only listed houses are added. `.nbt` files that aren't listed are skipped with a warning.
- Houses added to a village from Village Types go straight into that village's generated pools. Jigsaw blocks in them that point at the source village's pools are rewritten, the same as the copied structures.
- Houses added to vanilla or other mods' villages are added to those villages' pools when a world starts. The same house is never added twice.

---

## Repurposed Structures Compatibility

When a `village_structures` file targets a Repurposed Structures village, for example `repurposed_structures:village_badlands`, VillagerAPI writes Repurposed Structures' own compatibility files instead of changing the pools itself:

- `data/{namespace}/rs_pool_additions/...`, which adds the houses to the village's `houses` or `zombie/houses` pool.
- `data/{namespace}/rs_pieces_spawn_counts/...`, for houses that set `max_count`.

```json
{
  "namespace": "morevillagers",
  "village_type": "repurposed_structures:village_bamboo",
  "houses": {
    "florist": {
      "location": "morevillagers:village/bamboo/florist",
      "weight": 4,
      "processors": "repurposed_structures:villages/bamboo/mossify",
      "max_count": 1
    }
  }
}
```

Repurposed Structures applies these files itself, and without it installed they're simply never read, so the same pack works with or without it. If you already ship hand-written `rs_pool_additions` or `rs_pieces_spawn_counts` files for the same houses, remove them, or the houses are added twice.

---

## Generated Village Packs

Everything VillagerAPI makes for villages, meaning copied structures, pools, structure files, biome tags, house structures and Repurposed Structures files, goes into a separate datapack for each villager pack:

- Pack ID: `villagerpack:<namespace>/generated`
- Folder: `.villagerapi/generated/<namespace>/` in your game directory. It is rebuilt every time datapacks load, so don't edit it by hand, but it's useful for checking what was generated.

These packs sit at the bottom of the datapack list. Any datapack or mod that provides the same file wins, and a warning in the log names the pack that overrides it. There's a second warning when a village type was copied from vanilla files that another pack changes, since those changes aren't in the copy.

---

## Structure Palette Tools

Two Python scripts that make block-swapped copies of structure files, for example turning the desert village into a badlands one. They only need Python 3, with no extra packages. You can download them with the villager pack generator on the More Villagers website.

1. Copy both scripts into a folder of structures, for example a copy of `data/minecraft/structure/village/desert` from the Minecraft jar, and run `python villager_palette_scan.py`. It writes `block_replacements.json`, listing every block the structures use.
2. Change the right-hand side of any block you want swapped, for example `"minecraft:sand": "minecraft:red_sand"`. Properties like `facing` are kept, so swap stairs for stairs and slabs for slabs.
3. Run `python villager_palette_apply.py`. The swapped copies are written to `generated_structures/` with the same folder layout, and the originals aren't changed.

`block_replacements.json` also has `replace_jigsaw_final_states`, which swaps the block each jigsaw becomes after generation, and `rename_files`, for example `{"desert_": "badlands_"}`. Leave `rename_files` empty when making replacements for a Village Types folder, since those have to keep their original names.

---

## Translations and Textures

### Language file

`assets/{namespace}/lang/en_us.json`

```json
{
  "entity.minecraft.villager.morevillagers.enderian": "Enderian",
  "block.morevillagers.purpur_altar": "Purpur Altar",
  "itemGroup.morevillagers.villagerpack_tab": "More Villagers",
  "filled_map.endcity": "End City Explorer Map"
}
```

| What | Key |
|------|-----|
| Profession | `entity.minecraft.villager.{namespace}.{profession}` |
| Workstation | `block.{namespace}.{name}` |
| Creative tab | `itemGroup.{namespace}.villagerpack_tab` |
| Map names | Whatever key you used in the trade's `set_name` |

Other language files go next to `en_us.json` with their usual names, like `de_de.json` or `zh_cn.json`.

### Texture sizes

| Texture | Path | Size |
|---------|------|------|
| Workstation faces | `assets/{namespace}/textures/block/...` | 16x16 |
| Profession overlay | `assets/{namespace}/textures/entity/villager/profession/{name}.png` | 64x64 |
| Villager type | `assets/{namespace}/textures/entity/villager/type/{name}.png` | 64x64 |
| Zombie villager versions | same paths under `entity/zombie_villager/` | 64x64 |
| Baby villager and zombie villager type | `.../villager/baby/{name}.png`, `.../zombie_villager/baby/{name}.png` | Same layout as the vanilla baby textures |
| Map decoration | `assets/{namespace}/textures/map/decorations/{name}.png` | 8x8 |

Villager textures follow the vanilla layout. The vanilla textures are a good starting point: https://minecraft.wiki/w/List_of_entity_textures#Mob_overlays

---

## Datapack Content You Still Need

VillagerAPI registers things and generates trades, but a few ordinary datapack files are still up to you.

### Acquirable job sites

`data/minecraft/tags/point_of_interest_type/acquirable_job_site.json`

Without this, unemployed villagers will walk right past your workstation.

```json
{
  "replace": false,
  "values": [
    "morevillagers:alchemist",
    "morevillagers:enderian"
  ]
}
```

Keep `replace` set to `false` so you don't wipe out vanilla's and other mods' job sites.

This is a POI type tag, so every entry must be a POI type ID, like `morevillagers:miner`, never a block ID. A POI that uses a block from another mod, say `waystones:waystone`, still goes in the tag under its own POI ID. If the tag lists anything that isn't a registered POI type, Minecraft refuses to load the whole tag. That also breaks `minecraft:village`, which includes it, and then no villager in the world can take any job, vanilla ones included. The log shows `Couldn't load tag minecraft:acquirable_job_site as it is missing following references`.

### Everything else

- Structure tags for your explorer maps: `data/{namespace}/tags/worldgen/structure/`
- Loot tables for workstation drops and hero gifts
- Mining tags for workstations: `data/minecraft/tags/block/mineable/`

---

## Reference Lists

### Vanilla map decorations

Use these with the `minecraft:` prefix, either in a trade's `decoration` or as a structure tag's `map_decoration`:

- Markers: `red_marker`, `blue_marker`, `target_x`, `target_point`, `red_x`
- Banners: `banner_white`, `banner_red`, `banner_blue` and the other dye colors
- Structures: `mansion`, `monument`, `village_desert`, `village_plains`, `village_savanna`, `village_snowy`, `village_taiga`, `jungle_temple`, `swamp_hut`, `trial_chambers`, `abandoned_camp`, `ancient_city`, `desert_pyramid`, `mineshaft`, `ocean_ruin_warm`
- Other: `player`, `frame`, `player_off_map`, `player_off_limits`

### Vanilla explorer map tags

These are the tags vanilla's cartographer uses, all with the `minecraft:` prefix:

- `on_ocean_monument_maps`
- `on_woodland_mansion_maps`
- `on_jungle_pyramid_maps`
- `on_swamp_hut_maps`
- `on_buried_trial_chambers_maps`
- `on_desert_village_maps`, `on_plains_village_maps`, `on_savanna_village_maps`, `on_snowy_village_maps`, `on_taiga_village_maps`

Vanilla has renamed these tags before, so if a map stops working after an update, check the vanilla data for the current names. If you want a map for one of these structures from your own profession, the cleanest approach is still your own `structure_tags` file plus your own tag listing the structure. That way your map item, icon and namespace are all yours.

### More Villagers explorer maps

All in the `morevillagers` namespace:

| Tag | Map item | Structure |
|-----|----------|-----------|
| `on_ancient_city_explorer_maps` | `ancient_city_map` | Ancient cities |
| `on_bastion_remnant_explorer_maps` | `bastion_remnant_map` | Bastion remnants |
| `on_end_city_explorer_maps` | `end_city_map` | End cities |
| `on_fortress_explorer_maps` | `fortress_map` | Nether fortresses |
| `on_jungle_temple_explorer_maps` | `jungle_temple_map` | Jungle pyramids |
| `on_mineshaft_explorer_maps` | `mineshaft_map` | Mineshafts |
| `on_pillager_outpost_explorer_maps` | `pillager_outpost_map` | Pillager outposts |
| `on_shipwreck_maps` | `shipwreck_map` | Shipwrecks |
| `on_swamp_hut_explorer_maps` | `swamp_hut_map` | Swamp huts |
| `on_trail_ruins_maps` | `trail_ruins_map` | Trail ruins |
| `on_trial_chambers_maps` | `trial_chambers_map` | Trial chambers |

---

## Validation and Logs

When the game starts, VillagerAPI checks every pack it finds and writes a report to the log. For each pack it lists:

- **Info:** how many professions, trades, types and other files it found.
- **Warnings:** things that probably still work but look off. Examples are no `pack.mcmeta`, no `assets/` folder, a profession without a `work_sound`, or textures without models.
- **Errors:** things that will break a feature. Examples are a profession pointing at a POI that doesn't exist, a trade file without `profession` or `levels`, a structure tag config missing a required field, or a structure tag in `data/` with no values.
- **Critical:** the pack has no config, or no `villagers/` or `data/` folder, so there's nothing to load.

The report is there to help you. It doesn't stop a pack from loading. A pack with errors still loads everything that does work.

Other lines worth looking for:

- The list of found packs, in order, printed right after validation.
- `Villagerpack trades for '...': defined N levels`, one line per profession villager packs changed. It says how many levels were defined (the profession had no trades there before), merged into (added to existing trades) or replaced. With debug logging on for `villagerapi`, there's also one line per level with the number of trades and the cap if there is one.
- `Built villagerpack trades for N profession(s): [...]`, listing every profession villager packs changed.
- `Registered villager type mappings for N biome(s) from villagerpacks`
- `Generated village type ... from ... (N pools, N structures, N added houses)`, one line per village type.
- `Added N villagerpack village type(s) to minecraft:villages` and `Added N villagerpack house(s) to existing village pools`
- `Prepared Repurposed Structures compat for N house(s) ...`
- `Pack '...' overrides N village file(s) generated in '...'`, when another pack replaces generated village files. The other pack's versions are used.
- `Village type ... was copied from vanilla files, but pack '...' changes N of those files`
- `Could not find the existing trade set '...' ... this level was left unchanged`
- `Linked POI type ... to block ...`, when a POI whose block didn't exist yet was connected to it once the block was registered.
- `Villagerpack ... is ignored: its namespace ... is already used by ...`
- `Could not find decoration texture ... using a tinted blank map icon instead`
- `Profession '...' is not registered, trade sets for it will use the default key convention`

---

## Troubleshooting

**The pack isn't found at all**
- Make sure `villagerapi_config.json` is in the pack's root, not in a subfolder. A zip that contains a folder that contains the files is a common mistake.
- Check the log for a duplicate namespace error.
- Make sure the pack is directly inside `villagerpacks`, not nested deeper.

**Registrations work but trades, textures or tags don't**
- The pack is missing `pack.mcmeta`, or it's malformed, so it couldn't be added as a datapack or resource pack.

**The world won't load and the log says "Unbound values" or "Unbound tags"**
- "Unbound tags" naming one of your structure tags means the real tag file under `data/{namespace}/tags/worldgen/structure/` is missing or in the wrong folder.
- "Unknown registry key" for a map item means the matching `structure_tags` config is missing or has no `tag` field, so the map item was never registered.
- A trade that fails to parse is named in the log by its generated trade ID. Look at the matching trade in your file.

**The world won't load and the log says "No key gives" or "No key wants"**
- A trade file uses the older `buy_a` / `sell` format from the 1.21.1 version. Rewrite it with `wants` and `gives`. See [Trades](#trades).

**A village type never generates**
- Look for `Generated village type` in the log. If it's missing, look for errors about the village type file, `copy_from` or `biomes`.
- Look for `Added N villagerpack village type(s) to minecraft:villages`. If there's a warning that the type isn't registered as a structure, a generated file failed to load, and the log has the reason.
- It only generates in new chunks, and only in its `biomes`. Use `/locate structure <namespace>:<name>` in a new world.

**A village has the wrong villagers**
- Map the village's biomes to your villager type with [biome mappings](#biome-mappings). Without a mapping, villagers keep the type stored in the copied structures.

**Added houses never appear**
- The house isn't listed in the `houses` object, or the `.nbt` file name doesn't match. Both are logged.
- The target village's pool couldn't be found. Look for `has no pool` in the log, and check `village_type`.
- Weights are relative to the village's other houses. Vanilla houses mostly use 1 to 3, so a weight of 1 is rare in a big pool.

**Houses appear twice in Repurposed Structures villages**
- The pack still ships its own `rs_pool_additions` files for the same houses. Remove them, since VillagerAPI generates them now.

**Villagers ignore the workstation**
- The POI's block comes from another mod and never got linked. Look for `Linked POI type` in the log. If it's missing, check that the block ID is right and that the mod adding it is installed.
- `acquirable_job_site` lists a block ID instead of a POI ID, so the whole tag failed to load. See [Acquirable job sites](#acquirable-job-sites).
- The POI isn't in `acquirable_job_site`.
- The profession's `poi_type` isn't the POI's file name.
- The POI's `block` ID is wrong. Look for "references missing block" in the log.

**Workstations aren't in the creative tab**
- The workstation's blockstate or item model is missing, so the entry shows up blank or not at all.
- Check that `creative_tab_icon` is a real item.

**Custom villager types never show up**
- No biome mapping points at the type, or the mapping uses the wrong ID.
- Existing villagers keep their old type. Test with freshly spawned ones.

**A map trade never appears**
- There's no matching structure within range of the villager, or it's the wrong dimension.
- The `filtered` / `discard` step is doing its job and throwing away a map that found nothing. That's expected.

**A map shows the wrong icon on the map itself**
- The trade's `decoration` controls the marker on the map. The structure tag config's `map_decoration` only controls the generated inventory icon. Make sure both point where you expect.

**A biome trade or replace doesn't seem to apply**
- The key is a biome ID instead of a villager type ID.
- The villager was already given its trades before you changed the pack. Trades are rolled when a villager reaches a level, so test with a new villager.
- A key without a namespace resolves to your pack's namespace. Write `minecraft:desert`, not `desert`, for vanilla types.

**The original trades at a level are gone**
- That level has `"replace": true`, either in your pack or in a pack above it. Remove it to add to the level instead.
- A `trade_list_max` is capping the level. The original trades can be dropped to make room for higher weighted ones.

**My trades never show up on vanilla villagers**
- Look for "Could not find the existing trade set" in the log. VillagerAPI couldn't read the original trades, so it left the level alone. Use `"replace": true` on that level, or check that the profession ID is right.
- Your pack's `trade_list_amount` for that level is zero or negative.
- Every trade in the list has a `trade_list_weight` of zero or less.

**My trades only show up on some villagers**
- `trade_list_amount` is lower than the number of trades you listed, so villagers only get some of them. That's expected.
- `trade_list_max` is set, by you or by a pack above you, and your trades are losing out to others. Raise the weights or the cap.

**My changes to another pack's profession aren't applied**
- A pack above yours replaces that level. Move your pack up in `villagerpacks_order.json` with manual ordering enabled.

**Hero gifts don't work**
- On NeoForge, add the data map file.
- Check the loot table path and that the profession ID is right.

---

## For Mod Developers

### Shipping a pack inside your mod

Put `villagerapi_config.json` at the root of your mod jar, next to `pack.mcmeta` if you have one, and add `villagers/` alongside your usual `assets/` and `data/`. VillagerAPI picks it up like any other pack. Give it a namespace that doesn't clash with anything in the `villagerpacks` folder.

In a development environment, your resources may be loaded from a different classpath entry than your classes. Nothing breaks, because VillagerAPI names packs by their namespace, but you may see oddly named mod containers in the log.

### How trades are picked in game

To handle `trade_list_amount`, `trade_list_weight` and `trade_list_max`, VillagerAPI takes over trade picking for the profession levels that villager packs change, and only those. Every other level, including other mods' professions that no villager pack touches and the wandering trader, is picked by the game as normal. If anything goes wrong while picking, VillagerAPI logs it and hands the level back to the game.

For every level it takes over, VillagerAPI still writes a normal trade set holding all the trades combined. If another mod replaces how villagers pick trades entirely, villagers still get the right trades, just without the per-pack amounts, weights and caps.

### Blocks your POIs use

A villager pack's POI types can use blocks your mod registers in its own code, on both loaders, with nothing extra to set up.

On Fabric, mods start up one after another, and VillagerAPI may register your pack's POI types before your mod has registered its blocks. VillagerAPI registers those POIs straight away, so their IDs exist and tags like `acquirable_job_site` always load. It then watches block registration and links each POI to its block as soon as that block is added, and logs `Linked POI type ... to block ...`. If a block somehow still isn't linked by the time a world starts, VillagerAPI tries once more then.

On NeoForge, deferred registers add all blocks before any POI types, so POIs have their blocks from the start.

If a POI's block doesn't exist at all, the world-start check logs `POI type '...' references missing block ...` and that profession won't work.
