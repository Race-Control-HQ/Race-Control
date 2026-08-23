"""
attest.py
---------
Apple App Attest verification for RaceControl.

This is how the published iOS app authenticates to the backend without a
user-visible API key: the app proves, using a hardware-backed key attested by
Apple, that each request comes from a genuine, unmodified copy of *our* app on a
real Apple device. There is no shared secret shipped in the binary.

Flow:
  1. App requests a one-time challenge:            GET  /attest/challenge
  2. App attests a fresh key with Apple, sends it: POST /attest/verify   -> JWT
  3. App calls the API with `Authorization: Bearer <JWT>`.
  4. When the JWT expires, the app signs a new challenge with the same key
     (an "assertion") to mint a fresh JWT:          POST /attest/token   -> JWT

The heavy cryptographic verification (certificate chain to Apple's root, nonce,
key-id binding) is done by the well-tested `pyattest` library. This module adds
the parts pyattest leaves to the caller: single-use challenges, the signing
counter (replay protection), the App ID (RP) check, a persistent key store, and
short-lived JWT issuance.
"""

from __future__ import annotations

import base64
import hashlib
import logging
import os
import secrets
import sqlite3
import struct
import threading
import time
from dataclasses import dataclass
from typing import Optional

import jwt
from cbor2 import loads as cbor_loads
from cryptography.hazmat.primitives.serialization import (
    Encoding,
    PublicFormat,
    load_pem_public_key,
)
from cryptography.x509 import load_der_x509_certificate

from pyattest.assertion import Assertion
from pyattest.attestation import Attestation
from pyattest.configs.apple import AppleConfig

log = logging.getLogger("racecontrol")


# --------------------------------------------------------------------------- #
#  Configuration (from environment)
# --------------------------------------------------------------------------- #
@dataclass
class AttestConfig:
    enabled: bool
    app_id: str            # "<TEAMID>.<bundle id>"
    production: bool       # False = Apple's "development" attestation environment
    jwt_secret: str
    jwt_ttl: int
    root_ca: Optional[bytes]  # override Apple's bundled root (tests only)
    db_path: str

    @classmethod
    def from_env(cls) -> "AttestConfig":
        enabled = os.environ.get("APP_ATTEST_ENABLED", "").lower() in ("1", "true", "yes")
        team = os.environ.get("APPLE_TEAM_ID", "").strip()
        bundle = os.environ.get("APP_BUNDLE_ID", "").strip()
        app_id = f"{team}.{bundle}" if team and bundle else ""
        production = os.environ.get("APP_ATTEST_PRODUCTION", "").lower() in ("1", "true", "yes")
        jwt_secret = os.environ.get("JWT_SECRET", "").strip()
        jwt_ttl = int(os.environ.get("JWT_TTL_SECONDS", 24 * 60 * 60))
        cache_dir = os.environ.get("FASTF1_CACHE", ".")
        db_path = os.environ.get("ATTEST_DB", os.path.join(os.path.dirname(cache_dir) or ".", "attest_keys.sqlite"))

        if enabled and (not app_id or not jwt_secret):
            raise RuntimeError(
                "APP_ATTEST_ENABLED is set but APPLE_TEAM_ID / APP_BUNDLE_ID / "
                "JWT_SECRET are missing."
            )
        return cls(enabled, app_id, production, jwt_secret, jwt_ttl, None, db_path)


# --------------------------------------------------------------------------- #
#  Persistent key store (survives restarts / redeploys)
# --------------------------------------------------------------------------- #
class KeyStore:
    """Maps an attested key id -> (public key PEM, last signing counter)."""

    def __init__(self, path: str):
        # check_same_thread=False + a lock: FastAPI may touch this from threads.
        self._db = sqlite3.connect(path, check_same_thread=False)
        self._lock = threading.Lock()
        self._db.execute(
            "CREATE TABLE IF NOT EXISTS keys "
            "(key_id TEXT PRIMARY KEY, public_key_pem TEXT NOT NULL, counter INTEGER NOT NULL)"
        )
        self._db.commit()

    def save(self, key_id: str, public_key_pem: str, counter: int) -> None:
        with self._lock:
            self._db.execute(
                "INSERT OR REPLACE INTO keys VALUES (?, ?, ?)",
                (key_id, public_key_pem, counter),
            )
            self._db.commit()

    def get(self, key_id: str) -> Optional[tuple[str, int]]:
        with self._lock:
            row = self._db.execute(
                "SELECT public_key_pem, counter FROM keys WHERE key_id = ?", (key_id,)
            ).fetchone()
        return (row[0], row[1]) if row else None

    def update_counter(self, key_id: str, counter: int) -> None:
        with self._lock:
            self._db.execute(
                "UPDATE keys SET counter = ? WHERE key_id = ?", (counter, key_id)
            )
            self._db.commit()

    def count(self) -> int:
        with self._lock:
            return self._db.execute("SELECT COUNT(*) FROM keys").fetchone()[0]


# --------------------------------------------------------------------------- #
#  One-time challenge store (in-memory; short-lived)
# --------------------------------------------------------------------------- #
class ChallengeStore:
    def __init__(self, ttl_seconds: int = 300):
        self._ttl = ttl_seconds
        self._items: dict[str, float] = {}
        self._lock = threading.Lock()

    def issue(self) -> str:
        challenge = base64.b64encode(secrets.token_bytes(32)).decode()
        with self._lock:
            self._prune()
            self._items[challenge] = time.time() + self._ttl
        return challenge

    def consume(self, challenge: str) -> bool:
        """True if the challenge was valid and unused; single-use."""
        with self._lock:
            expiry = self._items.pop(challenge, None)
        return expiry is not None and expiry >= time.time()

    def _prune(self) -> None:
        now = time.time()
        expired = [k for k, exp in self._items.items() if exp < now]
        for k in expired:
            self._items.pop(k, None)


# --------------------------------------------------------------------------- #
#  Verifier
# --------------------------------------------------------------------------- #
class AttestError(Exception):
    """Raised for any attestation/assertion failure (mapped to HTTP 401)."""


class AppAttestVerifier:
    def __init__(self, config: AttestConfig):
        self.config = config
        self.keys = KeyStore(config.db_path)
        self.challenges = ChallengeStore()

    # -- Challenge -------------------------------------------------------- #
    def new_challenge(self) -> str:
        return self.challenges.issue()

    # -- Diagnostics (no secrets) ---------------------------------------- #
    def status(self) -> dict:
        return {
            "enabled": True,
            "appId": self.config.app_id,
            "production": self.config.production,
            "registeredKeys": self.keys.count(),
            "pendingChallenges": len(self.challenges._items),
        }

    # -- Attestation (establish a new key) ------------------------------- #
    async def verify_attestation(self, key_id_b64: str, attestation_b64: str,
                                 challenge: str) -> str:
        if not self.challenges.consume(challenge):
            raise AttestError("Invalid or expired challenge")
        try:
            key_id = base64.b64decode(key_id_b64)
            attestation = base64.b64decode(attestation_b64)
            challenge_bytes = base64.b64decode(challenge)
        except Exception as exc:  # noqa: BLE001
            raise AttestError("Malformed attestation payload") from exc

        cfg = AppleConfig(
            key_id=key_id,
            app_id=self.config.app_id,
            production=self.config.production,
            root_ca=self.config.root_ca,
        )
        try:
            att = Attestation(attestation, challenge_bytes, cfg)
            await att.verify()  # cert chain, nonce, key-id, app-id, aaguid
        except Exception as exc:  # noqa: BLE001
            raise AttestError(f"Attestation verification failed: {exc}") from exc

        # Extract the attested public key (from the leaf cert) and initial
        # counter (from authData) to store for future assertion checks.
        try:
            decoded = cbor_loads(attestation)
            leaf = load_der_x509_certificate(decoded["attStmt"]["x5c"][0])
            public_key_pem = leaf.public_key().public_bytes(
                Encoding.PEM, PublicFormat.SubjectPublicKeyInfo
            ).decode()
            auth_data = decoded["authData"]
            self._check_rp_id(auth_data)
            counter = struct.unpack("!I", auth_data[33:37])[0]
        except AttestError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise AttestError("Could not parse attestation") from exc

        self.keys.save(key_id_b64, public_key_pem, counter)
        return self._issue_jwt(key_id_b64)

    # -- Assertion (prove liveness, mint a fresh token) ------------------ #
    async def verify_assertion(self, key_id_b64: str, assertion_b64: str,
                               challenge: str) -> str:
        if not self.challenges.consume(challenge):
            raise AttestError("Invalid or expired challenge")
        record = self.keys.get(key_id_b64)
        if record is None:
            # Server has no record of this key (e.g. store was wiped). The app
            # should fall back to a full re-attestation.
            raise AttestError("Unknown key; re-attestation required")
        public_key_pem, stored_counter = record

        try:
            assertion = base64.b64decode(assertion_b64)
            challenge_bytes = base64.b64decode(challenge)
            expected_hash = hashlib.sha256(challenge_bytes).digest()
            public_key = load_pem_public_key(public_key_pem.encode())
        except Exception as exc:  # noqa: BLE001
            raise AttestError("Malformed assertion payload") from exc

        cfg = AppleConfig(key_id=b"", app_id=self.config.app_id,
                          production=self.config.production, root_ca=self.config.root_ca)
        try:
            asrt = Assertion(assertion, expected_hash, public_key, cfg)
            asrt.verify()  # verifies the signature over the nonce
        except Exception as exc:  # noqa: BLE001
            raise AttestError(f"Assertion verification failed: {exc}") from exc

        # pyattest verifies the signature but leaves replay-protection to us:
        # enforce the RP id and a strictly-increasing signing counter.
        decoded = cbor_loads(assertion)
        auth_data = decoded["authenticatorData"]
        self._check_rp_id(auth_data)
        counter = struct.unpack("!I", auth_data[33:37])[0]
        if counter <= stored_counter:
            raise AttestError("Assertion counter did not increase (possible replay)")

        self.keys.update_counter(key_id_b64, counter)
        return self._issue_jwt(key_id_b64)

    # -- JWT -------------------------------------------------------------- #
    def _issue_jwt(self, key_id_b64: str) -> str:
        now = int(time.time())
        payload = {"sub": key_id_b64, "iat": now, "exp": now + self.config.jwt_ttl}
        return jwt.encode(payload, self.config.jwt_secret, algorithm="HS256")

    def verify_app_token(self, token: str) -> bool:
        if not token:
            return False
        try:
            jwt.decode(token, self.config.jwt_secret, algorithms=["HS256"])
            return True
        except jwt.PyJWTError:
            return False

    # -- Helpers ---------------------------------------------------------- #
    def _check_rp_id(self, auth_data: bytes) -> None:
        expected = hashlib.sha256(self.config.app_id.encode()).digest()
        if auth_data[:32] != expected:
            raise AttestError("RP ID (App ID) mismatch")
