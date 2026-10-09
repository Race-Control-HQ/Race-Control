# RaceControl Backend

A FastAPI service that runs [FastF1](https://docs.fastf1.dev) and serves clean JSON to the
iOS, Android and web apps.

## Quick start

Requires Python 3.10+.

```bash
./run.sh            # creates a venv, installs deps, starts the API
```

The API starts on http://localhost:8000, with interactive docs at
http://localhost:8000/docs. With no environment variables set it is fully open, which is
what you want for local development.

The first request for a given race downloads and caches its data, so it is slow once and
fast thereafter.

## Tests

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest -v
```

`requirements.in` is the hand-edited spec and `requirements.txt` is the pip-compile lock.
If you change one, recompile and commit both.

## Documentation

- [Run the backend](https://docs.getracecontrol.com/getting-started/backend)
- [API reference](https://docs.getracecontrol.com/api/)
- [Architecture and module boundaries](https://docs.getracecontrol.com/architecture/backend)
- [Self-hosting](https://docs.getracecontrol.com/self-hosting/backend) and
  [environment variables](https://docs.getracecontrol.com/self-hosting/environment)

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
To contribute, see the [contributing guide](https://docs.getracecontrol.com/contributing/).
