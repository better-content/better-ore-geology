#!/usr/bin/env python3
"""Bounded native dedicated-server startup without EV, DAG, Unearthed or the probe mod.
Prepare with ./gradlew prepareExposureLaunch. This is not a renderer or full-pack test.
"""
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import time
from run_exposure_probe import stop

ROOT = Path(__file__).resolve().parents[1]
RUN = ROOT / 'run-exposure/standalone-server'


def main():
    launch = json.loads((ROOT / 'build/exposure-launch/standaloneserver.json').read_text())
    command = launch['command']
    for excluded in ('excavated-variants', 'dynamic-asset-generator', 'unearthed-', 'hyle-'):
        if any(excluded in argument for argument in command):
            raise RuntimeError(f'standalone launch contains {excluded}')
    if 'exposure_probe' in launch['environment'].get('MOD_CLASSES', ''):
        raise RuntimeError('standalone launch contains the companion probe')
    RUN.mkdir(parents=True, exist_ok=True)
    (RUN / 'eula.txt').write_text('eula=true\n')
    properties = RUN / 'server.properties'
    if not properties.exists():
        properties.write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25586\n'
                              'view-distance=4\nsimulation-distance=4\nlevel-seed=831274\n'
                              'level-type=minecraft:flat\ngenerator-settings={"layers":'
                              '[{"block":"minecraft:bedrock","height":1},'
                              '{"block":"minecraft:stone","height":3}],"biome":"minecraft:plains"}\n')
    log = RUN / 'native.log'
    lock = (Path.home() / '.worklane/forge-cache.lock').open('a')
    fcntl.lockf(lock, fcntl.LOCK_EX)
    process = None
    ready = False
    try:
        with log.open('w') as output:
            process = subprocess.Popen(command, cwd=RUN, env=dict(os.environ, **launch['environment']),
                                       stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                       text=True, start_new_session=True)
        deadline = time.monotonic() + 600
        while time.monotonic() < deadline:
            text = log.read_text(errors='replace')
            if re.search(r'Done \([0-9.]+s\)! For help, type', text):
                ready = True
                process.stdin.write('list\n')
                process.stdin.flush()
                time.sleep(2)
                break
            if process.poll() is not None:
                raise RuntimeError(f'standalone server exited {process.returncode} before readiness')
            time.sleep(.5)
        if not ready:
            raise RuntimeError('standalone server readiness expired')
    finally:
        stop(process, graceful=True)
    text = log.read_text(errors='replace')
    if process.returncode != 0 or re.search(r'/(?:ERROR|FATAL)\]|Exception in thread|Encountered an unexpected exception', text):
        raise RuntimeError(f'standalone startup/shutdown failed; inspect {log}')
    result = {'schema': 'better_ore_exposure_probe.standalone.v1', 'functional_status': 'pass',
              'native_server_ready': ready, 'optional_dependencies_absent': True,
              'process_cleanup_complete': True, 'diagnostics': 'audit separately', 'log': str(log)}
    (RUN / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
