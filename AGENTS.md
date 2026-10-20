# The P — Codex repository instructions

## Project identity
- App display name: **The P** (not FitWave); Android only.
- Brand icon: `assets/the_p_approved_logo.png`, the user's **approved** black intertwined-line logo with pale-pink orbit and star details. Do not replace it with a generated placeholder.
- Mascot: A1 **奶霜丝带** silver/white-haired pink-eyed anime-style fitness girl; see `assets/A1_mascot_reference.png`. Treat this as a design reference, **not** an animation-ready Live2D rig.
- Default visual design: soft white / cream pink with clean, accessible Compose components. Prioritize clear training interaction over decoration.
- User-facing language: Chinese. Code: Kotlin for Android, Gradle Kotlin DSL, English technical identifiers.

## Hard architectural requirements
- Build a new Android **native** application, using Kotlin + Jetpack Compose Material 3. Do not base the UI on HTML/WebView/Capacitor.
- Room/SQLite is the **sole source of truth** for training data. DataStore for preferences. MVVM/UDF, StateFlow/Coroutines, Hilt and repositories.
- Offline-first training. A failed cloud sync or AI call must never block logging sets or opening past workouts.
- Use stable compatible versions selected at implementation time; do not blindly copy stale version numbers.
- Develop in incremental, testable phases; prioritize one complete workout flow in Alpha. Do not claim Live2D, AI, cloud sync or widget finished just because interfaces are present.
- Never embed third-party model secrets in APK; user must opt in to cloud/AI.
- Preserve existing FitWave V4.x apps and historical data. Build V5 debug with a distinct application ID; import via explicit JSON with validation and transactional Room writes. Do not force uninstall of V4.x.
- Read `docs/V5_ARCHITECTURE.md` for architecture, data model, and acceptance criteria, and `docs/PROJECT_CONTEXT.md` for settled decisions.

## Development discipline
- Work in `D:\githubProject\TheP_Android` (or the local repository folder the user chooses) without touching old V4.x folders.
- Start with a plan and state exactly which phase you are implementing. For large work, define acceptance checks before editing.
- After each phase, run available Gradle build, JVM unit tests, and relevant checks; report real outputs and distinguish tool limitations from success.
- The Android test device is Xiaomi 15 Pro. User controls Android Studio and phone; provide concrete install/test steps where needed.
- Do not copy code, models or images from reference GitHub projects without checking copyright and licenses.
- Ask before deleting/migrating user data, overwriting files, enabling permissions or adding cloud services.
