# Platform Differences

The iOS, Android and web apps are independent clients of the same idea. Each follows its own platform's conventions rather than one being a port of another. Every difference below is deliberate.

## iOS

Built against Apple's Human Interface Guidelines.

- Dark-first, OLED-tuned palette with official F1 team and tyre colours.
- 44pt or larger touch targets, Dynamic Type and semantic SF Symbols.
- Tab-bar navigation with five tabs.
- Consistent loading, error-with-retry and empty states on every screen.
- No response cache: the app expects a reachable backend.

## Android

Built against Material 3 and Android accessibility conventions, with the same dark-first palette and F1 colours.

| Difference from iOS | Why |
|---|---|
| Settings lives in the ⋮ overflow menu, not top-left | The leading app bar slot belongs to navigation on Android. |
| 48dp touch targets throughout | The iOS build targets 44pt, which is below Android's accessibility minimum. |
| Tab rows instead of segmented controls where the option count varies | Race sessions run from one to six; standings has four modes. Material segmented buttons do not scroll. |
| No Material You dynamic colour | Wallpaper-derived theming would collide with the official F1 team and tyre colours the app uses to convey meaning. |
| No floating action button | The app is read-only. There is nothing to promote. |
| Reminders survive reboot and refresh daily | Android alarms do not persist across a reboot the way iOS notifications do, and users may not open the app between races. |
| Charts are drawn on Compose Canvas | An in-house charting layer rather than a library. See the note at the top of `core/ui/RcCharts.kt`. |
| Offline cache with a "showing cached data" banner | A 10 MB response cache backs the schedule and standings. Justified by Android's more variable connectivity and the backend's slow cold loads. |
| Settings save immediately on change | Platform convention. There is no Save button. |

The complete screen-by-screen mapping, with every component and the reasoning for each decision, is in the [Android Feature Inventory](/architecture/android-feature-inventory).

## Web

A browser client built with Next.js, replicating the mobile apps' end-user screens.

| Difference | Why |
|---|---|
| No session reminder notifications | There is no clean web equivalent without a Service Worker and push infrastructure the backend does not have. Deliberately left out. |
| Favourites are stored in `localStorage` | Matches the mobile apps: favourites do not sync anywhere, since the backend has no user accounts. |
| Talks to the backend through a BFF layer | A browser cannot do device attestation or hold a secret. See [Web App (BFF)](/architecture/web). |

![Race results in the web app](/images/web/web-results.png)

## Keeping the platforms honest

Three rendering stacks over one JSON contract can drift. A feature added to one client does not have to land in the others in the same pull request, but the [Visual Parity Checklist](/contributing/visual-parity) is how gaps are tracked rather than forgotten.
