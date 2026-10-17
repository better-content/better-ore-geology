"""Unit coverage for the geological resource generator's ownership boundary."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("generate_geological_worldgen.py")
SPEC = importlib.util.spec_from_file_location("geological_worldgen_generator", SCRIPT)
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class GeologicalWorldgenGeneratorTest(unittest.TestCase):
    def test_checked_in_ownership_inventory_matches_manifest(self):
        manifest = json.loads(GENERATOR.MANIFEST.read_text(encoding="utf-8"))
        owned = json.loads(GENERATOR.OWNERSHIP.read_text(encoding="utf-8"))
        self.assertEqual(sorted(manifest), owned)

    def test_optional_biome_boundary_regeneration_preserves_selectors_and_membership(self):
        Path.home().joinpath(".tmp").mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=Path.home() / ".tmp") as temporary:
            data = Path(temporary) / "data"
            before = {}
            for relative in GENERATOR.OPTIONAL_BIOME_SELECTORS:
                path = data / relative
                original = json.loads((GENERATOR.DATA / relative).read_text())
                provider = "#aether:is_aether" if "aether" in relative else "#twilightforest:in_twilight_forest"
                original["biomes"] = provider
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(json.dumps(original))
                before[relative] = original
            unowned = data / "forge/biome_modifier/custom.json"
            unowned.write_text('{"biomes":"#aether:is_aether","keep":true}')
            with patch.object(GENERATOR, "DATA", data):
                GENERATOR.generate_optional_biome_boundaries()
            self.assertEqual('{"biomes":"#aether:is_aether","keep":true}', unowned.read_text())
            for relative, original in before.items():
                expected = dict(original, biomes=GENERATOR.optional_biome_selector(original["biomes"]))
                self.assertEqual(expected, json.loads((data / relative).read_text()))
            first = {str(path.relative_to(data)): path.read_bytes() for path in data.rglob("*.json")}
            with patch.object(GENERATOR, "DATA", data):
                GENERATOR.generate_optional_biome_boundaries()
            self.assertEqual(first, {str(path.relative_to(data)): path.read_bytes() for path in data.rglob("*.json")})
            for name, provider in GENERATOR.OPTIONAL_BIOME_TAGS.items():
                wrapper = json.loads((data / f"tags/worldgen/biome/{name}.json").read_text())
                self.assertEqual({"replace": False, "values": [{"id": "#" + provider, "required": False}]}, wrapper)
                # A single optional nested tag has exactly its provider's membership,
                # including a present empty tag; an absent provider binds to empty.
                for members in [set(), {"test:first_biome", "test:second_biome"}]:
                    present = {provider: members}
                    resolved = set().union(*(present.get(entry["id"][1:], set()) for entry in wrapper["values"]))
                    self.assertEqual(members, resolved)
                absent = {}
                self.assertEqual(set(), set().union(*(absent.get(entry["id"][1:], set()) for entry in wrapper["values"])))

    def test_regeneration_preserves_unowned_resources(self):
        Path.home().joinpath(".tmp").mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=Path.home() / ".tmp") as temporary:
            root = Path(temporary)
            data = root / "data"
            configured = data / "worldgen/configured_feature"
            placed = data / "worldgen/placed_feature"
            modifiers = data / "forge/biome_modifier"
            for directory in (configured, placed, modifiers):
                directory.mkdir(parents=True)

            (configured / "custom_feature.json").write_text('{"keep":true}', encoding="utf-8")
            (configured / "ironstone.json").write_text('{"old":true}', encoding="utf-8")
            (configured / "retired_family.json").write_text('{"old":true}', encoding="utf-8")
            (placed / "ironstone_home.json").write_text('{"old":true}', encoding="utf-8")
            (placed / "aether_ironstone.json").write_text('{"keep":true}', encoding="utf-8")
            (placed / "retired_family_echo.json").write_text('{"old":true}', encoding="utf-8")
            (modifiers / "add_ironstone_home.json").write_text('{"old":true}', encoding="utf-8")
            (modifiers / "add_aether_ironstone.json").write_text('{"keep":true}', encoding="utf-8")
            (modifiers / "add_retired_family_echo.json").write_text('{"old":true}', encoding="utf-8")

            manifest = root / "geological_worldgen.json"
            manifest.write_text(json.dumps({
                "ironstone": {
                    "morphology": "lenticular_oolitic_bed", "home": [-16, 80],
                    "echo": [112, 288], "home_budget": 34, "home_spread": 10,
                    "echo_budget": 42, "echo_spread": 12, "home_count": 7,
                    "echo_count": 1, "baseline_supply": 280,
                }
            }), encoding="utf-8")
            ownership = root / "geological_worldgen.outputs.json"
            ownership.write_text(json.dumps(["ironstone", "retired_family"]), encoding="utf-8")

            with (patch.object(GENERATOR, "DATA", data),
                  patch.object(GENERATOR, "MANIFEST", manifest),
                  patch.object(GENERATOR, "OWNERSHIP", ownership)):
                GENERATOR.main()

            self.assertEqual('{"keep":true}', (configured / "custom_feature.json").read_text())
            self.assertEqual('{"keep":true}', (placed / "aether_ironstone.json").read_text())
            self.assertEqual('{"keep":true}', (modifiers / "add_aether_ironstone.json").read_text())
            self.assertTrue((configured / "ironstone.json").exists())
            self.assertTrue((placed / "ironstone_home.json").exists())
            self.assertTrue((modifiers / "add_ironstone_home.json").exists())
            self.assertFalse((configured / "retired_family.json").exists())
            self.assertFalse((placed / "retired_family_echo.json").exists())
            self.assertFalse((modifiers / "add_retired_family_echo.json").exists())
            self.assertEqual(["ironstone"], json.loads(ownership.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()
