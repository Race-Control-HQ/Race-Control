# Run the Backend

The backend is a FastAPI service that runs FastF1 and serves JSON to all three clients.

Requirements: **Python 3.10+**.

## Start it

```bash
cd backend
./run.sh            # creates a venv, installs deps, starts the API
```

The API starts on **http://localhost:8000**. Open **http://localhost:8000/docs** for the interactive Swagger UI.

The first request for a given race downloads and caches its data (FastF1 caches to `backend/.fastf1_cache/`), so it is slow once and fast thereafter.

## Manual start

If you would rather not use `run.sh`:

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.in
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## Authentication in local development

If you set none of `APP_ATTEST_ENABLED`, `PLAY_INTEGRITY_ENABLED` or `API_TOKEN`, the API is fully open. That is the default for `./run.sh` and is intended for local development only.

To try the authenticated paths locally, copy `backend/.env.example` to `backend/.env` and fill in the values. Every variable is described in [Environment Variables](/self-hosting/environment).

## Check it is working

```bash
curl http://localhost:8000/api/health
curl http://localhost:8000/api/seasons
```

`/api/health` is deliberately unauthenticated so a hosting platform can probe it.

## Run the tests

```bash
cd backend
source .venv/bin/activate
pip install -r requirements.txt
python -m pytest -v
```

## Where next

- [Core Endpoints](/api/endpoints) lists everything the backend serves.
- [Backend architecture](/architecture/backend) explains the module boundaries and the conventions to keep.
- [Self-hosting the backend](/self-hosting/backend) covers a real deployment.
