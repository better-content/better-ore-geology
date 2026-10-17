#!/usr/bin/env python3
"""Own the reproducible native exposure model/blockstate boundary; never edits mineral pixels."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/better_ore_geology'
FAMILIES = ('coal_measures', 'ironstone', 'copper_bloom', 'tin_quartz', 'brassroot', 'evaporite_beds', 'hotstone', 'black_shale')
ROLES = ('quiet', 'fragment', 'richer', 'original')
FACES = ('north', 'east', 'south', 'west', 'up', 'down')


def resources(family, host):
    block = ('' if host == 'stone' else 'deepslate_') + family
    textures = {'particle': f'better_ore_geology:block/exposure/{block}/original_0_south'}
    for role_index, role in enumerate(ROLES):
        for variant in range(3):
            for face in FACES:
                textures[f'slot{role_index * 3 + variant}_{face}'] = f'better_ore_geology:block/exposure/{block}/{role}_{variant}_{face}'
    model = {
        'loader': 'better_ore_geology:exposure', 'family': family,
        'ambientocclusion': True, 'textures': textures,
        'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            face: {'texture': '#' + face, 'cullface': face} for face in FACES}}],
        'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]}}
    }
    yield ASSETS / f'models/block/{block}_exposure.json', model
    yield ASSETS / f'blockstates/{block}.json', {'variants': {'': {'model': f'better_ore_geology:block/{block}_exposure'}}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--family', choices=FAMILIES)
    args = parser.parse_args()
    count = 0
    for family in (args.family,) if args.family else FAMILIES:
        for host in ('stone', 'deepslate'):
            for path, expected in resources(family, host):
                if args.write:
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(json.dumps(expected, indent=2) + '\n')
                elif json.loads(path.read_text()) != expected:
                    raise SystemExit(f'Exposure resource drift: {path}')
                count += 1
    print(f'{"wrote" if args.write else "verified"}: {count} exposure model/blockstate resources')


if __name__ == '__main__':
    main()
