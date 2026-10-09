# Design System

Each app follows its own platform's conventions, but they share one palette. The colours are carried across exactly, because they encode brand and F1 semantics.

## Palette

```
racingRed      #E10600    background      #0A0A0C     positive  #30D158
racingRedDim   #B00500    surface         #16161A     negative  #FF453A
racingRedText  #FF5A50    surfaceElevated #202027     warning   #FF9F0A
stroke         white 8%   textPrimary     #F2F2F5     info      #64D2FF
                          textSecondary   #A0A0AA
                          textTertiary    #8A8A94

Tyres: soft #F0402C · medium #F5D000 · hard #EBEBEB · inter #40B14B · wet #1E6FE0
```

Flag colours follow the same "colour is data" reasoning as tyre compounds:

| Flag | Colour |
|---|---|
| Yellow | `#FFD500` |
| Double yellow | `#FF9500` |
| Red | `#FF453A` |
| Safety car | `#FF6A00` |
| Virtual safety car | `#AF52DE` |

## Dark only

All the apps are dark only. The palette is OLED-tuned by design, and there is no light theme.

## Where the tokens live

| Client | Location |
|---|---|
| iOS | The `Theme` enum |
| Android | `core/design/`, mapped onto a Material 3 `ColorScheme`, with semantic extras in a custom `RaceControlColors` exposed through a `CompositionLocal` |
| Web app | `RaceControlWeb/src/app/globals.css` |
| Project site | `RaceControlSite/src/app/globals.css` |

On Android, `primary` is racingRed, `background` is `#0A0A0C`, `surface` is `#16161A`, `surfaceContainerHigh` is `#202027`, `outline` is the stroke and `error` is negative. Tyre colours, positive, warning, info and team colours have no Material 3 slot, which is why they live in `RaceControlColors`.

## No dynamic colour

Android's Material You is deliberately switched off. Wallpaper-derived colour would destroy the team-colour and tyre-colour semantics the app relies on.

## Spacing and shape

Spacing (4, 8, 16, 24, 32) and radii (8, 14, 20 and pill) transfer unchanged between iOS points and Android dp. Both platforms use an 8-unit grid.

The medium corner radius is 14 on both. Material 3 cards default to 12dp; keeping 14dp is a deliberate, harmless brand carry-over.

## Typography

SF Pro on iOS and Roboto on Android, mapped to the Material type scale with all sizes in `sp`.

Monospaced digits are used heavily for lap times and positions. On Android that is `FontFeatureSetting("tnum")` on Roboto.

## Touch targets

44pt or larger on iOS. 48dp on Android, which is Android's accessibility minimum.

## States

Every screen has the same three supporting states: a loading indicator, an error state with a retry action, and an empty state. A race or season with nothing to show should read as empty, not broken.

## Accessibility

- **iOS:** Dynamic Type, semantic SF Symbols, `.accessibilityLabel` on charts, and reduce-motion respected in animated transitions.
- **Android:** font scaling to 200%, content descriptions on every icon-only control, `chartSemantics` in `RcCharts.kt` for charts, and reduce-motion honoured through `ANIMATOR_DURATION_SCALE`.

The [Visual Parity Checklist](/contributing/visual-parity) includes an accessibility-label check for every visualization.
