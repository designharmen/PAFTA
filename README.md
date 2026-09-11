# PAFTA

A CAD / BIM viewing and markup application for Android tablets, built on a
fully free and open-source stack. Design system: **Harmen Design**.

## Repository layout

```
pafta/
├── core/                  pure-Kotlin, no Android dependency — unit tested on the JVM
│   ├── geometry/          vectors, bounds, polygons, arcs, the 2D view transform
│   ├── units/             mm/cm/m/inch/foot conversion, parsing, display formatting
│   ├── measure/           distance / polyline / angle / area engine, snapping
│   ├── dxf/               DXF R12 reader and writer (PAFTA's own engine)
│   └── project/           the `.pafta` container, project store, undo, auto-save
└── app/                   Android application, Jetpack Compose UI
```

Two screens: the **project library** (import, open, delete) and the **editor**
(tool rail, drawing viewport, inspector).

The split is deliberate: everything that can be tested without a device lives in
`core/`, so the parts most likely to be wrong — unit arithmetic, DXF parsing,
file round-trips, pan/zoom maths — are covered by real tests rather than by
looking at the screen.

## Building

Requires the **Android SDK** (API 35 platform + build-tools) and network access
to Google's Maven repository, which is where AGP, AndroidX and Compose are
published.

```bash
# the whole app
./gradlew assembleDebug

# install on a connected device
./gradlew installDebug
```

### Building only the pure-Kotlin core

The core modules need neither the Android SDK nor Google's Maven. On a machine
or CI container that has neither:

```bash
./gradlew -PpaftaCoreOnly=true test
```

`-PpaftaCoreOnly=true` excludes `:app` from the build, so nothing tries to
resolve the Android Gradle Plugin.

## Status

See [docs/ROADMAP.md](docs/ROADMAP.md) for what is done and what is next, and
[docs/PHASE0-ENVIRONMENT.md](docs/PHASE0-ENVIRONMENT.md) for exactly which parts
of the build have been verified and which have not.

## Licences

PAFTA uses only MIT / BSD / Apache-2.0 / LGPL / GPL / AGPL components — no
commercial or subscription-gated SDK. See [docs/LICENCES.md](docs/LICENCES.md).
