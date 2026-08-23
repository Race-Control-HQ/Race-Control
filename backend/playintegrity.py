"""
playintegrity.py
-----------------
Google Play Integrity verification for RaceControl.

This is the Android counterpart of attest.py: how the published Android app
authenticates to the backend without a user-visible API key. The app asks
Google Play Services to vouch for it, sends the resulting token here, and we
ask Google's Play Integrity API whether that vouching is genuine. There is no
shared secret shipped in the APK.

Flow:
  1. App requests a one-time nonce:                  GET  /playintegrity/challenge
  2. App calls IntegrityManager.requestIntegrityToken(nonce), gets an opaque
     token from Google Play services, sends it here: POST /playintegrity/verify -> JWT
  3. App calls the API with `Authorization: Bearer <JWT>`.
  4. When the JWT expires, the app repeats step 1-2 for a fresh one. Unlike App
     Attest there is no persistent per-device key to renew via a lightweight
     assertion: a fresh integrity token is requested each time, which is why
     the JWT TTL should be generous (hours, not minutes) to stay within Play
     Integrity's per-app request quota.

Verification, unlike attest.py, is a single call to Google rather than local
cryptography: we send the opaque token to Google's decodeIntegrityToken
endpoint and Google hands back signed verdicts (is this really our app, is it
unmodified, is the device genuine, was this token requested recently). We
still have to check those verdicts ourselves: Google will happily decode a
token and tell you the truth even if that truth is "this is a repackaged
APK on a rooted emulator".

Requires, from the RaceControl Android app's Play Console + Google Cloud
project (see .env.example for the full list): a service account with the
Play Integrity API enabled and granted access to the linked app, the app's
package name, and (recommended) its release signing certificate digest(s).
None of that can be provisioned from here; this module only consumes it.
"""

from __future__ import annotations

import json
import logging
import os
import time
from dataclasses import dataclass, field
from typing import Optional

import jwt
import requests
from google.auth.transport.requests import Request as GoogleAuthRequest
from google.oauth2 import service_account

from attest import ChallengeStore  # single-use nonce store; identical shape to App Attest's

log = logging.getLogger("racecontrol")

_DECODE_URL = "https://playintegrity.googleapis.com/v1/{package}:decodeIntegrityToken"
_SCOPES = ["https://www.googleapis.com/auth/playintegrity"]

# Verdicts we accept for deviceIntegrity.deviceRecognitionVerdict, weakest to
# strongest. Real Play Store installs on real hardware report at least
# MEETS_DEVICE_INTEGRITY; MEETS_BASIC_INTEGRITY is a lower bar worth allowing
# only while testing on an emulator/rooted dev device before release.
_DEVICE_VERDICT_STRENGTH = {
    "MEETS_BASIC_INTEGRITY": 1,
    "MEETS_DEVICE_INTEGRITY": 2,
    "MEETS_STRONG_INTEGRITY": 3,
}


# --------------------------------------------------------------------------- #
#  Configuration (from environment)
# --------------------------------------------------------------------------- #
@dataclass
class PlayIntegrityConfig:
    enabled: bool
    package_name: str
    cloud_project_number: str
    credentials_info: Optional[dict]  # parsed service-account JSON
    jwt_secret: str
    jwt_ttl: int
    allow_unevaluated: bool          # accept UNEVALUATED app/device verdicts (pre-Play testing)
    min_device_verdict: str          # weakest deviceRecognitionVerdict entry accepted
    signing_cert_sha256: tuple[str, ...] = field(default_factory=tuple)  # optional allowlist
    max_token_age_seconds: int = 300  # reject a decoded token whose requestDetails is stale

    @classmethod
    def from_env(cls) -> "PlayIntegrityConfig":
        enabled = os.environ.get("PLAY_INTEGRITY_ENABLED", "").lower() in ("1", "true", "yes")
        package_name = os.environ.get("ANDROID_PACKAGE_NAME", "").strip()
        cloud_project_number = os.environ.get("GOOGLE_CLOUD_PROJECT_NUMBER", "").strip()
        jwt_secret = os.environ.get("JWT_SECRET", "").strip()
        jwt_ttl = int(os.environ.get("JWT_TTL_SECONDS", 24 * 60 * 60))
        allow_unevaluated = os.environ.get(
            "PLAY_INTEGRITY_ALLOW_UNEVALUATED", ""
        ).lower() in ("1", "true", "yes")
        min_device_verdict = os.environ.get(
            "PLAY_INTEGRITY_MIN_DEVICE_VERDICT", "MEETS_DEVICE_INTEGRITY"
        ).strip().upper()
        cert_digests = tuple(
            d.strip() for d in os.environ.get("ANDROID_SIGNING_CERT_SHA256", "").split(",") if d.strip()
        )

        creds_json = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS_JSON", "").strip()
        creds_path = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", "").strip()
        credentials_info: Optional[dict] = None
        if creds_json:
            try:
                credentials_info = json.loads(creds_json)
            except json.JSONDecodeError as exc:
                raise RuntimeError(
                    "GOOGLE_APPLICATION_CREDENTIALS_JSON is set but is not valid JSON"
                ) from exc
        elif creds_path:
            with open(creds_path, "r", encoding="utf-8") as fh:
                credentials_info = json.load(fh)

        if enabled:
            missing = [
                name for name, val in (
                    ("ANDROID_PACKAGE_NAME", package_name),
                    ("GOOGLE_CLOUD_PROJECT_NUMBER", cloud_project_number),
                    ("JWT_SECRET", jwt_secret),
                ) if not val
            ]
            if missing or credentials_info is None:
                if credentials_info is None:
                    missing.append("GOOGLE_APPLICATION_CREDENTIALS_JSON (or _CREDENTIALS path)")
                raise RuntimeError(
                    "PLAY_INTEGRITY_ENABLED is set but required config is missing: "
                    + ", ".join(missing)
                )
            if min_device_verdict not in _DEVICE_VERDICT_STRENGTH:
                raise RuntimeError(
                    f"PLAY_INTEGRITY_MIN_DEVICE_VERDICT={min_device_verdict!r} is not one of "
                    f"{sorted(_DEVICE_VERDICT_STRENGTH)}"
                )

        return cls(
            enabled=enabled,
            package_name=package_name,
            cloud_project_number=cloud_project_number,
            credentials_info=credentials_info,
            jwt_secret=jwt_secret,
            jwt_ttl=jwt_ttl,
            allow_unevaluated=allow_unevaluated,
            min_device_verdict=min_device_verdict or "MEETS_DEVICE_INTEGRITY",
            signing_cert_sha256=cert_digests,
        )


# --------------------------------------------------------------------------- #
#  Verifier
# --------------------------------------------------------------------------- #
class PlayIntegrityError(Exception):
    """Raised for any verification failure (mapped to HTTP 401 by main.py)."""


class PlayIntegrityVerifier:
    def __init__(self, config: PlayIntegrityConfig):
        self.config = config
        self.challenges = ChallengeStore()
        self._credentials = service_account.Credentials.from_service_account_info(
            config.credentials_info, scopes=_SCOPES
        )

    # -- Challenge (nonce) -------------------------------------------------- #
    def new_challenge(self) -> str:
        return self.challenges.issue()

    # -- Diagnostics (no secrets) -------------------------------------------- #
    def status(self) -> dict:
        return {
            "enabled": True,
            "packageName": self.config.package_name,
            "cloudProjectNumber": self.config.cloud_project_number,
            "allowUnevaluated": self.config.allow_unevaluated,
            "minDeviceVerdict": self.config.min_device_verdict,
            "signingCertPinned": bool(self.config.signing_cert_sha256),
            "pendingChallenges": len(self.challenges._items),
        }

    # -- Verification --------------------------------------------------------#
    def verify_token(self, nonce: str, integrity_token: str) -> str:
        if not self.challenges.consume(nonce):
            raise PlayIntegrityError("Invalid or expired nonce")
        if not integrity_token:
            raise PlayIntegrityError("Missing integrity token")

        payload = self._decode(integrity_token)
        self._check_verdicts(payload, expected_nonce=nonce)

        # There's no stable per-device identifier to key a JWT on the way
        # attest.py keys one on the attested key id, so the subject is just
        # "a Play-verified install of this package", good enough, since the
        # JWT itself (not a persistent identity) is what authorises requests.
        return self._issue_jwt()

    def _decode(self, integrity_token: str) -> dict:
        try:
            self._credentials.refresh(GoogleAuthRequest())
        except Exception as exc:  # noqa: BLE001
            log.exception("Failed to refresh Google service-account credentials")
            raise PlayIntegrityError("Could not authenticate to Google Play Integrity API") from exc

        url = _DECODE_URL.format(package=self.config.package_name)
        try:
            resp = requests.post(
                url,
                headers={"Authorization": f"Bearer {self._credentials.token}"},
                json={"integrityToken": integrity_token},
                timeout=10,
            )
        except requests.RequestException as exc:
            raise PlayIntegrityError(f"Play Integrity API request failed: {exc}") from exc

        if resp.status_code != 200:
            raise PlayIntegrityError(
                f"Play Integrity API returned {resp.status_code}: {resp.text[:300]}"
            )
        try:
            body = resp.json()
            return body["tokenPayloadExternal"]
        except (ValueError, KeyError) as exc:
            raise PlayIntegrityError("Malformed Play Integrity API response") from exc

    def _check_verdicts(self, payload: dict, expected_nonce: str) -> None:
        request_details = payload.get("requestDetails", {})
        app_integrity = payload.get("appIntegrity", {})
        device_integrity = payload.get("deviceIntegrity", {})

        # 1. Nonce and package must match what we issued / expect: otherwise
        #    this could be a token minted for a different request or a
        #    different app entirely being replayed at us.
        if request_details.get("nonce") != expected_nonce:
            raise PlayIntegrityError("Nonce mismatch")
        if request_details.get("requestPackageName") != self.config.package_name:
            raise PlayIntegrityError("Package name mismatch")

        # 2. Freshness: a genuine request is decoded within seconds; a large
        #    gap suggests a captured token being replayed later.
        timestamp_ms = request_details.get("timestampMillis")
        if timestamp_ms is not None:
            age = time.time() - (int(timestamp_ms) / 1000.0)
            if age > self.config.max_token_age_seconds:
                raise PlayIntegrityError(f"Integrity token is stale ({age:.0f}s old)")

        # 3. App recognition: is this really our app, unmodified, from Play?
        app_verdict = app_integrity.get("appRecognitionVerdict")
        if app_verdict != "PLAY_RECOGNIZED":
            if not (self.config.allow_unevaluated and app_verdict == "UNEVALUATED"):
                raise PlayIntegrityError(f"App integrity verdict rejected: {app_verdict}")

        # 4. Signing certificate, optional but recommended: pins against a
        #    resigned/repackaged APK even if it somehow passed the above.
        if self.config.signing_cert_sha256:
            seen = set(app_integrity.get("certificateSha256Digest", []))
            if not seen.intersection(self.config.signing_cert_sha256):
                raise PlayIntegrityError("Signing certificate not recognised")

        # 5. Device integrity: is this a genuine, unmodified device?
        device_verdicts = device_integrity.get("deviceRecognitionVerdict", [])
        min_strength = _DEVICE_VERDICT_STRENGTH[self.config.min_device_verdict]
        best_seen = max(
            (_DEVICE_VERDICT_STRENGTH.get(v, 0) for v in device_verdicts), default=0
        )
        if best_seen < min_strength:
            if not (self.config.allow_unevaluated and not device_verdicts):
                raise PlayIntegrityError(
                    f"Device integrity verdict {device_verdicts} below required "
                    f"{self.config.min_device_verdict}"
                )

    # -- JWT ------------------------------------------------------------------#
    def _issue_jwt(self) -> str:
        now = int(time.time())
        payload = {"sub": "play-integrity", "iat": now, "exp": now + self.config.jwt_ttl}
        return jwt.encode(payload, self.config.jwt_secret, algorithm="HS256")

    def verify_app_token(self, token: str) -> bool:
        if not token:
            return False
        try:
            jwt.decode(token, self.config.jwt_secret, algorithms=["HS256"])
            return True
        except jwt.PyJWTError:
            return False
