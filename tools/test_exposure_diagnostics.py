#!/usr/bin/env python3
"""Synthetic adversarial checks for the exposure-only native diagnostic classifier."""
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

import exposure_diagnostics as audit


def warning(thread, logger, message):
    return f'[12:34:56] [{thread}/WARN] [{logger}]: {message}'


class ExposureDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.cwd = Path(self.temporary.name)
        (self.cwd / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\n')
        self.meta = {'side': 'client', 'round': 1, 'launch_cwd': str(self.cwd),
                     'initial_absent_configs': [], 'optional_bic_provider_absent': True,
                     'audio_backend': 'null',
                     'server_properties': str(self.cwd / 'server.properties'),
                     'asset_proof': {'minecraft_sounds_sha1': audit.SOUNDS_SHA1,
                                     'shader_sha256': audit.SHADER_SHA256}}

    def verdict(self, lines, **metadata):
        return audit.classify('\n'.join(lines) + '\n', dict(self.meta, **metadata))

    def test_exact_client_diagnostics_are_bounded(self):
        module = warning('main', 'ne.mi.fm.lo.mo.ModFileParser/LOADING',
                         'Mod file /home/dev/.gradle/caches/modules-2/files-2.1/net.minecraftforge/fmlcore/'
                         '1.20.1-47.4.13/' + audit.MODULE_HASHES['fmlcore'] + '/fmlcore-1.20.1-47.4.13.jar is missing mods.toml file')
        lines = [module, warning('main', 'mixin/', audit.PSEUDO),
                 warning('Render thread', 'minecraft/VanillaPackResourcesBuilder',
                         "Assets URL 'union:/cache/forge-1.20.1-47.4.13_mapped_official_1.20.1.jar%23181!/assets/.mcassetsroot' uses unexpected schema")]
        for _ in range(2):
            lines.extend([warning('Render thread', 'minecraft/SoundEngine',
                                  'Missing sound for event: minecraft:item.goat_horn.play'),
                          warning('Render thread', 'minecraft/SoundEngine',
                                  'Missing sound for event: minecraft:entity.goat.screaming.horn_break'),
                          audit.ALSOFT,
                          '[12:34:56] [Render thread/INFO] [minecraft/SoundEngine]: Sound engine started',
                          warning('Render thread', 'minecraft/ShaderInstance', audit.SHADER_MESSAGE)])
        self.assertEqual('pass', self.verdict(lines)['status'])

    def test_unknown_warning_and_error_fail(self):
        self.assertEqual('fail', self.verdict([warning('main', 'new.mod/CORE', 'Product warning')])['status'])
        self.assertEqual('fail', self.verdict(['[12:34:56] [main/ERROR] [minecraft/Test]: failure'])['status'])
        self.assertEqual('fail', self.verdict(['[12:34:56] [main/FATAL] [minecraft/Test]: failure'])['status'])
        self.assertEqual('fail', self.verdict(['WARN malformed'])['status'])

    def test_malformed_optional_class_and_namespace_fail(self):
        self.assertEqual('fail', self.verdict([warning('main', 'mixin/', audit.PSEUDO.replace('SeaTerror', 'Other'))])['status'])
        self.assertEqual('fail', self.verdict([warning('main', 'mixin/', audit.PSEUDO)], optional_bic_provider_absent=False)['status'])
        self.assertEqual('fail', self.verdict([warning('Render thread', 'minecraft/SoundEngine',
                                                   'Missing sound for event: othermod:item.goat_horn.play')])['status'])

    def test_sampler_name_and_asset_proof_fail(self):
        line = warning('Render thread', 'minecraft/ShaderInstance', audit.SHADER_MESSAGE)
        self.assertEqual('fail', self.verdict([line.replace('Sampler2', 'Sampler3')])['status'])
        self.assertEqual('fail', self.verdict([line], asset_proof={})['status'])

    def test_initial_defaults_require_absence_and_null(self):
        config = warning('modloading-worker-0', 'ne.mi.co.ForgeConfigSpec/CORE',
                         f'Configuration file {self.cwd}/config/forge-client.toml is not correct. Correcting')
        default = warning('modloading-worker-0', 'ne.mi.co.ForgeConfigSpec/CORE',
                          'Incorrect key client.showLoadWarnings was corrected from null to its default, true. ')
        self.assertEqual('pass', self.verdict([config, default], round=0,
                                              initial_absent_configs=['config/forge-client.toml'])['status'])
        self.assertEqual('fail', self.verdict([config, default])['status'])
        self.assertEqual('fail', self.verdict([config, default.replace('from null', 'from false')], round=0,
                                              initial_absent_configs=['config/forge-client.toml'])['status'])
        self.assertEqual('fail', self.verdict([config, default.replace('true.', 'false.')], round=0,
                                              initial_absent_configs=['config/forge-client.toml'])['status'])

    def test_relative_server_config_requires_recorded_initial_absence(self):
        config = warning('Server thread', 'ne.mi.co.ForgeConfigSpec/CORE',
                         'Configuration file ./world/serverconfig/forge-server.toml is not correct. Correcting')
        default = warning('Server thread', 'ne.mi.co.ForgeConfigSpec/CORE',
                          'Incorrect key server.permissionHandler was corrected from null to its default, forge:default_handler. ')
        self.assertEqual('pass', self.verdict([config, default], side='server', round=0,
                                              initial_absent_configs=['world/serverconfig/forge-server.toml'])['status'])

    def test_wrong_forge_module_hash_fails(self):
        message = ('Mod file /cache/net.minecraftforge/fmlcore/1.20.1-47.4.13/' + 'a' * 40
                   + '/fmlcore-1.20.1-47.4.13.jar is missing mods.toml file')
        self.assertEqual('fail', self.verdict([warning('main', 'ne.mi.fm.lo.mo.ModFileParser/LOADING', message)])['status'])

    def test_excess_counts_and_openal_failure_fail(self):
        sound = warning('Render thread', 'minecraft/SoundEngine',
                        'Missing sound for event: minecraft:item.goat_horn.play')
        self.assertEqual('fail', self.verdict([sound] * 3)['status'])
        self.assertEqual('fail', self.verdict([audit.ALSOFT])['status'])
        self.assertEqual('fail', self.verdict([audit.ALSOFT] * 3 +
                         ['[12:34:56] [Render thread/INFO] [minecraft/SoundEngine]: Sound engine started'])['status'])

    def test_absent_optional_biome_tag_fails(self):
        line = warning('main', 'minecraft/MappedRegistry',
                       'Not all defined tags for registry ResourceKey[minecraft:root / minecraft:worldgen/biome] are present in data pack: aether:is_aether, twilightforest:in_twilight_forest')
        self.assertEqual('fail', self.verdict([line])['status'])

    def test_offline_requires_real_loopback_properties(self):
        lines = [warning('Server thread', 'minecraft/DedicatedServer', item) for item in audit.OFFLINE]
        self.assertEqual('pass', self.verdict(lines, side='server')['status'])
        (self.cwd / 'server.properties').write_text('online-mode=false\nserver-ip=0.0.0.0\n')
        self.assertEqual('fail', self.verdict(lines, side='server')['status'])

    def test_launch_manifest_normalizes_basenames_and_pins_profile(self):
        raw = {'schema': audit.LAUNCH_SCHEMA, 'side': 'client', 'round': 0,
               'family': 'copper_bloom', 'forge_version': '47.4.13',
               'minecraft_version': '1.20.1', 'profile': 'isolated-userdev',
               'provider_mods': ['minecraft', 'forge'],
               'initial_absent_configs': ['forge-client.toml'],
               'openal_driver': 'null', 'openal_rt_priority': 0}
        log = self.cwd / 'native-round-0.log'
        with patch.object(audit, '_pinned_asset_proof', return_value={}):
            normalized = audit.normalize_launch_metadata(raw, log)
        self.assertEqual(['config/forge-client.toml'], normalized['initial_absent_configs'])
        self.assertTrue(normalized['optional_bic_provider_absent'])
        with self.assertRaises(ValueError):
            audit.normalize_launch_metadata(dict(raw, forge_version='47.4.14'), log)
        with self.assertRaises(ValueError):
            audit.normalize_launch_metadata(dict(raw, initial_absent_configs=['unknown.toml']), log)
        with self.assertRaises(ValueError):
            audit.normalize_launch_metadata(dict(raw, openal_rt_priority=1), log)


if __name__ == '__main__':
    unittest.main()
