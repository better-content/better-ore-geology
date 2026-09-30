from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src" / "main" / "resources"
LEGACY_SPEC_PATH = ROOT / "ore_spec.json"
LEGACY_TEXTURES_DIR = ROOT / "textures"

SHORT_ID_MAP = {
    "copper_sulfide_ore": "copper_sulfide",
    "zinc_ore": "zinc",
    "tin_ore": "tin",
    "nickel_sulfide_ore": "nickel_sulfide",
    "titanium_iron_oxide_ore": "titanium_iron_oxide",
    "sulfur_bearing_pyrite_ore": "sulfur_bearing_pyrite",
    "thorium_ore": "thorium",
    "uranium_ore": "uranium",
}

BLOCK_BASE_MAP = {value: key for key, value in SHORT_ID_MAP.items()}

TAG_MAP = {
    "coal_measures": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/coal"],
    "ironstone": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/iron"],
    "copper_sulfide": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/copper"],
    "zinc": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/zinc"],
    "tin": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/tin"],
    "lead_zinc_vein": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/lead", "forge:ores/zinc"],
    "nickel_sulfide": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/nickel"],
    "tin_tungsten_greisen": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/tin", "forge:ores/tungsten"],
    "titanium_iron_oxide": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/titanium", "forge:ores/iron"],
    "bauxite_laterite": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/aluminum"],
    "phosphate_rock": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/phosphate"],
    "sulfur_bearing_pyrite": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/sulfur"],
    "kimberlite_pipe": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/diamond"],
    "emerald_schist_beryl_vein": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/emerald"],
    "lazurite_vein": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/lapis"],
    "cupriferous_redbed_redstone_vein": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/redstone", "forge:ores/copper"],
    "quartz_vein": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/quartz"],
    "amethyst_beryl_pegmatite": ["minecraft:mineable/pickaxe", "forge:ores"],
    "soul_bearing_black_shale_soulstone_vein": ["minecraft:mineable/pickaxe", "forge:ores"],
    "thorium": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/thorium"],
    "uranium": ["minecraft:mineable/pickaxe", "forge:ores", "forge:ores/uranium"],
}

VEIN_SIZE_MAP = {
    "coal_measures": 24,
    "ironstone": 20,
    "copper_sulfide": 20,
    "zinc": 16,
    "tin": 16,
    "nickel_sulfide": 16,
    "phosphate_rock": 16,
    "lead_zinc_vein": 14,
    "tin_tungsten_greisen": 14,
    "quartz_vein": 14,
    "bauxite_laterite": 18,
    "sulfur_bearing_pyrite": 18,
    "kimberlite_pipe": 10,
    "amethyst_beryl_pegmatite": 10,
    "emerald_schist_beryl_vein": 10,
    "lazurite_vein": 10,
    "cupriferous_redbed_redstone_vein": 12,
    "soul_bearing_black_shale_soulstone_vein": 12,
    "titanium_iron_oxide": 12,
}

COUNT_MAP = {
    "coal_measures": 18,
    "ironstone": 14,
    "copper_sulfide": 12,
    "zinc": 10,
    "tin": 10,
    "lead_zinc_vein": 8,
    "nickel_sulfide": 8,
    "phosphate_rock": 8,
    "sulfur_bearing_pyrite": 8,
    "tin_tungsten_greisen": 6,
    "bauxite_laterite": 6,
    "quartz_vein": 6,
    "kimberlite_pipe": 4,
    "amethyst_beryl_pegmatite": 4,
    "emerald_schist_beryl_vein": 4,
    "lazurite_vein": 4,
    "cupriferous_redbed_redstone_vein": 5,
    "soul_bearing_black_shale_soulstone_vein": 5,
    "titanium_iron_oxide": 5,
}

UNIFORM_DISTRIBUTION = {
    "coal_measures",
    "ironstone",
    "copper_sulfide",
    "zinc",
    "tin",
    "bauxite_laterite",
    "phosphate_rock",
    "sulfur_bearing_pyrite",
}

WORLD_MIN_Y = -128
WORLD_MAX_Y = 512

DISABLED_VANILLA_FEATURES = [
    "minecraft:ore_coal_upper",
    "minecraft:ore_coal_lower",
    "minecraft:ore_iron_upper",
    "minecraft:ore_iron_middle",
    "minecraft:ore_iron_small",
    "minecraft:ore_copper_large",
    "minecraft:ore_copper",
    "minecraft:ore_gold_extra",
    "minecraft:ore_gold",
    "minecraft:ore_gold_lower",
    "minecraft:ore_redstone",
    "minecraft:ore_redstone_lower",
    "minecraft:ore_diamond",
    "minecraft:ore_diamond_large",
    "minecraft:ore_diamond_buried",
    "minecraft:ore_lapis",
    "minecraft:ore_lapis_buried",
    "minecraft:ore_emerald",
]


def short_id(source_id: str) -> str:
    return SHORT_ID_MAP.get(source_id, source_id)


def block_base(ore_id: str) -> str:
    return BLOCK_BASE_MAP.get(ore_id, ore_id)


def title_case(name: str) -> str:
    return " ".join(part.capitalize() for part in name.split("_"))


def display_name_for_block(base: str) -> str:
    words = title_case(base)
    if words.endswith(" Ore"):
        return f"{words} Deposit"
    return f"{words} Deposit"


def distribution(ore_id: str) -> str:
    return "uniform" if ore_id in UNIFORM_DISTRIBUTION else "triangle"


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def reset_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def append_tag_value(tag_files: dict[Path, set[str]], path: Path, value: str) -> None:
    tag_files.setdefault(path, set()).add(value)


SURFACE_SAMPLE_VARIANTS = 5
OIL_SHALE_TEXTURE = "oil_bearing_shale"


def write_rgba_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    """Write a tiny dependency-free RGBA PNG for generated block textures."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in pixels)

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    path.write_bytes(png)


def ore_chunk_display_name(display_name: str) -> str:
    family_name = display_name.removesuffix(" Deposit")
    if family_name.endswith(" Ore"):
        return f"{family_name} Chunk"
    return f"{family_name} Ore Chunk"


def ore_chunk_loot_table(block_id: str, chunk_id: str) -> dict[str, object]:
    return {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1.0,
                "entries": [
                    {
                        "type": "minecraft:alternatives",
                        "children": [
                            {
                                "type": "minecraft:item",
                                "name": f"better_ore_geology:{block_id}",
                                "conditions": [
                                    {
                                        "condition": "minecraft:match_tool",
                                        "predicate": {
                                            "enchantments": [
                                                {
                                                    "enchantment": "minecraft:silk_touch",
                                                    "levels": {"min": 1},
                                                }
                                            ]
                                        },
                                    }
                                ],
                            },
                            {
                                "type": "minecraft:item",
                                "name": f"better_ore_geology:{chunk_id}",
                            },
                        ],
                    }
                ],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }
        ],
    }


def generate_ore_chunks() -> None:
    ore_dir = RESOURCES / "data" / "better_ore_geology" / "better_ore_geology"
    item_model_dir = RESOURCES / "assets" / "better_ore_geology" / "models" / "item"
    item_texture_dir = RESOURCES / "assets" / "better_ore_geology" / "textures" / "item"
    loot_dir = RESOURCES / "data" / "better_ore_geology" / "loot_tables" / "blocks"
    recipe_dir = RESOURCES / "data" / "better_ore_geology" / "recipes"
    chunk_crushing_dir = recipe_dir / "compat" / "create" / "crushing" / "ore_chunks"
    chunk_milling_dir = recipe_dir / "compat" / "create" / "milling" / "ore_chunks"
    reassembly_dir = recipe_dir / "crafting" / "ore_reassembly"
    chunk_tag_path = RESOURCES / "data" / "better_ore_geology" / "tags" / "items" / "ore_chunks.json"
    lang_path = RESOURCES / "assets" / "better_ore_geology" / "lang" / "en_us.json"

    for path in item_model_dir.glob("ore_chunk_*.json"):
        path.unlink()
    reset_dir(chunk_crushing_dir)
    reset_dir(chunk_milling_dir)
    reset_dir(reassembly_dir)

    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for key in list(lang):
        if key.startswith("item.better_ore_geology.ore_chunk_"):
            del lang[key]

    chunk_ids = []
    for definition_path in sorted(ore_dir.glob("*.json")):
        definition = json.loads(definition_path.read_text(encoding="utf-8"))
        primary = next(
            (variant for variant in definition["variants"] if variant["host"] == "stone"),
            definition["variants"][0],
        )
        primary_block_id = primary["block_id"]
        chunk_id = f"ore_chunk_{primary_block_id}"
        crushed_id = f"crushed_{primary_block_id}"
        texture_path = item_texture_dir / f"{chunk_id}.png"
        if not texture_path.is_file():
            raise RuntimeError(f"missing ore chunk texture: {texture_path.relative_to(ROOT)}")

        chunk_ids.append(f"better_ore_geology:{chunk_id}")
        write_json(
            item_model_dir / f"{chunk_id}.json",
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"better_ore_geology:item/{chunk_id}"},
            },
        )
        lang[f"item.better_ore_geology.{chunk_id}"] = ore_chunk_display_name(definition["display_name"])

        write_json(
            chunk_crushing_dir / f"{primary_block_id}.json",
            {
                "type": "create:crushing",
                "conditions": [{"type": "forge:mod_loaded", "modid": "create"}],
                "ingredients": [{"item": f"better_ore_geology:{chunk_id}"}],
                "processingTime": 400,
                "results": [
                    {"item": f"better_ore_geology:{crushed_id}"},
                    {"item": f"better_ore_geology:{crushed_id}", "chance": 0.3},
                    {"item": f"better_ore_geology:{crushed_id}", "chance": 0.3},
                    {"item": f"better_ore_geology:{crushed_id}", "chance": 0.3},
                ],
            },
        )
        write_json(
            chunk_milling_dir / f"{primary_block_id}.json",
            {
                "type": "create:milling",
                "conditions": [{"type": "forge:mod_loaded", "modid": "create"}],
                "ingredients": [{"item": f"better_ore_geology:{chunk_id}"}],
                "processingTime": 400,
                "results": [
                    {"item": f"better_ore_geology:{crushed_id}"},
                    {"item": f"better_ore_geology:{crushed_id}", "chance": 0.1},
                ],
            },
        )

        for variant in definition["variants"]:
            block_id = variant["block_id"]
            host_block_id = variant["copy_properties_from"]
            write_json(loot_dir / f"{block_id}.json", ore_chunk_loot_table(block_id, chunk_id))
            write_json(
                recipe_dir / "compat" / "create" / "crushing" / f"{block_id}.json",
                {
                    "type": "create:crushing",
                    "conditions": [{"type": "forge:mod_loaded", "modid": "create"}],
                    "ingredients": [{"type": "forge:nbt", "item": f"better_ore_geology:{block_id}"}],
                    "processingTime": 250,
                    "results": [
                        {"item": f"better_ore_geology:{chunk_id}"},
                        {"item": host_block_id},
                    ],
                },
            )
            write_json(
                reassembly_dir / f"{block_id}.json",
                {
                    "type": "minecraft:crafting_shapeless",
                    "ingredients": [
                        {"item": f"better_ore_geology:{chunk_id}"},
                        {"item": host_block_id},
                    ],
                    "result": {"item": f"better_ore_geology:{block_id}"},
                },
            )

    write_json(chunk_tag_path, {"replace": False, "values": chunk_ids})
    write_json(lang_path, lang)


def generate_oil_shale_texture(path: Path) -> None:
    palette = [
        (25, 27, 27, 255),
        (34, 37, 38, 255),
        (44, 43, 40, 255),
        (55, 49, 43, 255),
        (29, 35, 39, 255),
    ]
    pixels = []
    for y in range(16):
        row = []
        band = (y // 2) % len(palette)
        for x in range(16):
            color = palette[(band + (x // 5) + (1 if (x * 3 + y * 5) % 17 == 0 else 0)) % len(palette)]
            if (x + y * 2) % 23 == 0:
                color = (68, 65, 52, 255)
            elif (x * 2 + y) % 29 == 0:
                color = (38, 50, 52, 255)
            row.append(color)
        pixels.append(row)
    write_rgba_png(path, pixels)


def sample_element(
    start: list[float],
    end: list[float],
    texture_seed: int,
    angle: float,
) -> dict[str, object]:
    width = max(2, round(end[0] - start[0]))
    depth = max(2, round(end[2] - start[2]))
    u = 1 + texture_seed % max(1, 14 - width)
    v = 1 + (texture_seed // 7) % max(1, 14 - depth)
    top_uv = [u, v, min(15, u + width), min(15, v + depth)]
    height = max(1, round(end[1] - start[1]))
    side_uv = [u, v, min(15, u + width), min(15, v + height)]
    element = {
        "from": start,
        "to": end,
        "faces": {
            "down": {"texture": "#all", "uv": top_uv},
            "up": {"texture": "#all", "uv": top_uv},
            "north": {"texture": "#all", "uv": side_uv},
            "south": {"texture": "#all", "uv": side_uv},
            "west": {"texture": "#all", "uv": side_uv},
            "east": {"texture": "#all", "uv": side_uv},
        },
    }
    if angle:
        element["rotation"] = {
            "origin": [(start[0] + end[0]) / 2, 0, (start[2] + end[2]) / 2],
            "axis": "y",
            "angle": angle,
            "rescale": False,
        }
    return element


def surface_sample_elements(block_id: str, variant: int) -> list[dict[str, object]]:
    """Build separated, visible rubble chips instead of miniature ore blocks."""
    digest = hashlib.sha256(f"{block_id}:{variant}".encode()).digest()
    elements = []
    is_oil = block_id == "oil_seep"
    slots = [(2, 2), (9, 3), (3, 9), (9, 9), (6, 6)]
    piece_count = (3 + digest[0] % 2) if not is_oil else (4 + digest[0] % 2)
    angles = [-22.5, 0, 22.5]
    for piece in range(piece_count):
        offset = 1 + piece * 5
        slot_x, slot_z = slots[piece]
        x = slot_x + digest[offset] % 2
        z = slot_z + digest[offset + 1] % 2
        if is_oil:
            width = 3 + digest[offset + 2] % 3
            depth = 2 + digest[offset + 3] % 3
            height = 1 + digest[offset + 4] % 3
        else:
            width = 3 + digest[offset + 2] % 3
            depth = 3 + digest[offset + 3] % 3
            height = 2 + digest[offset + 4] % 3
        angle = angles[digest[offset + 1] % len(angles)]
        elements.append(sample_element([x, 0.02, z], [x + width, height, z + depth], digest[offset], angle))
    return elements


def generate_surface_samples() -> None:
    ore_dir = RESOURCES / "data" / "better_ore_geology" / "better_ore_geology"
    blockstate_dir = RESOURCES / "assets" / "better_ore_geology" / "blockstates"
    block_model_dir = RESOURCES / "assets" / "better_ore_geology" / "models" / "block"
    item_model_dir = RESOURCES / "assets" / "better_ore_geology" / "models" / "item"
    loot_dir = RESOURCES / "data" / "better_ore_geology" / "loot_tables" / "blocks"
    lang_path = RESOURCES / "assets" / "better_ore_geology" / "lang" / "en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))

    definitions = []
    for definition_path in sorted(ore_dir.glob("*.json")):
        definition = json.loads(definition_path.read_text(encoding="utf-8"))
        primary = next(
            (variant for variant in definition["variants"] if variant["host"] == "stone"),
            definition["variants"][0],
        )
        sample_id = f"surface_sample_{definition['id']}"
        textures = primary["textures"]
        texture = textures.get("south") or textures.get("all") or textures.get("side")
        if not texture:
            raise RuntimeError(f"surface sample has no usable opaque ore texture: {sample_id}")
        definitions.append((sample_id, definition["display_name"], texture))
    definitions.append(("oil_seep", "Oil Seep", f"better_ore_geology:block/{OIL_SHALE_TEXTURE}"))
    generate_oil_shale_texture(RESOURCES / "assets" / "better_ore_geology" / "textures" / "block" / f"{OIL_SHALE_TEXTURE}.png")

    for path in blockstate_dir.glob("crushed_*.json"):
        path.unlink()
    for path in loot_dir.glob("crushed_*.json"):
        path.unlink()
    for path in block_model_dir.glob("crushed_*_[0-4].json"):
        path.unlink()
    for path in block_model_dir.glob("surface_sample_[0-4].json"):
        path.unlink()
    for path in blockstate_dir.glob("surface_sample_*.json"):
        path.unlink()
    for path in loot_dir.glob("surface_sample_*.json"):
        path.unlink()
    for path in block_model_dir.glob("surface_sample_*_[0-4].json"):
        path.unlink()
    for path in item_model_dir.glob("surface_sample_*.json"):
        path.unlink()

    for key in list(lang):
        if key.startswith("block.better_ore_geology.crushed_") or key.startswith("block.better_ore_geology.surface_sample_"):
            del lang[key]

    geometry_signatures = set()
    for block_id, display_name, texture in definitions:
        variants = []
        block_geometry = []
        for index in range(SURFACE_SAMPLE_VARIANTS):
            model_id = f"{block_id}_{index}"
            elements = surface_sample_elements(block_id, index)
            block_geometry.append(elements)
            write_json(
                block_model_dir / f"{model_id}.json",
                {
                    "ambientocclusion": False,
                    "render_type": "minecraft:cutout",
                    "textures": {"all": texture, "particle": "#all"},
                    "elements": elements,
                },
            )
            for rotation in [0, 90, 180, 270]:
                variants.append({"model": f"better_ore_geology:block/{model_id}", "y": rotation})
        write_json(
            blockstate_dir / f"{block_id}.json",
            {"variants": {"waterlogged=false": variants, "waterlogged=true": variants}},
        )
        drop_block_id = (
            f"small_ore_chunk_{block_id.removeprefix('surface_sample_')}"
            if block_id.startswith("surface_sample_")
            else block_id
        )
        write_json(
            loot_dir / f"{block_id}.json",
            {
                "type": "minecraft:block",
                "pools": [
                    {
                        "rolls": 1.0,
                        "entries": [
                            {
                                "type": "minecraft:item",
                                "name": f"better_ore_geology:{drop_block_id}",
                            }
                        ],
                        "conditions": [{"condition": "minecraft:survives_explosion"}],
                    }
                ],
            },
        )
        if block_id == "oil_seep" or block_id.startswith("surface_sample_"):
            item_id = (
                f"small_ore_chunk_{block_id.removeprefix('surface_sample_')}"
                if block_id.startswith("surface_sample_") else block_id
            )
            write_json(
                item_model_dir / f"{item_id}.json",
                {
                    "parent": f"better_ore_geology:block/{block_id}_2",
                    "display": {
                        "gui": {
                            "rotation": [30, 225, 0],
                            "translation": [0, 3, 0],
                            "scale": [1.15, 1.15, 1.15],
                        }
                    },
                },
            )
        if block_id.startswith("surface_sample_"):
            lang[f"block.better_ore_geology.{block_id}"] = f"Surface Sample: {display_name}"
        else:
            lang[f"block.better_ore_geology.{block_id}"] = display_name

        signature = json.dumps(block_geometry, sort_keys=True)
        if signature in geometry_signatures:
            raise RuntimeError(f"surface sample geometry is not unique: {block_id}")
        geometry_signatures.add(signature)

    write_json(lang_path, lang)


def main() -> None:
    if not LEGACY_SPEC_PATH.exists() or not LEGACY_TEXTURES_DIR.exists():
        missing = []
        if not LEGACY_SPEC_PATH.exists():
            missing.append(str(LEGACY_SPEC_PATH.relative_to(ROOT)))
        if not LEGACY_TEXTURES_DIR.exists():
            missing.append(str(LEGACY_TEXTURES_DIR.relative_to(ROOT)))
        raise SystemExit(
            "Legacy generator inputs are missing: "
            + ", ".join(missing)
            + ". This repository now keeps generated resources under src/main/resources."
        )

    spec = json.loads(LEGACY_SPEC_PATH.read_text(encoding="utf-8"))

    ore_dir = RESOURCES / "data" / "better_ore_geology" / "better_ore_geology"
    worldgen_dir = RESOURCES / "data" / "better_ore_geology" / "realistic_ore_generation"
    disabled_dir = RESOURCES / "data" / "better_ore_geology" / "disabled_placed_features"
    configured_feature_dir = RESOURCES / "data" / "better_ore_geology" / "worldgen" / "configured_feature"
    placed_feature_dir = RESOURCES / "data" / "better_ore_geology" / "worldgen" / "placed_feature"
    biome_modifier_dir = RESOURCES / "data" / "better_ore_geology" / "forge" / "biome_modifier"
    texture_dir = RESOURCES / "assets" / "better_ore_geology" / "textures" / "block"
    blockstate_dir = RESOURCES / "assets" / "better_ore_geology" / "blockstates"
    block_model_dir = RESOURCES / "assets" / "better_ore_geology" / "models" / "block"
    item_model_dir = RESOURCES / "assets" / "better_ore_geology" / "models" / "item"
    loot_dir = RESOURCES / "data" / "better_ore_geology" / "loot_tables" / "blocks"

    for directory in [
        ore_dir,
        worldgen_dir,
        disabled_dir,
        configured_feature_dir,
        placed_feature_dir,
        biome_modifier_dir,
        texture_dir,
        blockstate_dir,
        block_model_dir,
        item_model_dir,
        loot_dir,
    ]:
        reset_dir(directory)

    tag_files: dict[Path, set[str]] = {}
    lang: dict[str, str] = {}

    for ore in spec["ores"]:
        source_id = ore["ore_id"]
        ore_id = short_id(source_id)
        base = block_base(ore_id)
        display_name = display_name_for_block(base)
        stone_block = base
        deepslate_block = f"deepslate_{base}"
        tags = TAG_MAP.get(ore_id, ["minecraft:mineable/pickaxe"])

        write_json(
            ore_dir / f"{ore_id}.json",
            {
                "id": ore_id,
                "display_name": display_name,
                "variants": [
                    {
                        "host": "stone",
                        "block_id": stone_block,
                        "texture_mode": "cube_all",
                        "textures": {"all": f"better_ore_geology:block/{stone_block}"},
                        "copy_properties_from": "minecraft:stone",
                    },
                    {
                        "host": "deepslate",
                        "block_id": deepslate_block,
                        "texture_mode": "cube_column_like",
                        "textures": {
                            "side": f"better_ore_geology:block/{deepslate_block}_side",
                            "top": f"better_ore_geology:block/{deepslate_block}_top",
                            "bottom": f"better_ore_geology:block/{deepslate_block}_bottom",
                        },
                        "copy_properties_from": "minecraft:deepslate",
                    },
                ],
                "tags": tags,
            },
        )

        shutil.copyfile(ROOT / "textures" / "stone" / f"{source_id}_stone.png", texture_dir / f"{stone_block}.png")
        shutil.copyfile(ROOT / "textures" / "deepslate" / f"{source_id}_deepslate_side.png", texture_dir / f"{deepslate_block}_side.png")
        shutil.copyfile(ROOT / "textures" / "deepslate" / f"{source_id}_deepslate_top.png", texture_dir / f"{deepslate_block}_top.png")
        shutil.copyfile(ROOT / "textures" / "deepslate" / f"{source_id}_deepslate_bottom.png", texture_dir / f"{deepslate_block}_bottom.png")

        write_json(blockstate_dir / f"{stone_block}.json", {"variants": {"": {"model": f"better_ore_geology:block/{stone_block}"}}})
        write_json(block_model_dir / f"{stone_block}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"better_ore_geology:block/{stone_block}"}})
        write_json(item_model_dir / f"{stone_block}.json", {"parent": f"better_ore_geology:block/{stone_block}"})

        write_json(blockstate_dir / f"{deepslate_block}.json", {"variants": {"": {"model": f"better_ore_geology:block/{deepslate_block}"}}})
        write_json(
            block_model_dir / f"{deepslate_block}.json",
            {
                "parent": "minecraft:block/cube_bottom_top",
                "textures": {
                    "side": f"better_ore_geology:block/{deepslate_block}_side",
                    "top": f"better_ore_geology:block/{deepslate_block}_top",
                    "bottom": f"better_ore_geology:block/{deepslate_block}_bottom",
                },
            },
        )
        write_json(item_model_dir / f"{deepslate_block}.json", {"parent": f"better_ore_geology:block/{deepslate_block}"})

        loot_template = lambda name: {
            "type": "minecraft:block",
            "pools": [
                {
                    "rolls": 1.0,
                    "entries": [{"type": "minecraft:item", "name": f"better_ore_geology:{name}"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}],
                }
            ],
        }
        write_json(loot_dir / f"{stone_block}.json", loot_template(stone_block))
        write_json(loot_dir / f"{deepslate_block}.json", loot_template(deepslate_block))

        lang[f"block.better_ore_geology.{stone_block}"] = display_name
        lang[f"block.better_ore_geology.{deepslate_block}"] = f"Deepslate {display_name}"

        for block_id in [stone_block, deepslate_block]:
            full_id = f"better_ore_geology:{block_id}"
            append_tag_value(tag_files, RESOURCES / "data" / "minecraft" / "tags" / "blocks" / "mineable" / "pickaxe.json", full_id)

            for tag in tags:
                namespace, tag_path = tag.split(":", 1)
                append_tag_value(tag_files, RESOURCES / "data" / namespace / "tags" / "blocks" / f"{tag_path}.json", full_id)
                if not (namespace == "minecraft" and tag_path.startswith("mineable/")):
                    append_tag_value(tag_files, RESOURCES / "data" / namespace / "tags" / "items" / f"{tag_path}.json", full_id)

        y_bands = ore.get("y_bands", {})
        for variant in ["stone", "deepslate"]:
            target_tag = "better_ore_geology:overworld_ore_replaceables"
            band = y_bands.get(variant)
            if not band:
                continue

            feature_id = f"{ore_id}_{variant}"

            worldgen_definition = {
                "ore_id": ore_id,
                "variant": variant,
                "target_tag": target_tag,
                "vein_size": VEIN_SIZE_MAP.get(ore_id, 8),
                "count_per_chunk": COUNT_MAP.get(ore_id, 8),
                "distribution": distribution(ore_id),
                "min_y": band["min_y"],
                "max_y": band["max_y"],
                "biome_filter": "#minecraft:is_overworld",
                "generation_step": "underground_ores",
                "enabled": True,
            }
            write_json(worldgen_dir / f"{feature_id}.json", worldgen_definition)

            write_json(
                configured_feature_dir / f"{feature_id}.json",
                {
                    "type": "minecraft:ore",
                    "config": {
                        "size": worldgen_definition["vein_size"],
                        "discard_chance_on_air_exposure": 0.0,
                        "targets": [
                            {
                                "target": {
                                    "predicate_type": "minecraft:tag_match",
                                    "tag": target_tag,
                                },
                                "state": {
                                    "Name": f"better_ore_geology:{stone_block if variant == 'stone' else deepslate_block}"
                                },
                            }
                        ],
                    },
                },
            )

            height_placement = {
                "type": "minecraft:height_range",
                "height": {
                    "type": f"minecraft:{'uniform' if worldgen_definition['distribution'] == 'uniform' else 'trapezoid'}",
                    "min_inclusive": {"above_bottom": worldgen_definition["min_y"] - WORLD_MIN_Y},
                    "max_inclusive": {"below_top": WORLD_MAX_Y - worldgen_definition["max_y"]},
                },
            }
            write_json(
                placed_feature_dir / f"{feature_id}.json",
                {
                    "feature": f"better_ore_geology:{feature_id}",
                    "placement": [
                        {"type": "minecraft:count", "count": worldgen_definition["count_per_chunk"]},
                        {"type": "minecraft:in_square"},
                        height_placement,
                        {"type": "minecraft:biome"},
                    ],
                },
            )

            write_json(
                biome_modifier_dir / f"add_{feature_id}.json",
                {
                    "type": "forge:add_features",
                    "biomes": worldgen_definition["biome_filter"],
                    "features": f"better_ore_geology:{feature_id}",
                    "step": worldgen_definition["generation_step"],
                },
            )

    write_json(disabled_dir / "vanilla.json", {"features": DISABLED_VANILLA_FEATURES})
    write_json(RESOURCES / "assets" / "better_ore_geology" / "lang" / "en_us.json", lang)
    write_json(
        biome_modifier_dir / "remove_disabled_vanilla_ores.json",
        {
            "type": "forge:remove_features",
            "biomes": "#minecraft:is_overworld",
            "features": DISABLED_VANILLA_FEATURES,
            "steps": ["underground_ores"],
        },
    )

    for path, values in tag_files.items():
        write_json(path, {"replace": False, "values": sorted(values)})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--surface-samples-only", action="store_true")
    parser.add_argument("--ore-chunks-only", action="store_true")
    arguments = parser.parse_args()
    if arguments.surface_samples_only:
        generate_surface_samples()
    elif arguments.ore_chunks_only:
        generate_ore_chunks()
    else:
        main()
        generate_ore_chunks()
        generate_surface_samples()
