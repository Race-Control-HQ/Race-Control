"""
End-to-end test of the App Attest HTTP flow through the FastAPI app, using the
pyattest attestation factory. Exercises: challenge -> attest -> JWT -> auth'd
request, plus rejection of unauthenticated requests and wrong tokens.
"""

import base64
import hashlib
import importlib
import os
import sys
from pathlib import Path

os.environ["APP_ATTEST_ENABLED"] = "true"
os.environ["APPLE_TEAM_ID"] = "ABCDE12345"
os.environ["APP_BUNDLE_ID"] = "com.owlmedia.racecontrol"
os.environ["JWT_SECRET"] = "endpoint-test-secret"
os.environ["ATTEST_DB"] = "/tmp/attest_endpoint_test.sqlite"
if os.path.exists("/tmp/attest_endpoint_test.sqlite"):
    os.remove("/tmp/attest_endpoint_test.sqlite")

for m in ["attest", "main"]:
    sys.modules.pop(m, None)

import main  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from pyattest.testutils.factories.attestation import apple as apple_factory  # noqa: E402

APP_ID = "ABCDE12345.com.owlmedia.racecontrol"

# Point the verifier at pyattest's fixture root instead of Apple's real root.
FIXTURE_ROOT = (Path(apple_factory.__file__).parent / ".." / ".." / "fixtures" / "root_cert.pem").read_bytes()
main.attest_verifier.config.root_ca = FIXTURE_ROOT

main.svc.get_seasons = lambda: [2026, 2025, 2024]
c = TestClient(main.app)


def test_flow():
    # Unauthenticated API call is rejected.
    assert c.get("/api/seasons").status_code == 401
    print("PASS: unauthenticated /api call rejected (401)")

    # Health stays open for the platform probe.
    assert c.get("/api/health").status_code == 200
    print("PASS: /api/health open")

    # 1) challenge
    challenge = c.get("/attest/challenge").json()["challenge"]

    # 2) attest (device side, simulated with the factory)
    raw, public_key = apple_factory.get(app_id=APP_ID, nonce=base64.b64decode(challenge))
    key_id = base64.b64encode(hashlib.sha256(public_key).digest()).decode()
    resp = c.post("/attest/verify", json={
        "keyId": key_id,
        "attestation": base64.b64encode(raw).decode(),
        "challenge": challenge,
    })
    assert resp.status_code == 200, resp.text
    token = resp.json()["token"]
    print("PASS: /attest/verify issued a JWT")

    # 3) authorised API call with the JWT
    ok = c.get("/api/seasons", headers={"Authorization": f"Bearer {token}"})
    assert ok.status_code == 200 and ok.json() == [2026, 2025, 2024]
    print("PASS: /api/seasons authorised with app JWT")

    # Wrong token rejected.
    bad = c.get("/api/seasons", headers={"Authorization": "Bearer not-a-real-jwt"})
    assert bad.status_code == 401
    print("PASS: forged token rejected")

    # A reused challenge can't attest again.
    replay = c.post("/attest/verify", json={
        "keyId": key_id,
        "attestation": base64.b64encode(raw).decode(),
        "challenge": challenge,
    })
    assert replay.status_code == 401
    print("PASS: reused challenge rejected at /attest/verify")


if __name__ == "__main__":
    test_flow()
    print("\nALL ENDPOINT TESTS PASSED")
