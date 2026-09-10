# Phase 0 — environment report

Written from the container this phase was developed in. It records what was
actually executed, and what could not be, so that no claim here rests on
assumption.

## What this container has

| Tool | Result |
| --- | --- |
| JDK | OpenJDK 21.0.10 ✅ |
| Gradle | 8.14.3 (plus a generated wrapper) ✅ |
| Kotlin | 2.0.21, resolved from Maven Central ✅ |
| CMake | present ✅ |
| Maven Central | reachable ✅ |
| `plugins.gradle.org`, `services.gradle.org` | reachable ✅ |

## What it does not have, and why that matters

| Missing | Consequence |
| --- | --- |
| Android SDK (no `sdkmanager`, no platform, no build-tools) | `assembleDebug` cannot run |
| `dl.google.com` — blocked by network policy (403 on CONNECT) | the SDK cannot be installed, **and** Google's Maven repo is unreachable, so AGP / AndroidX / Compose cannot be resolved |
| Android NDK | the native phases (Assimp, IfcOpenShell, LibreDWG, CuraEngine) cannot be compiled here |
| `/dev/kvm`, no CPU virtualisation flags | no emulator, even if the SDK were present |
| `adb`, no attached device | nothing can be installed or run |

`maven.google.com` answers but only as a 301 redirect to `dl.google.com`, which
is the blocked host, so it is not a way around this.

## Therefore

**The `:app` module has not been compiled.** Not "probably compiles" — it has
not been put through a compiler at all, because no Android toolchain exists in
this container. The Compose code in `app/` is written against AGP 8.7.3,
Kotlin 2.0.21 and Compose BOM 2024.10.01, and it needs a first real build on a
machine with the SDK. Expect that first build to surface errors; that is the
normal shape of this work, not a failure.

**The `core/` modules have been compiled and tested for real**, with the
wrapper, in this container:

```
$ ./gradlew -PpaftaCoreOnly=true test
BUILD SUCCESSFUL
109 tests, 0 failed
```

| Module | Tests |
| --- | --- |
| `core:geometry` | 37 |
| `core:dxf` | 19 |
| `core:units` | 18 |
| `core:measure` | 18 |
| `core:project` | 17 |

Four of those tests failed on first run and are recorded in
[ROADMAP.md](ROADMAP.md#phase-0-defects-found-and-fixed) along with the three
production bugs they caught.

## First build on a real machine

```bash
cd pafta
./gradlew assembleDebug          # expect to iterate on errors here
./gradlew installDebug
```

Things worth checking first when that build fails:

1. **Material icon names.** `ToolRail.kt` and `RightPanel.kt` reference icons
   from `material-icons-extended`. They were chosen to be long-standing names,
   but they could not be verified against the artifact here. A wrong name is an
   unresolved-reference error and a one-line fix.
2. **`collectAsStateWithLifecycle`** needs `lifecycle-runtime-compose`, which is
   in the catalogue — confirm it resolves.
3. **`viewModel { ... }`** uses the lambda-initialiser overload from
   `lifecycle-viewmodel-compose` 2.8.x.
