# The P V5.0 — handoff summary from prior ChatGPT planning

**Important:** This document transmits decisions and constraints, not the verbatim complete conversation. It is a product brief, not evidence that the functionality is already implemented.

## 1. Origin, current status
- Evolved from FitWave (V3.2 training plan, V3.3-3.5 web app, V3.6 Android via Capacitor, V4.0 offline analysis, V4.1 A1 mascot and floating desktop companion, V4.2 prototype SQLite/voice/AI/cloud/widget integrations).
- V4.1 **was successfully built and installed by user on Xiaomi 15 Pro**; six smoke checks reported passing: pink theme, overlay permission, visible floating pet, drag/snap/card/hide/restore, rest timer syncing, training persistence. This does **not** establish that all background edge cases, V4.2 modules, or true Live2D work.
- User was dissatisfied with quality and layered architecture. Decision: **restart V5 as native Kotlin** rather than keep patching existing HTML.

## 2. Brand and visual identity (approved)
- Name: **The P**. This supersedes 'FitWave' for the new app. Use The P on icon/title/splash/launcher/settings.
- Approved logo is in `assets/the_p_approved_logo.png` (black calligraphic interwoven mark with thin blush-pink circular arcs and four-point sparkles, on white). Do not redraw; adapt it to icon requirements while keeping core shape.
- A1 mascot is **奶霜丝带**: white/silver-haired pink-eyed anime girl, gentle/cute yet energetic, pink ribbons and sports apparel. A1 design sheet in `assets/A1_mascot_reference.png`. No whale requirement; the earlier DeepSeek whale was only an example.
- Theme: cream-white + sakura/blush pink; strong text contrast and legible training controls. Character is complementary to workout workflow.

## 3. Platform and stack (approved)
- Android only; primary target Xiaomi 15 Pro. Kotlin + Jetpack Compose/Material3, MVVM/UDF, Hilt, Coroutines/Flow, Room SQLite sole training DB, DataStore settings, WorkManager eventual cloud sync, Jetpack Glance widget.
- Original app HTML/Capacitor is *not* a starting-point UI or source of truth. It is legacy import material only.
- Separate domain workout logic from character engine, Android overlay/service, voice/AI and cloud sync.

## 4. V5.0 intended functionality
1. **Professional workout logger:** user-editable split plan and exercises, heat-up sets, weight/reps/RIR, rest time, last-set suggestions, per-set durable saves, resume interrupted workout, edit/cancel/finish safeguards.
2. **Performance:** session history, weekly activity, PR, estimated 1RM (clearly an estimate), volume/muscle groups, weight/waist/measurement logging, basic charts.
3. **A1 mascot and overlay:** eventually genuinely rigged Live2D (separate layered asset production and SDK licensing needed), synchronized facial states with workout, drag/snap/hide/stop floating character, battery/permission-conscious native service. Sprite fallback.
4. **AI and voice:** Android speech input and TTS, safe backend proxy for model, training-aware assistant with user's approval for changes; optional with privacy safeguards.
5. **Nutrition and cardio:** current tailored CPB-style 3-meal reference, editable food targets, stairs + walking. No forced snacks, milk, yogurt, broccoli (user dislikes those).
6. **Android system integration:** rest notifications, widget, haptics, handling MIUI/HyperOS permissions and battery limitations.
7. **Data reliability and optional cloud:** Room sole truth, JSON import/export, validated V4.x migration, eventual Supabase account/sync with conflict resolution; default local/offline.

## 5. Existing customized plan (initial demo content)
- Four strength days: Mon chest/triceps; Tue back/biceps/abs; Thu shoulders/upper chest/abs; Sat legs.
- Mon: bench press 3x8–12; incline DB bench 3x8–12; pec deck 2x12–15; cable triceps pushdown 2x10–15.
- Tue: lat pulldown 3x8–12; seated row 3x8–12; DB curl 2x10–15; crunch 2x12–20.
- Thu: seated shoulder press 3x8–12; DB lateral raise 3x12–20; reverse pec deck 2x12–20; incline machine bench 2x10–12; cable crunch 2x12–15.
- Sat: leg press 3x8–12; leg extension 2x10–15; seated leg curl 3x10–15; standing calf raise 2x12–20.
- Tue/Thu mornings 10–15 stories of residential stair climbing; Wed 30min walk; Sunday optional walk. This is starting data, freely editable.
- Nutrition initial reference: breakfast 80g dry oats + 2 eggs + banana; lunch braised chicken drumstick + 250g cooked rice + 2 braised eggs + vegetables; dinner ~300g raw chicken breast, potatoes 250g, sweet potatoes 200g, vegetables 300g, oil 15g. Approximate goals ~2300 kcal training days / 2100 rest / 130–160g protein, not medical prescriptions.

## 6. Data migration from V4.x
- Legacy JSON LocalStorage key `forge_training_v33_2026`, root with `sessions`, `cardio`, `body`, `settings`, `startDate` among fields. `sessions` keyed as `YYYY-MM-DD__mon/tue/thu/sat`, sets under `session.sets[exerciseId]`; values `weight`, `reps`, `done`.
- User must export actual JSON from old app. Do not expect Codex to have the real export or credentials; no real user training export is included in this handoff.
- Import using Android SAF file picker → validate JSON → preview records/counts → Room transaction → import log/source hash/idempotency → per-section reconciliation. Repeated import must not duplicate sessions, and failure must roll back.
- V4.2 prototype SQLite is not a V5 Room DB; do not copy it directly. Keep V4 installed during migration. Prefer `com.thep.android.dev` or other separate debug ID (final ID decision pending).

## 7. Phase sequence (approved)
0. Tech PoCs: native Compose build, Room, V4 import parser, SDK/overlay compatibility on Xiaomi 15 Pro.
1. **V5.0 Alpha (DO FIRST):** native Compose design system, home/workout/progress/settings, Room, edit plan, sets/logging/rest, history, JSON migration & backup, tests. Verify end-to-end and process-restart persistence.
2. Beta: background rest alerts, overlay service, widget, regression and power management.
3. RC: *true* A1 Live2D layered model, SDK, animation/state mapping, fallback; license review. Current art is a flattened design sheet, not usable as an already-rigged model.
4. Full: AI voice, food/cardio refinement, optional secure cloud sync and end-to-end QA.

## 8. Work protocol
- Collaborate iteratively. Show plan, modify local project, run tests, share exact Gradle output. Don't call a feature 'done' before actual verification.
- Ask clarification only for missing essentials, don't redesign settled brand/technology. Use Chinese in user-facing progress.
- Keep sensitive fitness data and cloud credentials out of logs and source control. Use Git from day one.
