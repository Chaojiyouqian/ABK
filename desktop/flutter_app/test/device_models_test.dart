import 'package:abk_desktop/src/core/models/device_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('groups ABK runtime children with group metadata as module sets', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'abi-bridge',
      'name': 'ABK ABI Bridge',
      'type': 'builtin',
      'source': 'abk',
      'groupName': 'ABK Control Module',
      'groupId': 'abk-control',
      'groupRepoUrl':
          'https://github.com/xingguangcuican6666/ABK_control_module',
    });

    expect(module.isCustomModuleSetChild, isTrue);
    expect(module.isStandardRuntimeModule, isFalse);
    expect(module.isCustomModule, isFalse);
  });

  test('treats ksud modules without group metadata as standard modules', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'zygisk-next',
      'name': 'Zygisk Next',
      'type': 'standard',
      'source': 'ksud',
    });

    expect(module.isStandardRuntimeModule, isTrue);
    expect(module.isCustomModuleSetChild, isFalse);
    expect(module.isCustomModule, isFalse);
  });

  test('does not treat non-abk sources with group metadata as module sets', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'zygisk-next',
      'name': 'Zygisk Next',
      'type': 'standard',
      'source': 'ksud',
      'groupName': 'Not an ABK set',
      'groupId': 'fake-group',
      'groupRepoUrl': 'https://example.com/not-abk',
    });

    expect(module.isStandardRuntimeModule, isTrue);
    expect(module.isCustomModuleSetChild, isFalse);
    expect(module.isCustomModule, isFalse);
  });

  test('treats abk custom single modules without group metadata as custom', () {
    final module = AbkRuntimeModule.fromJson(const <String, dynamic>{
      'id': 'abk-control',
      'name': 'ABK Control Module',
      'type': 'builtin',
      'source': 'abk',
    });

    expect(module.isCustomModule, isTrue);
    expect(module.isStandardRuntimeModule, isFalse);
    expect(module.isCustomModuleSetChild, isFalse);
  });
}
