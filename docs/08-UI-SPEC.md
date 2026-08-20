# 08 — UI Specification

Jetpack Compose, Material 3. Phone-first, portrait-primary. The interface must be usable one-handed, since a user will often be wearing headphones and holding the phone loosely.

---

## 1. Design stance

The user is listening, not reading. The screen's job is to stay out of the way.

- One decision on screen at a time.
- No staff notation. No piano keyboard. No music-theory jargon in primary copy.
- No timers, no countdown bars, no visible score during an exercise. Nothing that creates time pressure — see `02-PEDAGOGY.md` §6.
- Feedback is immediate, quiet, and brief. A correct answer is not a celebration.
- Large touch targets (minimum 56 dp), high contrast, generous spacing. The user's attention is on their ears.

---

## 2. Screen inventory (Phase 1)

| Screen | Route | Purpose |
|---|---|---|
| Onboarding | `onboarding` | 3 cards max. What this is, headphones suggestion, start. Skippable. |
| Diagnostic | `diagnostic` | M0 sub-tests, one at a time |
| Home | `home` | Start session, current progress at a glance |
| Practice | `practice` | The core loop |
| Session summary | `summary/{sessionId}` | What happened, what's next |
| Progress | `progress` | Mastery map, per-degree accuracy, confusion view |
| Settings | `settings` | Label style, tuning, session length, theme, reminder opt-in, discard saved session (clears only the resumable session — never placement or skill progress) |

No bottom navigation bar with four tabs. Home is the hub; Progress and Settings are reachable from it. The app has one job and the navigation should reflect that.

---

## 2a. Every screen must have a way out

*(Authored from the maintainer's written brief of 2026-08-19, which referenced this section before it existed in the repo; replace with the canonical text if one exists elsewhere.)*

- Every screen has a clear, always-visible way to leave it. A user with no way out of a confusing screen is the worst state the app can be in.
- Leaving Practice mid-session — by the visible control or the system back gesture — must never lose progress: the session is saved through the resume mechanism (`05-DATA-MODEL.md` `resumeStateJson`) and offered back on the next visit.
- Any control that claims to advance or change state must give immediate visible confirmation. A control with zero transition feedback is indistinguishable from nothing happening.

---

## 3. The degree ladder (the key widget)

This is the answer input for all of Module 2. It replaces the piano keyboard that every competing app defaults to.

**Layout:** a vertical column of buttons, lowest scale degree at the bottom, ascending upward. Vertical position maps to pitch height — the spatial metaphor is doing pedagogical work, reinforcing that these are ordered positions in a scale, not arbitrary labels.

**Contents:** only the degrees in the current active set. `M2.DEG_SET_1` shows three buttons (1, 3, 5) with the inactive positions rendered as dimmed, non-interactive gaps — so the visual shape of the scale is present from the start and new degrees appear in their correct slots rather than reshuffling the layout.

**Label style:** number (default) or solfège syllable, per settings. The toggle is instant and does not require a restart.

**States per button:** idle, pressed, correct (brief), incorrect (brief), disabled.

**Sizing:** the ladder must fit seven buttons plus the reserved gaps on a 5-inch screen without scrolling. Scrolling during an answer is unacceptable.

**Feedback:** on incorrect, the chosen button flashes muted red and the correct button pulses. Then the audio contrast sequence plays (`02-PEDAGOGY.md` §6). Do not advance until it completes.

---

## 4. Practice screen

Vertical layout, top to bottom:

1. **Minimal progress indicator** — a thin, unlabeled bar filling with elapsed session time, from session start toward the configured session length (`07-ADAPTIVE-ENGINE.md` §8's wall-clock budget). No item count, no percentage, no score, no digits. It fills forward and never counts down, which is what keeps it on the right side of §1's "no timers, no countdown bars": it answers "how much of my session have I used," not "how long until I'm cut off." An earlier item-count version was reported from live use as a bar that "continues to not move" — items answered is not what the session-length setting promises.
2. **Playback state** — a large, calm visual indicating that audio is playing, in which phase (reference / gap / target). This is not decoration: a user needs to know whether the app is still playing the setup or has moved to the question. Distinguish phases clearly and non-verbally.
3. **Replay button** — always available, unlimited, unpenalized. Replay count is recorded but never shown to the user or used against them. On an item that plays no reference of its own (an L1 group item, an L6/L7 audiation-block item), replay plays a brief home reminder in front of the target instead of the bare note again: replay exists for "I didn't catch that," and on a silent item the thing not caught is home itself. The first presentation stays silent — the retention demand is the level's point — and reliance on the reminder shows up in the recorded replay count, not in any penalty.
4. **Degree ladder** — the answer input.
5. **Skip** — small, low-emphasis, always present. A skipped item records `responseLabel = null` and does not enter the mastery window.

Between items: a short, consistent pause (~400 ms). Do not vary it randomly; unpredictable pacing is stressful.

---

## 5. Diagnostic screen

Same structural language as Practice, simpler answer widgets (two large buttons for binary choices).

Copy requirements:

- Frame as calibration, not testing. "Let's find your starting point."
- No score is shown at any point during or after.
- The result screen states where the user is starting and why in plain terms — never a level, grade, percentile, or diagnosis.
- For a user routed to the M1 remediation path, the copy is neutral and forward-looking: the app is starting with the fundamentals and will build up. **No implication of deficit.** See `02-PEDAGOGY.md` §8. If phrasing here is uncertain, stop and ask rather than improvising.

---

## 6. Progress screen

Three sections:

1. **Mastery map** — the skill graph as a simple vertical path: mastered, in progress, locked. Tapping a node shows which mastery criterion is currently unmet, in plain language ("you're at 87% — 90% needed" or "the reference is still playing before every note; it needs to fade further").
2. **Per-degree accuracy** — a horizontal bar per active scale degree. This is the most genuinely useful screen in the app: it tells a user that their 7 is weak.
3. **Confusion view** — plain-language statements derived from the confusion matrix: "You often hear 4 as 3." Not a matrix grid. A grid is correct and unreadable.

The independence check result (`M2.INDEPENDENCE_CHECK`) is surfaced here as the headline achievement, because it is the actual goal of the app.

---

## 7. Motivation mechanics (constrained)

Ear training is repetitive and drop-off is severe; some structure helps. But engagement mechanics slide easily into manipulation, and the well-documented dark patterns in this space — coercive notifications, guilt framing, loss-aversion pressure — are prohibited here.

**Permitted:**
- A streak counter, displayed passively on Home. **With forgiveness:** a missed day does not reset it immediately; a grace mechanism absorbs occasional misses. Streak forgiveness is the mechanic that reduces churn without coercion, and it is the only one worth having.
- Session completion acknowledgment: brief, factual.
- The mastery map as intrinsic progress.
- A single optional daily reminder, **default off**, user-scheduled.

**Prohibited:**
- Notifications the user did not explicitly enable.
- Any guilt, loss, or urgency framing in copy ("your streak is about to die", "you're falling behind", "don't lose your progress").
- Leaderboards, leagues, social comparison, competitive framing.
- Any mechanic where the app pushes rather than the user pulls.
- Celebration animations disproportionate to the achievement.

If a proposed mechanic works by making the user feel bad about not using the app, it does not ship.

---

## 8. Theming

- Material 3 dynamic color where available, with a defined fallback scheme.
- Full dark theme. Practice sessions happen at night with headphones on; a bright screen is a real problem.
- Contrast meeting WCAG AA minimum, including the ladder button states.
- Do **not** use color as the only signal for correct/incorrect — pair with shape, icon, or position change. Red/green alone fails for a meaningful share of users.

---

## 9. Accessibility

- Full TalkBack support. Every ladder button has a content description including its label and position.
- Audio exercises are inherently inaccessible to deaf users; the store listing should say so plainly rather than the app failing confusingly.
- Respect system font scaling up to 200% without layout breakage. Test the ladder at maximum scale.
- Respect `reduce_motion`; when set, replace animated transitions with instant state changes.
- Haptic feedback on answer, respecting the system haptics setting and the app's own toggle.

---

## 10. Copy rules

- American English throughout.
- Second person, plain, short. No exclamation marks in default copy.
- No music-theory term appears without being introduced in context first.
- The words "tone deaf," "talent," "gifted," and "natural ability" do not appear anywhere. Framing is: this is a trainable skill, and progress comes from repetition.
- All strings in `strings.xml`. No hardcoded user-facing text.
