# Villager Pack System Documentation

A data-driven framework for creating custom villager professions, types, trades, and biome mappings. Just drop JSON files in the `villagerpacks` folder—no code required.

---

## Quick Start

1. Navigate to your Minecraft instance directory
2. Create `villagerpacks` folder (if missing)
3. Inside, create a folder for your pack (e.g., `my_pack`)
4. **Create `villagerapi_config.json` in the root** (REQUIRED)
5. Add your JSON files following the structure below

---

## Pack Structure

```
villagerpacks/my_pack/
├── villagerapi_config.json        # Pack configuration (REQUIRED)
├── pack.mcmeta                    # Pack metadata (required for datapacks/resource packs)
├── pack.png                       # Pack icon (optional)
├── villagers/                     # Custom villager data
│   ├── workstations/              # Custom workstation blocks
│   ├── poi_types/                 # Workstation POI definitions
│   ├── types/                     # Villager appearances
│   ├── professions/               # Job definitions
│   ├── trades/                    # Base trades
│   ├── biome_trades/              # Biome-specific trades
│   ├── gifts/                     # Hero gift mappings
│   ├── structure_tags/            # Structure tags for treasure maps
│   └── biome_mappings/            # Biome→type connections
├── assets/morevillagers/          # Textures & translations
│   ├── lang/en_us.json
│   ├── textures/
│   │   ├── block/                 # Workstation textures
│   │   ├── entity/
│   │   │   ├── villager/
│   │   │   │   ├── profession/
│   │   │   │   └── type/
│   │   │   └── zombie_villager/
│   │   │       ├── profession/
│   │   │       └── type/
│   │   └── map/
│   │       └── decorations/       # Custom map decoration icons
│   ├── models/
│   │   ├── block/                 # Workstation block models
│   │   └── item/                  # Workstation item models
│   └── blockstates/               # Workstation blockstates
└── data/                          # Loot tables & tags
    ├── morevillagers/
    │   ├── loot_table/
    │   └── tags/worldgen/structure/
    └── minecraft/tags/
        ├── block/mineable/
        └── point_of_interest_type/
```

---

## Component Reference

### villagerapi_config.json (REQUIRED)

**Every villagerpack MUST have this file in its root directory.** Without it, the pack will be completely ignored.

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

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| namespace | string | Yes* | Sanitized pack name | Unique identifier for your pack. Must be lowercase, use underscores for spaces, only contain a-z, 0-9, _, ., and - |
| display_name | string | No | Pack folder name | Human-readable name shown in logs and creative tab |
| description | string | No | Empty | Brief description of your pack |
| version | string | No | "1.0.0" | Version string |
| author | string | No | "Unknown" | Pack creator name |
| creative_tab_icon | string | No | "minecraft:emerald" | Item to use as creative tab icon (format: namespace:item_id) |

\* While the namespace field is technically optional (will use sanitized pack folder name as fallback), it's **strongly recommended** to always specify it explicitly.

**Creative Tabs**: All workstations from your pack automatically appear in a creative tab with your chosen icon. The translation key is `itemGroup.{namespace}.villagerpack_tab`.

---

### pack.mcmeta

Required for resource pack and datapack features to work properly:

```json
{
  "pack": {
    "pack_format": 48,
    "supported_formats": { "min_inclusive": 38, "max_inclusive": 1000 },
    "description": "My More Villagers Pack"
  }
}
```

Pack format 48 is for Minecraft 1.21.1. Check https://minecraft.wiki/w/Pack_format for other versions.

---

### Workstations

**Path**: `villagers/workstations/[name].json`

Defines custom workstation blocks that villagers will use. These blocks automatically appear in your pack's creative tab.

```json
{
  "name": "purpur_altar",
  "namespace": "morevillagers"
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| name | string | Yes | - | Block identifier |
| namespace | string | No | Pack namespace | Namespace to register block under |

**Required assets**:
- Block textures: `assets/{namespace}/textures/block/{name}_front.png`, `_back.png`, `_left.png`, `_right.png`, `_top.png`, `_bottom.png`
- Block model: `assets/{namespace}/models/block/{name}.json`
- Item model: `assets/{namespace}/models/item/{name}.json`
- Blockstate: `assets/{namespace}/blockstates/{name}.json`
- Loot table: `data/{namespace}/loot_table/blocks/{name}.json`
- Mineable tag: `data/minecraft/tags/block/mineable/axe.json`

All textures should be 16x16 PNG files.

---

### POI Types

**Path**: `villagers/poi_types/[name].json`

Defines which block serves as the workstation. The filename becomes the POI identifier.

```json
{
  "block": "morevillagers:purpur_altar",
  "namespace": "morevillagers"
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| block | string | Yes | - | Block resource location |
| namespace | string | No | Pack namespace | Namespace to register POI under |

**Example**: File `enderian.json` with `"block": "morevillagers:purpur_altar"` creates POI type `enderian` linked to the purpur altar block.

---

### Villager Types

**Path**: `villagers/types/[name].json`

Controls villager appearance by biome. Must include matching textures.

```json
{
  "name": "jungle_dweller",
  "namespace": "morevillagers"
}
```

**Required textures**:
- `assets/{namespace}/textures/entity/villager/type/{name}.png`
- `assets/{namespace}/textures/entity/villager/type/{name}.png.mcmeta`
- `assets/{namespace}/textures/entity/zombie_villager/type/{name}.png`
- `assets/{namespace}/textures/entity/zombie_villager/type/{name}.png.mcmeta`

The .mcmeta file should contain:
```json
{
  "villager": {
    "hat": "full"
  }
}
```

---

### Professions

**Path**: `villagers/professions/[name].json`

Links a profession to its workstation and work sound.

```json
{
  "poi_type": "enderian",
  "work_sound": "minecraft:entity.villager.work_cleric",
  "namespace": "morevillagers"
}
```

**Required textures**:
- `assets/{namespace}/textures/entity/villager/profession/{name}.png`
- `assets/{namespace}/textures/entity/villager/profession/{name}.png.mcmeta`
- `assets/{namespace}/textures/entity/zombie_villager/profession/{name}.png`
- `assets/{namespace}/textures/entity/zombie_villager/profession/{name}.png.mcmeta`

The .mcmeta file should contain:
```json
{
  "villager": {
    "hat": "partial"
  }
}
```

**Available vanilla work sounds**: armorer, butcher, cartographer, cleric, farmer, fisherman, fletcher, leatherworker, librarian, mason, shepherd, toolsmith, weaponsmith (all prefixed with `minecraft:entity.villager.work_`)

---

### Structure Tags & Map Decorations

**Path**: `villagers/structure_tags/[name].json`

Defines custom structure tags for treasure map trades and their map decoration icons.

```json
{
  "tag": "on_end_city_explorer_maps",
  "map_decoration": "end_city_decoration",
  "map_color": "#ac7bac",
  "namespace": "morevillagers"
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| tag | string | Yes | - | Structure tag identifier |
| map_decoration | string | Yes | - | Map decoration icon identifier |
| map_color | string | Yes | - | Hex color for map marker (#RRGGBB) |
| namespace | string | No | Pack namespace | Namespace for registration |

**Required assets**:
- Structure tag file: `data/{namespace}/tags/worldgen/structure/{tag}.json`
- Map decoration texture: `assets/{namespace}/textures/map/decorations/{map_decoration}.png`

**Structure tag file format**:
```json
{
  "replace": false,
  "values": [
    "minecraft:end_city"
  ]
}
```

**Map decoration texture**: Should be a small PNG icon (typically 8x8 or 16x16).

**Using in trades**: Reference as `"{namespace}:{tag}"` in treasure map trades (e.g., `"morevillagers:on_end_city_explorer_maps"`).

---

### Trades

**Path**: `villagers/trades/[profession].json`

Defines what villagers buy and sell at each experience level (1=Novice to 5=Master).

```json
{
  "profession": "morevillagers:enderian",
  "levels": {
    "1": [
      {
        "buy_a": {
          "item": "minecraft:end_stone",
          "count": 24
        },
        "sell": "minecraft:emerald",
        "max_uses": 16,
        "xp": 2
      }
    ],
    "4": [
      {
        "buy_a": {
          "item": "minecraft:emerald",
          "count": 14
        },
        "sell": {
          "item_type": "treasure_map",
          "structure_tag": "morevillagers:on_end_city_explorer_maps",
          "display_name": "filled_map.endcity",
          "map_decoration": "morevillagers:end_city_decoration"
        },
        "max_uses": 12,
        "xp": 15
      }
    ]
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
    "count": 14
  },
  "sell": {
    "item_type": "treasure_map",
    "structure_tag": "morevillagers:on_end_city_explorer_maps",
    "display_name": "filled_map.endcity",
    "map_decoration": "morevillagers:end_city_decoration"
  },
  "max_uses": 12,
  "xp": 15
}
```

**Note**: Treasure map trades will only generate if the structure exists nearby. Test in the appropriate dimension (e.g., End Cities only in The End).

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
              "sell": "minecraft:gold_ingot",
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

## Additional Assets

### Translations

**Path**: `assets/{namespace}/lang/en_us.json`

```json
{
  "entity.minecraft.villager.morevillagers.enderian": "Enderian",
  "entity.minecraft.villager.enderian": "Enderian",
  
  "entity.minecraft.villager.morevillagers.jungle_dweller": "Jungle Dweller",
  "entity.minecraft.villager.jungle_dweller": "Jungle Dweller",
  
  "block.morevillagers.purpur_altar": "Purpur Altar",
  
  "itemGroup.morevillagers.villagerpack_tab": "More Villagers",
  
  "filled_map.endcity": "End City Explorer Map"
}
```

**Creative tab translation**: Use `itemGroup.{namespace}.villagerpack_tab` to name your creative tab.

### Textures

**Workstation blocks**: 6 faces at `assets/{namespace}/textures/block/{name}_front.png`, etc. (16x16)

**Profession overlay**: `assets/{namespace}/textures/entity/villager/profession/{name}.png` (64x64)

**Villager type**: `assets/{namespace}/textures/entity/villager/type/{name}.png` (64x64)

**Map decorations**: `assets/{namespace}/textures/map/decorations/{name}.png` (8x8 or 16x16)

All villager textures should follow Minecraft's villager texture layout.

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
    "morevillagers:enderian"
  ]
}
```

Always use `"replace": false` for mod compatibility.

---

## Validation & Troubleshooting

### Pack Recognition

**CRITICAL**: Packs are only recognized if they have `villagerapi_config.json` in the root directory. Without this file, your pack will be completely ignored.

### Validation on Startup

The mod validates all villagerpacks on startup and logs:
- ✅ **Valid packs**: Load successfully
- ⚠️ **Warnings**: Pack loads but has issues (missing textures, etc.)
- ❌ **Errors**: Pack loads but features won't work (missing POI types, etc.)
- 🚫 **Critical failures**: Pack rejected and won't load

### Common Issues

**Pack not loading at all**
- Check for `villagerapi_config.json` in pack root
- Verify namespace contains only lowercase letters, numbers, underscores, dots, and hyphens
- Ensure pack has `villagers/` or `data/` folder

**Villagers won't recognize workstation**
- Check `acquirable_job_site.json` includes your POI type
- Verify block ID matches workstation registration
- Ensure POI filename matches profession's `poi_type` field
- Check workstation block exists in registry

**Workstations not in creative tab**
- Verify `villagerapi_config.json` exists
- Check creative_tab_icon is a valid item
- Ensure workstations have proper namespace

**Custom types don't appear**
- Add biome mappings
- Include textures at correct path with .mcmeta files
- Match type names exactly between files

**Trades crash or don't work**
- Validate JSON syntax at https://jsonlint.com
- Check all item IDs (use F3+H in-game)
- Verify component formatting
- Test structure tags exist and structures are nearby

**Treasure map trades not appearing**
- Ensure structure tag file exists in `data/` folder
- Check map decoration is registered
- **Test in correct dimension** (e.g., End City maps only work in The End)
- Verify structures exist nearby (maps search for nearest structure)

**Hero gifts not working**
- Check gift mapping references correct profession
- For NeoForge, verify data map exists
- Ensure loot table is valid JSON

**Biome trades missing**
- Match villager type names exactly
- Set up biome mappings first
- Register base trades before biome overrides

---

## Map Decorations & Structure Tags

**Map decorations** (vanilla):
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

Visit https://minecraft.wiki/w/Item_components for complete component documentation.

---

## Best Practices

1. **Always specify namespace** in `villagerapi_config.json`
2. **Use descriptive names** for professions, types, and workstations
3. **Test thoroughly** in all relevant dimensions and biomes
4. **Validate JSON** before adding to pack
5. **Include translations** for better user experience
6. **Document your pack** with a clear description
7. **Use appropriate pack format** for your Minecraft version
8. **Keep textures consistent** with Minecraft's style

---

## Example Pack

For a complete example, see the More Villagers pack structure or use the [Villager Pack Generator](https://morevillagers.neoterra.online/villager-pack-gen) to create packs without manual JSON editing.

---
