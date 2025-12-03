# Villager Pack System Documentation

A data-driven framework for creating custom villager professions, types, trades, and biome mappings. Just drop JSON files in the `villagerpacks` folder—no code required.

---

## Quick Start

1. Navigate to your Minecraft instance directory
2. Create `villagerpacks` folder (if missing)
3. Inside, create a folder for your pack (e.g., `my_pack`)
4. Add your JSON files following the structure below

---

## Pack Structure

```
villagerpacks/my_pack/
├── pack.mcmeta                    # Pack metadata (required)
├── pack.png                       # Pack icon (optional)
├── villagers/                     # Custom villager data (required)
│   ├── poi_types/                 # Workstation blocks
│   ├── types/                     # Villager appearances
│   ├── professions/               # Job definitions
│   ├── trades/                    # Base trades
│   ├── biome_trades/              # Biome-specific trades
│   ├── gifts/                     # Hero gift mappings
│   └── biome_mappings/            # Biome→type connections
├── assets/morevillagers/          # Textures & translations
│   ├── lang/en_us.json
│   └── textures/entity/
│       ├── villager/
│       │   ├── profession/
│       │   └── type/
│       └── zombie_villager/
│           ├── profession/
│           └── type/
└── data/                          # Loot tables & tags
```

---

## Component Reference

### pack.mcmeta

Every pack needs this in the root folder:

```json
{
  "pack": {
    "pack_format": 48,
    "supported_formats": { "min_inclusive": 42, "max_inclusive": 1000 },
    "description": "My More Villagers Pack"
  }
}
```

Check https://minecraft.wiki/w/Pack_format for more details.

---

### POI Types

**Path**: `villagers/poi_types/[name].json`

Defines which block serves as the workstation. The filename becomes the POI identifier.

```json
{
  "block": "minecraft:crying_obsidian",
  "namespace": "morevillagers"
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| block | string | Yes | - | Block resource location |
| namespace | string | No | villagerapi | Namespace to register POI under |

**Example**: File `alchemist.json` creates POI type `alchemist`

---

### Villager Types

**Path**: `villagers/types/[name].json`

Controls villager appearance by biome. Must include matching textures in `assets/morevillagers/textures/entity/villager/type/[name].png` and `assets/morevillagers/textures/entity/zombie_villager/type/[name].png`

```json
{
  "name": "jungle_dweller",
  "namespace": "morevillagers"
}
```

The filename becomes the type identifier used in biome mappings.

---

### Professions

**Path**: `villagers/professions/[name].json`

Must include matching textures in `assets/morevillagers/textures/entity/villager/profession/[name].png` and `assets/morevillagers/textures/entity/zombie_villager/profession/[name].png`

Links a profession to its workstation and work sound.

```json
{
  "poi_type": "alchemist",
  "work_sound": "minecraft:entity.villager.work_cleric",
  "namespace": "morevillagers"
}
```

**Available vanilla work sounds**: armorer, butcher, cartographer, cleric, farmer, fisherman, fletcher, leatherworker, librarian, mason, shepherd, toolsmith, weaponsmith (all prefixed with `minecraft:entity.villager.work_`)

You can define your own sounds in the assets directory and use them instead.

---

### Trades

**Path**: `villagers/trades/[profession].json`

Defines what villagers buy and sell at each experience level (1=Novice to 5=Master).

```json
{
  "profession": "morevillagers:alchemist",
  "levels": {
    "1": [
      {
        "buy_a": {
          "item": "minecraft:nether_wart",
          "count": 22
        },
        "sell": {
          "item": "minecraft:emerald",
          "count": 1
        },
        "max_uses": 16,
        "xp": 2
      }
    ],
    "2": [ /* ... */ ]
  }
}
```

**Trade fields**:

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| buy_a | ItemStack | Yes | - | First purchase item |
| buy_b | ItemStack | No | - | Second purchase item |
| sell | ItemStack | Yes | - | Item sold to player |
| max_uses | integer | No | 16 | Uses before restocking |
| xp | integer | No | 1 | XP given to villager |
| price_multiplier | float | No | 0.05 | Price fluctuation rate |

**ItemStack formats**:

```json
// Simple
"minecraft:emerald"

// With count
{
  "item": "minecraft:emerald",
  "count": 5
}

// With components (enchantments, custom name, etc.)
{
  "item": "minecraft:diamond_sword",
  "count": 1,
  "components": {
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 5
      }
    },
    "minecraft:custom_name": "{\"text\":\"Legendary Blade\",\"color\":\"gold\"}"
  }
}
```

**Treasure map trade**:

```json
{
  "buy_a": {
    "item": "minecraft:emerald",
    "count": 12
  },
  "sell": {
    "item_type": "treasure_map",
    "structure_tag": "minecraft:on_ocean_explorer_maps",
    "display_name": "filled_map.monument",
    "map_decoration": "minecraft:banner_blue"
  }
}
```

---

### Biome-Specific Trades

**Path**: `villagers/biome_trades/[profession].json`

Adds extra or replaces trades for villagers in specific biomes.

```json
{
  "profession": "morevillagers:merchant",
  "biome_overrides": {
    "morevillagers:jungle_dweller": {
      "levels": {
        "5": {
          "replace": false,
          "trades": [
            {
              "buy_a": {
                "item": "minecraft:pufferfish",
                "count": 4
              },
              "sell": "minecraft:emerald",
              "max_uses": 12,
              "xp": 30
            }
          ]
        }
      }
    },
    "minecraft:desert": {
      "levels": {
        "3": {
          "replace": true,
          "trades": [
            {
              "buy_a": {
                "item": "minecraft:emerald",
                "count": 4
              },
              "sell": "minecraft:gold",
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

Keys in `biome_overrides` can be either custom villager types or vanilla biome IDs.

---

### Hero Gifts

**Path**: `villagers/gifts/[profession].json`

Specifies which loot table villagers use when throwing gifts after raids.

```json
{
  "profession": "morevillagers:alchemist",
  "loot_table": "morevillagers:gameplay/hero_of_the_village/alchemist_gift"
}
```

Create the loot table at `data/morevillagers/loot_table/gameplay/hero_of_the_village/alchemist_gift.json`:

```json
{
  "type": "minecraft:gift",
  "pools": [
    {
      "rolls": 1.0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:potion",
          "weight": 2
        },
        {
          "type": "minecraft:item",
          "name": "minecraft:glowstone_dust",
          "weight": 1
        }
      ]
    }
  ]
}
```

**NeoForge users**: Also add to `data/neoforge/data_maps/villager_profession/raid_hero_gifts.json`:

```json
{
  "values": {
    "morevillagers:alchemist": {
      "loot_table": "morevillagers:gameplay/hero_of_the_village/alchemist_gift"
    }
  }
}
```

---

### Biome Mappings

**Path**: `villagers/biome_mappings/[name].json`

Controls which villager type spawns in each biome. Multiple mapping files are allowed—later files override earlier ones.

```json
{
  "biomes": {
    "minecraft:jungle": "morevillagers:jungle_dweller",
    "minecraft:bamboo_jungle": "morevillagers:jungle_dweller",
    "minecraft:desert": "morevillagers:desert_nomad"
  }
}
```

---

## Item Components

Minecraft now uses components instead of NBT. Here are the most common:

**Enchantments**:
```json
"minecraft:enchantments": {
  "levels": {
    "minecraft:sharpness": 5,
    "minecraft:unbreaking": 3
  }
}
```

**Damage**:
```json
"minecraft:damage": 50
```

Visit https://minecraft.wiki/w/Item_components for more info on Item components.

---

## Additional Assets

### Translations

**Path**: `assets/morevillagers/lang/en_us.json`

```json
{
  "entity.minecraft.villager.morevillagers.alchemist": "Alchemist",
  "entity.minecraft.villager.alchemist": "Alchemist",

  "entity.minecraft.villager.morevillagers.jungle_dweller": "Jungle Dweller"
  "entity.minecraft.villager.jungle_dweller": "Jungle Dweller"
}
```

### Textures

**Profession overlay**: `assets/morevillagers/textures/entity/villager/profession/[profession].png`

**Villager type**: `assets/morevillagers/textures/entity/villager/type/[type].png`

Both should be 64x64 PNG files following Minecraft's villager texture layout.

You can find the layout and vanilla villager profession and type textures here https://minecraft.wiki/w/List_of_entity_textures#Mob_overlays.

---

## Data Pack Content

### Acquirable Job Sites Tag

**Path**: `data/minecraft/tags/point_of_interest_type/acquirable_job_site.json`

This makes your POI types usable by unemployed villagers:

```json
{
  "replace": false,
  "values": [
    "morevillagers:alchemist",
    "morevillagers:merchant"
  ]
}
```

Always use `"replace": false` for mod compatibility.

---

## Map Decorations & Structure Tags

**Map decorations** (for treasure maps):
- Banner colors: `banner_white`, `banner_red`, `banner_blue`, etc.
- Markers: `red_marker`, `blue_marker`, `target_x`, `target_point`
- Other: `player`, `frame`, `red_x`

All prefixed with `minecraft:`

**Structure tags** (vanilla):
- `minecraft:on_treasure_maps` - Buried treasure
- `minecraft:on_ocean_explorer_maps` - Ocean monuments
- `minecraft:on_woodland_explorer_maps` - Woodland mansions
- `minecraft:on_jungle_explorer_maps` - Jungle temples
- `minecraft:on_trial_chambers_maps` - Trial chambers

**Structure tags** (More Villagers):
- `morevillagers:on_fortress_explorer_maps` - Nether fortresses
- `morevillagers:on_bastion_remnant_explorer_maps` - Bastion remnants
- `morevillagers:on_end_city_explorer_maps` - End cities
- `morevillagers:on_swamp_hut_explorer_maps` - Swamp huts
- `morevillagers:on_jungle_temple_explorer_maps` - Jungle pyramids
- `morevillagers:on_pillager_outpost_explorer_maps` - Pillager outposts
- `morevillagers:on_mineshaft_explorer_maps` - Mineshafts
- `morevillagers:on_ancient_city_explorer_maps` - Ancient cities

---

## Troubleshooting

**Villagers won't recognize workstation**
- Check `acquirable_job_site.json` includes your POI type
- Verify block ID is correct
- Ensure POI filename matches profession's `poi_type` field

**Custom types don't appear**
- Add biome mappings
- Include textures at correct path
- Match type names exactly between files

**Trades crash or don't work**
- Validate JSON syntax at https://jsonlint.com
- Check all item IDs (use F3+H in-game)
- Verify component formatting
- Test structure tags exist

**Hero gifts not working**
- Check gift mapping path
- For NeoForge, verify data map exists
- Ensure loot table is valid JSON

**Biome trades missing**
- Match villager type names exactly
- Set up biome mappings first
- Register base trades before biome overrides


---






