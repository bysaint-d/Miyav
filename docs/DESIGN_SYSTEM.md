# MIYAV design system (Pawhome direction)

One palette and one set of shapes for the Android app, the Expo app and the desktop admin console.

| Token | Value | Use |
| --- | --- | --- |
| background | `#F7F5EF` | Warm cream page background |
| surface | `#FFFFFF` | Cards, sheets, tables |
| mint | `#DCEEE5` | Selected states, success, hero panels |
| sage | `#B9DCD0` | Avatars, secondary badges, chart series |
| peach | `#F5C6A5` (soft `#FBE7D9`) | Warm accents, adoption, "new" badges |
| primary | `#315B49` | Primary actions, active navigation, charts |
| text | `#263C35` | Body text and headings |
| secondary text | `#67706A` | Captions and hints |
| decoration | `#858D85` | Non-text details only |

The brief suggested `#858D85` for secondary text. On the cream background it reaches about 3.1:1 contrast, below the 4.5:1 the Expo design test enforces, so text uses `#67706A` (same hue, about 4.7:1) and `#858D85` is kept for decoration.

## Where the tokens live

- Desktop: `desktop-app/src/main/resources/az/miyav/desktop/miyav.css`, as looked-up colours on `.root` (`-miyav-primary`, `-miyav-mint`, ...). Components only reference tokens.
- Android: `android-app/src/main/res/values/colors.xml`. Existing resource names are kept so layouts did not change; `brand_coral` is the primary action colour.
- Expo: `src/design/tokens.ts` in the Expo project (`lightColors`, `darkColors`).

## Shapes and spacing

- Radius: 14 for controls, 22 for cards, 26–28 for sheets and hero panels, pill for chips and badges.
- Shadow: one soft shadow, `rgba(38, 60, 53, 0.07)`, 22 px blur, 6 px down. No borders on cards.
- Spacing scale: 4, 8, 12, 16, 20, 24, 32.

## Desktop components

`az.miyav.desktop.admin.Components` assembles the shared pieces: `card`, `stat` (number, label, hint), `badge` with tones (mint, sage, peach, warning, danger, neutral), `avatar` (initials or photo), `empty` states, and the date helpers in Baku time. Status colours are consistent: walking now / active = mint, accepted / adopted = sage, pending / draft = warning, cancelled / hidden = danger, finished = neutral.
