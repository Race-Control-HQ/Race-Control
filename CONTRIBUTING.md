# Contributing to RaceControl

Thanks for your interest in RaceControl. Bug reports, feature ideas and pull requests are
all welcome. By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

**The full contributing guide lives in the docs:
[docs.getracecontrol.com/contributing](https://docs.getracecontrol.com/contributing/).**

## Where to go

- **Bugs and feature requests:** open an [issue](https://github.com/Race-Control-HQ/Race-Control/issues/new/choose)
  using the matching form. Search existing issues first.
- **Questions and ideas:** start a [discussion](https://github.com/Race-Control-HQ/Race-Control/discussions).
- **Security vulnerabilities:** do not open a public issue. Follow [SECURITY.md](SECURITY.md).
- **Larger changes:** open an issue to discuss the approach before writing code.

## In the docs

| | |
|---|---|
| [Development setup](https://docs.getracecontrol.com/contributing/development) | The checks to run locally for each piece |
| [Pull requests](https://docs.getracecontrol.com/contributing/pull-requests) | What a good pull request looks like |
| [Continuous integration](https://docs.getracecontrol.com/contributing/ci) | What CI runs, and when |
| [Visual parity checklist](https://docs.getracecontrol.com/contributing/visual-parity) | For changes that touch a chart |
| [Writing documentation](https://docs.getracecontrol.com/contributing/documentation) | How the docs are organised |

## The short version

1. Fork the repository and create a branch from `main`.
2. Keep the change focused: one fix or feature per pull request.
3. Add or update tests, and run the checks for the piece you changed.
4. Update the docs in [`RaceControlDocs/`](RaceControlDocs/) if you change setup steps,
   endpoints or configuration.
5. Fill in the pull request template and make sure CI is green.

Never commit secrets. Only the `.env.example` templates are tracked.

## Licence

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE).
