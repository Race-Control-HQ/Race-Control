"""
Offline tests for the App Attest verification module.

App Attest can't run in a simulator or CI without a real Apple device, so these
tests exercise the *server-side* verification logic using pyattest's attestation
factory (with its fixture root CA) plus self-generated EC assertions. Run with:

    python test_attest.py     (or: pytest test_attest.py)
"""

import asyncio
import base64
import hashlib
import os
import struct
from pathlib import Path

import cbor2
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from pyattest.testutils.factories.attestation import apple as apple_factory

import attest

APP_ID = "ABCDE12345.com.owlmedia.racecontrol"
FIXTURE_ROOT = (
    Path(apple_factory.__file__).parent / ".." / ".." / "fixtures" / "root_cert.pem"
).read_bytes()


def _make_verifier(tmp_db: str) -> attest.AppAttestVerifier:
    cfg = attest.AttestConfig(
        enabled=True, app_id=APP_ID, production=False,
        jwt_secret="test-secret", jwt_ttl=3600, root_ca=FIXTURE_ROOT, db_path=tmp_db,
    )
    return attest.AppAttestVerifier(cfg)


def _b64(b: bytes) -> str:
    return base64.b64encode(b).decode()


def _build_assertion(private_key: ec.EllipticCurvePrivateKey, app_id: str,
                     counter: int, challenge_bytes: bytes) -> bytes:
    """Construct a valid Apple-style assertion CBOR blob, signed with `private_key`."""
    auth_data = (
        hashlib.sha256(app_id.encode()).digest()  # rpIdHash
        + b"\x00"                                  # flags
        + struct.pack("!I", counter)               # sign counter
    )
    client_data_hash = hashlib.sha256(challenge_bytes).digest()
    nonce = hashlib.sha256(auth_data + client_data_hash).digest()
    signature = private_key.sign(nonce, ec.ECDSA(hashes.SHA256()))
    return cbor2.dumps({"signature": signature, "authenticatorData": auth_data})


def test_attestation_issues_token(tmp_path="/tmp/attest_test_1.sqlite"):
    if os.path.exists(tmp_path): os.remove(tmp_path)
    v = _make_verifier(tmp_path)
    challenge = v.new_challenge()
    raw, public_key = apple_factory.get(app_id=APP_ID, nonce=base64.b64decode(challenge))
    key_id = _b64(hashlib.sha256(public_key).digest())

    token = asyncio.run(v.verify_attestation(key_id, _b64(raw), challenge))
    assert v.verify_app_token(token), "issued JWT should validate"
    assert v.keys.get(key_id) is not None, "key should be stored"
    print("PASS: attestation issues a valid token and stores the key")


def test_challenge_is_single_use(tmp_path="/tmp/attest_test_2.sqlite"):
    if os.path.exists(tmp_path): os.remove(tmp_path)
    v = _make_verifier(tmp_path)
    c = v.new_challenge()
    assert v.challenges.consume(c) is True
    assert v.challenges.consume(c) is False, "challenge must not be reusable"
    assert v.challenges.consume("bogus") is False
    print("PASS: challenges are single-use")


def test_assertion_and_replay(tmp_path="/tmp/attest_test_3.sqlite"):
    if os.path.exists(tmp_path): os.remove(tmp_path)
    v = _make_verifier(tmp_path)
    # Seed a key as if it had been attested, counter starts at 0.
    priv = ec.generate_private_key(ec.SECP256R1())
    pem = priv.public_key().public_bytes(
        serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
    ).decode()
    key_id = _b64(b"seeded-key")
    v.keys.save(key_id, pem, 0)

    # A valid assertion with an increased counter mints a token.
    challenge = v.new_challenge()
    assertion = _build_assertion(priv, APP_ID, counter=1, challenge_bytes=base64.b64decode(challenge))
    token = asyncio.run(v.verify_assertion(key_id, _b64(assertion), challenge))
    assert v.verify_app_token(token)
    assert v.keys.get(key_id)[1] == 1, "counter should advance to 1"
    print("PASS: valid assertion mints a token and advances the counter")

    # Replaying the same counter must be rejected.
    challenge2 = v.new_challenge()
    replay = _build_assertion(priv, APP_ID, counter=1, challenge_bytes=base64.b64decode(challenge2))
    try:
        asyncio.run(v.verify_assertion(key_id, _b64(replay), challenge2))
        assert False, "counter replay should have been rejected"
    except attest.AttestError as e:
        assert "counter" in str(e).lower()
    print("PASS: counter replay is rejected")


def test_bad_challenge_rejected(tmp_path="/tmp/attest_test_4.sqlite"):
    if os.path.exists(tmp_path): os.remove(tmp_path)
    v = _make_verifier(tmp_path)
    raw, public_key = apple_factory.get(app_id=APP_ID, nonce=os.urandom(32))
    key_id = _b64(hashlib.sha256(public_key).digest())
    try:
        asyncio.run(v.verify_attestation(key_id, _b64(raw), "never-issued-challenge"))
        assert False, "unknown challenge should be rejected"
    except attest.AttestError as e:
        assert "challenge" in str(e).lower()
    print("PASS: attestation with an unissued challenge is rejected")


def test_wrong_app_id_rejected(tmp_path="/tmp/attest_test_5.sqlite"):
    if os.path.exists(tmp_path): os.remove(tmp_path)
    v = _make_verifier(tmp_path)
    challenge = v.new_challenge()
    # Attestation built for a DIFFERENT app id.
    raw, public_key = apple_factory.get(app_id="WRONG00000.com.evil.clone",
                                        nonce=base64.b64decode(challenge))
    key_id = _b64(hashlib.sha256(public_key).digest())
    try:
        asyncio.run(v.verify_attestation(key_id, _b64(raw), challenge))
        assert False, "attestation for another app id should be rejected"
    except attest.AttestError:
        pass
    print("PASS: attestation for a different app id is rejected")


if __name__ == "__main__":
    test_attestation_issues_token()
    test_challenge_is_single_use()
    test_assertion_and_replay()
    test_bad_challenge_rejected()
    test_wrong_app_id_rejected()
    print("\nALL ATTEST TESTS PASSED")
