# Pull Requests

## Opening one

1. Fork the repository and create a branch from `main`.
2. Keep the change focused: one fix or feature per pull request.
3. Add or update tests for behaviour you change. The backend, iOS and Android pieces all have unit test suites.
4. Update the documentation if you change setup steps, endpoints or configuration.
5. Fill in the pull request template and make sure CI is green.

## The template

The template asks for four things.

**What and why.** What the change does and what problem it solves. Link the issue with `Closes #123`.

**Components touched.** Backend, iOS app, Android app, web app, project site, docs, or CI and repo tooling.

**How it was tested.** The commands you ran and the devices or simulators you used. Add screenshots for UI changes.

**Checklist.**

- Tests added or updated for changed behaviour.
- Docs updated if setup, endpoints or configuration changed.
- `backend/requirements.txt` recompiled with pip-compile if `backend/requirements.in` changed.
- No secrets, keys or real `.env` values committed.

## One platform at a time is fine

A feature added to one client does not have to land in the others in the same pull request. Say so in the description so parity can be tracked.

For a visualization change, record the season and round you tested with, so reviewers use the same one. See the [Visual Parity Checklist](/contributing/visual-parity#shared-fixture).

## Labels

Pull requests are labelled automatically by the component they touch: `backend`, `ios`, `android`, `web`, `site`, `docs`, `ci` and `documentation`.

Release notes are generated from labels, so a maintainer may also add `enhancement`, `bug` or `accessibility`.

## Review

`CODEOWNERS` assigns a reviewer automatically. Expect questions about the other platforms if your change touches shared behaviour or a payload shape.

## Dependency updates

Dependabot opens weekly pull requests for the backend, the web app, the project site, the docs, the Android app and the GitHub Actions workflows. Minor and patch updates are grouped. Major updates arrive individually so they can be reviewed.

Some major versions are deliberately ignored while they are blocked upstream. The reasons are in the comments in `.github/dependabot.yml`.
