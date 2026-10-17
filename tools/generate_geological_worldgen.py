#!/usr/bin/env python3
"""Generate the small, auditable Realistic Ores geological worldgen resource set."""

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/main/resources/data/better_ore_geology"
MANIFEST = ROOT / "tools/geological_worldgen.json"
OWNERSHIP = ROOT / "tools/geological_worldgen.outputs.json"

# Keep optional foreign tags behind bound, owned selectors even without their provider.
OPTIONAL_BIOME_TAGS = {
    "aether_biomes": "aether:is_aether",
    "twilight_biomes": "twilightforest:in_twilight_forest",
}
OPTIONAL_BIOME_SELECTORS = (
    "forge/biome_modifier/add_ironstone_aether.json",
    "forge/biome_modifier/add_twilight_gold_home.json",
    "forge/biome_modifier/add_twilight_gold_echo.json",
    "forge/biome_modifier/add_tin_quartz_twilight.json",
    "forge/biome_modifier/add_coal_measures_twilight.json",
    "forge/biome_modifier/add_ironstone_twilight.json",
    "forge/biome_modifier/add_copper_bloom_twilight.json",
    "disabled_placed_features/twilight_gold.json",
)


def optional_biome_selector(selector: str) -> str:
    for name, provider in OPTIONAL_BIOME_TAGS.items():
        if selector == "#" + provider:
            return "#better_ore_geology:" + name
    return selector


def generate_optional_biome_boundaries() -> None:
    for name, provider in OPTIONAL_BIOME_TAGS.items():
        write(DATA / f"tags/worldgen/biome/{name}.json", {
            "replace": False,
            "values": [{"id": "#" + provider, "required": False}],
        })
    # These dimension resources also include hand-authored selectors outside the
    # home/echo family generator. Own only their selector field, never feature payloads.
    for relative in OPTIONAL_BIOME_SELECTORS:
        path = DATA / relative
        if not path.exists():
            continue
        value = json.loads(path.read_text(encoding="utf-8"))
        selector = optional_biome_selector(value["biomes"])
        if selector != value["biomes"]:
            value["biomes"] = selector
            write(path, value)


def write(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def target(tag: str, state: str) -> dict:
    block = state if ":" in state else f"better_ore_geology:{state}"
    return {
        "target": {"predicate_type": "minecraft:tag_match", "tag": tag},
        "state": {"Name": block},
    }


def height(kind: str, band: list[int]) -> dict:
    provider = {
        "type": f"minecraft:{kind}",
        "min_inclusive": {"absolute": band[0]},
        "max_inclusive": {"absolute": band[1]},
    }
    if kind == "trapezoid":
        provider["plateau"] = max(0, (band[1] - band[0]) // 3)
    return {"type": "minecraft:height_range", "height": provider}


def main() -> None:
    definitions = json.loads(MANIFEST.read_text(encoding="utf-8"))
    configured = DATA / "worldgen/configured_feature"
    placed = DATA / "worldgen/placed_feature"
    modifiers = DATA / "forge/biome_modifier"
    legacy = DATA / "realistic_ore_generation"
    configured.mkdir(parents=True, exist_ok=True)
    placed.mkdir(parents=True, exist_ok=True)
    modifiers.mkdir(parents=True, exist_ok=True)
    # Track ownership explicitly so retired generated families can be cleaned
    # without touching unrelated configured features or dimension resources.
    previously_generated = set(definitions)
    if OWNERSHIP.exists():
        previously_generated = set(json.loads(OWNERSHIP.read_text(encoding="utf-8")))
    owned_families = previously_generated | set(definitions)
    for family in owned_families:
        for profile in ("home", "echo"):
            (placed / f"{family}_{profile}.json").unlink(missing_ok=True)
            (modifiers / f"add_{family}_{profile}.json").unlink(missing_ok=True)
        (configured / f"{family}.json").unlink(missing_ok=True)
    shutil.rmtree(legacy, ignore_errors=True)

    for family, definition in definitions.items():
        targets = definition.get("targets", [
            {"tag": "minecraft:stone_ore_replaceables", "state": family},
            {"tag": "minecraft:deepslate_ore_replaceables", "state": f"deepslate_{family}"},
        ])
        configured_targets = [target(entry["tag"], entry["state"]) for entry in targets]
        write(configured / f"{family}.json", {
            "type": "better_ore_geology:geological_deposit",
            "config": {
                "targets": configured_targets,
                "morphology": definition["morphology"],
                "block_budget": definition["home_budget"],
                "budget_spread": definition["home_spread"],
                "deposit_class": "home",
                "discard_chance_on_air_exposure": 0.0,
            },
        })
        for profile in ("home", "echo"):
            feature = f"{family}_{profile}"
            placement = []
            count_key = f"{profile}_count"
            rarity_key = f"{profile}_rarity"
            if count_key in definition:
                placement.append({"type": "minecraft:count", "count": definition[count_key]})
            else:
                placement.append({"type": "minecraft:rarity_filter", "chance": definition[rarity_key]})
            placement.extend([
                {"type": "minecraft:in_square"},
                height("trapezoid" if profile == "home" else "uniform", definition[profile]),
                {"type": "minecraft:biome"},
            ])
            # Echo features carry a larger memorable body without duplicating configuration files.
            configured_ref = f"better_ore_geology:{family}"
            if profile == "echo":
                configured_ref = {"type": "better_ore_geology:geological_deposit", "config": {
                    "targets": configured_targets,
                    "morphology": definition["morphology"],
                    "block_budget": definition["echo_budget"],
                    "budget_spread": definition["echo_spread"],
                    "deposit_class": "echo",
                    "discard_chance_on_air_exposure": 0.0,
                }}
            write(placed / f"{feature}.json", {"feature": configured_ref, "placement": placement})
            write(modifiers / f"add_{feature}.json", {
                "type": "forge:add_features",
                "biomes": definition.get("biomes", "#minecraft:is_overworld"),
                "features": f"better_ore_geology:{feature}",
                "step": "underground_ores",
            })
    generate_optional_biome_boundaries()
    write(OWNERSHIP, sorted(definitions))


if __name__ == "__main__":
    main()
