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
| Settings | `settings` | Label style, tuning, session length, theme, reminder opt-in, discard saved session (clears only the resumable session — never placement or skill progress), export your data (`20-PHASE-2-SPEC.md` §6) |

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

**Sizing:** every button in the active set is at least the minimum touch target (§1), always, on every screen size and at every font scale. **This is inviolable and outranks the no-scroll preference below.** A button too small to press is the worst failure this widget has, and it is a silent one: a `Column` of fixed-height slots in a bounded parent neither scrolls nor warns, it squeezes its trailing children to nothing. That is exactly what shipped through Phase 1 — on the 5-inch reference screen the tonic rendered at zero height and could not be pressed, at the very first skill node, for the entire life of the build. It went unnoticed because nothing measured it; `PracticeScreenLayoutTest` and `DegreeLadderLayoutTest` now do, on every run.

The ladder should not need to scroll, and at the node a user is actually on it does not. But when the active set genuinely cannot fit — seven buttons need `7×56 + 6×8 = 440dp` against roughly 308dp of usable height on a 5-inch screen once §4's mandated chrome is placed — the ladder scrolls rather than shrinking a button. The original rule here asserted that seven buttons plus gaps fit a 5-inch screen without scrolling; that was never true and the arithmetic had not been checked. See `20-PHASE-2-SPEC.md` §8.2.

**Gaps are not touch targets.** An inactive position is non-interactive and absent from the accessibility tree, so §1's minimum does not apply to it. It occupies a slim slot — enough to hold its place in the scale's shape, not a full button's height. Sizing gaps like buttons cost 44dp each for no benefit and was the direct cause of the squeeze above.

**Feedback:** on incorrect, the chosen button flashes muted red and the correct button pulses. Then the audio contrast sequence plays (`02-PEDAGOGY.md` §6). Do not advance until it completes.

---

## 3a. First-run task framing

*(Authored 2026-08-20 to resolve a dangling reference: `11-ONBOARDING-CLARITY.md` §3 and `20-PHASE-2-SPEC.md` §5 both cited this section before it existed in the repo. This is the general UI-level rule; `11-ONBOARDING-CLARITY.md` is the full standard and wins on any detail.)*

**No new task shape reaches a first-time user without a first-run explanation screen carrying a worked example.** This is a shipping gate, not a nicety — the Phase 1 build dropped users straight into a cadence-plus-degree exercise with nothing explaining what the sounds meant or what the buttons did, and it was unusable on first contact even for someone with music background. Explaining what a control does and what a sound means is not front-loading theory; `01-PRODUCT-SPEC.md` §6's "no forced tutorial" rule prohibits a wall of theory, not an explanation of the mechanic.

**What counts as a new task shape:** a different question being asked, a different answer control, or a different thing to listen for. Module 2's degree identification, mode identification, chromatic degrees, and prediction items are each a distinct shape. A *difficulty* change within a shape is not — a fading cadence, a wider register, a new key is the same task made harder, and gets the one-line in-context announcement of `11-ONBOARDING-CLARITY.md` §9.3, never a screen. Interrupting practice with a full explanation for something the user already knows how to do is its own defect.

**What the screen must contain**, per `11-ONBOARDING-CLARITY.md` §1's three steps, in this order:

1. A plain-language explanation of what will happen and what to do. Two to four short sentences. Every term the exercise uses is defined in the same breath it is first used, in the order the user will encounter it — including the causal link between what plays and what is being asked, which is never left to be inferred from audio alone.
2. One worked example in real audio — the same renderer and player the exercise itself uses — played through end to end with the correct answer shown and explained, before the user is asked to answer anything.
3. A dismiss/start button. Nothing else.

**What it must not be:** a gate. There is no comprehension check, no quiz, no forced repeat viewing, and no requirement to finish it before practicing. The session underneath is already starting behind it, so dismissing lands on a ready item rather than a spinner.

**When it appears: every time the learner enters that module.** Not once ever — every time. Opening a session in it, resuming a session in it, or the plan climbing onto its node partway through all count as entering, and each raises the screen. Within a module already entered it does not reappear between items; only leaving practice and coming back counts as entering again.

This replaces an earlier once-ever rule, by the maintainer's direct instruction after live use (2026-08-22), and the reason is worth keeping. Once-ever was tracked by persisted flags, so a screen dismissed on any past build became permanently unreachable, with nothing on screen saying so. Ending a session did not bring it back; neither did clearing the saved session, because that is a different store. The app read as having deleted its own instructions. A learner who does not need the screen dismisses it in one tap; a learner who does need it cannot get it back at all, and the second failure is far worse than the first.

Consequence, accepted deliberately: a returning learner sees their current module's explanation at the start of essentially every session. The screens are therefore held to `11-ONBOARDING-CLARITY.md` §9.4's brevity requirement more strictly than before, and dismissing must always be one tap from the top of the screen.

**Audio while it is up:** none. The session starting behind the screen refers to loading — the covered item renders and pre-renders, but its audio is withheld until the screen is dismissed. Playing it underneath, which the first implementation did, hands the user the sound and the sentence explaining the sound in the same instant, and teaches neither. Reported from live use on every module.

**Recall:** every screen that introduces a task shape carries a small, low-emphasis help affordance that reopens the explanation on demand, permanently. Recall shows the identical explanation and worked example, never an abbreviated version, and always the one for the module the learner is currently on — `11-ONBOARDING-CLARITY.md` §5. A user who forgets on their tenth session gets exactly what they got on their first.

**Acceptance:** a build stage that introduces a new task shape is not complete without this screen, and "the code works" does not satisfy it. This applies to every future module and phase (`11-ONBOARDING-CLARITY.md` §4, `20-PHASE-2-SPEC.md` §5.1), and belongs in the acceptance criteria of any `09-BUILD-PLAN.md` stage that adds one.

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
