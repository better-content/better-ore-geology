#!/usr/bin/env python3
"""Strict, bounded classification of nonshipping exposure-probe native launch diagnostics.

Metadata is captured before each launch, especially initial_absent_configs. It is not
inferred from a later corrected config tree. No warning outside these exact forms passes.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import zipfile

VERSION = '1.20.1-47.4.13'
LAUNCH_SCHEMA = 'better_ore_exposure_probe.launch.v1'
FAMILIES = frozenset(('coal_measures', 'ironstone', 'copper_bloom', 'tin_quartz',
                      'brassroot', 'evaporite_beds', 'hotstone', 'black_shale'))
SOUNDS_SHA1 = '40a4222b7ada165fa98cb9215129aeb8d9b6b379'
SHADER_SHA256 = {
    'json': 'd4ea27980006208a198c7f181e25aab6d6abeea49518c8ddbd5fac2edc80d515',
    'vsh': 'dad84bd4d8e538c775704199d3953055e12ec76450904ad46f39073982161cde',
    'fsh': '968b666a761dd7c3641b4aa852ce266680db57f82e3bb8d4218d8c7af51312d4',
}
MODULES = frozenset(('javafmllanguage', 'lowcodelanguage', 'mclanguage', 'fmlcore'))
MODULE_HASHES = {
    'javafmllanguage': 'cf01e2179f72bd2829c65f5226d7dd0cfb6c686',
    'lowcodelanguage': 'e3f96129ea14eba84316f2d69816253e228e6e4c',
    'mclanguage': '85796906112d9b621a7b2996f8bfda49da991ef0',
    'fmlcore': 'f77e3021d1eb31d61f8d1b92d18ed4fb3c2806e5',
}
PSEUDO = ('Error loading class: net/mcreator/borninchaosv/procedures/'
          'SeaTerrorStomachPriShchielchkiePKMProcedure '
          '(java.lang.ClassNotFoundException: '
          'net.mcreator.borninchaosv.procedures.SeaTerrorStomachPriShchielchkiePKMProcedure)')
OFFLINE = (
    '**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!',
    'The server will make no attempt to authenticate usernames. Beware.',
    'While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.',
    'To change this, set "online-mode" to "true" in the server.properties file.',
)
SOUNDS = frozenset(('minecraft:item.goat_horn.play',
                    'minecraft:entity.goat.screaming.horn_break'))
SHADER_MESSAGE = ('Shader rendertype_entity_translucent_emissive could not find '
                  'sampler named Sampler2 in the specified shader program.')
ALSOFT = '[ALSOFT] (EE) Failed to set real-time priority for thread: Operation not permitted (1)'
TERMINAL = re.compile(r'^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d,\d{3} main WARN Advanced terminal features are not available in this environment$')
RECORD = re.compile(r'^\[\d\d:\d\d:\d\d\] \[(?P<thread>[^]/]+)/(?P<level>WARN|ERROR|FATAL)\] \[(?P<logger>[^]]+)\]: (?P<message>.+)$')
MODULE = re.compile(r'^Mod file /[^ ]+/net\.minecraftforge/(?P<module>[a-z]+)/1\.20\.1-47\.4\.13/(?P<hash>[a-f0-9]{39,40})/(?P=module)-1\.20\.1-47\.4\.13\.jar is missing mods\.toml file$')
UNION = re.compile(r"^Assets URL 'union:/[^']+/forge-1\.20\.1-47\.4\.13_mapped_official_1\.20\.1\.jar%23\d+!/(?P<kind>assets|data)/\.mcassetsroot' uses unexpected schema$")
CONFIG = re.compile(r'^Configuration file (?P<path>\S+) is not correct\. Correcting$')
DEFAULT = re.compile(r'^Incorrect key (?P<key>.+?) was corrected from null to its default, (?P<value>.+?)\. ?$')
SOUND_STARTED = re.compile(r'^\[\d\d:\d\d:\d\d\] \[Render thread/INFO\] \[minecraft/SoundEngine\]: Sound engine started$')
SEVERITY = re.compile(r'\b(?:WARN|WARNING|ERROR|FATAL)\b|\[ALSOFT\] \((?:EE|WW)\)')

CONFIG_KEYS = {
    'config/forge-client.toml': {
        'client': 'SimpleCommentedConfig:{}',
        'client.alwaysSetupTerrainOffThread': 'false',
        'client.experimentalForgeLightPipelineEnabled': 'false',
        'client.showLoadWarnings': 'true',
        'client.useCombinedDepthStencilAttachment': 'false',
        'client.compressLanIPv6Addresses': 'true',
        'client.calculateAllNormals': 'false',
        'client.stabilizeDirectionGetNearest': 'true',
        'client.allowMipmapLowering': 'false',
    },
    'config/unearthed-common.toml': {
        'general settings': 'SimpleCommentedConfig:{}',
        'general settings.enable_regolith_to_dirt_recipe': 'false',
    },
    'config/hyle-common.toml': {
        'generation settings': 'SimpleCommentedConfig:{}',
        'generation settings.enable_debug': 'false',
        'generation settings.disable_all': 'false',
        'generation settings.disable_cobble': 'false',
        'generation settings.disable_dirt': 'false',
        'generation settings.disable_ore': 'false',
    },
    'world/serverconfig/forge-server.toml': {
        'server': 'SimpleCommentedConfig:{}',
        'server.removeErroringBlockEntities': 'false',
        'server.removeErroringEntities': 'false',
        'server.fullBoundingBoxLadders': 'false',
        'server.zombieBaseSummonChance': '0.1',
        'server.zombieBabyChance': '0.05',
        'server.permissionHandler': 'forge:default_handler',
        'server.advertiseDedicatedServerToLan': 'true',
        'server.useItemWithDurationZero': 'false',
    },
}
CONFIG_LIMITS = {'server': frozenset(('config/fml.toml', 'config/hyle-common.toml',
                                     'config/unearthed-common.toml', 'world/serverconfig/forge-server.toml')),
                 'client': frozenset(('config/fml.toml', 'config/hyle-common.toml',
                                     'config/unearthed-common.toml', 'config/forge-client.toml'))}


def _pinned_asset_proof():
    """Inspect the installed, version-pinned vanilla resources, not a caller assertion."""
    cache = Path.home() / '.gradle/caches/forge_gradle'
    sounds = cache / 'assets/objects/40' / SOUNDS_SHA1
    shader_jar = cache / 'minecraft_repo/versions/1.20.1/client-extra.jar'
    proof = {}
    if sounds.is_file() and hashlib.sha1(sounds.read_bytes()).hexdigest() == SOUNDS_SHA1:
        proof['minecraft_sounds_sha1'] = SOUNDS_SHA1
    try:
        with zipfile.ZipFile(shader_jar) as archive:
            hashes = {suffix: hashlib.sha256(archive.read(
                f'assets/minecraft/shaders/core/rendertype_entity_translucent_emissive.{suffix}')).hexdigest()
                for suffix in SHADER_SHA256}
        if hashes == SHADER_SHA256:
            proof['shader_sha256'] = hashes
    except (OSError, KeyError, zipfile.BadZipFile):
        pass
    return proof


def normalize_launch_metadata(raw, log_path):
    """Convert one prelaunch manifest into inputs bound to this native log's directory."""
    if raw.get('schema') != LAUNCH_SCHEMA:
        raise ValueError('unsupported launch metadata schema')
    side = raw.get('side')
    round_index = raw.get('round')
    if (side not in ('client', 'server') or type(round_index) is not int or round_index not in (0, 1)
            or raw.get('family') not in FAMILIES or raw.get('forge_version') != '47.4.13'
            or raw.get('minecraft_version') != '1.20.1' or raw.get('profile') != 'isolated-userdev'):
        raise ValueError('wrong side, round, family, or pinned native profile')
    if log_path.name != f'native-round-{round_index}.log':
        raise ValueError('launch metadata round differs from log filename')
    providers = raw.get('provider_mods')
    if not isinstance(providers, list) or any(not isinstance(x, str) for x in providers) or len(providers) != len(set(providers)):
        raise ValueError('provider_mods must be an explicit unique mod-id list')
    config_basenames = raw.get('initial_absent_configs')
    if not isinstance(config_basenames, list) or any(not isinstance(x, str) for x in config_basenames):
        raise ValueError('initial_absent_configs must be explicit basenames')
    allowed = {Path(path).name: path for path in CONFIG_LIMITS[side]}
    if len(config_basenames) != len(set(config_basenames)) or not set(config_basenames) <= allowed.keys():
        raise ValueError('unsupported or repeated initial absent config')
    if side == 'server':
        if raw.get('server_loopback') is not True or raw.get('online_mode') is not False:
            raise ValueError('server launch must declare loopback and offline mode')
    elif (raw.get('openal_driver') != 'null' or type(raw.get('openal_rt_priority')) is not int
          or raw['openal_rt_priority'] != 0):
        raise ValueError('client launch must declare null OpenAL and disabled real-time priority')
    cwd = log_path.resolve().parent
    return {'side': side, 'round': round_index, 'launch_cwd': str(cwd),
            'initial_absent_configs': [allowed[name] for name in config_basenames],
            'optional_bic_provider_absent': 'borninchaosv' not in providers,
            'server_properties': str(cwd / 'server.properties'),
            'audio_backend': raw.get('openal_driver'),
            'asset_proof': _pinned_asset_proof()}


def _metadata_errors(meta):
    errors = []
    if meta.get('side') not in ('server', 'client') or type(meta.get('round')) is not int or meta['round'] not in (0, 1):
        errors.append('metadata needs side=server/client and round=0/1')
        return errors
    absent = meta.get('initial_absent_configs')
    if not isinstance(absent, list) or any(not isinstance(x, str) for x in absent):
        errors.append('initial_absent_configs must be an explicit list')
    elif len(absent) != len(set(absent)) or not set(absent) <= CONFIG_LIMITS[meta['side']]:
        errors.append('initial_absent_configs contains duplicate or unsupported paths')
    elif meta['round'] != 0 and absent:
        errors.append('initial_absent_configs is only valid in round 0')
    if not isinstance(meta.get('launch_cwd'), str) or not Path(meta['launch_cwd']).is_absolute():
        errors.append('launch_cwd must be absolute')
    if type(meta.get('optional_bic_provider_absent')) is not bool:
        errors.append('optional_bic_provider_absent must be explicit boolean')
    return errors


def _config_name(path, cwd):
    try:
        candidate = Path(path)
        if not candidate.is_absolute():
            candidate = Path(cwd) / candidate
        relative = candidate.resolve().relative_to(Path(cwd).resolve()).as_posix()
    except (ValueError, OSError):
        return None
    return relative if relative in CONFIG_LIMITS['server'] | CONFIG_LIMITS['client'] else None


def _offline_verified(meta):
    try:
        props = Path(meta['server_properties'])
        if not props.is_file() or props.resolve() != (Path(meta['launch_cwd']) / 'server.properties').resolve():
            return False
        settings = dict(line.split('=', 1) for line in props.read_text().splitlines()
                        if '=' in line and not line.lstrip().startswith('#'))
        return settings.get('online-mode') == 'false' and settings.get('server-ip') == '127.0.0.1'
    except (KeyError, OSError, ValueError):
        return False


def classify(log_text, meta):
    """Return a fail-closed JSON-compatible verdict for one complete launch stdout log."""
    errors = _metadata_errors(meta)
    if errors:
        return {'status': 'fail', 'metadata_errors': errors, 'classifications': [],
                'unclassified_count': 0, 'unclassified': [], 'counts': {}}
    side, round_index = meta['side'], meta['round']
    absent = set(meta['initial_absent_configs'])
    counts = Counter()
    records = []
    unknown = []
    seen_config = set()
    sound_started = []
    alsoft_lines = []
    asset_proof = meta.get('asset_proof', {})

    def accept(category, detail, number, line, limit=1):
        key = f'{category}:{detail}'
        counts[key] += 1
        if counts[key] > limit:
            unknown.append({'line': number, 'text': line, 'reason': f'excess {key}: {counts[key]} > {limit}'})
        else:
            records.append({'line': number, 'category': category, 'detail': detail})

    for number, line in enumerate(log_text.splitlines(), 1):
        if SOUND_STARTED.fullmatch(line):
            sound_started.append(number)
            continue
        if line == ALSOFT:
            if side == 'client' and meta.get('audio_backend') == 'null':
                alsoft_lines.append(number)
                accept('openal_priority', 'null_driver_eperm', number, line, 2)
            else:
                unknown.append({'line': number, 'text': line, 'reason': 'OpenAL warning outside declared null audio client'})
            continue
        if TERMINAL.fullmatch(line):
            accept('terminal', 'noninteractive', number, line)
            continue
        match = RECORD.fullmatch(line)
        if not match:
            if SEVERITY.search(line):
                unknown.append({'line': number, 'text': line, 'reason': 'unknown or malformed severity record'})
            continue
        if match['level'] != 'WARN':
            unknown.append({'line': number, 'text': line, 'reason': 'ERROR/FATAL severity'})
            continue
        thread, logger, message = match['thread'], match['logger'], match['message']
        module = MODULE.fullmatch(message)
        if (thread == 'main' and logger == 'ne.mi.fm.lo.mo.ModFileParser/LOADING'
                and module and module['module'] in MODULES
                and MODULE_HASHES[module['module']] == module['hash']):
            accept('forge_userdev_module', module['module'], number, line)
            continue
        union = UNION.fullmatch(message)
        if logger == 'minecraft/VanillaPackResourcesBuilder' and thread == ('main' if side == 'server' else 'Render thread') and union:
            accept('forge_union_mcassetsroot', union['kind'], number, line)
            continue
        if thread == 'main' and logger == 'mixin/' and message == PSEUDO and meta['optional_bic_provider_absent']:
            accept('absent_optional_pseudo', 'borninchaosv_sea_terror', number, line)
            continue
        if side == 'server' and thread == 'Server thread' and logger == 'minecraft/DedicatedServer' and message in OFFLINE and _offline_verified(meta):
            accept('loopback_offline', str(OFFLINE.index(message)), number, line)
            continue
        config = CONFIG.fullmatch(message)
        if config and ((logger == 'ne.mi.fm.lo.FMLConfig/CORE' and thread == 'main')
                       or logger == 'ne.mi.co.ForgeConfigSpec/CORE'):
            name = _config_name(config['path'], meta['launch_cwd'])
            if name in absent and round_index == 0 and (name != 'config/fml.toml') == (logger == 'ne.mi.co.ForgeConfigSpec/CORE'):
                seen_config.add(name)
                accept('initial_config', name, number, line)
                continue
        default = DEFAULT.fullmatch(message)
        if (default and logger == 'ne.mi.co.ForgeConfigSpec/CORE' and round_index == 0
                and ((side == 'client' and thread == 'modloading-worker-0')
                     or (side == 'server' and thread in ('main', 'Server thread')))):
            for name in absent & CONFIG_KEYS.keys():
                if name in seen_config and CONFIG_KEYS[name].get(default['key']) == default['value']:
                    accept('initial_null_default', f'{name}:{default["key"]}', number, line)
                    break
            else:
                unknown.append({'line': number, 'text': line, 'reason': 'unapproved config default or no initial file warning'})
            continue
        if side == 'client' and thread == 'Render thread' and logger == 'minecraft/SoundEngine' and message.startswith('Missing sound for event: '):
            event = message.removeprefix('Missing sound for event: ')
            if event in SOUNDS and asset_proof.get('minecraft_sounds_sha1') == SOUNDS_SHA1:
                accept('vanilla_empty_sound_event', event, number, line, 2)
                continue
        if side == 'client' and thread == 'Render thread' and logger == 'minecraft/ShaderInstance' and message == SHADER_MESSAGE:
            if asset_proof.get('shader_sha256') == SHADER_SHA256:
                accept('vanilla_unused_sampler', 'rendertype_entity_translucent_emissive:Sampler2', number, line, 2)
                continue
        unknown.append({'line': number, 'text': line, 'reason': 'unclassified warning'})

    for number in alsoft_lines:
        if not any(number < started <= number + 10 for started in sound_started):
            unknown.append({'line': number, 'reason': 'OpenAL priority notice without subsequent Sound engine started'})
    if absent != seen_config:
        errors.append(f'initial_absent_configs did not match config correction warnings: {sorted(absent ^ seen_config)}')
    return {'status': 'pass' if not unknown and not errors else 'fail',
            'metadata_errors': errors, 'classifications': records,
            'unclassified_count': len(unknown), 'unclassified': unknown[:64],
            'counts': dict(sorted(counts.items()))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--log', required=True, type=Path)
    parser.add_argument('--metadata', required=True, type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    try:
        metadata = normalize_launch_metadata(json.loads(args.metadata.read_text()), args.log)
        result = classify(args.log.read_text(errors='replace'), metadata)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        result = {'status': 'fail', 'metadata_errors': [str(error)], 'classifications': [],
                  'unclassified_count': 0, 'unclassified': [], 'counts': {}}
    result['log'] = str(args.log.resolve())
    result['metadata'] = str(args.metadata.resolve())
    encoded = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.write_text(encoded)
    else:
        print(encoded, end='')
    return 0 if result['status'] == 'pass' else 1


if __name__ == '__main__':
    raise SystemExit(main())
