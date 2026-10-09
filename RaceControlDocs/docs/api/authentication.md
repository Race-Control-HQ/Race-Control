# Authentication Endpoints

These endpoints bootstrap the mobile apps' device attestation. They sit outside `/api` and are not reachable through the web app's proxy.

For the reasoning behind the design, see [Device Attestation](/architecture/authentication).

## Apple App Attest

Active when `APP_ATTEST_ENABLED=true`.

| Method | Path | Description |
|---|---|---|
| GET | `/attest/status` | Non-sensitive diagnostics for verifying App Attest setup on a device. |
| GET | `/attest/challenge` | Issues a one-time nonce. |
| POST | `/attest/verify` | Verifies an attestation against Apple's certificate chain and returns a JWT. |
| POST | `/attest/token` | Verifies an assertion (signature and replay counter) and returns a fresh JWT. |

The flow:

1. `GET /attest/challenge` returns a one-time nonce.
2. The app calls `DCAppAttestService.attestKey`, tied to that nonce.
3. `POST /attest/verify` checks the certificate chain to Apple's root and returns a JWT.
4. The app calls `/api/...` with `Authorization: Bearer <JWT>`.
5. Before the JWT expires, the app calls `generateAssertion` and `POST /attest/token` for a fresh one.

## Google Play Integrity

Active when `PLAY_INTEGRITY_ENABLED=true`.

| Method | Path | Description |
|---|---|---|
| GET | `/playintegrity/status` | Non-sensitive diagnostics for verifying Play Integrity setup on a device. |
| GET | `/playintegrity/challenge` | Issues a one-time nonce. |
| POST | `/playintegrity/verify` | Decodes the integrity token through Google's REST API, checks its verdicts and returns a JWT. |

The flow:

1. `GET /playintegrity/challenge` returns a one-time nonce.
2. The app asks the Play Integrity API to vouch for the app and device, tied to that nonce.
3. `POST /playintegrity/verify` checks the verdicts and returns a JWT.
4. The app calls `/api/...` with `Authorization: Bearer <JWT>`.
5. When the cached JWT expires, the app requests a fresh integrity token and repeats step 3. There is no persistent per-device key to renew.

## Tokens

Both mechanisms mint interchangeable JWTs, signed with `JWT_SECRET` and valid for `JWT_TTL_SECONDS` (24 hours by default). The backend authorises a request from either without needing to know which one issued it.

When either status endpoint reports `enabled: false`, that mechanism is switched off on the backend. Both also report `adminTokenSet`, which says whether a shared `API_TOKEN` is configured, never its value.
