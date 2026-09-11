# Calories

A calorie counter for Android that stays out of your way. Built as a
replacement for [waistline](https://github.com/davidhealey/waistline), and it
can import waistline's backup.

## Diary

- **Meals are yours.** There are no fixed breakfast / lunch / dinner slots. Tap
  **+** to start a meal whenever you eat; tap a meal to see, edit or add items;
  long-press it to rename or delete.
- **Entries are pills.** Tap one to change its amount or remove it. Long-press
  and drag it onto another meal card to move it there; the diary scrolls while
  you hold the pill near the top or bottom edge.
- **Quick add.** Type in the field at the bottom: your saved foods and meal
  presets filter as you type. The list opens upward with the best match at the
  bottom, right above the field, marked with →. **Enter** takes it and jumps to
  the amount, with the unit already shown and the food's serving prefilled.
  **Enter** again adds it to the most recent meal (the one marked
  "Quick add goes here"), creating one if the day has none. Focus returns to the
  food field, so you can keep going.
- The top dropdown row searches Open Food Facts for what you typed, and the
  barcode button scans a product. Either way you end up back in the quick-add
  field with that food picked.
- **Day start.** Settings → *Day starts at* sets when a new diary day begins.
  With 05:00, anything logged at 03:00 still counts toward the previous day.
- Settings → *Start typing on launch* opens the app with the cursor already in
  the quick-add field and the keyboard up. Off by default.
- Use ‹ › to move between days; tap the date to jump back to today.

## Foods and meals

- A food has nutrients entered against a **reference amount** (e.g. per 100 g),
  in g, ml or pcs. Its **serving** is separate and only prefills the amount when
  you add it.
- **Scan** or **Search online** fetches products from Open Food Facts. The
  reference amount is always 100 g (100 ml for drinks) with per-100 values; the
  product's serving size only goes into the serving field. You review and edit
  everything before saving.
- **Meals** (the second tab) are presets: a set of foods and amounts that quick
  add adds in one go. The amount you type is a multiplier.
- Diary entries keep the numbers they were logged with, so editing or deleting a
  food never changes past days.

## Stats

Calories, protein, carbs or fat per day over the last 7, 14 or 30 days, or all
time. Tap a bar for its value. Days with nothing logged are gaps and don't count
toward the average. By default bars start at zero; Settings → *Scale bars to the
data* starts them just under the lowest day instead.

## Widget

Shows today's calories, updating as you log and resetting at your day start.
Its own settings (shown when you place it, or later from the launcher's widget
settings) choose whether protein, carbs and fat appear underneath, the text and
background colors (with transparency), and the corner radius.

## Backup and import

Settings → *Data*:

- **Export backup** / **Restore backup**: everything in one JSON file. Restoring
  replaces all current data.
- **Import from waistline**: pick the file from waistline's *Export database*
  (`waistline_export.json`). Foods, the diary and saved meals are **added** to
  what's already here (foods you already have are reused, but importing the same
  file twice duplicates the diary).
  - Each waistline meal slot becomes a meal named after it (Breakfast, Lunch…).
  - Nutrients come out exactly as waistline showed them.
  - Foods move to a 100 g / 100 ml reference; if waistline had them per some
    other portion (say 38 g), that portion becomes the serving.
  - Mass and volume units convert (kg, l, dl, oz…); anything else ("serving",
    "bun", …) becomes pcs.
  - Archived and hidden foods aren't added to your food list, but diary entries
    that used them still import. Recipes aren't imported; burned-calorie entries
    are skipped.

Food data comes from [Open Food Facts](https://openfoodfacts.org) under the Open
Database License.

## Building

A fully containerized, CLI-driven Gradle build. No JDK, Android SDK, or Gradle is
needed on the host — everything runs inside a podman container defined by the
`Containerfile`. The only host dependency is `podman` (and `adb` to install).

Build the toolchain image once:

```sh
make image
```

Build a debug APK (rebuilds the image if needed):

```sh
make debug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Other targets:

```sh
make release            # assembleRelease
make clean              # gradle clean
make gradle ARGS="tasks"   # run any gradle task in the container
make gradle ARGS="testDebugUnitTest"   # JVM unit tests
make shell              # interactive shell inside the build container
```

The Gradle cache is persisted in a named volume (`android-gradle-cache`) so
incremental builds and the debug keystore survive between runs.

### Install on a device

The build stays containerized; only `adb` runs on the host:

```sh
sudo dnf install android-tools     # Fedora
make install                       # adb install -r the debug APK
```

### Notes

- **Container-only by design.** There is no Gradle wrapper (`gradlew`); the
  pinned Gradle version lives solely in the `Containerfile`. Build through
  `make`, not on the host.
- SDK level, build-tools, and Gradle versions are all `ARG`s at the top of the
  `Containerfile` — change them in one place.
- The app is plain Java on the Android framework (no AndroidX); the only library
  is ZXing core for barcode decoding.
- `WaistlineImportTest.realExport` runs against a `waistline_export.json` in the
  repo root if one is present (it's gitignored) and writes per-day totals to
  `app/build/waistline-totals.tsv`.
