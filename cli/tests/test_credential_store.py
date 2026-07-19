import base64
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


CLI_DIR = Path(__file__).resolve().parents[1]
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import credential_store  # noqa: E402


class FakeNativeBackend:
    name = "test-native"

    def __init__(self, token=None):
        self.token = token
        self.deleted = False

    def get(self):
        return self.token

    def set(self, token):
        self.token = token

    def delete(self):
        existed = self.token is not None
        self.token = None
        self.deleted = True
        return existed


class CredentialStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.directory = Path(self.temp_dir.name) / "config"
        self.machine_id = b"linux:test-machine-id"

    def _unavailable(self):
        raise credential_store.NativeStoreUnavailable("not available")

    def _fallback_store(self, machine_id=None):
        return credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: machine_id or self.machine_id,
        )

    def test_native_backend_is_preferred_and_verified(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        result = store.store("github-token")

        self.assertFalse(result.degraded)
        self.assertEqual("test-native", result.backend)
        self.assertEqual("github-token", store.read())
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertNotIn("github-token", store.path.read_text(encoding="utf-8"))

    def test_machine_bound_fallback_round_trips_without_plaintext(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()

        result = store.store("github-token")

        self.assertTrue(result.degraded)
        self.assertEqual(credential_store.FALLBACK_BACKEND, result.backend)
        self.assertEqual("github-token", store.read())
        raw = store.path.read_text(encoding="utf-8")
        self.assertNotIn("github-token", raw)
        metadata = json.loads(raw)
        self.assertFalse(metadata["native_cleanup_pending"])
        self.assertEqual(32, len(base64.b64decode(metadata["seed"])))
        self.assertEqual(12, len(base64.b64decode(metadata["nonce"])))
        self.assertEqual(16, len(base64.b64decode(metadata["tag"])))
        if os.name != "nt":
            self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
            self.assertEqual(0o600, store.path.stat().st_mode & 0o777)

    @unittest.skipIf(os.name == "nt", "POSIX permissions only")
    def test_read_repairs_restored_fallback_permissions(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        self.directory.chmod(0o755)
        store.path.chmod(0o644)

        self.assertEqual("github-token", store.read())

        self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
        self.assertEqual(0o600, store.path.stat().st_mode & 0o777)

    def test_fallback_notice_runs_before_persistence(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        observations = []

        store.store(
            "github-token",
            before_fallback=lambda: observations.append(store.path.exists()),
        )

        self.assertEqual([False], observations)

    def test_rewriting_fallback_uses_fresh_seed_and_nonce(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("first-token")
        first = json.loads(store.path.read_text(encoding="utf-8"))

        store.store("second-token")
        second = json.loads(store.path.read_text(encoding="utf-8"))

        self.assertNotEqual(first["seed"], second["seed"])
        self.assertNotEqual(first["nonce"], second["nonce"])
        self.assertEqual("second-token", store.read())

    def test_machine_identifier_change_fails_closed(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        moved = self._fallback_store(b"linux:different-machine")

        with self.assertRaises(credential_store.CredentialCorrupt):
            moved.read()

    def test_tampering_is_detected(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        tag = bytearray(base64.b64decode(metadata["tag"]))
        tag[0] ^= 1
        metadata["tag"] = base64.b64encode(tag).decode("ascii")
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_cleanup_state_tampering_is_authenticated(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["native_cleanup_pending"] = True
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_invalid_metadata_is_rejected_and_can_be_reset(self):
        self.directory.mkdir(parents=True)
        path = self.directory / credential_store.CREDENTIAL_FILE_NAME
        path.write_text('{"version": 99}', encoding="utf-8")
        store = self._fallback_store()

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()
        self.assertTrue(store.delete())
        self.assertFalse(path.exists())

    def test_locked_native_backend_does_not_downgrade(self):
        class LockedBackend(FakeNativeBackend):
            def set(self, token):
                raise credential_store.NativeStoreError("locked")

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: LockedBackend(),
            machine_id_provider=lambda: self.machine_id,
        )

        with self.assertRaises(credential_store.NativeStoreError):
            store.store("github-token")
        self.assertFalse(store.path.exists())

    def test_fallback_requires_stable_machine_identifier(self):
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: b"",
        )

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.store("github-token")
        self.assertFalse(store.path.exists())

    def test_delete_removes_native_credential_and_marker(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")

        self.assertTrue(store.delete())

        self.assertTrue(backend.deleted)
        self.assertFalse(store.path.exists())
        self.assertIsNone(backend.token)

    def test_native_metadata_write_failure_rolls_back_credential(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("github-token")

        self.assertIsNone(backend.token)
        self.assertTrue(backend.deleted)
        self.assertFalse(store.path.exists())

    def test_native_metadata_write_failure_restores_previous_credential(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("new-token")

        self.assertEqual("old-token", backend.token)
        self.assertEqual("old-token", store.read())

    def test_post_set_unavailability_rolls_back_without_fallback(self):
        class ReadFailsAfterSetBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False

            def set(self, token):
                super().set(token)
                if token == "new-token":
                    self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

        backend = ReadFailsAfterSetBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        fallback_notices = []

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.store(
                "new-token",
                before_fallback=lambda: fallback_notices.append(True),
            )

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertEqual([], fallback_notices)

    def test_fallback_upgrade_rolls_back_a_failed_native_write(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class ReadFailsAfterSetBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

        backend = ReadFailsAfterSetBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("fallback-token")
        native_available = True

        self.assertEqual("fallback-token", store.read())

        self.assertIsNone(backend.token)
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])

    def test_failed_native_rollback_leaves_write_ahead_cleanup_state(self):
        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

            def delete(self):
                raise credential_store.NativeStoreError("cleanup failed")

        backend = RollbackFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("new-token")

        self.assertEqual("new-token", backend.token)
        self.assertFalse(store.path.exists())
        metadata = json.loads(store.pending_path.read_text(encoding="utf-8"))
        self.assertEqual("cleanup-required", metadata["state"])
        self.assertNotIn("new-token", store.pending_path.read_text(encoding="utf-8"))
        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()

        metadata["state"] = "clean"
        store.pending_path.write_text(json.dumps(metadata), encoding="utf-8")
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_failed_native_rollback_without_aes_state_stays_fail_closed(self):
        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_verification = False
                self.fail_rollback = False

            def set(self, token):
                if self.fail_rollback and token == "old-token":
                    raise credential_store.NativeStoreError("rollback failed")
                super().set(token)
                if token == "new-token":
                    self.fail_verification = True
                    self.fail_rollback = True

            def get(self):
                if self.fail_verification:
                    self.fail_verification = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

        backend = RollbackFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: (_ for _ in ()).throw(
                credential_store.NativeStoreUnavailable(
                    "machine identifier unavailable"
                )
            ),
        )
        store.store("old-token")

        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("new-token")

        self.assertEqual("new-token", backend.token)
        primary = store.path.read_text(encoding="utf-8")
        pending = store.pending_path.read_text(encoding="utf-8")
        self.assertNotIn("old-token", primary + pending)
        self.assertNotIn("new-token", primary + pending)
        metadata = json.loads(pending)
        self.assertEqual("cleanup-required", metadata["state"])
        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()
        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("third-token")

        metadata["kind"] = "native"
        store.pending_path.write_text(json.dumps(metadata), encoding="utf-8")
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_failed_write_ahead_marker_prevents_native_mutation(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")

        with (
            mock.patch.object(
                store,
                "_write_pending_metadata",
                side_effect=OSError("read-only filesystem"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("new-token")

        self.assertEqual("old-token", backend.token)
        self.assertFalse(store.pending_path.exists())
        self.assertEqual("old-token", store.read())

    def test_logout_resets_corrupt_pending_marker_after_verified_cleanup(self):
        backend = FakeNativeBackend("uncertain-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.directory.mkdir(parents=True)
        store.pending_path.write_text('{"version": 1}', encoding="utf-8")

        self.assertTrue(store.delete())

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_logout_retains_corrupt_pending_marker_without_native_cleanup(self):
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: self.machine_id,
        )
        store.directory.mkdir(parents=True)
        store.pending_path.write_text('{"version": 1}', encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()

        self.assertTrue(store.pending_path.exists())

    def test_uncertain_fallback_upgrade_retains_cleanup_retry_state(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False
                self.allow_cleanup = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

            def delete(self):
                if not self.allow_cleanup:
                    raise credential_store.NativeStoreError("cleanup failed")
                return super().delete()

        backend = RollbackFailureBackend()
        factory_available = False

        def backend_factory():
            if not factory_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("fallback-token")
        factory_available = True

        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertFalse(metadata["native_cleanup_pending"])
        self.assertEqual("fallback-token", store._decrypt_fallback(metadata))
        self.assertTrue(store.pending_path.exists())

        factory_available = False
        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.delete()
        self.assertTrue(store.path.exists())
        self.assertTrue(store.pending_path.exists())

        factory_available = True
        backend.allow_cleanup = True
        self.assertTrue(store.delete())
        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_fallback_upgrades_when_native_storage_becomes_available(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")
        native_available = True

        self.assertEqual("github-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertEqual("github-token", backend.token)

    def test_fallback_delete_also_removes_stale_native_credential(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend("stale-native-token")
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("fallback-token")
        native_available = True

        self.assertTrue(store.delete())

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())

    def test_delete_without_marker_recovers_orphaned_native_credential(self):
        backend = FakeNativeBackend("orphaned-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        self.assertTrue(store.delete())

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())

    def test_failed_native_delete_keeps_valid_marker_for_retry(self):
        class DeleteFailureBackend(FakeNativeBackend):
            def delete(self):
                raise credential_store.NativeStoreError("credential is locked")

        backend = DeleteFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")

        with self.assertRaises(credential_store.NativeStoreError):
            store.delete()

        self.assertEqual("github-token", backend.token)
        self.assertTrue(store.path.exists())

    def test_corrupt_marker_is_reset_even_when_native_cleanup_fails(self):
        class DeleteFailureBackend(FakeNativeBackend):
            def delete(self):
                raise credential_store.NativeStoreError("credential is locked")

        self.directory.mkdir(parents=True)
        path = self.directory / credential_store.CREDENTIAL_FILE_NAME
        path.write_text('{"version": 99}', encoding="utf-8")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: DeleteFailureBackend("secret"),
            machine_id_provider=lambda: self.machine_id,
        )

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()

        self.assertFalse(path.exists())

    def test_native_provider_mismatch_fails_closed(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["provider"] = "different-native"
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.read()

        self.assertTrue(store.delete())
        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())

    def test_keyring_delete_error_does_not_claim_success_while_token_remains(self):
        class PasswordDeleteError(Exception):
            pass

        class Errors:
            pass

        class Backend:
            token = "github-token"

            def get_password(self, service, account):
                return self.token

            def delete_password(self, service, account):
                raise PasswordDeleteError("delete failed")

        backend = Backend()
        adapter = credential_store.KeyringBackend(
            backend,
            "test-keyring",
            Errors,
        )

        with self.assertRaises(credential_store.NativeStoreError):
            adapter.delete()

        self.assertEqual("github-token", backend.token)

    def test_keyring_delete_error_is_success_when_post_read_confirms_absence(self):
        class PasswordDeleteError(Exception):
            pass

        class Errors:
            pass

        class Backend:
            token = "github-token"

            def get_password(self, service, account):
                return self.token

            def delete_password(self, service, account):
                self.token = None
                raise PasswordDeleteError("response was lost")

        backend = Backend()
        adapter = credential_store.KeyringBackend(
            backend,
            "test-keyring",
            Errors,
        )

        self.assertTrue(adapter.delete())
        self.assertIsNone(backend.token)

    def test_no_marker_read_has_no_filesystem_side_effect(self):
        store = self._fallback_store()

        self.assertIsNone(store.read())

        self.assertFalse(self.directory.exists())

    def test_hkdf_derivation_is_stable_and_domain_separated(self):
        first = credential_store._hkdf_sha256(
            bytes(range(32)),
            b"linux:test-machine-id",
        )
        second = credential_store._hkdf_sha256(
            bytes(range(32)),
            b"linux:different-machine-id",
        )

        self.assertEqual(
            "70d615c39ee8d05832535b63aec247de7aff239b23487e2466bd078455009746",
            first.hex(),
        )
        self.assertNotEqual(first, second)

    def test_aes_gcm_self_test_exercises_selected_backend(self):
        if credential_store._AES_BACKEND is None:
            with self.assertRaises(credential_store.CredentialStoreError):
                credential_store.aes_gcm_self_test()
        else:
            self.assertEqual(
                credential_store._AES_BACKEND,
                credential_store.aes_gcm_self_test(),
            )

    def test_pycryptodome_aes_gcm_fallback_round_trips(self):
        try:
            from Cryptodome.Cipher import AES as fallback_aes
            backend_name = "pycryptodomex"
        except ImportError:
            try:
                from Crypto.Cipher import AES as fallback_aes
                backend_name = "pycryptodome"
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        store = self._fallback_store()

        with (
            mock.patch.object(credential_store, "_AES_BACKEND", backend_name),
            mock.patch.object(credential_store, "_PYAES", fallback_aes),
        ):
            store.store("github-token")
            self.assertEqual("github-token", store.read())


if __name__ == "__main__":
    unittest.main()
