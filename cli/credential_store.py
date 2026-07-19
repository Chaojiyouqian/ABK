#!/usr/bin/env python3
"""Persistent GitHub credential storage for the ABK CLI."""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import os
import re
import secrets
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    _AES_BACKEND = "cryptography"
    _PYAES = None
except ImportError:
    AESGCM = None
    try:
        from Cryptodome.Cipher import AES as _PYAES

        _AES_BACKEND = "pycryptodomex"
    except ImportError:
        try:
            from Crypto.Cipher import AES as _PYAES

            _AES_BACKEND = "pycryptodome"
        except ImportError:
            _PYAES = None
            _AES_BACKEND = None


CREDENTIAL_FILE_NAME = "credentials.json"
CREDENTIAL_PENDING_FILE_NAME = "credentials.pending.json"
CREDENTIAL_FORMAT_VERSION = 1
CREDENTIAL_SERVICE = "ABK CLI"
CREDENTIAL_ACCOUNT = "github.com"
FALLBACK_BACKEND = "machine-bound-aes-gcm"
MAX_CREDENTIAL_FILE_SIZE = 64 * 1024
MAX_TOKEN_SIZE = 4096
_SEED_SIZE = 32
_NONCE_SIZE = 12
_TAG_SIZE = 16
_HKDF_INFO = b"abk-cli/github-token/key/v1"
_AAD = b"abk-cli|github.com|credential|v1|hkdf-sha256|aes-256-gcm"


class CredentialStoreError(RuntimeError):
    """Base class for persistent credential storage failures."""


class NativeStoreUnavailable(CredentialStoreError):
    """Raised when no supported native credential store can be used."""


class NativeStoreError(CredentialStoreError):
    """Raised when an available native credential store rejects an operation."""


class NativeRollbackError(NativeStoreError):
    """Raised when a partially changed native credential cannot be restored."""


class CredentialCorrupt(CredentialStoreError):
    """Raised when encrypted credential metadata cannot be authenticated."""


@dataclass(frozen=True)
class StoreResult:
    backend: str
    degraded: bool
    location: str


class KeyringBackend:
    """Small adapter around one explicitly selected system keyring backend."""

    def __init__(self, backend, name, errors):
        self._backend = backend
        self.name = name
        self._errors = errors

    def _translate(self, operation, exc):
        unavailable = tuple(
            error
            for error in (
                getattr(self._errors, "NoKeyringError", None),
                getattr(self._errors, "InitError", None),
            )
            if isinstance(error, type)
        )
        if unavailable and isinstance(exc, unavailable):
            raise NativeStoreUnavailable(
                f"{self.name} is unavailable"
            ) from exc
        raise NativeStoreError(
            f"{self.name} could not {operation} the GitHub credential"
        ) from exc

    def get(self):
        try:
            return self._backend.get_password(
                CREDENTIAL_SERVICE,
                CREDENTIAL_ACCOUNT,
            )
        except Exception as exc:
            self._translate("read", exc)

    def set(self, token):
        try:
            self._backend.set_password(
                CREDENTIAL_SERVICE,
                CREDENTIAL_ACCOUNT,
                token,
            )
        except Exception as exc:
            self._translate("store", exc)

    def delete(self):
        existing = self.get()
        if existing is None:
            return False
        try:
            self._backend.delete_password(
                CREDENTIAL_SERVICE,
                CREDENTIAL_ACCOUNT,
            )
            return True
        except Exception as exc:
            try:
                if self.get() is None:
                    return True
            except CredentialStoreError:
                pass
            self._translate("delete", exc)


def create_native_backend(platform_name=None):
    """Return only ABK-approved OS credential backends.

    Generic keyring discovery is deliberately avoided because user-installed
    third-party backends may store secrets in plaintext.
    """
    platform_name = platform_name or sys.platform
    try:
        from keyring import errors

        if platform_name == "win32":
            from keyring.backends.Windows import WinVaultKeyring

            backend = WinVaultKeyring()
            name = "windows-credential-manager"
        elif platform_name == "darwin":
            from keyring.backends.macOS import Keyring

            backend = Keyring()
            name = "macos-keychain"
        elif platform_name.startswith("linux"):
            from keyring.backends.SecretService import Keyring

            backend = Keyring()
            name = "secret-service"
        else:
            raise NativeStoreUnavailable(
                f"no supported native credential store for {platform_name}"
            )

        try:
            priority = backend.priority
        except Exception as exc:
            raise NativeStoreUnavailable(
                f"{name} is unavailable"
            ) from exc
        if priority <= 0:
            raise NativeStoreUnavailable(f"{name} is unavailable")
        return KeyringBackend(backend, name, errors)
    except NativeStoreUnavailable:
        raise
    except (ImportError, ModuleNotFoundError) as exc:
        raise NativeStoreUnavailable(
            "system credential storage support is not installed"
        ) from exc
    except Exception as exc:
        raise NativeStoreUnavailable(
            "system credential storage is unavailable"
        ) from exc


def _machine_identifier(platform_name=None):
    platform_name = platform_name or sys.platform
    if platform_name == "win32":
        try:
            import winreg

            path = r"SOFTWARE\Microsoft\Cryptography"
            access = winreg.KEY_READ
            if hasattr(winreg, "KEY_WOW64_64KEY"):
                access |= winreg.KEY_WOW64_64KEY
            with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, path, 0, access) as key:
                value, _ = winreg.QueryValueEx(key, "MachineGuid")
        except (ImportError, OSError) as exc:
            raise NativeStoreUnavailable(
                "Windows MachineGuid is unavailable"
            ) from exc
        value = str(value).strip()
        if value:
            return f"windows:{value}".encode("utf-8")
    elif platform_name == "darwin":
        try:
            result = subprocess.run(
                ["/usr/sbin/ioreg", "-rd1", "-c", "IOPlatformExpertDevice"],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise NativeStoreUnavailable(
                "macOS platform UUID is unavailable"
            ) from exc
        match = re.search(r'"IOPlatformUUID"\s*=\s*"([^"]+)"', result.stdout)
        if result.returncode == 0 and match:
            return f"macos:{match.group(1)}".encode("utf-8")
    else:
        for path in (Path("/etc/machine-id"), Path("/var/lib/dbus/machine-id")):
            try:
                value = path.read_text(encoding="ascii").strip()
            except (OSError, UnicodeError):
                continue
            if value and value != "uninitialized":
                return f"linux:{value}".encode("ascii")
    raise NativeStoreUnavailable("a stable machine identifier is unavailable")


def _hkdf_sha256(seed, machine_id, length=32):
    salt = hashlib.sha256(b"abk-cli/machine/v1\0" + machine_id).digest()
    pseudorandom_key = hmac.new(salt, seed, hashlib.sha256).digest()
    output = b""
    previous = b""
    counter = 1
    while len(output) < length:
        previous = hmac.new(
            pseudorandom_key,
            previous + _HKDF_INFO + bytes((counter,)),
            hashlib.sha256,
        ).digest()
        output += previous
        counter += 1
    return output[:length]


def _credential_aad(native_cleanup_pending=False):
    state = b"pending" if native_cleanup_pending else b"clean"
    return _AAD + b"|native-cleanup=" + state


def _encrypt_aes_gcm(key, nonce, plaintext, aad=None):
    aad = _credential_aad() if aad is None else aad
    if _AES_BACKEND == "cryptography":
        encrypted = AESGCM(key).encrypt(nonce, plaintext, aad)
        return encrypted[:-_TAG_SIZE], encrypted[-_TAG_SIZE:]
    if _AES_BACKEND in {"pycryptodome", "pycryptodomex"}:
        cipher = _PYAES.new(key, _PYAES.MODE_GCM, nonce=nonce, mac_len=_TAG_SIZE)
        cipher.update(aad)
        return cipher.encrypt_and_digest(plaintext)
    raise CredentialStoreError(
        "persistent credentials require cryptography or PyCryptodome AES-GCM"
    )


def _decrypt_aes_gcm(key, nonce, ciphertext, tag, aad=None):
    aad = _credential_aad() if aad is None else aad
    try:
        if _AES_BACKEND == "cryptography":
            return AESGCM(key).decrypt(nonce, ciphertext + tag, aad)
        if _AES_BACKEND in {"pycryptodome", "pycryptodomex"}:
            cipher = _PYAES.new(
                key,
                _PYAES.MODE_GCM,
                nonce=nonce,
                mac_len=_TAG_SIZE,
            )
            cipher.update(aad)
            return cipher.decrypt_and_verify(ciphertext, tag)
    except Exception as exc:
        raise CredentialCorrupt(
            "the encrypted GitHub credential failed authentication"
        ) from exc
    raise CredentialStoreError(
        "persistent credentials require cryptography or PyCryptodome AES-GCM"
    )


def aes_gcm_self_test():
    """Exercise the AES-GCM implementation included in a source or frozen CLI."""
    key = hashlib.sha256(b"abk-cli/credential-self-test/key").digest()
    nonce = hashlib.sha256(b"abk-cli/credential-self-test/nonce").digest()[:12]
    plaintext = b"abk-cli-credential-self-test"
    ciphertext, tag = _encrypt_aes_gcm(key, nonce, plaintext)
    if _decrypt_aes_gcm(key, nonce, ciphertext, tag) != plaintext:
        raise CredentialStoreError("credential AES-GCM self-test failed")
    return _AES_BACKEND


def _encode(value):
    return base64.b64encode(value).decode("ascii")


def _decode(value, *, name, expected_size=None, maximum_size=None):
    if not isinstance(value, str):
        raise CredentialCorrupt(f"credential field {name} is invalid")
    try:
        decoded = base64.b64decode(value, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise CredentialCorrupt(
            f"credential field {name} is not valid Base64"
        ) from exc
    if expected_size is not None and len(decoded) != expected_size:
        raise CredentialCorrupt(f"credential field {name} has an invalid size")
    if maximum_size is not None and len(decoded) > maximum_size:
        raise CredentialCorrupt(f"credential field {name} is too large")
    return decoded


class CredentialStore:
    def __init__(
        self,
        directory,
        *,
        native_backend_factory=create_native_backend,
        machine_id_provider=_machine_identifier,
    ):
        self.directory = Path(directory)
        self.path = self.directory / CREDENTIAL_FILE_NAME
        self.pending_path = self.directory / CREDENTIAL_PENDING_FILE_NAME
        self._native_backend_factory = native_backend_factory
        self._machine_id_provider = machine_id_provider

    def _read_metadata(self):
        try:
            try:
                file_status = self.path.lstat()
            except FileNotFoundError:
                return None
            if not stat.S_ISREG(file_status.st_mode):
                raise CredentialCorrupt(
                    "credential metadata is not a regular file"
                )
            if os.name != "nt":
                self.directory.chmod(0o700)
                self.path.chmod(0o600)
            if file_status.st_size > MAX_CREDENTIAL_FILE_SIZE:
                raise CredentialCorrupt("credential metadata is too large")
            metadata = json.loads(self.path.read_text(encoding="utf-8"))
        except CredentialCorrupt:
            raise
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise CredentialCorrupt("credential metadata is unreadable") from exc
        if not isinstance(metadata, dict):
            raise CredentialCorrupt("credential metadata is invalid")
        if metadata.get("version") != CREDENTIAL_FORMAT_VERSION:
            raise CredentialCorrupt("credential metadata version is unsupported")
        return metadata

    def _read_pending_document(self):
        try:
            try:
                file_status = self.pending_path.lstat()
            except FileNotFoundError:
                return None
            if not stat.S_ISREG(file_status.st_mode):
                raise CredentialCorrupt(
                    "native credential transaction marker is not a regular file"
                )
            if os.name != "nt":
                self.directory.chmod(0o700)
                self.pending_path.chmod(0o600)
            if file_status.st_size > MAX_CREDENTIAL_FILE_SIZE:
                raise CredentialCorrupt(
                    "native credential transaction marker is too large"
                )
            metadata = json.loads(
                self.pending_path.read_text(encoding="utf-8")
            )
        except CredentialCorrupt:
            raise
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise CredentialCorrupt(
                "native credential transaction marker is unreadable"
            ) from exc
        return metadata

    def _read_pending_metadata(self):
        metadata = self._read_pending_document()
        if metadata is not None:
            self._validate_native_transaction(metadata)
        return metadata

    def _write_json_file(self, path, metadata, *, prefix):
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        if os.name != "nt":
            self.directory.chmod(0o700)
        payload = json.dumps(
            metadata,
            indent=2,
            ensure_ascii=True,
            sort_keys=True,
        ) + "\n"
        fd, temporary_name = tempfile.mkstemp(
            prefix=prefix,
            suffix=".tmp",
            dir=self.directory,
        )
        try:
            if os.name != "nt":
                os.fchmod(fd, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                fd = None
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_name, path)
        finally:
            if fd is not None:
                os.close(fd)
            try:
                Path(temporary_name).unlink()
            except FileNotFoundError:
                pass

    def _write_metadata(self, metadata):
        self._write_json_file(
            self.path,
            metadata,
            prefix=".credentials-",
        )

    def _write_pending_metadata(self, metadata):
        self._write_json_file(
            self.pending_path,
            metadata,
            prefix=".credential-pending-",
        )

    def _remove_metadata(self):
        try:
            self.path.unlink()
            return True
        except FileNotFoundError:
            return False
        except OSError as exc:
            raise CredentialStoreError(
                "credential metadata could not be removed"
            ) from exc

    def _remove_pending_metadata(self):
        try:
            self.pending_path.unlink()
            return True
        except FileNotFoundError:
            return False
        except OSError as exc:
            raise CredentialStoreError(
                "native credential transaction marker could not be removed"
            ) from exc

    def _fallback_metadata(self, token, *, native_cleanup_pending=False):
        if _AES_BACKEND is None:
            raise CredentialStoreError(
                "persistent credentials require an AES-GCM backend"
            )
        encoded_token = token.encode("utf-8")
        if not encoded_token or len(encoded_token) > MAX_TOKEN_SIZE:
            raise CredentialStoreError("the GitHub credential has an invalid size")
        machine_id = self._machine_id_provider()
        if not isinstance(machine_id, bytes) or not machine_id:
            raise NativeStoreUnavailable("a stable machine identifier is unavailable")
        seed = secrets.token_bytes(_SEED_SIZE)
        nonce = secrets.token_bytes(_NONCE_SIZE)
        key = _hkdf_sha256(seed, machine_id)
        ciphertext, tag = _encrypt_aes_gcm(
            key,
            nonce,
            encoded_token,
            _credential_aad(native_cleanup_pending),
        )
        return {
            "version": CREDENTIAL_FORMAT_VERSION,
            "backend": FALLBACK_BACKEND,
            "native_cleanup_pending": native_cleanup_pending,
            "kdf": "hkdf-sha256",
            "cipher": "aes-256-gcm",
            "seed": _encode(seed),
            "nonce": _encode(nonce),
            "ciphertext": _encode(ciphertext),
            "tag": _encode(tag),
        }

    def _decrypt_fallback(self, metadata):
        expected = {
            "version",
            "backend",
            "native_cleanup_pending",
            "kdf",
            "cipher",
            "seed",
            "nonce",
            "ciphertext",
            "tag",
        }
        if set(metadata) != expected:
            raise CredentialCorrupt("credential metadata fields are invalid")
        if (
            metadata.get("backend") != FALLBACK_BACKEND
            or not isinstance(metadata.get("native_cleanup_pending"), bool)
            or metadata.get("kdf") != "hkdf-sha256"
            or metadata.get("cipher") != "aes-256-gcm"
        ):
            raise CredentialCorrupt("credential algorithms are unsupported")
        seed = _decode(metadata["seed"], name="seed", expected_size=_SEED_SIZE)
        nonce = _decode(metadata["nonce"], name="nonce", expected_size=_NONCE_SIZE)
        ciphertext = _decode(
            metadata["ciphertext"],
            name="ciphertext",
            maximum_size=MAX_TOKEN_SIZE,
        )
        tag = _decode(metadata["tag"], name="tag", expected_size=_TAG_SIZE)
        machine_id = self._machine_id_provider()
        if not isinstance(machine_id, bytes) or not machine_id:
            raise CredentialCorrupt("the machine identifier is unavailable")
        plaintext = _decrypt_aes_gcm(
            _hkdf_sha256(seed, machine_id),
            nonce,
            ciphertext,
            tag,
            _credential_aad(metadata["native_cleanup_pending"]),
        )
        try:
            token = plaintext.decode("utf-8")
        except UnicodeError as exc:
            raise CredentialCorrupt("the decrypted GitHub credential is invalid") from exc
        if not token or len(plaintext) > MAX_TOKEN_SIZE:
            raise CredentialCorrupt("the decrypted GitHub credential has an invalid size")
        return token

    def read(self, *, include_native=True):
        if self._read_pending_metadata() is not None:
            raise NativeRollbackError("native credential cleanup is pending")
        metadata = self._read_metadata()
        if metadata is None:
            return None
        backend_name = metadata.get("backend")
        if backend_name == FALLBACK_BACKEND:
            token = self._decrypt_fallback(metadata)
            if metadata["native_cleanup_pending"]:
                raise NativeRollbackError(
                    "native credential cleanup is pending"
                )
            if include_native:
                self._upgrade_fallback_to_native(token)
            return token
        if backend_name == "native":
            expected = {"version", "backend", "provider", "service", "account"}
            if set(metadata) != expected:
                raise CredentialCorrupt("native credential metadata is invalid")
            if (
                metadata.get("service") != CREDENTIAL_SERVICE
                or metadata.get("account") != CREDENTIAL_ACCOUNT
            ):
                raise CredentialCorrupt("native credential identity is invalid")
            if not include_native:
                return None
            backend = self._native_backend_factory()
            if metadata.get("provider") != backend.name:
                raise NativeStoreUnavailable(
                    "the configured native credential provider is unavailable"
                )
            return backend.get()
        raise CredentialCorrupt("credential backend is unsupported")

    def _native_metadata(self, backend):
        return {
            "version": CREDENTIAL_FORMAT_VERSION,
            "backend": "native",
            "provider": backend.name,
            "service": CREDENTIAL_SERVICE,
            "account": CREDENTIAL_ACCOUNT,
        }

    def _native_transaction_metadata(self, backend):
        return {
            "version": CREDENTIAL_FORMAT_VERSION,
            "kind": "native-credential-transaction",
            "state": "cleanup-required",
            "provider": backend.name,
            "service": CREDENTIAL_SERVICE,
            "account": CREDENTIAL_ACCOUNT,
        }

    def _validate_native_transaction(self, metadata):
        expected = {
            "version",
            "kind",
            "state",
            "provider",
            "service",
            "account",
        }
        if (
            not isinstance(metadata, dict)
            or set(metadata) != expected
            or metadata.get("version") != CREDENTIAL_FORMAT_VERSION
            or metadata.get("kind") != "native-credential-transaction"
            or metadata.get("state") != "cleanup-required"
            or not isinstance(metadata.get("provider"), str)
            or not metadata["provider"]
            or metadata.get("service") != CREDENTIAL_SERVICE
            or metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            raise CredentialCorrupt(
                "native credential transaction marker is invalid"
            )

    def _restore_native_credential(self, backend, previous_token):
        try:
            if previous_token is None:
                backend.delete()
            else:
                backend.set(previous_token)
            restored = backend.get()
        except CredentialStoreError as exc:
            raise NativeRollbackError(
                f"{backend.name} could not restore the previous GitHub credential"
            ) from exc
        if previous_token is None:
            restored_ok = restored is None
        else:
            restored_ok = (
                isinstance(restored, str)
                and hmac.compare_digest(restored, previous_token)
            )
        if not restored_ok:
            raise NativeRollbackError(
                f"{backend.name} did not verify the restored GitHub credential"
            )

    def _replace_native_credential(self, backend, token, previous_token):
        try:
            backend.set(token)
            stored = backend.get()
            if not isinstance(stored, str) or not hmac.compare_digest(stored, token):
                raise NativeStoreError(
                    f"{backend.name} did not verify the stored GitHub credential"
                )
        except CredentialStoreError:
            try:
                self._restore_native_credential(backend, previous_token)
            except NativeRollbackError as rollback_exc:
                raise NativeRollbackError(
                    f"{backend.name} failed to store the GitHub credential and "
                    "could not restore its previous value"
                ) from rollback_exc
            raise

    def _restore_primary_metadata(self, existing_metadata):
        try:
            current_metadata = self._read_metadata()
        except CredentialStoreError:
            current_metadata = object()
        if current_metadata == existing_metadata:
            return
        if existing_metadata is None:
            self._remove_metadata()
        else:
            self._write_metadata(existing_metadata)
        if self._read_metadata() != existing_metadata:
            raise CredentialStoreError(
                "previous credential metadata could not be restored"
            )

    def _store_native_transaction(
        self,
        backend,
        token,
        previous_token,
        existing_metadata,
    ):
        pending_metadata = self._native_transaction_metadata(backend)
        try:
            self._write_pending_metadata(pending_metadata)
            if self._read_pending_metadata() != pending_metadata:
                raise CredentialStoreError(
                    "native credential transaction marker failed verification"
                )
        except Exception as exc:
            raise CredentialStoreError(
                "native credential transaction could not be started"
            ) from exc

        try:
            self._replace_native_credential(backend, token, previous_token)
        except NativeRollbackError:
            # The write-ahead marker remains authoritative and prevents any
            # later process from reading an uncertain native credential.
            raise
        except CredentialStoreError:
            try:
                self._remove_pending_metadata()
            except CredentialStoreError as cleanup_exc:
                raise NativeRollbackError(
                    "native credential rollback succeeded, but its transaction "
                    "marker could not be cleared"
                ) from cleanup_exc
            raise

        try:
            self._write_metadata(self._native_metadata(backend))
        except Exception as exc:
            try:
                self._restore_native_credential(backend, previous_token)
                self._restore_primary_metadata(existing_metadata)
                self._remove_pending_metadata()
            except Exception as rollback_exc:
                raise NativeRollbackError(
                    "native credential metadata failed and the previous state "
                    "could not be restored"
                ) from rollback_exc
            raise CredentialStoreError(
                "native credential metadata could not be persisted"
            ) from exc

        try:
            self._remove_pending_metadata()
        except CredentialStoreError as exc:
            raise NativeRollbackError(
                "native credential was stored, but its transaction marker "
                "could not be cleared"
            ) from exc

    def _upgrade_fallback_to_native(self, token):
        existing_metadata = self._read_metadata()
        try:
            backend = self._native_backend_factory()
            previous_token = backend.get()
        except CredentialStoreError:
            return False
        if previous_token is not None and not isinstance(previous_token, str):
            return False
        try:
            self._store_native_transaction(
                backend,
                token,
                previous_token,
                existing_metadata,
            )
        except NativeRollbackError:
            raise
        except CredentialStoreError:
            # The authenticated fallback remains authoritative until every
            # native write and metadata step succeeds.
            return False
        return True

    def store(self, token, *, before_fallback=None):
        if not isinstance(token, str) or not token:
            raise CredentialStoreError("the GitHub credential is empty")
        if len(token.encode("utf-8")) > MAX_TOKEN_SIZE:
            raise CredentialStoreError("the GitHub credential is too large")
        if self._read_pending_metadata() is not None:
            raise NativeRollbackError("native credential cleanup is pending")
        existing_metadata = self._read_metadata()
        if existing_metadata is not None:
            existing_backend = existing_metadata.get("backend")
            if existing_backend == FALLBACK_BACKEND:
                self._decrypt_fallback(existing_metadata)
                if existing_metadata["native_cleanup_pending"]:
                    raise NativeRollbackError(
                        "native credential cleanup is pending"
                    )
            elif existing_backend == "native":
                self.read(include_native=False)
            else:
                raise CredentialCorrupt("credential backend is unsupported")
        try:
            backend = self._native_backend_factory()
            previous_token = backend.get()
        except NativeStoreUnavailable:
            if (
                existing_metadata is not None
                and existing_metadata.get("backend") != FALLBACK_BACKEND
            ):
                raise
            if before_fallback is not None:
                before_fallback()
            metadata = self._fallback_metadata(token)
            try:
                self._write_metadata(metadata)
            except Exception as exc:
                raise CredentialStoreError(
                    "encrypted credential metadata could not be persisted"
                ) from exc
            if not hmac.compare_digest(self._decrypt_fallback(metadata), token):
                raise CredentialStoreError(
                    "the encrypted GitHub credential failed verification"
                )
            return StoreResult(
                backend=FALLBACK_BACKEND,
                degraded=True,
                location=str(self.path),
            )
        if previous_token is not None and not isinstance(previous_token, str):
            raise NativeStoreError(
                f"{backend.name} returned an invalid GitHub credential"
            )
        if (
            existing_metadata is not None
            and existing_metadata.get("backend") == "native"
            and existing_metadata.get("provider") != backend.name
        ):
            raise NativeStoreUnavailable(
                "the configured native credential provider is unavailable"
            )
        self._store_native_transaction(
            backend,
            token,
            previous_token,
            existing_metadata,
        )
        return StoreResult(
            backend=backend.name,
            degraded=False,
            location=backend.name,
        )

    def delete(self):
        try:
            pending_metadata = self._read_pending_document()
        except CredentialCorrupt as exc:
            raise CredentialCorrupt(
                "native credential transaction metadata cannot be safely reset"
            ) from exc
        if pending_metadata is not None:
            try:
                self._validate_native_transaction(pending_metadata)
            except CredentialCorrupt as exc:
                return self._delete_corrupt_pending(pending_metadata, exc)
            try:
                backend = self._native_backend_factory()
            except NativeStoreUnavailable:
                # Keep the marker until the recorded provider is available.
                raise
            if pending_metadata.get("provider") != backend.name:
                raise NativeStoreUnavailable(
                    "the pending native credential provider is unavailable"
                )
            native_removed = backend.delete()
            remaining = backend.get()
            if remaining is not None:
                raise NativeStoreError(
                    f"{backend.name} did not verify GitHub credential deletion"
                )
            marker_removed = self._remove_metadata()
            pending_removed = self._remove_pending_metadata()
            return native_removed or marker_removed or pending_removed
        try:
            metadata = self._read_metadata()
        except CredentialCorrupt:
            return self._delete_unusable_metadata()
        if metadata is None:
            try:
                return self._native_backend_factory().delete()
            except NativeStoreUnavailable:
                return False
        if metadata.get("backend") == FALLBACK_BACKEND:
            try:
                self._decrypt_fallback(metadata)
            except CredentialCorrupt:
                return self._delete_unusable_metadata()
            try:
                native_removed = self._native_backend_factory().delete()
            except NativeStoreUnavailable:
                if metadata["native_cleanup_pending"]:
                    raise NativeStoreUnavailable(
                        "native credential cleanup is still pending"
                    )
                native_removed = False
            marker_removed = self._remove_metadata()
            return native_removed or marker_removed
        expected = {"version", "backend", "provider", "service", "account"}
        if (
            metadata.get("backend") != "native"
            or set(metadata) != expected
            or metadata.get("service") != CREDENTIAL_SERVICE
            or metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            return self._delete_unusable_metadata()
        try:
            backend = self._native_backend_factory()
        except NativeStoreUnavailable:
            # A valid native marker identifies a credential that still needs
            # cleanup. Keep it so a later logout can retry the fixed account.
            raise
        if metadata.get("provider") != backend.name:
            return self._delete_unusable_metadata(backend=backend)
        removed = backend.delete()
        marker_removed = self._remove_metadata()
        return removed or marker_removed

    def _delete_corrupt_pending(self, metadata, marker_error):
        """Reset a damaged transaction marker only after verified cleanup."""
        if (
            not isinstance(metadata, dict)
            or not isinstance(metadata.get("provider"), str)
            or not metadata["provider"]
            or metadata.get("service") != CREDENTIAL_SERVICE
            or metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            raise CredentialCorrupt(
                "native credential transaction metadata cannot identify its "
                "provider safely"
            ) from marker_error
        try:
            backend = self._native_backend_factory()
            if metadata["provider"] != backend.name:
                raise NativeStoreUnavailable(
                    "the damaged native credential provider is unavailable"
                )
            native_removed = backend.delete()
            if backend.get() is not None:
                raise NativeStoreError(
                    f"{backend.name} did not verify GitHub credential deletion"
                )
        except CredentialStoreError as exc:
            raise CredentialCorrupt(
                "native credential transaction metadata is damaged and cleanup "
                "could not be verified"
            ) from exc
        try:
            marker_removed = self._remove_metadata()
            pending_removed = self._remove_pending_metadata()
        except CredentialStoreError as exc:
            raise CredentialCorrupt(
                "native credential cleanup succeeded, but damaged transaction "
                "metadata could not be reset"
            ) from exc
        return native_removed or marker_removed or pending_removed

    def _delete_unusable_metadata(self, *, backend=None):
        """Reset unreadable metadata while best-effort cleaning fixed native state."""
        native_removed = False
        cleanup_error = None
        try:
            backend = backend or self._native_backend_factory()
            native_removed = backend.delete()
        except NativeStoreUnavailable:
            # No usable native provider means there is no cleanup operation we
            # can perform. Removing the unusable marker still resets the CLI.
            pass
        except CredentialStoreError as exc:
            cleanup_error = exc

        marker_removed = self._remove_metadata()
        if cleanup_error is not None:
            raise CredentialCorrupt(
                "credential metadata was reset, but native cleanup could not "
                "be verified"
            ) from cleanup_error
        return native_removed or marker_removed
