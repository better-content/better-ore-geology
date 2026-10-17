#!/usr/bin/env python3
"""Bounded nonshipping native copper scene, reload, mining and save/restart acceptance.
Prepare first with ./gradlew prepareExposureLaunch. Requires Xvfb; no mouse input.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'run-exposure'
FAMILIES = ('coal_measures', 'ironstone', 'copper_bloom', 'tin_quartz', 'brassroot',
            'evaporite_beds', 'hotstone', 'black_shale')
FAMILY = 'copper_bloom'


def load(path):
    return json.loads(path.read_text())


def stop(process, graceful=False):
    if process is None:
        return
    if process.poll() is None:
        if graceful:
            try:
                process.stdin.write('stop\n')
                process.stdin.flush()
            except (OSError, BrokenPipeError):
                pass
        else:
            os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=90)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=15)
                raise RuntimeError('native process required forced termination')
    # The entire launch owns its process group, including Xvfb and wrapper children.
    try:
        os.killpg(process.pid, 0)
    except ProcessLookupError:
        return
    os.killpg(process.pid, signal.SIGTERM)
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        try:
            os.killpg(process.pid, 0)
        except ProcessLookupError:
            return
        time.sleep(.2)
    os.killpg(process.pid, signal.SIGKILL)
    raise RuntimeError('native launch process group did not finish cleanly')


def launch_metadata(side, round_index, cwd, provider_mods=None):
    """Record independently verified fixture inputs before Minecraft can create config files."""
    sound_sha1 = '40a4222b7ada165fa98cb9215129aeb8d9b6b379'
    sound = Path.home() / f'.gradle/caches/forge_gradle/assets/objects/40/{sound_sha1}'
    if hashlib.sha1(sound.read_bytes()).hexdigest() != sound_sha1:
        raise RuntimeError('Pinned vanilla sound-event proof mismatch')
    expected_shaders = {
        'json': 'd4ea27980006208a198c7f181e25aab6d6abeea49518c8ddbd5fac2edc80d515',
        'vsh': 'dad84bd4d8e538c775704199d3953055e12ec76450904ad46f39073982161cde',
        'fsh': '968b666a761dd7c3641b4aa852ce266680db57f82e3bb8d4218d8c7af51312d4',
    }
    archive = Path.home() / '.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar'
    with zipfile.ZipFile(archive) as source:
        stem = 'assets/minecraft/shaders/core/'
        shader = source.read(stem + 'rendertype_entity_translucent_emissive.json')
        definition = json.loads(shader)
        parts = {'json': shader, 'vsh': source.read(stem + definition['vertex'].split(':')[-1] + '.vsh'),
                 'fsh': source.read(stem + definition['fragment'].split(':')[-1] + '.fsh')}
        actual_shaders = {kind: hashlib.sha256(data).hexdigest() for kind, data in parts.items()}
    if actual_shaders != expected_shaders:
        raise RuntimeError(f'Pinned vanilla shader proof mismatch: {actual_shaders}')
    config_paths = [cwd / 'config' / name for name in ('fml.toml', 'hyle-common.toml', 'unearthed-common.toml')]
    config_paths.append(cwd / ('config/forge-client.toml' if side == 'client' else 'world/serverconfig/forge-server.toml'))
    properties = cwd / 'server.properties'
    server = dict(line.split('=', 1) for line in properties.read_text().splitlines()
                  if '=' in line and not line.startswith('#')) if side == 'server' else {}
    if side == 'server' and (server.get('server-ip') != '127.0.0.1' or server.get('online-mode') != 'false'):
        raise RuntimeError('Native fixture must remain offline and loopback-only')
    if any((cwd / 'mods').glob('*.jar')):
        raise RuntimeError('Undeclared fixture mods are not allowed')
    providers = provider_mods if provider_mods is not None else [
        'minecraft', 'forge', 'better_ore_geology', 'better_ore_exposure_probe', 'excavated_variants',
        'dynamic_asset_generator', 'defaultresources', 'unearthed', 'hyle']
    return {'schema': 'better_ore_exposure_probe.launch.v1', 'profile': 'isolated-userdev',
            'side': side, 'round': round_index, 'family': FAMILY, 'launch_cwd': str(cwd.resolve()),
            'forge_version': '47.4.13', 'minecraft_version': '1.20.1', 'provider_mods': providers,
            'initial_absent_configs': [p.name for p in config_paths if not p.exists()],
            'optional_bic_provider_absent': 'born_in_chaos_v1' not in providers,
            'server_loopback': side == 'server', 'online_mode': False,
            'server_properties': str(properties.resolve()) if side == 'server' else None,
            'openal_driver': 'null' if side == 'client' else None, 'audio_backend': 'null' if side == 'client' else None,
            'openal_rt_priority': 0 if side == 'client' else None,
            'asset_proof': {'minecraft_sounds_sha1': sound_sha1, 'shader_sha256': actual_shaders}}


def start(side, round_index):
    launch = load(ROOT / f'build/exposure-launch/{side}.json')
    cwd = RUN / side
    cwd.mkdir(parents=True, exist_ok=True)
    environment = dict(os.environ, **launch['environment'])
    command = launch['command'][:]
    command.insert(1, f'-Dbetter_ore_geology.exposureProbe.family={FAMILY}')
    (cwd / 'exposure-probe/client.json').unlink(missing_ok=True)
    (cwd / 'exposure-probe/server.json').unlink(missing_ok=True)
    if side == 'client':
        command = ['xvfb-run', '-a', '-s', '-screen 0 1280x900x24'] + command
        environment['LIBGL_ALWAYS_SOFTWARE'] = '1'
        environment['ALSOFT_DRIVERS'] = 'null'
        audio_config = cwd / 'openal-soft.conf'
        audio_config.write_text('[general]\nrt-prio=0\n')
        environment['ALSOFT_CONF'] = str(audio_config)
        environment['LD_LIBRARY_PATH'] = str(Path.home() / '.local/share/minecraft-native/libflite1-2.2-7/usr/lib/x86_64-linux-gnu')
    log = cwd / f'native-round-{round_index}.log'
    (cwd / f'native-round-{round_index}.input.json').write_text(
        json.dumps(launch_metadata(side, round_index, cwd), indent=2) + '\n')
    output = log.open('w')
    process = subprocess.Popen(command, cwd=cwd, env=environment, stdin=subprocess.PIPE,
                               stdout=output, stderr=subprocess.STDOUT, text=True, start_new_session=True)
    output.close()
    return process, log


def wait_server(process, log):
    deadline = time.monotonic() + 900
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'server exited {process.returncode}: {log}')
        if 'EXPOSURE_PROBE_SERVER_READY' in log.read_text(errors='replace'):
            return
        time.sleep(1)
    raise RuntimeError(f'server startup expired: {log}')


def main():
    for side in ('server', 'client'):
        (RUN / side).mkdir(parents=True, exist_ok=True)
    (RUN / 'server/eula.txt').write_text('eula=true\n')
    properties = RUN / 'server/server.properties'
    if not properties.exists():
        properties.write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25585\nview-distance=4\n'
                              'simulation-distance=4\nlevel-seed=831274\nlevel-type=minecraft:flat\n'
                              'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
                              '{"block":"minecraft:stone","height":3}],"biome":"minecraft:plains"}\n'
                              'spawn-protection=0\nmax-tick-time=120000\n')
    options = RUN / 'client/options.txt'
    if not options.exists():
        options.write_text('renderDistance:6\nsimulationDistance:5\nmaxFps:30\ngraphicsMode:0\n'
                           'fullscreen:false\npauseOnLostFocus:false\nentityShadows:false\n')
    else:
        options.write_text(options.read_text().replace('simulationDistance:4\n', 'simulationDistance:5\n'))
    lock = (Path.home() / '.worklane/forge-cache.lock').open('a')
    fcntl.lockf(lock, fcntl.LOCK_EX)
    for round_index in range(2):
        server = client = None
        launched_logs = []
        try:
            print(json.dumps({'phase': 'server_start', 'round': round_index}), flush=True)
            server, server_log = start('server', round_index)
            launched_logs.append(server_log)
            wait_server(server, server_log)
            print(json.dumps({'phase': 'client_start', 'round': round_index}), flush=True)
            client, client_log = start('client', round_index)
            launched_logs.append(client_log)
            try:
                client.wait(timeout=900)
            except subprocess.TimeoutExpired:
                raise RuntimeError(f'client acceptance expired: {client_log}')
            if client.returncode != 0:
                raise RuntimeError(f'client exited {client.returncode}: {client_log}')
            client_report = load(RUN / 'client/exposure-probe/client.json')
            server_report = load(RUN / 'server/exposure-probe/server.json')
            if client_report.get('status') != 'pass' or not server_report.get('ready'):
                raise RuntimeError(f'native report failed; inspect {RUN}')
            if round_index == 1 and server_report.get('restart_states_verified_before_writes') != 288:
                raise RuntimeError('second native launch did not prove persisted wall states')
            for side, report in (('client', client_report), ('server', server_report)):
                (RUN / side / f'exposure-probe/round-{round_index}.json').write_text(json.dumps(report, indent=2) + '\n')
            print(json.dumps({'phase': 'round_functional_pass', 'round': round_index}), flush=True)
        finally:
            errors = []
            for process, graceful in ((client, False), (server, True)):
                try:
                    stop(process, graceful)
                except Exception as error:
                    errors.append(str(error))
            if errors:
                raise RuntimeError('process cleanup incomplete: ' + '; '.join(errors))
            for log in launched_logs:
                verdict = subprocess.run([sys.executable, '-B', str(ROOT / 'tools/exposure_diagnostics.py'),
                                          '--log', str(log), '--metadata', str(log.with_suffix('.input.json')),
                                          '--output', str(log.with_suffix('.diagnostics.json'))], check=False)
                if verdict.returncode != 0:
                    raise RuntimeError(f'strict native diagnostic gate failed: {log}')
    print(json.dumps({'status': 'pass', 'family': FAMILY, 'rounds': 2, 'software_rendering': True,
                      'process_cleanup_complete': True, 'strict_diagnostics_passed': True, 'fixture': str(RUN)}), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--family', choices=FAMILIES, help='use a separate two-round family fixture')
    parser.add_argument('--fixture-id', help='create a new, absent named fixture beneath the selected family')
    arguments = parser.parse_args()
    if arguments.fixture_id and (not arguments.family or not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,63}', arguments.fixture_id)):
        parser.error('--fixture-id requires --family and a lowercase alphanumeric/hyphen name')
    if arguments.family:
        FAMILY = arguments.family
        RUN = ROOT / 'run-exposure/families' / FAMILY
    if arguments.fixture_id:
        RUN /= arguments.fixture_id
        if RUN.exists():
            parser.error('--fixture-id must identify an absent fixture, never retained outputs')
    main()
