---
name: CodeMastery
description: Mastery-based programming learning platform for beginner university students, with Learner, Instructor and Administrator workspaces.
colors:
  ink-950: "#0A0B0F"
  ink-900: "#15171C"
  ink-700: "#3A3E46"
  ink-600: "#50545C"
  ink-500: "#6B7079"
  ink-400: "#80858E"
  line-200: "#E4E4E7"
  fill-150: "#EEEEF0"
  canvas-100: "#F6F6F7"
  surface: "#FFFFFF"
  chrome-raised: "#1C1E24"
  chrome-hover: "#2A2D35"
  chrome-muted: "#A3A7B0"
  focus-on-chrome: "#7DB7FF"
  brand-700: "#0652C4"
  brand-600: "#0866F2"
  brand-400: "#3B9CFA"
  brand-100: "#E3EEFF"
  sky-100: "#DFF6FB"
  sky-50: "#EEFAFD"
  mint-100: "#D3F7D9"
  mint-50: "#E9FBEC"
  mint-700: "#1F7A3A"
  butter-100: "#FCEBB8"
  butter-50: "#FEF5DB"
  butter-800: "#7A5200"
  lilac-100: "#F1D9FB"
  lilac-50: "#F8ECFD"
  lilac-700: "#7A2FB8"
  rose-100: "#FDE2E1"
  rose-50: "#FEF0EF"
  rose-700: "#B42318"
  rose-800: "#951C13"
  data-mastered: "#2E9E4F"
  data-learning: "#0866F2"
  data-weak: "#B07A00"
  data-unknown: "#80858E"
typography:
  display:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "3.5rem"
    fontWeight: 600
    lineHeight: 1.05
    letterSpacing: "-0.03em"
  page-title:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "2.5rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-0.025em"
  section-title:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.75rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  item-title:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.015em"
  title:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.35
    letterSpacing: "-0.005em"
  stat:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "2.5rem"
    fontWeight: 600
    lineHeight: 1
    letterSpacing: "-0.03em"
    fontFeature: "'tnum' 1, 'lnum' 1"
  body-reading:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.7
  body:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
  body-sm:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 600
    lineHeight: 1.2
  caption:
    fontFamily: "'Google Sans Flex', ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: 1.4
  code:
    fontFamily: "'JetBrains Mono', ui-monospace, monospace"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.6
rounded:
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  pill: "999px"
spacing:
  "2xs": "4px"
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "24px"
  xl: "32px"
  "2xl": "40px"
  "3xl": "48px"
  "4xl": "64px"
components:
  button-primary:
    backgroundColor: "{colors.chrome-raised}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 24px"
    height: "48px"
  button-primary-hover:
    backgroundColor: "{colors.chrome-hover}"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    borderColor: "{colors.line-200}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 20px"
    height: "48px"
  button-secondary-hover:
    backgroundColor: "{colors.fill-150}"
    textColor: "{colors.ink-900}"
  button-tertiary:
    textColor: "{colors.brand-600}"
    typography: "{typography.label}"
    padding: "0 12px"
    height: "48px"
  button-on-chrome:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-950}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 20px"
    height: "40px"
  button-on-chrome-secondary:
    backgroundColor: "{colors.chrome-raised}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 20px"
    height: "48px"
  button-danger:
    backgroundColor: "{colors.rose-700}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 24px"
    height: "48px"
  icon-button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    borderColor: "{colors.line-200}"
    rounded: "{rounded.pill}"
    size: "44px"
  icon-button-primary:
    backgroundColor: "{colors.chrome-raised}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    size: "44px"
  search-pill:
    backgroundColor: "{colors.fill-150}"
    textColor: "{colors.ink-900}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "0 20px 0 52px"
    height: "52px"
  text-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    typography: "{typography.body}"
    rounded: "{rounded.md}"
    padding: "0 16px"
    height: "52px"
  tile:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.lg}"
    padding: "24px"
  aside-panel:
    backgroundColor: "{colors.fill-150}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.xl}"
    padding: "32px 24px"
  segmented-control:
    backgroundColor: "{colors.fill-150}"
    textColor: "{colors.ink-600}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "4px"
    height: "44px"
  course-mark-logo:
    backgroundColor: "{colors.canvas-100}"
    rounded: "{rounded.md}"
    size: "48px"
  course-mark-generic:
    backgroundColor: "{colors.canvas-100}"
    textColor: "{colors.brand-700}"
    rounded: "{rounded.md}"
    size: "48px"
  account-avatar:
    backgroundColor: "{colors.brand-700}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    size: "40px"
  status-pill:
    backgroundColor: "{colors.fill-150}"
    textColor: "{colors.ink-900}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 14px"
    height: "36px"
  tag-mastery-unknown:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-700}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    height: "28px"
  tag-mastery-learning:
    backgroundColor: "{colors.sky-100}"
    textColor: "{colors.ink-900}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    height: "28px"
  tag-mastery-mastered:
    backgroundColor: "{colors.mint-100}"
    textColor: "{colors.ink-900}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    height: "28px"
  tag-mastery-weak:
    backgroundColor: "{colors.butter-100}"
    textColor: "{colors.ink-900}"
    typography: "{typography.caption}"
    rounded: "{rounded.xs}"
    height: "28px"
  guidance-entry:
    backgroundColor: "{colors.lilac-100}"
    textColor: "{colors.ink-900}"
    rounded: "{rounded.lg}"
    padding: "16px"
---

# CodeMastery — UI/UX Design Specification

This file is the single UI/UX specification for the CodeMastery frontend. It
defines the visual system, information architecture, user flows, screen
specifications and implementation handoff that Claude Code, Codex or a human
engineer must follow when rebuilding the frontend. It is a design document: it
does not approve APIs, routes, dependencies, files or backend behavior.

## Document Control

| Item | Value |
| --- | --- |
| Revision | `DESIGN-2026-11-08-r7` — aligned with the delegated technical decisions of 8 November 2026 (route map, Learner home scope, Markdown policy, notification fields, shared Mastery Map location; no visual change); `DESIGN-2026-11-08-r6` made factual and consistency corrections against `main`; `DESIGN-2026-11-07-r5` set the tile arrows and the mask-cut canvas reach; `DESIGN-2026-11-07-r4` set the button hierarchy; `DESIGN-2026-11-07-r3` implemented the consistency fixes C1–C14; `DESIGN-2026-11-07-r2` reconciled this file with the implemented UI; `DESIGN-2026-11-06-r1` was the full rewrite that replaced the 5 November 2026 "UX/UI Design Foundation" baseline |
| Repository state audited | r7: `main` at `4eb5b70be0c98df14e162b34d2c0314b02ab47b5` (8 November 2026) plus the decision records added to `docs/setup.md`, `docs/data-model.md` and `docs/repository-structure.md` in the same change; no code was read anew or changed. r6: `main` at `882751e0d16556e665cc4cf53dda22ebf9dc7ba6` (equal to `origin/main`, 8 November 2026, Asia/Ho_Chi_Minh), read from source: routes, theme tokens, shared components, the preview registry and its tests. r6 changed no code and re-ran no browser check. r2–r5: the redesign branch `feat/uiux-design-r1-implementation` at `2691e95dc1a1439f77d0e15f9b3a32ee19ce02c6` plus the 7 November 2026 working-tree changes, audited from source and in Chrome on 7 November 2026; that work is now part of `main`. r1: `main` at `061102e31a47cb73d43a92bb429c7d114492005a` (6 November 2026, Asia/Ho_Chi_Minh) |
| Design authority | The task instruction of 6 November 2026 from the project Leader (the session user; identity not verified beyond the local Git identity) explicitly delegated UI/UX and frontend-presentation decisions in this document. That delegation covers visual direction, design tokens, component specifications, layouts, interaction patterns and UX copy. It does **not** approve API contracts, route paths, role/permission rules, dependencies, new repository files, business rules or implementation start. |
| Prepared by | Claude Code (Anthropic, Claude Opus 5.5) with the installed Impeccable design skill, under the delegation above; r2 by Claude Code at the Leader's instruction of 7 November 2026 to make this file match the implemented UI. The previous baseline was prepared with OpenAI Codex. No reviewer approval is claimed here. r6 by Claude Code (Claude Opus 5.5) at the Leader's documentation-alignment instruction of 8 November 2026. r7 by Claude Code (Claude Opus 5.5) at the Leader's instruction of 8 November 2026 to align this file with the recorded decisions. |
| Version control | Tracked in Git at the repository root from r6; earlier revisions were a local file excluded from Git. |
| Visual reference | "E-learning Platform — Learning Plan Dashboard" by Bogdan Nikitin for Nixtio, published on Dribbble (shot 23956256). Its visual language was studied. No logo, artwork, illustration, avatar, copy or screen composition from it is reused (see [Reference Analysis](#reference-analysis)). |
| Governs | Root `CLAUDE.md` § Impeccable: this file is the authoritative UI/UX specification. It is subordinate to mandatory requirements, approved architecture, repository structure, ADRs and API contracts. If those conflict with this file, they win and the Blocker Protocol applies. |

### Status vocabulary

Every screen, flow and integration in this file carries one of these labels.

| Label | Meaning |
| --- | --- |
| **Implemented** | Routed in the production app on `main` with an approved contract. The redesign restyled it and kept its behavior. |
| **Preview** | Built as presentation components and shown only inside `/preview` with sample data. Not routed in production because its contract or route is pending. The layout and components in this file describe what is built. |
| **Decided** | A design decision made under the 6 November 2026 delegation. It is binding for implementation unless it is superseded by a later Leader-approved revision of this file. |
| **Proposed** | A UX concept that needs an approval this file cannot grant, usually a behavior change or a contract that does not exist yet. Routes and menu destinations are decided in CT16.routes (`CT16-ROUTES-2026-11-08-r1`); an approved route does not by itself make a Proposed screen buildable. |
| **Contract Pending** | The screen design is complete but the owning API/domain contract is DEFERRED. It must not be built against invented endpoints or data. |
| **Blocked** | Cannot be designed or built further without a missing decision. The dependency is named. |

A screen can carry two labels, for example Learner home: **Implemented** at
`/home` with only the sections its approved contract supports, and **Preview**
for the full design.

Route paths in the [sitemap](#sitemap) are approved by CT16.routes
(`CT16-ROUTES-2026-11-08-r1`). Approval is not registration: a path is added to
production, with its navigation item, only after its screen's contracts are
approved, its Issue is authorized and the screen is delivered. Until then the path
shows Page not found.

### Sources of truth used

| Area | Source | Use in this file |
| --- | --- | --- |
| Mandatory scope, roles, quality, English | [requirements](docs/requirements.md) §§3–6, 8 | Responsive desktop + mobile, English UI and demo data, no interface cloning (§3 objectives) |
| Product, user stories, adaptive flow | [architecture](docs/architecture.md) §§1–7, US-01–US-17 | Page inventory, flows, adaptive placement ("Next recommended", Skippable/Remedial labels, Mastery Map) |
| Adaptive semantics | [adaptive-learning](docs/adaptive-learning.md) §§4–11, 15 | Four states, three decisions, no hard block, explainability; deferred thresholds and algorithms |
| Data semantics | [data-model](docs/data-model.md) §§6–8, 11 | State catalogues, lesson progress flags, quiz scoring, submission and report lifecycles |
| Approved API | [OpenAPI](docs/api/openapi.yaml) | Exactly eight operations (listed in [Implementation Handoff](#implementation-handoff)) |
| Frontend contracts | [setup](docs/setup.md) CT16-2026-10-24-r1, CT16-FINAL-2026-10-25-r1 (F1, F2), CT16-PROFILE-2026-10-25-r1; [ADR002](docs/decisions/002-interim-delegated-contracts.md) FE07-2026-10-27-r1 | Session, routing, Profile, membership and accessible-composition rules preserved below |
| Delegated technical decisions (8 November 2026) | [`docs/setup.md`](docs/setup.md#ct16-route-map-learner-home-composition-and-ct37-markdown-policy--8-november-2026) (`CT16-ROUTES-2026-11-08-r1`, `CT16-LEARNER-HOME-2026-11-08-r1`, `CT37-MARKDOWN-2026-11-08-r1`), [data model](docs/data-model.md) (`CT11-READ-FIELDS-2026-11-08-r1`), [repository structure](docs/repository-structure.md) (`STRUCT-MASTERY-MAP-2026-11-08-r1`) | Approved routes and navigation, Final Learner home scope, Markdown rendering policy, notification fields, shared Mastery Map location. Approval of a decision is not implementation |
| Module ownership | [ADR001](docs/decisions/001-modular-monolith-module-boundaries.md) | Feature ownership; Interaction owns notifications; Admin consumes, does not decide |
| Current implementation | `frontend/src/**` (theme in `app/theme/theme.ts`, shared components in `shared/components/`, preview in `app/preview/`), `frontend/package.json`, `frontend/public/favicon.svg` | Implemented behavior and presentation; r2 values were read from these files and re-checked against `main` `882751e` for r6 |

### Implementation status (8 November 2026)

This is the state of the UI on `main` at `4eb5b70` (no frontend change since `882751e`). Statuses per screen are in the
[page inventory](#page-inventory); the visual system below describes what is built.

| Status | What it covers |
| --- | --- |
| **Implemented (production routes)** | Shell (ink frame, top bar with canvas reach, mobile drawer, canvas), landing page `/`, catalogue `/courses`, course detail and membership `/courses/:courseId`, sign in `/login` with the session-ended notice (F2), create account `/register`, Learner home `/home` (active memberships only), My courses `/my-courses`, Profile `/profile`, Page not found, Access denied, design preview `/preview`. Fonts, logo palette, header lockup, course marks and account avatar as specified in this file. |
| **Preview only (sample data in `/preview`)** | Full Learner home; course workspace (Path); lesson reader with Q&A; placement diagnostic; course assessments; quiz introduction, attempt, result and history; assignment; Mastery Map; notifications; Instructor course editor, lesson editor, question bank, quiz and assignment editors, lesson Q&A, skill graph editor, grading, learner insights; Administrator overview, users, courses, content reports and audit log; sign-in sample states. |
| **Proposed (shown in `/preview`, behavior or contract not approved; routes approved in CT16.routes)** | Teaching home; forgot password, reset password and expired reset link (CT02). |
| **Not implemented** | Instructor and Administrator navigation in production (both roles see only Catalogue); Instructor and Administrator start pages (they sign in to `/courses` until `/teach` and `/admin` are routed under CT16.routes; a Learner signs in to `/home`); the approved feature routes of the sitemap; progress, resume, lesson content, assessments, mastery, recommendations, notifications, Q&A, reports, users, metrics and audit APIs; Markdown rendering (policy approved in CT37.markdown); a dark theme. |

## Overview

**Creative North Star: "The Lit Path"**

CodeMastery shows a beginner programmer where they are, what they already know,
and the next sensible step, without hiding the course everyone shares. The
interface is a dark frame around a bright, quiet working canvas. The frame is
the product's constant: navigation, identity and focus live in it. The canvas is
where learning happens: large, calm white tiles on a pale ground, connected by
the dashed path line that runs through lessons and through the skill graph. The
path line comes from the logo, whose hexagonal "C" holds three connected skill
nodes. Colour is semantic: the four pastel families mean the four mastery states
and the adaptive guidance, never decoration.

The product is used by first-year university students in lecture halls,
libraries and dorm rooms, mostly on laptops in daylight and on phones between
classes. It is also used by instructors at a desk authoring content and grading,
and by an administrator handling users and reports. The design therefore works
in **Operate** mode for every workspace screen: familiarity, legibility and
state clarity outrank expression. Character comes from precise details: the
frame, the canvas reach, the path line, the type and the mastery palette.

**Design principles**

1. **Next step first.** Every learner screen answers "what should I do now?" before it shows history or totals.
2. **Four kinds of truth, never blended.** Lesson completion, course progress, assessment scores and skill mastery are different facts. Each has its own component, wording and colour, and none stands in for another (see [Domain Presentation Rules](#domain-presentation-rules)).
3. **Explain every adaptation.** A recommendation always shows its type, its target and the reason supplied by the backend. The canonical course order stays visible next to it.
4. **Guide, never lock.** A prerequisite gap produces a warning and a suggested review, never a lock icon or a disabled lesson.
5. **Honest data.** Real screens show decoded API data, including empty, unavailable and forbidden states. No placeholder numbers, invented charts, fake avatars or demo values outside `/preview`. Since 7 November 2026 the preview screens carry no visual "Sample data" label (Leader decision, see [S26](#s26-design-preview--implemented-route-no-preview-chrome)); the index subtitle says the screens use sample data, and some screens keep a short note naming the contract they stand in for.
6. **Earned familiarity.** Standard controls, standard navigation, standard tables. The frame and path line carry the identity, so controls stay ordinary.
7. **Accessible by construction.** Keyboard paths, visible focus, text labels for every state and reflow at 320 px are part of every component specification.

**Key Characteristics**

- Ink frame (`ink-950`) with the top bar and a canvas that rises into the active navigation item ("the canvas reach").
- Pale canvas (`canvas-100`) with borderless white tiles at 24 px radius.
- A tinted contextual aside (`fill-150`) carrying stacked, colour-coded guidance entries.
- A dashed path line linking ordered lessons and prerequisite skills.
- Pill-shaped buttons, filters and status chips; 44 px circular icon actions; neutral outlined circles for opening a tile and a graphite circle only for Resume/Continue; neutral outlined secondary buttons.
- One UI family (Google Sans Flex) in a fixed product scale with large medium-weight titles; JetBrains Mono for code; Poppins ExtraBold only in the wordmark.
- Brand blue from the logo for interaction (links, focus, selection, progress); ink for primary buttons.
- Identity blue (solid `brand-700`) for the signed-in account avatar only; no gradient fills on UI surfaces (the logo artwork and the skeleton shimmer are the only gradients).
- Course marks: one neutral container for every course, holding the official full-colour logo of the course's language or a Deep Logo Blue code glyph.

### Reference Analysis

The primary reference image (3228 × 2638 PNG still) was opened and inspected
directly in a headless browser on 6 November 2026. Colours below were sampled
from its pixels. The shot also contains an MP4, which was **not** reviewed. The
typeface was identified by eye as a Gilroy-style geometric grotesque; this was
not confirmed against metadata.

**Decomposition**

| Aspect | What the reference does |
| --- | --- |
| Composition | A device-like frame: pure black (`#000000`) border and top bar, a rounded light canvas inset with roughly 8 px of black showing at the sides and bottom. Two zones: a main work area (about 70%) and a right "My Events" column (about 30%) on a slightly darker panel (`#EFEFEF`). |
| Top bar | Wordmark left; centred icon navigation in which only the active item ("Learning Plan") shows its label; the canvas edge rises in a small rounded hump under the active item; user avatar, name, email and chevron on the right. |
| Main area | Large page title with search pill top-right; a left vertical rail of circular view toggles (active toggle is a black circle); a two-column zig-zag of lesson tiles joined by a green dashed connector with dots (`#5DB951`); a stat trio (Total / Completed / Upcoming) whose third tile is a dashed outline. |
| Tiles | White (`#FFFFFF`) on `#F6F6F6`, no border, no visible shadow, roughly 28 px radius. Title about 26 px medium weight, two-line description in grey (`#535353`), footer with a status pill left and circular actions right (grey `#EFEFEF` circles, black confirm circle). A circular badge overlaps the top-right corner. |
| Aside | Stacked entries tinted sky (`#DFF8FB`), lilac (`#F0D0FB`), butter (`#FBE8B3`) and mint (`#CDFAD2`), each with a header row (icon, type, date) and a lighter inner panel (`#EDFBFD`, `#F5E1FC`, `#FDF0CE`). A white pill holds the entry action ("Start at 12:30"). A dashed empty slot ends the stack. |
| Typography | One geometric sans. Page titles about 44 px, tile titles about 26 px, body about 16 px, stat numerals about 40 px. Medium-to-semibold weights; no light weights; tight tracking on display sizes. |
| Data display | Counts only (26 / 2 / 23); state shown by pill text plus tint. No charts or tables. |
| Ornament | Emoji beside titles and in pills, 3D cartoon avatars, two tilted cards, a floating colourful tool dock, music-note doodles. |

**Strengths to keep.** Very clear two-level hierarchy (page title, tile title);
generous and consistent spacing; a frame that makes the canvas feel like a
working surface; a path metaphor that matches mastery learning; colour used in
families rather than as scattered accents; few, large, confident controls.

**Weaknesses to correct.** Icon-only navigation hides destinations; secondary
grey text and pastel tiles are near contrast limits; emoji and tilted cards are
presentation-shot theatrics that break in a working product; lock icons on
upcoming lessons imply hard blocking, which CodeMastery forbids; the stat trio
and pills carry no interaction states, focus treatment or error states; there
is no table, form or dense-data language at all.

**Adopt, adapt, reject**

| Reference element | Decision for CodeMastery |
| --- | --- |
| Black frame + top bar + inset rounded canvas | **Adopt.** Ink `#0A0B0F` (slightly cool to sit with the logo blue). |
| Canvas hump under the active nav item | **Adopt** as the signature "canvas reach", always paired with `aria-current` and a white label. |
| Icon-only nav with one labelled item | **Adapt.** All items labelled at ≥ 1200 px; at 900–1199 px only non-active items collapse to icons with tooltips and accessible names. |
| Page title + search pill on one row | **Adopt.** |
| Left circular view rail | **Adapt** into the View Rail used only where one dataset has several views (Mastery Map graph/list, course path/outline). |
| White borderless tiles, ~28 px radius | **Adopt** at 24 px (`rounded.lg`). |
| Zig-zag tiles with dashed connector | **Adapt** into the Path Spine: one column of lesson tiles with the dashed line in the left gutter. Zig-zag is rejected; it breaks reading order and reflow. |
| Status pill + circular actions in tile footer | **Adopt.** |
| Black check circle | **Adapt** into the ink circular primary action ("Open lesson"); lesson completion lives inside the lesson. |
| Corner badge on tile | **Adapt** to adaptive markers (Recommended, Test-out, Remedial, Prerequisite gap). Always duplicated by a text tag. |
| Lock icons on upcoming items | **Reject.** Contradicts the no-hard-block rule. |
| Stat trio with dashed third tile | **Adopt** only for real counts. The dashed outline means "not yet" (Remaining, Unknown). |
| Tinted aside entries with inner panel and white pill action | **Adopt** as the Guidance Stack. Tints are semantic. |
| Dashed empty slot | **Adopt** for empty states and the file drop zone. |
| Geometric sans, medium titles | **Adopt** with Google Sans Flex (openly licensed; see Typography). |
| Emoji, 3D avatars, doodles, tilted cards, tool dock | **Reject.** |
| Avatar + name + email in top bar | **Adapt** to monogram + display name + role. Email must not be shown (CT16-PROFILE P2). |

**Fidelity contract.** Screens marked *High fidelity* in the
[page inventory](#page-inventory) must be recognisably in the reference's visual
language at about 90%: frame and reach, canvas and aside colours, tile radius and
padding, title scale, pill and circle controls, path line and stat trio. The
figure measures how closely these screens follow the reference's visual language
and design characteristics. It is not a target for reproducing the reference:
its composition, screens, artwork, illustrations, avatars, copy and identity are
not copied, and every CodeMastery screen is composed from its own content and
tasks. Screens
marked *Adapted* use the same tokens with a task-specific composition. Screens
marked *Specialized* (quiz attempt, editors, admin tables, auth) share tokens and
components but have their own structure; forcing the dashboard shape onto them
would harm the task. This matches the course requirement to address a learning
need "rather than merely cloning the interface of an existing platform".

### Anti-patterns

- Same-size cards as page scaffolding; cards inside cards; KPI hero rows with invented trends.
- Gradient text, gradient fills, glass blur, glowing halos, decorative purple-blue gradients.
- Emoji or Unicode symbols as icons. Icons are drawn SVG in one stroke style.
- Eyebrow or kicker labels above headings, and section numbers.
- Coloured `border-left` stripes on alerts, list items or tiles.
- Lock icons or disabled states for prerequisite gaps.
- "Mastery %", progress rings or confidence numbers. No numeric mastery exists.
- Spinners in the middle of content. Use skeletons.
- Modals for tasks that do not need interruption.
- Placeholder copy ("Lorem ipsum", "No data available") and fabricated demo values in connected screens.

## Colors

A restrained ink-and-canvas neutral system, one brand blue for interaction, and
five pastel families with fixed semantic jobs. Every value is in the frontmatter;
contrast ratios below were computed from the hex values (WCAG 2.x relative
luminance), not measured in a rendered browser.

### Primary

- **Logo Blue** (`brand-600`): links, focus rings, selected states, progress fills, the Learning state glyph, the current Path Spine node, selected graph nodes and edges. 5.01:1 on white, 4.64:1 on canvas. Taken from the logo's core stops (`#0866F2`).
- **Deep Logo Blue** (`brand-700`): link hover and pressed, link text on tinted grounds (6.21:1 on `sky-100`).
- **Light Logo Blue** (`brand-400`): decorative only (Path Spine dashes, graph highlights). 2.87:1 on white, so never text or a sole state cue.
- **Blue Wash** (`brand-100`): selected row and selected menu item background.

### Identity blue

- **Identity blue** (`brand-700`, solid): the signed-in account avatar (top bar 40 px, Profile 64 px) with a white initial (6.97:1), and the generic course mark's glyph on its neutral container (6.46:1 on `canvas-100`). Other people's monograms (Q&A authors) stay on ink. The brief gradient "brand fill" of 7 November 2026 was withdrawn the same day (C3).

### Neutral

- **Ink** (`ink-950`): the frame, top bar, code panels, tooltips, toasts; the label of white on-chrome buttons.
- **Ink Raised / Graphite** (`chrome-raised`): the fill of primary buttons, primary circles and chosen controls (selected page, View Rail toggle, answered quiz questions); hover and pressed surfaces on the frame; on-chrome secondary buttons; the current state of a frame utility (the bell on Notifications).
- **Ink Hover** (`chrome-hover`): hover of every graphite control (primary buttons and circles, on-chrome secondary).
- **Chrome Muted** (`chrome-muted`): inactive nav labels and icons on the frame (8.16:1 on ink).
- **Focus on Chrome** (`focus-on-chrome`): focus ring on the frame (9.45:1 on ink).
- **Text** (`ink-900`): all primary text (16.6:1 on canvas).
- **Text Strong Secondary** (`ink-700`): secondary text on tinted entries and tags.
- **Text Secondary** (`ink-600`): descriptions, metadata, table secondary cells. Required on `fill-150` (6.56:1).
- **Text Tertiary** (`ink-500`): timestamps and helper text on white or canvas only (4.98:1 / 4.61:1). Forbidden on `fill-150` (4.30:1 fails).
- **Strong Line** (`ink-400`): input borders, dashed "not yet" outlines, unchecked controls, upcoming Path Spine nodes (≥ 3.2:1 on every neutral ground).
- **Hairline** (`line-200`): table row separators and dividers only.
- **Panel Fill** (`fill-150`): aside panel, icon buttons, search pill, secondary buttons, neutral pills, segmented tracks.
- **Canvas** (`canvas-100`): the working ground inside the frame.
- **Surface** (`surface`): tiles, tables, forms, dialogs, menus.

### Semantic families

Each family has a tint (container), a soft inner layer, and a strong colour for
glyphs and small text. Text on tints is `ink-900`; secondary text on tints is
`ink-600` or `ink-700`.

| Family | Tint / soft / strong | Fixed meaning | Never use for |
| --- | --- | --- | --- |
| Sky | `sky-100` / `sky-50` / `brand-600` (text: `brand-700`) | Skill state **Learning**; information; "Total" stat | Success or warnings |
| Mint | `mint-100` / `mint-50` / `mint-700` | Skill state **Mastered**; lesson **Completed**; success; correct answer | Decoration, "new" badges |
| Butter | `butter-100` / `butter-50` / `butter-800` | Skill state **Weak**; prerequisite-gap warning; deadlines | Errors, recommendations |
| Lilac | `lilac-100` / `lilac-50` / `lilac-700` | Adaptive guidance: every recommendation (Continue, Test-out, Remedial), "Next recommended", diagnostic prompt | Mastery states |
| Rose | `rose-100` / `rose-50` / `rose-700` (danger button hover `rose-800`) | Errors, incorrect answers, destructive actions, locked accounts | Weak mastery |
| Neutral dashed | `surface` + dashed `ink-400` | Skill state **Unknown**; "Remaining"; empty slots; drop zones | Anything that exists |

Data-visualisation fills (`data-*`) are the stronger versions used for bars and
stacked segments, where pastel tints would fail the 3:1 graphic contrast:
Mastered 3.43:1, Learning 5.01:1, Weak 3.73:1, Unknown 3.71:1 on white. Unknown
segments also carry a diagonal hatch so they never depend on colour.

### Named Rules

**The Semantic Pastel Rule.** A pastel tint always means its family's job. If a
region is tinted, a reader can name why. Decorative tinting is forbidden.

**The Two-Blues Rule.** Graphite is for doing (primary buttons, primary circles); Logo
Blue is for where you are and what is selected (links, focus, selection,
progress). A primary button is never blue and a link is never ink. Identity
blue (`brand-700`) on the account avatar names the user; it is not a third
interactive colour.

**The Never-Alone Rule.** No state is communicated by colour alone. Every tinted
state carries a text label and a glyph (CT16-FINAL F2).

## Typography

**UI Font:** Google Sans Flex 4.005 (Google Fonts v23, SIL Open Font License 1.1),
the one primary UI typeface. Self-hosted variable WOFF2 on the weight axis (1–1000;
400–700 used), subset like the Google Fonts API into Latin, Latin Extended and
Vietnamese files with `unicode-range`, `font-display: swap`, fallback
`ui-sans-serif, system-ui, -apple-system, 'Segoe UI', sans-serif`.
**Code Font:** JetBrains Mono 2.211 (SIL OFL 1.1), variable weight 100–800, the
same three subsets. It is the theme's `codeFontStack` and the only face for
`pre`, `code`, `kbd`, `samp`, code blocks, predict-the-output snippets, the
code-snippet field and raw identifiers.
**Brand Font:** Poppins ExtraBold 800 4.004 (SIL OFL 1.1), one Latin file
declared as the family "CodeMastery Wordmark" so no other text can match it. It
sets only the "CodeMastery" wordmark (see Brand and logo).

**Selection (7 November 2026).** The Leader ranked PP Neue Montreal 3, Suisse
Int'l, Google Sans Flex, Inter and Be Vietnam Pro for the UI. PP Neue Montreal 3
(Pangram Pangram) and Suisse Int'l (Swiss Typefaces) are commercial: the project
holds no web-embedding licence that covers distribution to every team member
and every visitor, so neither can be bundled. Google Sans Flex, the next in rank,
is openly licensed, has complete Vietnamese coverage (Đ/đ, ă, â, ê, ô, ơ, ư and
every tone-marked letter; verified against the shipped files), a continuous
weight axis and small files, so it was selected and the lower-ranked candidates
were not evaluated further or installed. Its subset omits `₫` (U+20AB), which the
product does not display.

**Character:** a contemporary humanist-geometric sans with open apertures, a
double-storey `a`, generous x-height and calm proportions; legible at 13 px, with
tabular figures for scores and timers. Figtree (the UI face from 6 to 7 November
2026) and Plus Jakarta Sans (the face before it) are retired. They are historical
only: no current file, token or rule uses them.

### Hierarchy

| Role | Size / weight / line height | Use |
| --- | --- | --- |
| Display | 56 px / 600 / 1.05, tracking −0.03em (40 px below 600 px) | Landing hero title only (Persuade surface); theme variant `display` |
| Page title | 40 px / 600 / 1.1, tracking −0.025em (32 px below 600 px viewport) | One `h1` per page |
| Section title | 28 px / 600 / 1.2 (24 px below 600 px) | `h2` of page sections and aside panels |
| Item title | 24 px / 600 / 1.2 (20 px below 600 px) | Tile titles (lessons, courses), dialog titles, quiz question text |
| Title | 18 px / 600 / 1.35 | List item titles, table emphasis, skill names, form section titles |
| Stat | 40 px / 600 / 1, tabular lining figures | Stat trio, score on results |
| Body reading | 17 px / 400 / 1.7, max 70ch | Lesson content, long descriptions, feedback text |
| Body | 16 px / 400 / 1.55, max 65ch in prose | Descriptions, form text, dialog bodies |
| Body small | 15 px / 400 / 1.5 | Table cells, dense lists, secondary panels |
| Label | 15 px / 600 / 1.2 | Buttons, pills, tabs, nav, form labels |
| Caption | 13 px / 500 / 1.4 | Timestamps, helper text, tags, table headers. Never smaller. |
| Code | 15 px monospace / 1.6 | Code blocks, predict-the-output snippets, audit action codes |

### Named Rules

**The One Family Rule.** Google Sans Flex carries everything except code and
the wordmark. No other display face, no italic display, no uppercase tracked labels.

**The Wordmark Only Rule.** The brand face appears only in the "CodeMastery"
wordmark, never in headings, buttons or navigation.

**The Fixed Scale Rule.** Sizes are fixed rem steps that change only at the
600 px breakpoint. No `clamp()` or viewport units anywhere. One documented
exception: the top-bar lockup takes one more fixed step below 390 px (32 px mark)
so the narrowest bars never scroll; it is local to the lockup, not a global
breakpoint.

**The Monospace Is For Code Rule.** Monospace appears only for code, program
output and raw identifiers (audit codes, IDs on the Profile page).

## Brand and logo

**Mark.** `frontend/public/favicon.svg` is the one authoritative artwork; `BrandLogo`
renders it and `favicon.ico` (16/32/48/64 px) is generated from it. The geometry
(hexagonal "C", detached lower stroke, two links, three skill nodes) is unchanged.

**Palette on the ink frame (7 November 2026).** Every region of the previous mark
was nearly the same `#0866F2` (outer nodes `#3B9CFA`), which reads dim on `ink-950` (3.9:1) and merges the regions.
The refined palette keeps one family of blues and adds a single accent:

| Region | Colour | On `ink-950` | Role |
| --- | --- | --- | --- |
| Hexagonal C | Gradient `#5A96FF` → `#3A76FA` → `#2A62F0` (top left to bottom right) | 3.8–6.8:1 | Dominant shape, electric blue |
| Lower stroke | `#2A62F0` → `#4C8BFF` | 3.8–6.0:1 | Continues the C |
| Links | `#7BA6FF` → `#5A96FF` | 6.8–8.2:1 | Lighter so the thin connectors read |
| Outer nodes | `#46C6FA` → `#1FA0F0` | 6.9–10.0:1 | Sky, separate from the C |
| Focal node | `#B9A4FF` → `#8C6DFA` | 5.3–9.2:1 | Violet accent: the learner's current skill |

Blue, sky and violet are neighbours, so the mark stays calm; there is no glow,
no extra gradient layer and no neon. The UI accent `brand-600` is unchanged:
the mark is brighter because it sits on ink. One variant serves dark and light
backgrounds (checked at 16–160 px on `#0A0B0F` and `#FFFFFF`); no separate
light-background asset is needed.

**Wordmark.** `BrandWordmark` is the only wordmark component (top bar, drawer,
landing header, auth brand panel): the decorative mark plus "CodeMastery" in
Poppins ExtraBold 800, tracking −0.035em, white on ink, nudged 0.04em down for
optical centring. Mark, gap and text scale from one size: text 0.625 × the mark,
gap 0.3 × the mark, so the lockup keeps its proportions.

| Surface | Mark | Wordmark |
| --- | --- | --- |
| Top bar and landing header, ≥ 600 px (80 px bar) | 40 px | 25 px |
| Top bar and landing header, 390–599 px (64 px bar) | 36 px | 22.5 px |
| Top bar and landing header, < 390 px | 32 px | 20 px |
| Top bar below 360 px | as above | hidden visually; the link keeps the name |
| Mobile drawer header | 36 px | 22.5 px |
| Auth brand panel | 40 px | 24 px (fixed) |

The sizes are `presentationTokens.brandMark` (`narrow 32`, `xs 36`, `sm 40`), fixed steps
(the narrow step is a lockup-local `max-width: 389.95px` query). The
40 px top-bar lockup replaced the 32/20 px lockup on 7 November 2026; the bar
heights did not change. Poppins ExtraBold was the Leader's stated reference: heavy,
geometric and controlled, a deliberate contrast with the UI face. The surrounding
link supplies the accessible name ("CodeMastery home").

## Layout

### Shell

```text
┌──────────────────────────────── ink-950 frame ────────────────────────────────┐
│ [logo] CodeMastery    [◉ Catalogue] [My courses]       [bell] [AB  Name ▾]   │  top bar 80px (64px < 600)
│ ┌────────────────────╮╭─╮╭─────────────── canvas-100 ─────────────────────────┐│
│ │                    ╰╯ ╰╯  ← canvas reach under the active item            ││
│ │  Page title                                    ( [search] Search…     )    ││
│ │                                                          ┌── aside ──────┐ ││
│ │  main column                                             │ fill-150       │ ││
│ │                                                          │ 360/400px      │ ││
│ └──────────────────────────────────────────────────────────┴────────────────┘│
└───────────────────────────────── 8px frame ───────────────────────────────────┘
```

- **Frame.** `body` background is `ink-950`. The top bar is part of the frame and is sticky.
- **Canvas.** `canvas-100`, `rounded.xl` corners, inset 8 px from the left, right and bottom frame edges at ≥ 600 px. Below 600 px the canvas runs edge to edge with 24 px top corners only. The document scrolls; the canvas is not an inner scroll container.
- **Canvas reach (concave curved bottom-edge indicator).** Under the active top-nav item the bar's lower edge bends up in one soft, symmetric bell, so the canvas appears to rise into the active tab. Geometry traced from the reference (shot 23956256, the hump under "Learning Plan"; width ≈ 6.2 × height): 88 × 14 px, long concave shoulders that leave the straight edge tangentially, an inflection near mid-height and a rounded convex crest centred on the item. Profile per half `y(u) = ((1 − cos πu) / 2)^1.2`, drawn in `AppHeader` (`canvasReachPath`) as a Catmull-Rom spline converted to cubic Béziers and mirrored exactly; not an underline, pill, dot or notch.
  - **Technique (7 November 2026):** nothing is painted. The bell path is a CSS mask layer (`REACH_MASK`, 88 × 15) subtracted from the header with `mask-composite: exclude` (`-webkit-mask-composite: xor`), so the bar's own ink is cut and the opening is transparent. The canvas begins 14 px under the bar (`margin-top: -14px`, `padding-top: 14px`), and its outline is a `clip-path` (`canvasOutlinePath`) whose visible top corners start exactly at the bar's bottom edge; the opening therefore shows the canvas itself, continuous with the canvas below, with no seam, overlap line or fill colour to match. When the page scrolls under the sticky bar, whatever passes underneath (a white tile, text) shows through the opening.
  - It is decorative (no element, nothing focusable), re-centres when the window, the labels or the web fonts move the item (`ResizeObserver` on the header, the bar and its slots, plus font loading), and the active item also has `aria-current="page"` and a white label. The header exposes `data-canvas-reach`, `data-reach-for` and `data-reach-x` for tests.
- **Main column.** Max content width 1440 px inside the canvas; horizontal padding 40 px (≥ 1200), 32 px (900–1199), 24 px (600–899), 16 px (< 600). Page top padding 40 px (≥ 900), 24 px below.
- **Aside panel.** On pages that have one, a right column inside the canvas, `fill-150`, full canvas height, 360 px at 1200–1535 px and 400 px at ≥ 1536 px, with `rounded.xl` on its outer corners and no border. Below 1200 px the aside's content moves into the main column at the position each screen specifies.

### Grid and spacing

- 12-column grid, 24 px gutters at ≥ 900 px, 16 px below.
- Spacing scale (frontmatter `spacing`): 4, 8, 12, 16, 24, 32, 40, 48, 64. MUI keeps `spacing: 8`; use 0.5 steps for 4 px.
- Rhythm: page title to first content 32 px; between page sections 48 px; heading to its content 16 px; inside tiles 24 px padding, 12 px between title and description, 24 px before the footer; between tiles 16 px (lists) or 24 px (grids).
- More space above a heading than below it, always.

### Breakpoints

The MUI default breakpoints are kept: `xs 0`, `sm 600`, `md 900`, `lg 1200`,
`xl 1536`. No new global breakpoint values are introduced (CT16-FINAL F2).

| Width | Shell behavior |
| --- | --- |
| < 600 | Top bar 64 px: menu button, logo mark (36 px; 32 px below 390), wordmark (hidden < 360), utilities (Sign in, or bell and account button). "Create account" moves into the drawer below 900 px. Navigation in a left drawer. Canvas edge to edge. Asides stack into the main column. Tables become stacked rows. |
| 600–899 | As above with the 8 px frame inset. Two-column tile grids allowed. |
| 900–1199 | Inline top navigation; non-active items show icons with tooltips and accessible names. Asides still stack. |
| 1200–1535 | Full labelled navigation. Asides appear as right columns. |
| ≥ 1536 | Content capped at 1440 px; aside 400 px. |

### Page archetypes

| Archetype | Structure | Used by |
| --- | --- | --- |
| **Plan** | Title row + search; main column of tiles or Path Spine; aside Guidance Stack | Learner home, course path, Teaching home |
| **Browse** | Title row + search; filter bar; responsive tile grid; pagination | Catalogue, My courses |
| **Detail** | Title block with primary action; two columns of sections (content + facts aside) | Course detail, assignment, learner detail |
| **Reader** | Collapsible outline left (280 px), reading column (max 720 px), lesson aside right | Lesson |
| **Focus** | Reduced frame bar with task controls; single task column; navigator aside | Quiz attempt, diagnostic |
| **Canvas** | Full-width graph surface with View Rail left and detail aside right | Mastery Map, skill graph editor |
| **Manage** | Title row + actions; tabs or segmented filters; one table surface; side sheet for detail | Admin and Instructor management |
| **Form** | Single column max 720 px, grouped sections, sticky action bar | Editors, profile settings (future) |
| **Split auth** | Ink brand panel + white form panel | Sign in, register, recovery |

## Elevation & Depth

Depth comes from **tonal layering**: ink frame → canvas → white tiles, and
canvas → `fill-150` aside → tinted entries. Surfaces at rest have no border and
no shadow. Shadows appear only for elements that float above the page or respond
to a state.

### Shadow vocabulary

| Role | Value | Use |
| --- | --- | --- |
| Hover lift | `0 6px 20px rgba(10, 11, 15, 0.08)` with `translateY(-2px)` | Clickable tiles on hover (pointer devices only) |
| Control | `0 2px 8px rgba(10, 11, 15, 0.10)` | The selected segment of a segmented control (lifts the white pill off its track) |
| Raised | `0 12px 32px rgba(10, 11, 15, 0.12)` | Menus, popovers, tooltips, selected graph node, side sheets |
| Overlay | `0 24px 64px rgba(10, 11, 15, 0.20)` | Dialogs, mobile drawer |
| Scrim | `rgba(10, 11, 15, 0.48)` | Behind dialogs and the drawer |

### Named Rules

**The Flat-At-Rest Rule.** A surface declares elevation once: by tone or by
shadow, never a 1 px border plus a soft shadow. Tables are white surfaces on
canvas with hairline row separators and no outer border.

## Shapes

- Corner scale: `xs 8` (mastery and type tags), `sm 12` (tooltips, code blocks, inner panels of small entries), `md 16` (text fields, selects, inner panels of guidance entries, quiz options), `lg 24` (tiles, tables, aside entries, menus, side sheets), `xl 32` (canvas, aside panel, dialogs), `pill` (buttons, search, status pills, segmented controls, View Rail, timers).
- Circles: icon buttons (44 px), account avatar (40 px top bar, 64 px Profile), Q&A monograms (36 px), Path Spine nodes (20 px, 14 px not started, 10 px static), corner badges (40 px on tiles, 28 px on map nodes), quiz navigator (44 px).
- Course marks: 48 px squares at `rounded.md` (the radius scales with the mark).
- Dashed outlines: 1.5 px `ink-400`, 6 px dash / 6 px gap, `rounded.lg`. Reserved for "not yet" meanings (Unknown, Remaining, empty slot, drop zone).
- Path line: 2 px, 4 px dash / 6 px gap (`pathLine` in `theme.ts`), used by the Path Spine, prerequisite edges, the map legend and the auth brand path.

**The Pill-Is-An-Action-Or-A-State Rule.** Pills are reserved for things you can
press or for short states. A paragraph never sits in a pill.

## Components

Implementation note: components are realised through the MUI theme (palette,
typography, component overrides and custom variants in
`frontend/src/app/theme/theme.ts`) and the existing shared component files. See
[Implementation Handoff](#implementation-handoff) for file placement rules.

### Top bar and navigation

- **Structure:** brand lockup left (`BrandWordmark`: 40 px mark and 25 px Poppins ExtraBold wordmark from 600 px, 36/22.5 px below, 32/20 px below 390 px; see [Brand and logo](#brand-and-logo)), linking to the visitor's start page (`/` anonymous, `/home` for a Learner; `/teach` for an Instructor and `/admin` for an Administrator once those screens are routed, `/courses` until then); primary navigation centred at ≥ 900 px; utilities right (notification button where its contract exists — today only in `/preview` — and the account button).
- **Nav item:** 44 px tall pill, 16 px horizontal padding, 20 px icon + label. Default `chrome-muted`; hover `chrome-raised` pill background, white text; active white label, 600 weight, canvas reach below, `aria-current="page"`. Focus: 2 px `focus-on-chrome` ring, 2 px offset.
- **Links:** items are real links. A plain click routes in-app; Cmd/Ctrl/Shift/Alt-clicks (new tab or window) and items without a handler follow the native link.
- **Parent highlighting:** the item stays active for its child routes (for example `/courses/:courseId` keeps "Catalogue" active). This fixes the current exact-match defect.
- **Account button:** 52 px pill: monogram avatar (40 px circle, solid `brand-700`, white initial), display name (label, white) and role (caption, `chrome-muted`) at ≥ 900 px, chevron; hover `chrome-raised`. Opens a menu: "Profile", divider, "Sign out" (the preview's menu holds "Preview index" and "Reset sample data" instead). No email, no avatar image.
- **Notification button (preview only):** 44 px icon button with the bell glyph and a 20 px `brand-600` count badge ringed in ink; its name includes the count ("Notifications, 3 unread"). On the Notifications page it is current: `chrome-raised` circle and `aria-current="page"` (frame utilities use the drawer's active-row treatment; the canvas reach stays for navigation items).
- **Anonymous utilities:** "Sign in" text button (white) and "Create account" on-chrome white small pill (40 px); below 900 px "Create account" moves to the drawer footer as a full-width white pill.
- **Mobile drawer (< 900 px):** 304 px ink drawer from the left (at most viewport − 48 px), 64 px header with the 36 px lockup, a list of 52 px `rounded.md` rows (6 px dot, 20 px icon, label), active row `chrome-raised` with a Logo Blue dot and 600 weight, optional footer above a `chrome-raised` hairline. It keeps the existing `SidebarNav` behaviors: focus moves into the drawer, Escape and scrim close it, focus returns to the menu button, and crossing to ≥ 900 px closes it and restores focus.
- **Canvas reach motion:** the mask position slides between items in 240 ms on route change and browser history; the shape itself never deforms; instant with reduced motion. At 900–1199 px it sits under the active item, which keeps its label while others are icons. Below 900 px there is no reach: the drawer's active row is the indicator.

### Page header

Page title (`h1`, receives route focus per CT16) on the left; actions on the
right in this order: secondary actions, primary action, search pill. Optional
back link above the title for depth two ("‹ Catalogue", `ink-600`, chevron) or a
breadcrumb for depth ≥ 3 (caption, `ink-600` links). Optional one-line
description below the title (body, `ink-600`, max 65ch) and an optional meta row
(for example "Instructor · Name", status pills). No eyebrow label. 32 px below
the header. Below 900 px actions wrap under the title and the search pill
becomes full width.

### Buttons

Four ranks, one fill colour per rank (7 November 2026). The roles live in
`theme.ts` as `control` (`primary`, `primaryHover`, `secondary`) and the
`MuiButton` variants; components reuse them instead of styling buttons locally.

| Rank | MUI usage | Rest | Hover | Use |
| --- | --- | --- | --- | --- |
| **Primary** | `variant="contained"` (default colour `ink`) | Graphite pill (`chrome-raised` `#1C1E24`), white label | `chrome-hover` `#2A2D35` | The one main action of a region: submit a form, start, save, enroll, confirm |
| **Secondary** | `variant="outlined"` | White pill, 1 px `line-200` border, `ink-900` label | `fill-150` fill | Alternatives and supporting actions: cancel, refresh, filters' companions, "My courses" beside Enroll, error retry |
| **Tertiary** | `variant="text"` | No fill or border, `brand-600` label (12 px padding) | `brand-700` + underline | Low-emphasis inline actions ("Skip and start from the beginning", "View result", alert actions); on tinted grounds it may take `ink-900` (`color="inherit"`) for contrast |
| **Destructive** | `variant="contained" color="error"` | `rose-700` pill, white label | `rose-800` | Only to confirm destroying or removing (inside its confirmation dialog); the trigger is secondary ("Leave course") or a menu item |

- **Shared states:** 48 px (40 px `size="small"`), label type, pill radius; pressed scale 0.98; focus 2 px `brand-600` ring with 2 px offset (on the frame `focus-on-chrome`); disabled primary and destructive `fill-150` with `ink-500`, disabled secondary keeps its white surface and border with `ink-500`, disabled tertiary has no fill; busy submits keep their width and announce progress (`aria-busy` or a status line).
- **On the ink frame** (`color="onChrome"`): primary is a white pill with an ink label (hover `fill-150`); secondary is a graphite pill with a white label (hover `chrome-hover`). Used by the top-bar and landing "Create account", the landing hero and the quiz "Submit quiz".
- **Circular actions** (`CircleAction`, 44 px, 20 px icon, accessible name and tooltip): `ink` = primary graphite circle, `fill` = secondary outlined circle (white, `line-200` border), `plain` = no surface. Overflow "More actions" buttons and the lesson bar's previous/next circles use the secondary surface.
- **Tile arrow** (`CircleAffordance`, decorative because the tile's title link covers the tile): `secondary` by default — white circle, `line-200` border, graphite arrow; it fills `fill-150` while the tile is hovered. `primary` (graphite filled) only for the one important next step: the Learner home "Continue learning" tile (play glyph) and the resume lesson on the course path. Course tiles in the catalogue, My courses, Teaching and the design reference use the secondary arrow.
- **Hierarchy rule:** at most one primary per region (title row, form, dialog, tile, banner, action bar). An empty state that repeats the title row's action makes it secondary (Teaching "Create course", skill graph "Add skill"); an error block's "Try again" is secondary; a banner inside a page that already has a title-row primary uses a secondary action (placement diagnostic "Start diagnostic").
- **Graphite vs ink:** `ink-950` stays the frame, code panels, tooltips and toasts. Controls never use it: primary buttons, primary circles, the selected page number, the selected View Rail toggle and answered quiz-navigator circles use graphite. Blue is not used for buttons; it stays with links, focus, selection and progress.

### Inputs

- **Text field / select:** label above the field (label type, `ink-900`), 52 px white field, 1.5 px `ink-400` border, `rounded.md`, 16 px padding. Hover border `ink-700`. Focus border 2 px `brand-600`. Error border 2 px `rose-700`, error text below in caption `rose-700` with an alert glyph, linked by `aria-describedby`. Helper text in caption `ink-500`. Disabled `fill-150` background. Required fields are marked "(required)" in the label, not by an asterisk alone.
- **Search pill:** 52 px `fill-150` pill, leading search icon, placeholder `ink-600`; focus: white background + 2 px `brand-600` border. With a clear action, Escape and the 36 px clear button empty it and keep focus. Search submits on Enter or the Search button where the API contract defines explicit submission (catalogue: 100-character limit enforced by the field, no visible counter); preview tables filter after typing pauses.
- **Checkbox, radio, switch:** MUI controls recoloured: unchecked `ink-400` outline, checked `brand-600`, 44 px hit area.
- **Quiz option row:** full-width white row, `rounded.md`, min 64 px, 1.5 px `line-200` border; option letter in a 32 px `fill-150` circle; selected: 2 px `brand-600` border, letter circle filled `brand-600` with white letter, check glyph; keyboard via native radio/checkbox semantics.
- **File drop zone:** dashed outline region (Shapes), upload icon, "Choose a file or drop it here", accepted types and size limit shown when the contract defines them; states idle, drag-over (`brand-100` fill), uploading (progress bar), accepted (mint row with file name), rejected (rose message).
- **Date and time entry:** native `input type="date"` / `datetime-local` styled as text fields, with the timezone shown in the helper text. MUI X date pickers would be a new dependency and are not used.
- **Date-time display:** deadlines show an absolute date and time with an explicit timezone label next to a relative phrase ("Due in 2 days · 20 Nov, 23:59 ICT"). Locale and timezone are supplied by the caller (FE01 caller-owned formatting); this file selects none.

### Status pills and tags

- **Status pill** (`StatusPill`, `status-pill`): 36 px pill, label type, optional 18 px leading glyph in the family's strong colour. Variants: neutral (`fill-150`), mint, butter, rose, sky, lilac, dashed (white with dashed `ink-400`, for "not yet" states such as "Not started") and ink. The same component in the 28 px `tag` size (`rounded.xs`, caption 600, 16 px glyph) carries compact domain tags and account roles. Used for lesson progress, membership, publication, submission, report and account status.
- **Mastery tag:** 28 px, `rounded.xs`, caption 600. Glyph + exact state word: Unknown (white, dashed `ink-400` border, dashed-circle glyph), Learning (`sky-100`, half-filled circle in `brand-600`), Mastered (`mint-100`, check-circle in `mint-700`), Weak (`butter-100`, alert-triangle in `butter-800`).
- **Recommendation tag:** 28 px lilac tag with route glyph: "Continue", "Test-out" or "Remedial".
- **Count badge:** 20 px min-width pill, `brand-600` background, white caption; announced in the parent's accessible name ("Notifications, 3 unread").

### Tiles

- **Lesson tile:** white, `rounded.lg`, 24 px padding. Top: lesson title (item title) and position caption ("Lesson 4 · Loops"). Middle: one or two lines of summary if the contract provides one. Footer: status pill(s) left, actions right (the tile arrow: secondary, or graphite for the resume lesson). Optional corner badge (40 px circle at top-right, overlapping by 12 px) for an adaptive marker: lilac route glyph for Recommended, Test-out or Remedial; butter alert glyph for a prerequisite gap. Its meaning is always repeated as a text tag in the footer.
- **Course tile** (`CourseTile`): white, `rounded.lg`, 24 px padding. Course mark (below), title (item title, wraps), description (body `ink-600`, clamped to 3 lines with the full text available on the detail page), Instructor line (caption: "Instructor · Name" or "Instructor name unavailable"; Teaching tiles show lesson and learner counts instead), footer with membership or publication pill (when known), optional overflow menu (secondary outlined circle), and the secondary tile arrow. Used by the catalogue, My courses, the Teaching home and the design reference.
- **Course mark** (`CourseMark`, decorative, `aria-hidden`; the title names the course). One neutral container for every course: `canvas-100` with a 1 px inset `line-200` hairline at `rounded.md` (radius scales with size); 48 px on tiles, 32 px in Learner home rows:
  - **Language logo:** when the course's own title, otherwise its description, names exactly one programming language that has an approved logo, its official full-colour logo. Logos keep their colours and proportions; each is sized optically from one registry (visible-artwork box and scale: Python 0.66, TypeScript 0.56 of the container) and centred on its visible artwork. Approved logos: Python (python.org `python-logo-only.svg`, with only a `viewBox` added) and TypeScript (`ts-logo-256.svg` from the TypeScript brand pack). Java, C++ and Go are not included because their trademark terms require permission; JavaScript has no official logo.
  - **Generic mark:** otherwise (no language named, several named, or none with a logo) a `brand-700` `code` glyph at half the container size, in the same container. Course initials, ink and gradients are not used.
  - The course API has no language field, so no logo is inferred from anything but the course's own text. No current seed or E2E course names a language, so production tiles show the generic mark. In the preview every workspace reads one shared description per sample course, so "Introduction to Programming" (Python) shows the Python logo everywhere.
- **Stat tile:** `rounded.lg`, 20 px padding (16 px below 600 px), stat numeral + label in `ink-600`, centred. Tints by meaning: sky (total), mint (completed), dashed outline (remaining). Max three in a row; only real counts.
- **Clickable tiles:** the whole tile is one link whose accessible name is the title; inner buttons are separate focus targets placed after it. Hover lift on pointer devices.

### Path Spine

The signature component (`PathSpine`). An ordered list (`ol`) of lesson tiles
with a 2 px `brand-400` path line drawn as a 4 px dash / 6 px gap (`pathLine`
token, the same rhythm as prerequisite edges) in a 40 px left gutter (28 px below 600 px)
and a node aligned with each tile's title. The segment after a completed item is
solid `mint-700`. Nodes: completed or mastered = 20 px `mint-700` with a white
check; current/resume = 20 px `brand-600` with a 4 px `brand-100` ring;
recommended = 20 px white with a 4 px `lilac-700` ring; not completed = 14 px
white with a 2 px `ink-400` border; static (syllabus, landing steps) = 10 px
`ink-400`. A `rows` density (24/32 px gutter) serves compact lists such as the
course detail syllabus and the landing steps. Chapters appear as section titles
that interrupt the line (the line restarts below).
The node state is decorative; the tile's status pill carries the text. When a
lesson becomes completed, its node fills and the segment to the next node turns
solid in 400 ms (instant with reduced motion). This is the product's one
authored motion moment.

### Guidance Stack

The aside list adapted from the reference's events column. Each entry:
`rounded.lg`, 16 px padding, family tint; header row with 20 px glyph, type
label (label type) and meta (caption, right-aligned); an inner `rounded.md`
soft panel with the body text (body); an optional full-width white pill action.
Entry types: Recommendation (lilac), Deadline (butter, Contract Pending),
Result available (mint, Contract Pending), Reply or announcement (sky, Contract
Pending). On the Learner home only Recommendation entries are Final scope
(`CT16-LEARNER-HOME-2026-11-08-r1`); the other types stay Preview there. The list ends with a dashed empty slot when fewer than three entries
exist ("Nothing else needs your attention"). Entries are a `ul` with an `h2`
for the panel.

### View Rail

A vertical pill track (`fill-150`, 52 px wide) of 44 px circular toggles placed
in the left gutter at ≥ 1200 px; a horizontal segmented pill above the content
below that. Active toggle: graphite circle with white icon. It is a single-select
toggle group with `aria-pressed` items and visible text labels in tooltips and
accessible names ("Graph view", "List view"). Optional bottom group for zoom
in, zoom out and fit on canvas pages.

### Tables

- One white `rounded.lg` surface on canvas. Header row: caption 600 `ink-600`, 48 px, no fill, sortable headers show an arrow glyph and `aria-sort`. Rows: 60 px min, body-small, hairline separators, hover `canvas-100`, selected `brand-100`.
- Cell content: status as pills or tags; actions as icon buttons at the right; the first column holds the row's name and its link.
- Toolbar above the table (not inside): search pill, segmented filters, count ("24 users"), bulk actions only if a contract supports them.
- Pagination below: "Showing 1–20 of 124" + previous/next icon buttons + page numbers; display is 1-based while the API is 0-based (existing behavior).
- 600–899 px: horizontal scroll inside the surface with a sticky first column and a visible scroll shadow. < 600 px: stacked rows (name as title, then label–value pairs, then actions) inside the same surface, separated by hairlines.
- Loading: skeleton rows matching column widths. Empty: dashed empty slot inside the surface.

### Tabs and segmented filters

Pill segmented control (`SegmentedControl`): `fill-150` track with 4 px
padding (44 px tall), 36 px items, white active pill with `ink-900` label and the
Control shadow (`shadow.control`); inactive `ink-600`, hover
`line-200`; optional count in `ink-600` tabular figures. It scrolls
horizontally below 600 px instead of wrapping. Used for status filters
and in-page views. Page-level tabs (course editor, learner detail) use the same
control left-aligned under the page header; they map to URLs when routes exist.

### Feedback

- **Inline alert:** `rounded.md`, family tint, 20 px glyph, title (label) and body, optional actions on the right. No coloured left border. Used for page-level and form-level messages.
- **Tooltip:** `ink-950`, white caption, `rounded.sm`, 8 × 12 px padding, max 280 px, appears after 400 ms on hover and immediately on keyboard focus, dismissible with Escape. Tooltips repeat names; they never hold the only copy of information.
- **Toast:** ink pill at bottom centre, white body text, optional text action in `focus-on-chrome`, 6 s, pausable on hover/focus, announced politely. Only for background confirmations that do not belong next to a control.
- **Confirmation dialog:** see Dialogs. Destructive confirmations focus Cancel first (existing behavior).
- **Loading:** a visible `role="status"` line naming what is loading ("Loading your courses…"), followed by `fill-150` skeleton blocks shaped like the content (tiles, rows or a block) with a 1.2 s shimmer (static with reduced motion). No spinner accompanies content loading; busy submit buttons keep their own inline indicator.
- **Empty state:** dashed slot with a 24 px glyph, a title that names the situation, one sentence that teaches the next step, and one action.
- **Error state:** a `rose-100` block at `rounded.lg` with an alert glyph, a title that names the problem, one sentence of recovery, and a small secondary (outlined) "Try again" button when a retry is possible. Field and form errors use the rose inline alert.
- **System state** (Page not found, Access denied): centred block inside the shell, 64 px white circle with the glyph, page title, one sentence, actions ("Browse courses"; "Go to catalogue" and "Profile").

### Dialogs, side sheets and menus

- **Dialog:** white, `rounded.xl`, 32 px padding, max 560 px, Overlay shadow, scrim. Title (item title), body, actions right-aligned (secondary then primary). Focus is trapped and returns to the trigger. Full-screen sheet below 600 px.
- **Side sheet:** right-anchored 480 px panel for record detail in Manage pages (report detail, user detail); full-screen below 900 px.
- **Menu:** white, `rounded.lg`, Raised shadow, 44 px items, `brand-100` for the focused item.

### Progress

Linear only: 8 px pill track `line-200`, fill `brand-600`, with the value in
text beside it ("12 of 20 lessons · 60%"). No progress rings. Quiz timer is a
pill: white on the frame, clock glyph + `mm:ss` in tabular figures.
*Visual thresholds:* it turns butter when 20% of the time limit remains and rose
when one minute (or 10% of the limit, whichever is shorter) remains.
*Screen-reader announcements:* exactly one polite `role="status"` message at each of
those two changes — "N minutes left." (remaining minutes rounded up; "1 minute
left." for one) when it turns butter and "Less than a minute left." when it turns
rose — and nothing per second or per minute in between. These are presentation
thresholds, not quiz policy; the expiry itself comes from the server once the
Assessment contract provides it.

### Code and Markdown content

- Code block (`CodeBlock`): `ink-950` panel, `rounded.sm`, 20 px padding, JetBrains Mono 15 px in `fill-150`, horizontal scroll, focusable; a 40 px copy button on chrome whose tooltip and status message change to "Copied"; optional caption below in caption type. Inline code: `fill-150` background, 4 px radius.
- Markdown lesson content uses body-reading, 70ch measure, headings at section title and title sizes, 1.5 em paragraph spacing, tables in the table style without toolbar. Rendering follows CT37.markdown (`CT37-MARKDOWN-2026-11-08-r1`, [`docs/setup.md`](docs/setup.md#ct16-route-map-learner-home-composition-and-ct37-markdown-policy--8-november-2026)): CommonMark with tables and fenced code rendered as React elements; raw HTML is shown as literal text, never interpreted; only `https`/`http` URLs, root-relative paths (not `//…`) and `#` fragments become links, checked after decoding, and every other target (including `javascript:` and `data:`) is plain text; images render as links, not inline; Markdown headings start at `h2`; fenced code uses the code block. The renderer library is a separate dependency decision.

### Mastery Map node and edge

- **Node:** white `rounded.lg` 220 × 72 px, flat at rest with a 1 px inset `line-200` hairline, Hover lift shadow on hover; left 40 px state glyph circle in the state tint (dashed for Unknown); skill name (title, 2 lines max); state word (caption) under it. Selected: 2 px `brand-600` ring + Raised shadow. Recently updated: 2 px mint or butter ring. Recommendation target: 28 px lilac corner badge. Focusable, with accessible name "Loops, Weak, 2 prerequisites".
- **Edge:** 2 px `ink-400`, dashed 4/6 (`pathLine`), from prerequisite to dependent skill, closed arrowhead at the dependent end. Edges touching the selected node become solid `brand-600`.
- **Legend:** always visible below the canvas or in the aside: the four tags with one-line meanings, plus "Arrow: from a prerequisite to the skill that builds on it".

### Charts (Administration and insights)

Built as semantic HTML/SVG with the existing stack; no chart dependency is added.
Horizontal bars sorted by value, value labels at bar ends, `brand-600` fill for
single series, `data-*` fills plus hatch for mastery distributions (100% stacked
bars per skill, 2 px white gaps between segments). Every chart has a title, a
one-line definition of the metric, and a "Show as table" toggle that renders the
same numbers in a table. No trends, sparklines or projections unless the
reporting contract returns them.

### Iconography and imagery

- Icons: original outline SVGs in the existing `PresentationIcon` component, 24 px grid, 1.75 px stroke, round caps and joins, `currentColor`. Sizes 16, 20, 24. No icon font or package is added.
- Implemented glyph set: home, book, catalogue, grid, my-courses, path, map, graph, quiz, assignment, file, text, video, bell, user, users, shield, flag, audit, chart, message, reply, send, code, layers, sliders, search, filter, plus, minus, edit, trash, eye, eye-off, upload, download, external, copy, refresh, more, menu, close, sign-out, lock (account lock only), unlock, play, arrow, arrow-right, arrow-left, chevron-down/up/right/left, move-up, move-down, clock, calendar, check, check-circle, half-circle, dashed-circle, alert-triangle, alert, alert-circle, info, route (recommendation), skip (test-out), star, zoom-in, zoom-out, fit, list, grip, spark, flame. `star`, `spark` and `flame` are defined but unused.
- Imagery: no stock photos, no 3D avatars, no illustrations of people. The brand graphic is the logo's node-and-link geometry, redrawn as crisp vector shapes (auth brand panel, landing example map). Course marks are the official language logos or the generic code glyph (see [Tiles](#tiles)); they are the only third-party artwork in the UI.

### Motion

| Token | Value | Use |
| --- | --- | --- |
| Fast | 120 ms | Hover, press, colour changes |
| Standard | 200 ms | Menus, tooltips, segmented control, tile hover lift |
| Panel | 280 ms | Drawer, side sheet, dialog |
| Reach | 240 ms | Canvas reach slide |
| Signature | 400 ms | Path Spine completion fill |
| Easing in | `cubic-bezier(0.2, 0, 0, 1)` | Entrances, state changes |
| Easing out | `cubic-bezier(0.4, 0, 1, 1)` | Exits |

Motion conveys state only. No page-load choreography, no scroll-triggered
reveals, no parallax. `prefers-reduced-motion: reduce` makes every transition
instant and replaces shimmer with a static fill; the existing theme-level
reduced-motion rules and `motion.reducedMotion: 'system'` remain.

## Do's and Don'ts

### Do:

- **Do** keep the ink frame, canvas reach and canvas on every authenticated and public workspace page. The landing page (own ink header and hero), the auth split layout and the quiz attempt (Focus bar) are the deliberate exceptions.
- **Do** use exactly one `h1` per page and let it receive focus on route change.
- **Do** show the state word and glyph in every mastery, progress, membership and status indicator.
- **Do** use the Path Spine for anything with canonical order (lessons) and the dashed edge for prerequisites.
- **Do** put the reason text from the backend, verbatim, on every recommendation.
- **Do** use graphite for the one primary action per region, neutral outlined buttons for alternatives, and Logo Blue for links, focus and selection.
- **Do** show skeletons shaped like the content while loading.
- **Do** keep tables in one white surface with a toolbar above and pagination below.
- **Do** keep all interface text in English, sentence case.

### Don't:

- **Don't** show lock icons, disabled lessons or blocking dialogs for prerequisite gaps.
- **Don't** show a mastery percentage, a confidence score or a progress ring.
- **Don't** call membership "Completed" a mastery outcome or show it on the Mastery Map.
- **Don't** use emoji, Unicode symbols or third-party icon fonts as icons.
- **Don't** tint a region without a semantic reason, or give a UI surface a gradient fill (the logo artwork and the skeleton shimmer are the only gradients).
- **Don't** wrap a table, a form or a section heading in a tile.
- **Don't** show email, token data, account status or permissions in the account menu or Profile.
- **Don't** render numbers, names or charts that the approved API did not return.
- **Don't** put `ink-500` text on `fill-150`, or `brand-400` text anywhere.

## Information Architecture

### Workspaces

| Workspace | Who | Shell | Home (current → proposed) |
| --- | --- | --- | --- |
| Public | Anonymous visitors | Landing page at `/` (own ink header); other pages in the frame with "Catalogue" and Sign in / Create account | Landing page `/` (Implemented) |
| Learner | Learner role | Frame with Learner navigation (Home, Catalogue, My courses) | Start page `/home` (Implemented): sign-in, `/` and the logo lead there; the one-use `/profile` return still takes precedence after sign-in |
| Instructor | Instructor role | Frame with Catalogue only in production; full Instructor navigation in `/preview` | `/courses` (Implemented) → `/teach` Teaching home once it is routed (route approved in CT16.routes; screen Proposed, in preview) |
| Administrator | Administrator role | Frame with Catalogue only in production; full Administrator navigation in `/preview` | `/courses` (Implemented) → `/admin` Platform overview once it is routed (route and start page approved in CT16.routes; screen Contract Pending, in preview) |

Roles never inherit each other's navigation or rights. Hiding a navigation item
is not authorization; backend checks remain authoritative (ADR001 §§11–12).

### Primary navigation

| Role | Items (label → destination) | Status |
| --- | --- | --- |
| Anonymous | Catalogue → `/courses` | Implemented |
| Learner | Home → `/home` · Catalogue → `/courses` · My courses → `/my-courses` · bell → `/notifications` | Home, Catalogue and My courses Implemented; the bell and Notifications are Preview only (route approved) |
| Instructor | Teaching → `/teach` · Grading → `/teach/grading` · Learners → `/teach/learners` · Catalogue → `/courses` · bell → `/notifications` | Catalogue Implemented; the others exist only in `/preview` (routes approved) |
| Administrator | Overview → `/admin` · Users → `/admin/users` · Courses → `/admin/courses` · Reports → `/admin/reports` · Audit log → `/admin/audit` · Catalogue → `/courses` | Catalogue Implemented; the others exist only in `/preview` (routes approved) |
| Every signed-in role | Account menu: Profile → `/profile`, Sign out | Implemented |

These destinations are approved in CT16.routes (`CT16-ROUTES-2026-11-08-r1`). An
item appears only when its destination is routed in production; never render a
navigation item that leads to an unimplemented page. Roles never inherit items.
Active item: `/courses/:courseId` keeps Catalogue; the Learner course-workspace
paths (`/courses/:courseId/learn`, `…/lessons/…`, `…/diagnostic`, `…/mastery`,
`…/assessments`, `…/quizzes/…`, `…/attempts/…`, `…/assignments/…`) make My courses
active; `/teach/courses/…` makes Teaching active. The label
"Catalogue" replaces "Courses" for the public list so that "Courses" can mean
course administration for Administrators and "My courses" stays unambiguous.

### Sitemap

```text
Public
├── Landing (Implemented)                  /
├── Catalogue (Implemented)                /courses
│   └── Course detail + membership         /courses/:courseId
├── Sign in (Implemented)                  /login
├── Create account (Implemented)           /register
├── Forgot password (Proposed, CT02)       /forgot-password †
└── Reset password (Proposed, CT02)        /reset-password †

Learner
├── Learner home (Implemented, partial)    /home
├── My courses (Implemented)               /my-courses
├── Course workspace (Contract Pending)    /courses/:courseId/learn †
│   ├── Path (default view)
│   ├── Lesson + Q&A                       /courses/:courseId/lessons/:lessonId †
│   ├── Placement diagnostic               /courses/:courseId/diagnostic †
│   ├── Mastery Map                        /courses/:courseId/mastery †
│   └── Assessments                        /courses/:courseId/assessments †
│       ├── Quiz introduction              /courses/:courseId/quizzes/:quizId †
│       ├── Quiz attempt                   /courses/:courseId/quizzes/:quizId/attempt †
│       ├── Attempt history                /courses/:courseId/quizzes/:quizId/history †
│       ├── Quiz result                    /courses/:courseId/attempts/:attemptId †
│       └── Assignment                     /courses/:courseId/assignments/:assignmentId †
├── Notifications (Contract Pending)       /notifications † (Learner, Instructor)
└── Profile (Implemented)                  /profile

Instructor
├── Teaching home (Proposed)               /teach †
├── Course editor (Contract Pending)       /teach/courses/:courseId † (tabs …/content default, …/settings)
│   ├── Lesson editor                      /teach/courses/:courseId/lessons/:lessonId †
│   ├── Skills (graph + mapping + adaptive mode) /teach/courses/:courseId/skills †
│   ├── Assessments (question bank, quizzes) /teach/courses/:courseId/assessments †, …/quizzes/:quizId †
│   ├── Assignment editor                  /teach/courses/:courseId/assignments/:assignmentId †
│   ├── Learners → Learner detail          /teach/courses/:courseId/learners †, …/learners/:learnerId †
│   └── Q&A                                /teach/courses/:courseId/qa †
├── Learners (choose an owned course)      /teach/learners †
├── Grading (Contract Pending)             /teach/grading †, /teach/grading/:submissionId †
└── Profile (Implemented)                  /profile

Administrator
├── Platform overview (Contract Pending)   /admin †
├── Users (Contract Pending)               /admin/users †
├── Courses (Contract Pending)             /admin/courses †
├── Content reports (Contract Pending)     /admin/reports †
├── Audit log (Contract Pending)           /admin/audit †
└── Profile (Implemented)                  /profile

System: Page not found (Implemented, `*`), Access denied (Implemented in guard),
Design preview (Implemented, `/preview?screen=<id>`, sample data only; every
Preview and Proposed screen in this tree is reachable there)
```

`†` marks a path approved in CT16.routes (`CT16-ROUTES-2026-11-08-r1`, whose route
table has 22 entries) that is not registered in production: it shows Page not
found until its screen's contracts are approved, its Issue is authorized and the
screen is delivered. Creation screens use the literal segment `new` in the
identifier position (for example `/teach/courses/new`); record detail in
Administrator tables opens in a side sheet without a route; how the reset
capability is carried is CT02's decision. Contract Pending and Proposed
screens in this tree are built and viewable in `/preview` (status **Preview**).

### Page inventory

Fidelity: **H** high (the reference's visual language at ~90%, per the [fidelity contract](#reference-analysis); never its composition or artwork), **A** adapted, **S**
specialized. Source files are under `frontend/src/features/` unless noted.
**Preview** means the page is built and shown in `/preview` with sample data; the
contract named after it is what keeps it out of production.

| Page | Role | Purpose / primary action | Entry points | Source file | Status | Fidelity |
| --- | --- | --- | --- | --- | --- | --- |
| Catalogue | All | Find a published course / open it | Nav, landing "Browse courses", post-sign-in destination for Instructors and Administrators | `catalogue/pages/CourseCataloguePage.tsx` | Implemented | A |
| Course detail | All; Learner enrols | Decide to join / Enroll, Re-enroll, Leave, Sign in to enroll | Catalogue, My courses | `catalogue/pages/CourseDetailPage.tsx` + `CourseMembershipActions` | Implemented | A |
| Sign in | Anonymous | Authenticate / Sign in | Nav, guard redirect, registration success | `auth/pages/LoginPage.tsx` | Implemented | S |
| Create account | Anonymous | Register as Learner / Create account | Nav, sign-in page | `auth/pages/RegisterPage.tsx`, `auth/components/RegisterForm.tsx` | Implemented | S |
| Profile | Signed in | Read session identity | Account menu | `auth/pages/ProfilePage.tsx` | Implemented | A |
| My courses | Learner | Find joined courses / open one | Nav, course detail | `learning/pages/MyCoursesPage.tsx` | Implemented | H |
| Page not found / Access denied | All | Recover / go to catalogue | Any bad URL or role | `routes/index.tsx`, `routes/guards/ProtectedRoute.tsx` | Implemented | A |
| Design preview | Team | Review the design system and every screen with sample data | Direct URL `/preview?screen=<id>` | `app/preview/PreviewApp.tsx` (lazy chunk) + `app/preview/*` | Implemented | H |
| Forgot / reset password | Account holder | Recover access | Sign-in page (preview only) | `auth/pages/ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx` | Proposed, in preview (CT02; routes approved) | S |
| Learner home | Learner | Continue the right next step across courses | Nav, logo, `/` when signed in, sign-in | `learning/pages/LearnerHomePage.tsx` | Implemented (active courses only); full design Preview (CT22, Adaptive, Assessment, Interaction) | H |
| Course workspace (Path) | Learner | See the course path and the next step / Open lesson | My courses, course detail, home | `learning/pages/CourseLearningPage.tsx`, `learning/components/CourseOutline.tsx` | Preview | H |
| Lesson | Learner | Study and mark complete / Mark as complete | Path, recommendation, resume | `learning/pages/LessonPage.tsx`, `learning/components/LessonContent.tsx` | Preview | A |
| Placement diagnostic | Learner | Establish initial mastery or skip / Start diagnostic | Course workspace prompt | `mastery/components/PlacementPrompt.tsx` + quiz attempt | Preview | S |
| Course assessments | Learner | Find quizzes and assignments / Start | Course workspace | `assessment/pages/CourseAssessmentsPage.tsx` | Preview | A |
| Quiz attempt | Learner | Answer within the time limit / Submit quiz | Assessments, lesson, recommendation | `assessment/pages/QuizAttemptPage.tsx`, `QuizTimer.tsx`, `QuestionRenderer.tsx` | Preview | S |
| Quiz result | Learner | Understand score and next step | After submit, history | `assessment/pages/QuizResultPage.tsx` | Preview | A |
| Attempt history | Learner | Review past attempts | Result, assessments | `assessment/pages/AttemptHistoryPage.tsx` | Preview | A |
| Assignment | Learner | Submit before the deadline; read grade and feedback | Assessments, deadline entry | `assessment/pages/AssignmentDetailPage.tsx` | Preview (CT09) | A |
| Mastery Map | Learner | Understand skill states and next step | Course workspace, result | `mastery/pages/MasteryMapPage.tsx` + mastery components | Preview | H |
| Notifications | Learner, Instructor | Read in-app updates | Bell | `interaction/pages/NotificationsPage.tsx` | Preview | A |
| Teaching home | Instructor | Pick a course to work on / Create course | Nav | `instructor/course-management/pages/InstructorCoursesPage.tsx` | Proposed, in preview (route approved in CT16.routes; authoring and "Needs attention" data contracts pending) | H |
| Course editor | Instructor | Structure and publish content | Teaching home | `instructor/course-management/pages/CourseEditorPage.tsx` | Preview | S |
| Lesson editor | Instructor | Edit lesson, materials, skills | Course editor | `…/pages/LessonEditorPage.tsx` | Preview (CT09) | S |
| Question bank | Instructor | Author tagged questions | Course editor | `…/pages/QuestionBankPage.tsx` | Preview | S |
| Quiz editor / Assignment editor | Instructor | Build and publish assessments | Course editor | `…/pages/QuizEditorPage.tsx`, `AssignmentEditorPage.tsx` | Preview | S |
| Lesson Q&A | Instructor | Answer learner questions | Course editor, notification | `…/pages/LessonQaPage.tsx` | Preview | A |
| Skill graph editor | Instructor | Define skills, prerequisites, mappings; enable adaptive mode | Course editor | `instructor/skill-graph-editor/pages/SkillGraphEditorPage.tsx` | Preview (CT40, Adaptive §15) | S |
| Grading list / Grade submission | Instructor | Grade with feedback | Nav, notification | `instructor/grading/pages/SubmissionListPage.tsx`, `GradeSubmissionPage.tsx` | Preview (CT09) | S |
| Learner insights / Learner detail | Instructor | Inspect progress, scores, mastery, recommendation log | Nav, course editor | `instructor/learner-insights/pages/LearnerInsightsPage.tsx`, `LearnerDetailPage.tsx` | Preview | A |
| Platform overview | Administrator | Read platform metrics | Nav | `admin/reporting-dashboard/pages/ReportingDashboardPage.tsx` | Preview | A |
| Users | Administrator | Find users, change role, lock/unlock | Nav | `admin/user-management/pages/UserManagementPage.tsx` | Preview (CT12) | S |
| Courses (administration) | Administrator | Inspect and manage courses | Nav | `admin/course-management/pages/AdminCoursesPage.tsx` | Preview (CT12) | S |
| Content reports | Administrator | Hide or dismiss reported content | Nav | `admin/content-reports/pages/ContentReportQueuePage.tsx` | Preview (CT12 + Interaction) | S |
| Audit log | Administrator | Filter administrative actions | Nav | `admin/reporting-dashboard/pages/AuditLogPage.tsx` | Preview (CT12/CT31) | S |
| Landing | Anonymous | Understand the product / Browse courses | `/` | `catalogue/pages/LandingPage.tsx`, `mastery/components/ExampleMasteryMap.tsx` | Implemented | A (Persuade) |

## User Flows

Each flow lists the happy path, then the states that must be designed. "→"
means navigation; "⟳" means an in-place update.

### F1 Create account and sign in — Implemented

1. Visitor → "Create account" → form (email, display name, password, confirm password) → submit.
2. Success: inline mint alert "Account created. Sign in to continue." with a "Sign in" button → Sign in (no auto-login, ADR002 CT01.core). Nothing is passed to the sign-in page.
3. Sign in → success → the one-use `/profile` return when one was captured (CT16-PROFILE P1); otherwise a Learner lands on Learner home `/home` (Leader instruction, 7 November 2026; recorded beside P1 in `docs/setup.md`), an Instructor on `/teach` and an Administrator on `/admin` once those screens are routed, and both on `/courses` until then (`CT16-ROUTES-2026-11-08-r1`).

States: field errors on submit, then re-validated on blur for fields already in
error (focus the first invalid field or the error summary); the conflict
returned by the API for an existing email; the generic credential failure
message returned by the API for wrong credentials and locked accounts alike
(no account enumeration); busy button; network failure with retry. Password
rules appear as a checklist under the field that updates while typing, using
the exact approved predicates and wording ("At least 15 Unicode characters, at
most 72 UTF-8 bytes."; no trimming).

### F2 Session end — Implemented

Sign out (account menu) clears the session and replaces to `/login`. Expiry
(15 minutes, non-sliding) or a current-session 401 also replaces to `/login`.
Reload loses the session. A sky info alert (`role="status"`) on the sign-in page,
"Your session ended. Sign in again to continue.", is shown once when a session
present in this document ended without the user signing out. The provider's
lifecycle is unchanged; the notice only observes it (approved and activated on
6 November 2026).

### F3 Discover and enrol — Implemented

Catalogue → search (submit) and prerequisite filter → page → Course detail →
"Enroll in course" (Learner) or "Sign in to enroll" (anonymous) or a sky info
alert "Course membership is available to learners." (other roles) → ⟳ membership
pill "Active" + actions "Leave course" (secondary) and "My courses" (graphite primary
once joined); "Go to course" waits for the course workspace. Leave → confirmation ("Leave course?" / "Your learning history will
be preserved. You can re-enroll while the course is published.") → ⟳ "Left" +
"Re-enroll".

States: catalogue loading skeleton, no results with active filters ("No courses
match these filters" + "Clear filters"), no published courses, load error with
retry, course not available (concealed 404), membership read pending (pill
skeleton, not "Updating"), mutation pending (button busy), mutation error with
retry, stale responses ignored.

### F4 Continue learning — Contract Pending (CT21.remaining, CT22, CT09)

My courses or Learner home → course workspace (Path) → the current lesson tile
carries the brand node and "Resume" → Lesson → read materials → "Mark as
complete" → ⟳ status "Completed", Path Spine fills, sticky bar offers "Next
lesson" (canonical next) and, if one exists, the current recommendation.
When every required lesson is complete the course pill becomes "Completed".

States: first visit (no resume point: "Start course" on lesson 1), hidden or
unpublished lesson (concealed), material unavailable (file delivery error),
external video unavailable (link fallback), offline/network error on completion
(keep "Mark as complete" enabled, inline error, no optimistic state).

### F5 Placement diagnostic — Contract Pending (Assessment + Adaptive)

Enrolled Learner with no mastery data opens the course → lilac guidance banner
at the top of the Path: "Take a short placement diagnostic" / "CodeMastery uses
it to mark what you already know so you can skip it." → "Start diagnostic" →
Quiz attempt (Focus) → submit → Result → "See your Mastery Map" (states set for
the covered skills). Alternative: "Skip and start from the beginning" → default
path; banner dismissed. Whether the offer reappears after skipping is not
defined and must come from the contract.

### F6 Take a quiz — Contract Pending (CT04/CT10/CT23)

Assessments or lesson → quiz intro (kind, question count, time limit, attempt
number, "The timer starts when you begin") → "Start quiz" → attempt (Focus):
navigate questions, answer, see the timer → "Submit quiz" → confirmation
"Submit quiz? You answered 8 of 10. Unanswered questions score zero." → Result.
At expiry: the attempt auto-submits; a status line "Time is up. Submitting your
answers…" replaces the controls, then → Result.

States: start failure, timer drift (always count down to the server-provided
expiry when the contract supplies it), connectivity loss during the attempt
(inline rose alert; the timer keeps counting because expiry is fixed at start),
leave guard on the exit button ("Leave the quiz? The timer keeps running."),
submission failure with retry, already-submitted attempt (→ Result). The app
uses a declarative `BrowserRouter`, so in-app navigation blockers are not
available; browser Back and tab close are covered only by the native
`beforeunload` prompt, and returning to an active attempt depends on the
Assessment contract supporting resume.

### F7 Understand results and mastery — Contract Pending

Result → score, per-question correct/incorrect → "Skills updated" list with old
→ new state tags (only if the contract returns them) → recommendation card →
"Open Mastery Map" → select a skill → detail aside: state, related lessons,
latest score → "Open lesson". Mastery Map reflects the latest assessment
(US-07).

### F8 Follow a recommendation — Contract Pending (Adaptive)

Guidance Stack or Mastery Map "Next recommended" → type tag (Continue / Test-out
/ Remedial), target lesson, reason → "Open lesson". Remedial: the target lesson
shows a lilac "Recommended review" banner explaining why. Test-out: the skipped
lessons show "Mastered" with "Counted as complete"; the learner can still open
them. Advanced lesson with an unmastered prerequisite: butter warning banner
"You haven't mastered Loops yet. This lesson builds on it." with "Review Loops"
(when a remedial target exists) and "Continue anyway" — never blocking.

### F9 Submit an assignment — Contract Pending (Assessment + CT09)

Assignment → instructions and deadline → drop zone → upload → "Submitted · 20
Nov, 14:05" with replace option while ungraded and before the deadline → after
grading: grade and feedback, notification. After the deadline: drop zone
replaced by "The deadline has passed. Submissions are closed." (US-08).

### F10 Q&A, report and notifications — Contract Pending (Interaction)

Lesson → "Questions" section → ask (text area, "Post question") → appears in the
list. Reply notification → bell count → Notifications, where the message names the lesson in words (notification rows carry no link, `CT11-READ-FIELDS-2026-11-08-r1`).
Report: post overflow menu → "Report post" → dialog → confirmation "Thanks. An
administrator will review this post." Instructors reply from Lesson Q&A.

### F11 Instructor builds and publishes — Contract Pending (CT21.remaining, CT40, Assessment, CT09)

Teaching → Create course (title, description, prerequisites text) → Course
editor/Content: add chapter, add lesson, reorder with Move up / Move down, edit
lesson (Markdown text, file, external video link), map skills → Skills: add
skills, add prerequisites (cycle rejected with the server's message) → map
lessons → Assessments: questions (kind, options, correct answers, skill,
difficulty), quizzes (kind, time limit, questions), assignments (deadline) →
Publish course (confirmation listing what learners will see). Adaptive mode:
switch enabled only when the readiness checklist from the backend passes.

### F12 Instructor grades and inspects — Contract Pending

Grading → filter Ungraded → submission → download file → grade 0–100 + feedback
(required) → "Save grade" → confirmation "The learner will see this grade and
receive a notification." Learners → learner → progress, results, read-only
Mastery Map, recommendation log (type, target lesson, reason, time).

### F13 Administrator governs — Contract Pending (CT12, CT31, Interaction, reporting)

Users → search → row → "Change role" dialog (Learner / Instructor /
Administrator) or "Lock account" confirmation → audit entry created by the
backend. Reports → Open → side sheet with the reported content → "Hide
content" (resolves) or "Dismiss" → status updates. Overview → metrics per course
and skill. Audit log → filter by actor, action and date.

### F14 Access, errors and recovery — Implemented patterns, extended

| Situation | Design |
| --- | --- |
| Anonymous on a protected route | Replace to `/login` (Implemented). Proposed copy on the sign-in page: none unless F2 notice is approved. |
| Wrong role | Access denied page inside the shell: title "You don't have access to this page", body "You're signed in as an Instructor. This page is for learners.", actions "Go to catalogue" and "Profile". Uses only the session role. |
| Unknown URL | "Page not found" / "Check the address, or continue from the catalogue." / "Browse courses". |
| Hidden or missing course | "Course not available" / "This course doesn't exist or isn't published." / "Back to catalogue". |
| Network failure | Inline rose alert "We couldn't reach CodeMastery. Check your connection and try again." + "Try again". |
| Server problem (5xx, protocol) | "Something went wrong on our side. Try again in a moment." + "Try again". Never show server diagnostics. |
| Validation (400) | Field-level messages from the decoded problem detail; otherwise a form-level alert. |
| Empty data | Teach the next step (see each screen). |

## Screen Specifications

Wireframes use desktop width (≥ 1200 px) unless stated. Anything marked
*(contract)* appears only when the owning contract provides that data.
Wireframe glyphs (`●`, `◉`, `○`, `┆`, `✓`, `(→)`, `[search]`) are schematic;
the built UI uses `PresentationIcon` SVGs and real components. Names and course
titles in wireframes come from the seeded sample data or are illustrative.

### S1 App shell — Implemented (restyle)

Covered by [Layout](#layout) and the Top bar component. Skip link "Skip to main
content" is the first focusable element and targets `main#main-content`
(existing id). The `main` landmark holds the page; the top bar is a `header`
with a `nav` labelled "Main navigation"; the account menu is a separate button.

### S2 Sign in and Create account — Implemented (restyle), Specialized

```text
┌──────────── ink-950 ─────────────┬──────────────── surface ────────────────┐
│ [logo] CodeMastery                │                                          │
│                                   │   Sign in                                │
│  Learn programming at the pace    │   Welcome back. Continue where you       │
│  of what you actually know.       │   left off.                              │
│                                   │   Email address    [                  ]  │
│   ●┄┄┄┄●┄┄┄┄●   (logo node motif, │   Password         [            [eye] ]  │
│        ┆        crisp vector)     │   [        Sign in        ] (graphite)   │
│        ●                          │   New to CodeMastery? Create an account  │
│                                   │   Forgot password? (preview only, CT02)  │
└───────────────────────────────────┴──────────────────────────────────────────┘
```

- Split 5/7 columns at ≥ 900 px; the brand panel is `ink-950` with the 40 px lockup (linking to `/`), the 40 px headline "Learn programming at the pace of what you actually know.", a node-and-link vector (mint done node, `brand-600` current node, `chrome-muted` next node on a dashed `brand-400` path) and one product sentence in `chrome-muted`. The form panel is white with a 32 px top-left and bottom-left radius. Below 900 px the brand panel collapses to a 120 px ink header with the lockup; the form takes the page with 24 px top corners.
- Form column max 440 px, vertically centred, page title 40 px, subtitle in `ink-600`, an optional notice above the form (F2 alert).
- Password field has a show/hide icon button ("Show password"/"Hide password").
- Create account adds display name, password checklist (live, with check glyphs and text), confirm password.
- Keep the AutoFill focus-release behavior of the current `AuthLayout`.

### S3 Catalogue — Implemented (restyle), Adapted

```text
Course catalogue
Introductory programming courses built for mastery learning.
( [search] Search by title or description              ) [Search]
[Any prerequisites | No prerequisites | Prerequisites described]
┌───────────────┐ ┌───────────────┐ ┌───────────────┐
│ [</>]         │ │ [</>]         │ │ [logo]        │
│ Introduction  │ │ Programming   │ │ …             │
│ to Programming│ │ Practice      │ │               │
│ description…  │ │ description…  │ │               │
│ Instructor ·  │ │ Instructor ·  │ │               │
│ Name       (→)│ │ Name       (→)│ │               │
└───────────────┘ └───────────────┘ └───────────────┘
Showing 1–20 of 21 published courses                    ‹  1  2  ›
```

- Search pill and a graphite primary "Search" button on one row (submit on Enter or the button), the prerequisite segmented control below. Filters map exactly to `prerequisites=any|none|described`; search maps to `q` (max 100 UTF-16 code units, enforced by the field; no counter is shown). A prerequisite filter applies on selection with the last submitted search; unsent text in the field is never applied. No sort or category controls (FE07).
- Grid: 3 columns ≥ 1200, 2 at 600–1199, 1 below.
- Course tiles as specified, without a membership pill: the catalogue response has no membership data and no per-tile membership requests are added. Pagination below the grid: "Showing 1–20 of 21 published courses" with 44 px page buttons (current page graphite).
- Empty, error and loading per F3. Changing filters resets to page 1 (existing).

### S4 Course detail — Implemented (restyle), Adapted

```text
‹ Catalogue
Introduction to Programming          [Active] [Leave course] [ My courses ]
Instructor · Dr. Name
┌ main (2 fr) ────────────────────────────┐ ┌ Course facts (1 fr, fill-150) ──────┐
│ ┌ tile: About this course ────────────┐ │ │ Prerequisites                       │
│ │ description (body-reading, 70ch)    │ │ │  "None specified" / text            │
│ └─────────────────────────────────────┘ │ │ Published content                   │
│ ┌ tile: Syllabus ─────────────────────┐ │ │  2 chapters · 3 lessons             │
│ │ ▾ Getting Started                    │ │ └─────────────────────────────────────┘
│ │   · What a Program Is                │ │
│ │ ▸ Making Decisions                   │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

- Two columns (2 fr / 1 fr, min 260 px) from 900 px; one column below. "About this course" and "Syllabus" are white tiles; "Course facts" is a `fill-150` `rounded.lg` panel with a definition list (Prerequisites, Published content). The instructor is named once, in the title meta.
- Syllabus uses the Path Spine in its static `rows` form (no status, no links), since syllabus headings are not lesson links (FE07). Chapters are accordions with accessible disclosure buttons; the first chapter is open.
- Chapter and lesson counts are derived from the returned syllabus; nothing else is computed.
- Membership actions in the title row: membership pill, then the state's action (Enroll in course / Re-enroll as graphite primary with "My courses" secondary; once joined, "Leave course" secondary with "My courses" as graphite primary); Leave opens the confirmation. A status line reports "Checking membership…" / "Updating membership…", errors show a rose alert with "Try again". Anonymous: "Sign in to enroll". Non-learner roles: sky inline alert "Course membership is available to learners." "Go to course" waits for the course workspace.
- Membership status is shown as a pill with a human label (Active, Completed, Left), never the raw enum.

### S5 My courses — Implemented (restyle), High fidelity

```text
My courses
[Joined | Active | Completed | Left | All]                               3 courses
┌────────────────────────────────────┐ ┌────────────────────────────────────┐
│ [</>]                              │ │ [</>]                              │
│ Introduction to Programming        │ │ Programming Practice               │
│ description, up to 3 lines…        │ │ …                                  │
│ Instructor · Name                  │ │ Instructor · Name                  │
│ [Active]                    (⋯)(→) │ │ [Completed]                 (⋯)(→) │
└────────────────────────────────────┘ └────────────────────────────────────┘
```

- No search pill (the API has no search on `/api/me/courses`). The title row has one secondary "Refresh" button; the count ("3 courses") sits at the end of the filter row.
- Status filter as the segmented control mapping to `status=joined|active|completed|left|all` (default Joined = Active + Completed). The page URL carries it: `/my-courses?status=active` preselects Active (Learner home's "See all N active courses" uses it); a missing or unknown value means Joined; changing the control updates the URL (Joined removes the parameter).
- Two-column tiles ≥ 900 px. Tile actions: secondary arrow → course workspace when it exists, otherwise course detail (Implemented today); overflow menu with "View course details" and "Leave course" (opens the existing confirmation) or "Re-enroll" for Left.
- *(contract CT22)* Progress line inside the tile: "12 of 20 lessons · 60%".
- Empty states: Joined — "You haven't joined a course yet" / "Browse the catalogue to find one." / "Browse courses". Left — "No courses left" / "Courses you leave stay here so you can re-enroll." Error with retry.

### S6 Learner home — Implemented (active courses) + Preview (full design), High fidelity

The closest screen to the reference. Purpose: the next step across all active
courses.

```text
Welcome back, Minh
┌ main ───────────────────────────────────────────────┐ ┌ aside: Up next ──────────┐
│ Continue learning                                    │ │ ┌ lilac ───────────────┐ │
│ ┌ Lesson tile (current) ──────────────────────────┐ │ │ │ [route] Remedial Intro│ │
│ │ Introduction to Programming · Lesson 4           │ │ │ │ ┌──────────────────┐ │ │
│ │ For Loops                                        │ │ │ │ │ Review Variables │ │ │
│ │ [Resume]                                    (→)  │ │ │ │ │ — reason text    │ │ │
│ └──────────────────────────────────────────────────┘ │ │ │ │ from the backend │ │ │
│ Your courses                                         │ │ │ └──────────────────┘ │ │
│ ┌ row: Introduction to Programming ───────────────┐ │ │ │ [   Open lesson   ]  │ │
│ │ ████████░░░░ 12 of 20 lessons   Next: For Loops  │ │ │ └──────────────────────┘ │
│ └──────────────────────────────────────────────────┘ │ │ ┌ butter: Deadline ────┐ │
│ ┌ row: Programming Practice ──────────────────────┐ │ │ └──────────────────────┘ │
│ └──────────────────────────────────────────────────┘ │ │ ┌┄ Nothing else needs ┄┐ │
└──────────────────────────────────────────────────────┘ └──────────────────────────┘
```

- **Implemented at `/home` (6 November 2026):** title "Welcome back, *display name*", subtitle "Your active courses, one click from their syllabus.", then "Your courses": one white surface with a row per active membership (32 px course mark, title link to course detail, "Instructor · Name"), "See all *N* active courses in My courses" when more exist, and "Browse the catalogue". Loading, error with retry and the empty state ("You haven't joined a course yet") follow Feedback. "Continue learning", progress lines and the "Up next" aside are omitted because their contracts are pending. `/`, the logo and (since 7 November 2026, C4) sign-in lead a signed-in Learner here; a pending one-use `/profile` return still takes precedence after sign-in, and Instructors and Administrators keep `/courses`.
- **Preview (full design):** the wireframe above with sample data, including deadline, result and notification entries.
- **Final scope (`CT16-LEARNER-HOME-2026-11-08-r1`, approved composition; not implemented):**
  - "Your courses" stays the list of active memberships; each row adds the progress line and the canonical next lesson only from one approved learner-scoped CT22 read (no request per course).
  - "Continue learning" appears only when CT22 supplies the learner's most recent position; the frontend never compares courses itself.
  - "Up next" holds at most one recommendation per active course, that course's own current recommendation, unaltered (type, target lesson, backend reason verbatim, time), labelled with its course and listed in the order the API returns the courses. No ranking, merging, scoring or other cross-course logic; courses without a current recommendation show none.
  - Deadline, result-available and reply/announcement entries are not Final scope; they stay Preview, and learners reach them through Notifications and the course Assessments page.
  - Each element loads and fails on its own and never hides "Your courses"; an element whose read is not approved is omitted. This decision approves no CT22 or CT33 operation.
- Below 1200 px the aside's recommendation entries move to the top of the main column, above "Continue learning" (in the Preview design, deadlines follow and other entries go below the courses).
- "Your courses" is one white surface with a row per active course (title, linear progress with text, canonical next lesson, each only when supplied as above). There is no cross-course stat trio: totals across courses mean nothing to a learner.
- No search pill (no search contract for a learner's courses), no greeting avatar, no streaks, no time-spent charts (no data source).

### S7 Course workspace — Path — Contract Pending, built as Preview, High fidelity

```text
‹ My courses
Introduction to Programming                                        [Resume course]
[ Path | Mastery Map | Assessments ]          ← segmented section switch
┌─rail─┐ ┌ lilac banner: Take a short placement diagnostic … [Start (secondary)] [Skip] ┐  (F5 only)
│ (◉)  │ [ 20 lessons ] [ 12 completed ] [┄ 8 remaining ┄]   ← stat trio (CT22)
│ (≡)  │ Getting Started
└──────┘  ●─┐ ┌ Lesson tile: What a Program Is      [Completed]          (→) ┐
            ┆ └─────────────────────────────────────────────────────────────┘
            ● ┌ Lesson tile: Variables              [Mastered] Counted as complete ┐
            ┆
            ◉ ┌ Lesson tile: For Loops   (lilac badge)  [Recommended] [Resume] (→)┐
            ┆
            ○ ┌ Lesson tile: Functions  (butter badge)  [Prerequisite gap]     (→)┐
                                                   ┌ aside: Up next / Skills summary ┐
```

- Section switch (segmented control under the title): Path, Mastery Map, Assessments. Each maps to its own route once routes are approved.
- View Rail: Path (tiles with spine) and Outline (compact list rows: position, title, status) for fast scanning of long courses.
- Title-row primary action: "Resume course" when a resume point exists, otherwise "Start course" (CT22).
- Tile tags use domain words: Completed; Mastered (+ "Counted as complete"); Skippable (Test-out); Remedial; Recommended; Prerequisite gap. Canonical order is always the list order; recommendations never reorder it.
- Aside (≥ 1200 px): the current recommendation (lilac Guidance entry), a compact skills summary (count of skills per state as four tags, "Open Mastery Map"), and upcoming deadlines *(contract)*.
- Mobile: stat trio becomes a single line ("12 of 20 lessons completed") with the progress bar; recommendation entry first; spine gutter shrinks to 24 px.

### S8 Lesson — Contract Pending, built as Preview, Reader

```text
‹ Introduction to Programming
┌ outline 280 ┐ ┌ reading column ≤ 720 ──────────────────────┐ ┌ aside 360/400 ─────┐
│ Getting     │ │ Lesson 4 · Loops                            │ │ Skills in lesson   │
│ ✓ 1 What…   │ │ For Loops                                   │ │ [Loops · Learning] │
│ ✓ 2 Break…  │ │ [butter alert: prerequisite gap … ]         │ │ Recommended next   │
│ ◉ 4 For…    │ │ Markdown text (body-reading, 70ch)          │ │ (lilac entry)      │
│ ○ 5 Func…   │ │ ┌ code block (ink) ───────────────┐         │ │ Materials          │
│             │ │ └──────────────────────────────────┘        │ │ slides.pdf [down]  │
│             │ │ Video (external embed, 16:9) / link          │ │ Video link [ext]   │
│             │ │ Questions (Q&A section)                      │ │                    │
└─────────────┘ └──────────────────────────────────────────────┘ └────────────────────┘
 sticky action bar:  [‹ Previous]          [ Mark as complete ]          [Next lesson ›]
```

- Column budget: the outline shows as a 280 px sticky column (`presentationTokens.outlineWidth`) only at ≥ 1536 px; below that a secondary "Course outline" button in the title row opens it in a left drawer. The aside (Skills in this lesson, Recommended next, Materials) is a `fill-150` column from 1200 px and moves below the content under 1200 px.
- Sticky bottom action bar: a white pill bar (max 720 px, Raised shadow, 16 px from the bottom, 8 px on phones) with a previous-lesson circle, "Mark as complete" (ink) and "Next lesson" (secondary until completion, then ink; an icon button on phones). After completion the status "Completed" appears under the title.
- Materials: text (in-column), file (CT09 delivery; download button with name and type), external video (embedded player from the external link; a plain link fallback "Open video in a new tab"). No hosting UI.
- Q&A section below the content (Contract Pending): list of questions with replies, "Ask a question" composer, overflow "Report post".

### S9 Placement diagnostic — Contract Pending, built as Preview, Focus

Uses the Quiz attempt layout with the title "Placement diagnostic" and an intro
step: what it covers (skill names from the contract), the time limit, and both
choices "Start diagnostic" / "Skip and start from the beginning". Result step
lists skills with their new state tags and leads to the Mastery Map.

### S10 Course assessments — Contract Pending, built as Preview, Adapted

Title "Assessments"; segmented filter (All, Quizzes, Assignments); table on
desktop with Name, Type (Diagnostic / Practice / Test / Assignment), Time limit
or Deadline, Attempts or Submission status, Last score, action. Stacked rows on
mobile. Only published items for enrolled learners (US-12).

### S11 Quiz attempt — Contract Pending, built as Preview, Focus (Specialized)

```text
┌ ink frame bar ──────────────────────────────────────────────────────────────────┐
│ (x) Loops practice quiz        Question 4 of 10     [clock 07:42]  [Submit quiz] │
└─────────────────────────────────────────────────────────────────────────────────┘
┌ canvas ───────────────────────────────────────────────┐ ┌ navigator ───────────┐
│ What does this program print?                          │ │ (1)(2)(3)(◉)(5)       │
│ ┌ code (ink) ──────────────────────┐                   │ │ (6)(7)(8)(9)(10)      │
│ │ for i in range(3): print(i * 2)  │                   │ │ ● answered ○ not yet  │
│ └──────────────────────────────────┘                   │ │ 8 of 10 answered      │
│ ( A ) 0 2 4                                            │ └───────────────────────┘
│ ( B ) 2 4 6                                            │
│ [‹ Previous]                               [Next ›]    │
└────────────────────────────────────────────────────────┘
```

- No primary navigation during an attempt; the exit button opens the leave guard.
- Question kinds: single choice (radio), multiple choice (checkbox, "Select all that apply"), true/false (two radios), predict-the-output (choice format with a code block; no code execution).
- Navigator: numbered 44 px circles: answered = ink, current = Logo Blue ring, not answered = outline; a text summary below. Below 900 px the navigator becomes a horizontally scrollable strip under the frame bar.
- Timer and auto-submit per F6 and Progress.

### S12 Quiz result and attempt history — Contract Pending, built as Preview, Adapted

- Header: page title (quiz name), stat tiles: Score (e.g. "80%"; up to two decimals, as stored), Correct (8 of 10), Attempt (number). Submitted date-time caption.
- Question review: ordered list; each item shows the question, the learner's answer, and a Correct (mint) or Incorrect (rose) tag with glyph. Correct answers are shown only if the contract returns them.
- *(contract)* Skills updated: skill name with previous → current tags.
- Next step: recommendation entry; "Back to course".
- History: table Attempt, Submitted, Score, "View result"; newest first.

### S13 Assignment — Contract Pending (+ CT09), built as Preview, Adapted

Detail layout: title, deadline pill (butter when due within 48 hours; rose
"Closed" after the deadline), instructions (body-reading), submission panel
(drop zone or current file row with "Replace file"), grade panel after grading
(stat "Grade 85 / 100" and feedback text). Accepted types and size are shown
from the contract; until CT09 defines them the upload cannot be implemented.

### S14 Mastery Map — Contract Pending, built as Preview, High fidelity (Canvas)

```text
Introduction to Programming
[ Path | Mastery Map | Assessments ]
┌rail────┐ ┌ white canvas surface (React Flow) ───────────────────┐ ┌ aside ──────────────┐
│(graph) │ │ [Variables·M]┄┄▶[Conditionals·L]┄┄▶[Loops·W]┄┄▶[Fn·U]│ │ Next recommended    │
│(list)  │ │       ┆                          ▲                   │ │ (lilac entry)       │
│        │ │ [Expressions·M]┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┘                    │ │ Selected skill      │
│(+)     │ │                                                      │ │ Loops   [Weak]      │
│(−)     │ │                                                      │ │ Latest score 40%    │
│(fit)   │ └──────────────────────────────────────────────────────┘ │ Prerequisites       │
└────┘ Legend: [Unknown] [Learning] [Mastered] [Weak]  ┄▶ prerequisite │ Related lessons  │
                                                                   └─────────────────────┘
```

- Layout: deterministic left-to-right layering by prerequisite depth computed in `mastery/utils/masteryGraphLayout.ts` from the returned graph; no layout library is added. Ties ordered by skill name. This is presentation, not adaptive logic.
- Interactions: click or Enter selects a node; Tab moves between nodes in layer order; Escape clears selection; zoom and fit via rail buttons and standard gestures; minimap off.
- List view (default below 900 px and always available): table/rows with Skill, State, Prerequisites (names with their state tags), Related lessons, Latest score. It presents the same information as the graph.
- Aside: Next recommended entry; selected-skill detail with state tag and its one-line meaning, latest score *(contract)*, prerequisites, related lessons with lesson status and "Open lesson". Empty selection: "Select a skill to see related lessons and your latest score."
- Updates: after a new result, changed nodes show a mint or butter ring for 1.2 s and an "Updated" tag until the page is left (static tag with reduced motion).
- Empty: course has no skills: "This course doesn't have a skill map yet." No data for the learner: all nodes Unknown plus the diagnostic offer.

### S15 Notifications — Contract Pending, built as Preview, Adapted

Bell button in the top bar with count badge *(contract)*; it shows the current state while the page is open. Page: list grouped by
day (Today, Yesterday, date), each row: one neutral bell glyph, the message as
plain text (title type when unread, body when read), time caption and unread dot
(`brand-600`) with "Unread" text for screen readers. Rows show the message only:
the notification representation has no type or link field
(`CT11-READ-FIELDS-2026-11-08-r1`, partial approval of CT11.read), so rows carry no
category tint, no category label and no destination, and the UI never infers
either from the text. Notifications concern new content, upcoming deadlines,
results, Q&A replies or recommendations (US-09; architecture §6), named in the
message itself. Actions "Mark as read" / "Mark all as read" appear only if CT11.read
approves them (still pending). The preview's per-type tints and source links are
Preview only.

### S16 Profile — Implemented (restyle), Adapted

Page header "Profile" with the subtitle "Details from your current sign-in.",
then one white tile, max 720 px: account avatar (64 px circle, solid `brand-700`, white
initial), display name (section title), role as a neutral 28 px tag with the
user glyph. Definition list: Display name, Role, User ID (JetBrains Mono, `ink-700`,
plain text). No edit controls, no email, no account status (CT16-PROFILE P2).

### S17 Teaching home — Proposed + Contract Pending, built as Preview, High fidelity

Plan archetype. Title "Teaching", search pill, "Create course" primary. Main:
course tiles for owned courses with Published (mint) or Hidden (neutral) pill,
counts only from the contract (lessons, enrolled learners). Aside "Needs
attention": ungraded submissions (butter), unanswered questions (sky), courses
not ready for adaptive mode (lilac) — each Contract Pending.

### S18 Course editor and lesson editor — Contract Pending, built as Preview, Specialized

- Header: course title, publication pill, "Preview as learner" *(contract)*, "Publish" or "Hide" (confirmation). Tabs: Content · Skills · Assessments · Learners · Q&A · Settings.
- Content: chapter sections each with an ordered lesson list (rows with position, title, publication pill, skills count, actions Edit / Move up / Move down / overflow). "Add chapter", "Add lesson". Reordering uses buttons with announced results ("Moved For Loops to position 3"); drag-and-drop may be added later only with an accessible alternative kept.
- Lesson editor (Form archetype): Title; Content (Markdown textarea with a Preview tab rendered under CT37.markdown once a renderer dependency is approved); Materials list with "Add text", "Add file" (CT09), "Add video link"; Skills (multi-select chips of course skills; at least one required, US-11); Publication. Sticky action bar "Save changes" + status ("Saved", "Unsaved changes"). Leaving with unsaved changes asks for confirmation.

### S19 Question bank, quiz editor, assignment editor — Contract Pending, built as Preview, Specialized

- Question bank: Manage archetype. Filters by skill, difficulty (Easy/Medium/Hard), kind. Columns: Question (2 lines), Kind, Skill, Difficulty, Used in. "New question".
- Question form: kind; prompt; optional code snippet (code editor textarea with monospace); options with "Correct" toggles (exactly one for single choice and true/false; at least one for multiple choice; True/False labels fixed); skill (required); difficulty (required). Notice for used questions: "This question is used in past attempts. Saving creates a replacement question; past attempts keep the original." (data-model §11: used questions are immutable and edits create replacement questions; there is no revision entity).
- Quiz editor: kind (Diagnostic, Practice, Test), title, time limit in minutes (stored as seconds), ordered question list with add/remove/move, publication.
- Assignment editor: title, instructions, deadline (date-time with timezone label), optional lesson link, publication.

### S20 Skill graph editor — Contract Pending (CT40, Adaptive §15), built as Preview, Canvas

Same canvas as S14 in edit mode. Aside forms: "Add skill" (name, description);
selected skill → "Prerequisites" multi-select of other course skills (the
accessible way to create edges; drag-to-connect is an optional shortcut);
cycle rejection shows the server message in a rose alert at the form and keeps
the graph unchanged. Second tab "Lesson mapping": table of lessons × skills
chips. Third tab "Adaptive mode": readiness checklist from the backend (every
published lesson mapped; each skill has the minimum question count — the number
comes from configuration and is displayed, never hard-coded) and the switch,
disabled with the failing items listed until all pass (US-11).

### S21 Grading — Contract Pending (+ CT09), built as Preview, Specialized

List: Manage archetype; filters Ungraded / Graded / All; columns Learner,
Assignment, Course, Submitted, Status. Grade page: Detail archetype; file row
with "Download"; grade field (0–100, integer or decimal per contract); feedback
textarea (required); "Save grade" primary with the F12 confirmation. Graded
submissions are read-only (data-model: graded records cannot be replaced).

### S22 Learner insights — Contract Pending, built as Preview, Adapted

List per course: Learner, Progress *(CT22)*, Latest score, Mastery (four state
counts as tags), Last recommendation (type tag + time). Detail: header with
learner name and course; segmented views Progress, Results, Mastery Map
(read-only map; see Handoff on sharing), Recommendation log (table: Time, Type,
Target lesson, Reason; newest first). Only learners enrolled in the
instructor's own courses appear (US-14).

### S23 Platform overview — Contract Pending, built as Preview, Adapted

Title "Platform overview"; course filter. Sections, each a white surface with
its chart and "Show as table": Enrolments by course; Completion rate by course;
Average quiz score by course; Mastery distribution by skill (stacked bars of the
four states for the selected course). Metric definitions come from the
reporting contract and are printed under each title. No headline KPI cards with
deltas.

### S24 Users, Courses, Content reports, Audit log — Contract Pending, built as Preview, Specialized

- Users: columns Display name, Email *(only if the contract exposes it to Administrators)*, Role, Status (Active mint / Locked rose with lock glyph), actions. "Change role" dialog with three radios and the consequence sentence; "Lock account" confirmation "They won't be able to sign in or use protected features while locked."; "Unlock account".
- Courses: Title, Instructor, Publication, actions per CT12.
- Content reports: segmented Open / Resolved / Dismissed; columns Reported item, Type (Post, Lesson), Reporter, Reported at, Status; side sheet with the content excerpt and actions "Hide content" (danger confirmation) and "Dismiss report".
- Audit log: filters Actor, Action, Date range; columns Time, Actor, Action (code type, as stored), Target. Read-only.

### S25 Landing page — Implemented at `/`, Persuade

Approved and activated on 6 November 2026; a signed-in visitor is sent to their
start page instead. Its own ink header (no canvas reach): lockup, then
"Catalogue" and "Sign in" text buttons and a white "Create account" small pill
(hidden below 600 px). Ink hero: headline "Learn programming at the pace of what
you actually know." (40 px, 56 px from 900 px), one supporting sentence in
`chrome-muted`, white "Browse courses" pill and a `chrome-raised` "Create account"
pill; beside it from 1200 px (below it otherwise) a `canvas-100` `rounded.xl` card
holding the example Mastery Map from fixed synthetic data, titled "Example map"
and marked "Illustration · not learner data", with the four-state legend. It
loads as its own chunk. Below, a canvas section with top corners: "How a course
works" as a `rows` Path Spine (diagnostic, lessons and quizzes, map and
recommendations) and "For instructors" with "Sign in to teach". No testimonials,
statistics or institution logos.

### S26 Design preview — Implemented route, no preview chrome

`/preview` is the living reference of this system, loaded as a separate chunk
that production journeys never fetch. It never reads the session or calls the
API, and it does not create membership, progress or authentication data (FE07).

- **Index** (`/preview` or `?screen=overview`): "CodeMastery design preview" with
  a directory of every screen (live routes, then Learner, Instructor,
  Administrator, Public and account), each with its spec number and a Live (mint),
  Preview (sky) or Proposed (dashed outline) pill, followed by the design reference: colour swatches,
  semantic families, type scale, actions and inputs, states and tags, alerts,
  stat trio, composition (course tile, course mark samples, Path Spine, Guidance
  Stack, code block) and tables and feedback.
- **Screens** open by link: `/preview?screen=<id>` (with `item` and `tab` where a
  screen needs them). Each renders in the production shell of its role with
  that role's navigation, sample persona and account menu, so it looks as it
  would when deployed.
- **No preview chrome (7 November 2026, Leader decision):** no screen shows a
  sample-data banner, a workspace switcher, a standalone "Preview index" or "Reset
  sample data" button, or a "Simulate time running out" control.
- **Where index and reset live:** on screens rendered in a role shell, the logo
  returns to the index, and the account menu (not the page) holds two menu items,
  "Preview index" and "Reset sample data". Screens rendered without the shell —
  landing, sign-in sample states, session-ended notice, forgot password, reset
  password, expired reset link and the quiz attempt — have no account menu and move
  within the preview through their own links. Reloading also resets the in-memory
  sample state.
- **Remaining sample-data cues:** the index subtitle states that the screens use
  sample data (Principle 5), and some screens keep a neutral note (info glyph and
  text, no tint) naming the contract they stand in for.

## Domain Presentation Rules

| Concept | Source of truth | UI wording | Component | Must never |
| --- | --- | --- | --- | --- |
| Lesson completion | `LessonProgress` completion flag | Completed | Mint status pill, filled spine node | Imply mastery |
| Mastery-based skip | `LessonProgress` mastery-skip flag (implies completion) | Mastered · Counted as complete | Mint pill with skip glyph | Be shown as a skill state |
| Course progress | Completed (incl. mastered-skip) lessons ÷ required lessons (US-04, CT22) | "12 of 20 lessons · 60%" | Linear progress + text | Appear before CT22 provides it |
| Course membership | Membership `none/active/completed/left` (FE07) | Not enrolled, Active, Completed, Left | Neutral/mint pill | Be called progress or mastery |
| Assessment result | Attempt score 0–100, per-question correctness | Score 80%, Correct, Incorrect | Stat tile, mint/rose tags | Be called mastery |
| Skill mastery | `LearnerSkillMastery` state; missing row = Unknown | Unknown, Learning, Mastered, Weak | Mastery tag, map node | Show a number, percent or bar |
| Recommendation | `PathRecommendation` type, target lesson, reason, time | Continue, Test-out, Remedial + reason | Lilac guidance entry, type tag | Reorder the canonical path or omit the reason |
| Prerequisite gap | Adaptive warning (no hard block) | "You haven't mastered X yet" | Butter alert, butter corner badge | Use a lock or disable access |
| Manual assignment grade | `Submission` grade + feedback | Grade 85 / 100 | Stat, feedback text | Be presented as updating mastery |

Skill state meanings shown in legends and tooltips:

- **Unknown** — Not assessed yet.
- **Learning** — Some quiz evidence, not yet mastered.
- **Mastered** — Quiz evidence shows you've mastered this skill.
- **Weak** — Quiz evidence shows a gap to review.

Thresholds behind these states are configuration and are never displayed or
implied by the UI (Adaptive §4).

## Accessibility

**Target:** WCAG 2.2 Level AA, adopted as this project's design target. It is
not a conformance claim; conformance needs browser, keyboard, zoom and
screen-reader verification of the built UI.

| Area | Requirement |
| --- | --- |
| Structure | `header`, `nav` (labelled), `main#main-content`, `aside` (labelled) landmarks; one `h1`; heading levels without gaps; lists for Path Spine, Guidance Stack and navigator. |
| Focus | Visible 2 px ring with 2 px offset: `brand-600` on light grounds, `focus-on-chrome` on the frame. Never removed. Route change focuses the `h1` (existing); async updates never steal focus; dialog focus is trapped and returned. |
| Keyboard | Every action reachable by Tab; menus and segmented controls use arrow keys; Escape closes overlays; the graph supports Tab/Enter/Escape and has the List view equivalent. |
| Targets | Minimum 44 × 44 px for all interactive elements (WCAG 2.5.8 needs 24 px; the system uses 44). |
| Colour | Never the only cue (state words + glyphs + dashed/hatched patterns). Text contrast ≥ 4.5:1, large text and UI graphics ≥ 3:1, per the computed pairs in [Colors](#colors). |
| Forms | Visible labels, `autocomplete`, errors linked by `aria-describedby`, error summary on submit, no placeholder-only labels. |
| Live regions | Loading and status messages `role="status"`; errors `role="alert"` only for blocking failures; the quiz timer announces only its two visual threshold changes (see [Progress](#progress)), never every second. |
| Reflow and zoom | Usable at 320 px width and 200% zoom without horizontal page scroll (tables scroll inside their surface). |
| Motion | Respect `prefers-reduced-motion`; no flashing. |
| Media | External video embeds have titles; captions depend on the external provider and are not claimed. |
| Language | `lang="en"` on the document; English copy everywhere. |

Verification each release: keyboard-only pass of every implemented flow;
VoiceOver (Safari) and NVDA (Firefox or Chrome) pass of sign-in, catalogue,
course detail and My courses; 320 px and 200% zoom checks; automated checks in
the existing optional browser harness where it applies. Report what actually
ran; never mark a criterion as passed without evidence.

## Content Design & UX Writing

**Language.** All interface text and demo data in English. Sentence case for
titles, buttons, navigation, tabs and labels ("My courses", "Mark as complete").
Proper nouns and the feature name "Mastery Map" keep capitals. Keep the
incumbent spellings "Enroll", "Re-enroll" and "Catalogue".

**Voice.** Plain, calm, encouraging without hype. Address the learner as "you".
No exclamation marks in errors. No jokes in failure states.

### Terminology

| Use | Don't use |
| --- | --- |
| Course, Chapter, Lesson, Material | Module, Unit, Class |
| Enroll, Leave course, Re-enroll | Join, Subscribe, Drop |
| Completed (lesson or course) | Done, Finished, Passed |
| Progress (lessons completed) | Mastery, Score |
| Score (quiz), Grade (assignment) | Mastery, Points |
| Unknown / Learning / Mastered / Weak (skills) | Beginner/Expert, Not started, %, levels |
| Recommended, Continue, Test-out, Remedial | AI suggestion, Smart path |
| Prerequisite gap | Locked, Blocked |
| Placement diagnostic | Placement test, Exam |
| Practice quiz, Test (quiz kinds) | Exam, Homework |
| Learner, Instructor, Administrator | Student, Teacher, Admin (in UI copy) |
| Published, Hidden | Live, Draft, Unpublished |
| Active, Locked (accounts) | Enabled, Banned |
| Open, Resolved, Dismissed (reports) | Pending, Closed |

### CTA labels

Enroll in course · Re-enroll · Leave course · Sign in to enroll · Go to course
· Start course · Resume · Open lesson · Mark as complete · Next lesson · Start
diagnostic · Skip and start from the beginning · Start quiz · Submit quiz ·
View result · Open Mastery Map · Submit file · Replace file · Ask a question ·
Post question · Report post · Create course · Add chapter · Add lesson · Save
changes · Publish course · Hide course · Save grade · Change role · Lock account
· Unlock account · Hide content · Dismiss report · Try again · Browse courses.

A button names its action with a verb. A confirmation dialog repeats that verb
on its confirm button ("Leave course"), never "OK" or "Yes".

### Message patterns

| Type | Pattern | Example |
| --- | --- | --- |
| Error | What happened + what to do | "We couldn't load this course. Try again." |
| Field error | What's wrong + the rule | "Passwords must match." / "Use at least 15 Unicode characters and at most 72 UTF-8 bytes." |
| Empty | What this place is for + next step | "You haven't joined a course yet. Browse the catalogue to find one." |
| Success | What changed | "You're enrolled in Introduction to Programming." |
| Destructive confirm | Consequence + what's kept | "Leave course? Your learning history will be preserved. You can re-enroll while the course is published." |
| Warning | Situation + optional action, never blocking | "You haven't mastered Loops yet. This lesson builds on it." |
| Loading | Name the thing | "Loading your courses" |

Server-provided recommendation reasons and validation details are displayed as
plain text, never as HTML. Unknown server errors are replaced by the generic
patterns above.

## Implementation Handoff

### Binding constraints for implementers

- Read root `CLAUDE.md` (or your agent's equivalent instruction file) and the sources table at the top before any frontend task. This file is not implementation authorization; each task needs its own scope.
- Stack stays React 19 + TypeScript + Vite + MUI 9 + Emotion + React Router 7.18.4 + React Flow 12 (`frontend/package.json`). No new runtime dependency is introduced by this design: no icon package, chart library, graph layout library, drag-and-drop library or state store.
- Preserve every behavior in "Implemented" screens: strict decoders, memory-only session, generation and cancellation checks, one-use `/profile` return, route heading focus, failed-submit focus, dialog focus lifecycle, Learner-only `/my-courses`, public anonymous catalogue (CT16, FE07).
- Keep feature state in its owning feature (CT16-FINAL F1). Shared code stays presentational.
- New repository files (font asset, license file, new shared components) are structural changes that need explicit approval under `docs/repository-structure.md`. Prefer the existing files listed below. The preview, fonts, licence texts and shared presentation components were approved on 6–7 November 2026 and are recorded there; `CourseMark.tsx` and the two course-logo SVGs (7 November 2026), and the earlier `BrandLogo.tsx`, are recorded in `docs/repository-structure.md` by the r6 documentation change (8 November 2026).

### Approved API consumed today

| Operation | Used by |
| --- | --- |
| `POST /api/auth/register` | Create account |
| `POST /api/auth/login` | Sign in |
| `GET /api/courses` (`page`, `size`, `q`, `prerequisites`) | Catalogue |
| `GET /api/courses/{courseId}` | Course detail |
| `GET`, `PUT`, `DELETE /api/courses/{courseId}/membership` | Course detail membership actions |
| `GET /api/me/courses` (`page`, `size`, `status`) | My courses |

There is no API for progress, resume, lesson content, materials, assessments,
mastery, recommendations, notifications, Q&A, reports, users, metrics or audit.
Every screen that needs one is Contract Pending; build none of them against
invented endpoints, fixtures or mock services in production code.

### Theme mapping (`frontend/src/app/theme/theme.ts`)

| Theme area | Specification |
| --- | --- |
| `palette.primary` | `main brand-600`, `dark brand-700`, `light brand-400`, `contrastText #FFFFFF` (links, focus, selection, progress, checked controls) |
| Custom `palette.ink` and `palette.onChrome` (module augmentation in theme.ts) | `ink`: `main chrome-raised` (graphite), `dark chrome-hover`, `light ink-700`, `contrastText #FFFFFF`; `MuiButton` default `color: 'ink'` for contained buttons. `onChrome`: `main surface`, `dark fill-150`, `contrastText ink-950` for buttons on the ink frame |
| `palette.success/warning/error/info` | Mint, butter, rose, sky families (`main` = strong colour, `light` = tint, `dark` = strong text colour); `palette.secondary` is lilac |
| Semantic families and mastery states | Not in the MUI palette: `tones` (`{ tint, soft, strong, text }` per family) and `color` in `theme.ts`; mastery labels, meanings, glyphs and data colours in `StatusPill.tsx` (`masteryMeta`); recommendation labels in `recommendationMeta` |
| Tokens added 7 November 2026 | `color.chromeHover`, `color.rose800`, `shadow.control`, `pathLine` (`{ width 2, dash 4, gap 6, dasharray '4 6' }`), typography variant `display`; the short-lived `brandFill` gradient was removed |
| `palette.background` | `default canvas-100`, `paper surface`; `palette.text` primary `ink-900`, secondary `ink-600`; `divider line-200` |
| `typography` | Google Sans Flex stack (`fontStack`), `codeFontStack` JetBrains Mono, `brandFontStack` wordmark only; `htmlFontSize 16`; variants: `h1` page-title, `h2` section-title, `h3` item-title, `h4`/`subtitle1` title, `body1` body, `body2` body-sm, `caption`, `button` label; custom `stat`, `reading`, `code` variants |
| `shape.borderRadius` | 16 (inputs); tiles and surfaces set `lg`/`xl` explicitly |
| `spacing` | 8 (unchanged) |
| Component overrides | `MuiButton` (pill sizes 48/40, ink default, `outlined` = secondary fill, text, error), `MuiIconButton` (44 px circle), `MuiInputLabel` (label above, "(required)"), `MuiOutlinedInput` (fields + `hiddenLabel` search pill variant), `MuiToggleButtonGroup`/`MuiToggleButton` (segmented control, View Rail), `MuiTable*`, `MuiDialog*`, `MuiMenu`/`MuiMenuItem`, `MuiTooltip`, `MuiLinearProgress`, `MuiSkeleton`, `MuiAlert`, `MuiSnackbarContent`, `MuiDrawer`, `MuiPagination*`, `MuiBreadcrumbs`, `MuiLink`, checkbox, radio and switch. Status pills and tags are the `StatusPill` component, not `MuiChip` |
| `presentationTokens` | Shell geometry: top bar 80/64, canvas inset 8, aside 360/400, outline 280 (`outlineWidth`, the lesson outline column from 1536 px; C14), reading 720, content 1440, drawer 304, reach 88 × 14, `brandMark` `{ narrow 32, xs 36, sm 40 }`, `pagePadding` `{ xs 2, sm 3, md 4, lg 5 }` spacing units (16/24/32/40 px), page padding. Shadows and motion are separate `shadow` and `motion` exports. The theme header credits the Nixtio reference (shot 23956256); its comment still names revision r1, while the token values match this revision. |
| Global CSS | `::selection` `brand-100`; `caret-color brand-600`; `font-variant-numeric: tabular-nums` on numeric cells; reduced-motion rules kept |
| Fonts | Done 7 November 2026: Google Sans Flex, JetBrains Mono (Latin, Latin Extended, Vietnamese variable WOFF2) and Poppins 800 Latin in `app/theme/`, OFL texts in `public/`; Figtree retired |

### Shared component plan (existing files first)

| Existing file | New responsibility |
| --- | --- |
| `layouts/DashboardLayout.tsx` | Frame, canvas, optional aside slot, skip link; keeps responsive focus recovery |
| `shared/components/AppHeader.tsx` | Ink top bar: logo, inline primary nav with canvas reach, utilities, account menu |
| `shared/components/SidebarNav.tsx` | Mobile navigation drawer (< 900 px); keeps current-item, close and focus behavior |
| `layouts/AuthLayout.tsx` | Split auth layout; keeps AutoFill focus release |
| `shared/components/PageHeader.tsx` | Title row, description, breadcrumb, actions, search slot |
| `shared/components/StatusViews.tsx` | Skeleton-aware loading, dashed empty state, error state; heading level supplied by the caller |
| `shared/components/ConfirmDialog.tsx` | Restyled dialog; Cancel-first focus kept |
| `shared/components/PresentationIcon.tsx` | Expanded original icon set |
| `shared/components/BrandLogo.tsx` | `BrandLogo` (artwork) and `BrandWordmark` (lockup with responsive `BrandSize`) |
| `shared/components/FileUploadField.tsx` | Drop zone (needs CT09; built for the preview) |
| `shared/components/StatusPill.tsx` | Status pill, tag, mastery tag, recommendation tag, mastery legend |
| `shared/components/Tile.tsx` | Tile, title link, corner badge, circle action and affordance, stat tile and group, progress line, course tile, fact list, section heading |
| `shared/components/CourseMark.tsx` + `course-logo-*.svg` | Course mark: logo registry, language resolution, optical sizing, generic mark |
| `shared/components/PathSpine.tsx`, `GuidanceStack.tsx`, `ViewRail.tsx`, `DataTable.tsx`, `CodeBlock.tsx` | Path Spine, Guidance Stack, segmented control and View Rail, table with toolbar and pagination, code block |
| `shared/components/MarkdownRenderer.tsx` | Lesson Markdown under CT37.markdown (`CT37-MARKDOWN-2026-11-08-r1`); a Markdown library is a separate dependency decision |
| `shared/components/MasteryGraph.tsx`, `shared/utils/masteryGraphLayout.ts` (planned move) | Read-only Mastery Map graph and layout shared by the Learner map and Instructor learner detail (`STRUCT-MASTERY-MAP-2026-11-08-r1`); FE12 moves them from `features/mastery` |
| `features/learning/components/CourseOutline.tsx` | Path Spine and outline list |
| `features/mastery/components/*` | `MasteryStateChip`, `NextRecommendedCard`, `SkillDetailPanel`, `PlacementPrompt` as specified (`MasteryGraph` is here today and moves to `shared/`) |

Status pills, tiles, tables and the other primitives above are shared
presentation components (approved 6 November 2026), built on MUI and the theme.
Mastery-state labels and glyph names live in `StatusPill.tsx`, so
the Learner, Instructor and Administrator features use them without importing
each other's internals. The read-only Mastery Map graph moves to `shared/`
(`STRUCT-MASTERY-MAP-2026-11-08-r1`): it takes data through props only, Instructor
learner detail composes it with its own data, and no feature imports another
feature's internals. The move is FE12's; it has not happened yet.

### Delivery order

Phases 2–7 are built as presentation in `/preview` with sample data; each still
needs its contracts before it can be routed.

| Phase | Scope | Gate |
| --- | --- | --- |
| 0 Foundations | Theme tokens and overrides, font swap, icons, shell (header, drawer, canvas, reach), auth layout, status views, page header, dialog, `/preview` rebuild | Done 6–7 November 2026 |
| 1 Approved flows | Sign in, Create account, Catalogue, Course detail + membership, My courses, Profile, Not found, Access denied | Done 6 November 2026 |
| 2 Learning journey | Course workspace Path, Lesson, progress, resume | CT21.remaining, CT22, CT09, routes |
| 3 Assessment | Assessments list, quiz attempt, result, history, assignment | Assessment contracts (CT04/CT10/CT23), CT08, CT09 |
| 4 Adaptive | Diagnostic, Mastery Map, recommendations, warnings | Adaptive contracts (CT05–CT08, CT24.remaining, CT40), Final policy |
| 5 Interaction | Q&A, notifications, reports | Interaction-owned operations |
| 6 Instructor | Teaching, editors, skill graph, grading, insights | CT21.remaining, CT40, Assessment, CT22, CT09 |
| 7 Administrator | Overview, users, courses, reports, audit | CT12, CT31, reporting definitions |
| 8 Optional | Learner home, landing, session-ended notice | Done 6 November 2026 (Learner home with active courses only); the Final Learner home scope of `CT16-LEARNER-HOME-2026-11-08-r1` waits for the CT22 and CT33 reads |

### Acceptance criteria per implemented screen

1. Tokens only: no hard-coded colours, radii, shadows or font sizes outside `theme.ts`.
2. Matches its wireframe zones, order and responsive behavior at 360, 768, 1024, 1280 and 1440 px.
3. All states present: loading skeleton, empty, error with recovery, success feedback, forbidden/unavailable where applicable.
4. Keyboard-only completion of the primary action; visible focus throughout; route focus on `h1`.
5. Text labels on every state; contrast pairs from the Colors section; 44 px targets.
6. Domain wording from the terminology table; no raw enum values on screen.
7. Existing behavior tests still pass; incidental assertions (copy, structure) updated deliberately and listed in the PR.
8. `npm test` and `npm run build` (which includes `npm run typecheck`) run in `frontend/` with their actual results reported.

### Visual QA checklist

- Frame, canvas inset and radius, and canvas reach under the active item.
- Title sizes 40 / 28 / 24 / 18; body 16; no text below 13 px.
- Tiles borderless at 24 px radius with 24 px padding; no nested tiles.
- One primary (graphite) action per region; Logo Blue only for links, focus, selection and progress.
- Pastel tints only with their semantic job; Unknown and Remaining dashed.
- Path Spine aligned to tile titles; chapters interrupt it.
- Icons from `PresentationIcon` only, same stroke.
- No emoji, no gradient fills (logo artwork and skeleton shimmer aside), no lock for prerequisites, no mastery numbers.

### UX QA checklist

- Each page answers "where am I, what can I do, what happens next" within one screen.
- Primary action visible without scrolling at 1280 × 720 and 390 × 844.
- Recommendations show type, target and reason; canonical order unchanged.
- Destructive actions confirm with consequences and keep Cancel focused first.
- Errors name the problem and the recovery; retries do not duplicate mutations.
- Empty states teach the next step.
- Long titles (80+ characters), long display names and missing instructor names wrap or fall back without breaking layout.

### Regression verification

Keep the existing suites green: `httpClient`, `AuthProvider`, `routes/index`,
catalogue and learning API and page suites, `presentation.test.tsx`,
`useDebounce`, `format` (all under `frontend/src/`). Use the optional browser
harness (`npm run test:browser`, see `docs/testing.md`) and Playwright CLI for
rendered checks, and the `e2e/tests/learner-flow.spec.ts` flow against a real
backend where available. Browser and E2E results are reported separately from
unit results.

## Decisions, Open Questions & Change Control

### Key decisions (Decided, 6 November 2026)

| Decision | Rationale | Trade-off |
| --- | --- | --- |
| Ink frame + canvas + aside language from the reference | Matches the requested ~90% similarity of visual language (not of composition or artwork; see the fidelity contract); gives every workspace one recognisable shell | Dark chrome is heavier than the current white shell |
| Top navigation instead of the current left sidebar | Reference fidelity; few destinations per role; frees horizontal space for path and map | Administrators have five items; icons collapse at 900–1199 px |
| *Historical:* Figtree replaces Plus Jakarta Sans (superseded 7 November 2026 by Google Sans Flex, ranked selection; see Typography) | Closest licensed match to the reference face; better UI legibility at small sizes | One font asset change |
| Pastel families bound to mastery and guidance semantics | Uses the reference's palette while making colour meaningful | Fewer free colours for decoration (intended) |
| Ink primary buttons, blue interaction | Reference uses black actions; logo blue keeps brand presence where it helps wayfinding | Two strong colours to manage; Two-Blues Rule enforces it |
| Path Spine (single column) instead of zig-zag | Preserves reading order, reflow and keyboard order | Less playful than the reference |
| No lock icons | No-hard-block invariant | Diverges from the reference |
| List view equivalent for every graph | Accessibility and narrow screens | Two views to maintain |
| Charts without a chart library | Dependency policy; small metric set | Hand-built bars need careful a11y work |

### Leader decisions (7 November 2026)

| Decision | Recorded in |
| --- | --- |
| Google Sans Flex (UI), JetBrains Mono (code), Poppins ExtraBold 800 (wordmark only) | Typography |
| Logo palette tuned for the ink frame; one artwork for dark and light grounds | Brand and logo |
| Canvas reach redrawn as the traced concave bell (88 × 14 px) | Layout › Shell |
| Larger header lockup: 40 px mark from 600 px, fluid 32–36 px on phones (the fluid sizing was replaced the same day by fixed 36/32 px steps, C6) | Brand and logo |
| Course marks: official full-colour language logos with optical sizing, otherwise a Deep Logo Blue code glyph, all in one neutral container; never ink, initials or gradients | Tiles |
| Account avatar in solid identity blue (`brand-700`) | Colors › Identity blue, Top bar |
| A Learner signs in to Learner home; the one-use `/profile` return and other roles' `/courses` are unchanged | F1, Workspaces, `docs/setup.md` (CT16-PROFILE P1 amendment) |
| Consistency fixes C1–C14 | Design consistency review |
| Design preview without preview chrome (no notice, switcher, index or reset buttons); screens opened by link | S26 |

### Delegated technical decisions (8 November 2026)

Delegated technical approval by Claude Code under the Project Leader's explicit authorization for this task; not personal Leader review, implementation authorization or verification. Records: [`docs/setup.md`](docs/setup.md#ct16-route-map-learner-home-composition-and-ct37-markdown-policy--8-november-2026), [data model](docs/data-model.md), [repository structure](docs/repository-structure.md).

| Decision | Effect on this file |
| --- | --- |
| `CT16-ROUTES-2026-11-08-r1` (CT16.routes) | Sitemap paths approved with the activation rule; navigation destinations and active items; Instructor `/teach` and Administrator `/admin` start pages once routed, `/courses` until then |
| `CT16-LEARNER-HOME-2026-11-08-r1` (CT16.learner-home) | S6 Final scope; Guidance Stack on the Learner home limited to recommendation entries |
| `CT37-MARKDOWN-2026-11-08-r1` (CT37.markdown) | Code and Markdown content rules; open question 6 |
| `CT11-READ-FIELDS-2026-11-08-r1` (CT11.read, partial) | S15 rows message-only; F10 |
| `STRUCT-MASTERY-MAP-2026-11-08-r1` | Shared component plan; open question 5 |

### Open questions (do not block design)

1. Resolved 7 November 2026: Google Sans Flex, JetBrains Mono and Poppins (wordmark) assets approved by the Leader's typography instruction.
2. Resolved 8 November 2026 (`CT16-ROUTES-2026-11-08-r1`, delegated technical approval): concrete routes and post-sign-in destinations — Instructors start at `/teach` and Administrators at `/admin` once those screens are routed, `/courses` until then; Learners sign in to `/home` since 7 November 2026. Routes are registered only under the activation rule in the [sitemap](#sitemap).
3. Resolved 6 November 2026: the session-ended notice (F2) is implemented.
4. Resolved 6 November 2026: the landing page replaces the `/` redirect (S25).
5. Resolved 8 November 2026 (`STRUCT-MASTERY-MAP-2026-11-08-r1`, delegated technical approval): the read-only Mastery Map graph and layout move to `shared/` (FE12 performs the move; FE19 reuses it).
6. Resolved 8 November 2026 for the rendering and sanitisation policy (`CT37-MARKDOWN-2026-11-08-r1`, delegated technical approval; see [Code and Markdown content](#code-and-markdown-content)). Still open: the renderer library (Dependency Policy), and `CT37.embed` and `CT37.widget`.
7. Dark theme: not designed in this revision; tokens are role-named so a later theme can remap them.
8. Source of a course's language for course marks: the course API has no language field, so logos appear only when a course's own title or description names one language. A recorded language would need an API and data contract.
9. Resolved 7 November 2026: no visual sample-data label on preview screens (Leader decision recorded in [S26](#s26-design-preview--implemented-route-no-preview-chrome)); the index subtitle names the sample data.

### Implementation blockers by area

| Area | Blocker | Owner source |
| --- | --- | --- |
| Password recovery | CT02 deferred | ADR002 |
| Lesson content and materials | CT21.remaining, CT03.remaining, CT09 deferred; Markdown policy approved (CT37.markdown), renderer dependency and CT37.embed/widget pending | ADR002, setup CT16-FINAL, setup CT37.markdown |
| Progress and resume | CT22 progress/resume/atomic deferred | ADR002 FE07 exclusions |
| Assessments | Assessment operations (CT04/CT10/CT23) and CT08 event deferred | ADR002, testing/setup records |
| Mastery, recommendations, diagnostic, adaptive mode | Final thresholds, evidence rules, routing, settings and APIs deferred; CT40 structural contract unresolved | Adaptive §15, ADR001 §12 |
| Q&A, notifications, reports | Interaction-owned operations deferred; notification fields settled (`CT11-READ-FIELDS-2026-11-08-r1`, partial) | ADR001, data-model §8 and §12 |
| Instructor authoring and grading | CT21.remaining, CT40, Assessment, CT09 deferred | ADR002 |
| Administration and audit | CT12, CT31, reporting definitions deferred | ADR001 §11–12 |

None of these blocks the design in this file. Each blocks implementation of the
named screens until its contract is approved and recorded.

### Known source discrepancies (reported, not resolved here)

- Resolved: `theme.ts` now credits the Nixtio reference used here.
- Resolved 8 November 2026 (r6 documentation change): README described logout as planned although client-local sign-out is implemented under ADR001 §11.3; it now says so.
- Resolved 8 November 2026 (r6 documentation change): CONTRIBUTING's current-state statements about the OpenAPI file, module scaffolding, backend tests, the Frontend CI job and the E2E configuration were updated to the repository state.
- Architecture US-02 says leaving removes a course from My courses; FE07 keeps Left memberships visible under the Left and All filters while the default Joined filter excludes them. The design follows FE07: a left course disappears from the default view as US-02 states, and the filters add history that FE07-2026-10-27-r1 approved. No architecture text was changed.
- `frontend/src/app/theme/theme.ts` still names revision r1 in its header comment; the values match this revision. Source comments are outside this documentation revision.
- The preview `QuizTimer` announces "1 minutes left." when one minute remains at the butter threshold; the [Progress](#progress) copy rule says "1 minute left.". The timer is preview-only and should follow the rule when FE10 implements it.
- Resolved: the route guard shows the Access denied page from F14; behavior is unchanged.
- The `/preview` notifications screen still shows per-type tints and source links, and the preview Learner home shows deadline and result entries; both are Preview only and differ from the Final scope above (S15, S6). Preview source is outside this documentation revision.

### Design consistency review (7 November 2026)

The r2 audit compared the implemented UI with the rules above in source and in
Chrome at 1280 and 390 px and found 14 inconsistencies. The Leader approved the
proposed fixes for all of them the same day; r3 implements and records them.
Verified by unit tests, the browser harness (Chromium and WebKit) and E2E.

| # | Where | Issue found | Status and resolution |
| --- | --- | --- | --- |
| C1 | Course tiles mixing a logo and the generic mark | Neutral logo container next to a saturated gradient mark | **Resolved.** One neutral container for every mark; the generic mark is a `brand-700` code glyph |
| C2 | Preview fixtures | Same course showed Python in one workspace and the generic mark in another | **Resolved.** One shared description per sample course (`COURSE_DESCRIPTIONS`); "Introduction to Programming" shows Python everywhere |
| C3 | Gradient fill vs Colors rules | Gradient on avatar and course mark | **Resolved.** Gradient removed; avatar solid `brand-700`; no gradient remains |
| C4 | Sign-in destination | Learner signed in to `/courses` while `/`, logo and Home led to `/home` | **Resolved.** Learner fallback is `/home`; `/profile` return and other roles unchanged (CT16-PROFILE P1 amendment recorded) |
| C5 | Learner home rows | No course mark | **Resolved.** 32 px course mark in every row |
| C6 | Fixed Scale Rule | `clamp()`/viewport units in the lockup; 56 px hero outside the scale | **Resolved.** Lockup uses fixed steps (36 px, 32 px below 390 px); `display` (56/40 px, changes at 600 px) added to the scale |
| C7 | Elevation | Resting shadow on map nodes; ad hoc segmented shadow | **Resolved.** Nodes flat with a hairline (Mastery Map and skill graph editor); `shadow.control` token |
| C8 | Dashes | Map edges 5/5; Path Spine used the browser's dashed border | **Resolved.** `pathLine` 4/6 for the Path Spine, edges, legend and brand path |
| C9 | Course detail | Instructor named twice | **Resolved.** Removed from Course facts |
| C10 | Loading | Spinner beside the loading message | **Resolved.** Status line + skeleton only |
| C11 | Tokens | `#2A2D35`, `#951C13`, `#fff` hard-coded | **Resolved.** `chrome-hover`, `rose-800`, `surface` tokens |
| C12 | Preview index pills | "Proposed" used lilac | **Resolved.** Dashed "not yet" pill |
| C13 | Notifications (preview) | No current state | **Resolved.** Bell is current (`aria-current`, `chrome-raised`) on Notifications |
| C14 | Lesson outline | 260 px vs 280 px token | **Resolved.** Uses `presentationTokens.outlineWidth` (280 px) |

### Change control

- Visual or interaction changes to this file need a dated revision entry here and Leader approval or an explicit delegation like the one recorded in Document Control.
- A design revision never changes an API, route, role rule, dependency or business rule. Those changes go through their owning ADR or contract first; this file is then updated to match.
- Implementation that finds this file wrong reports the conflict and stops (Blocker Protocol). It does not silently diverge.
- Do not regenerate or overwrite this file with automated tooling (for example Impeccable `document`) without explicit authorization.

### Revision history

| Revision | Date | Change |
| --- | --- | --- |
| Baseline | 2026-11-05 | Inventory and constraints; all visual decisions pending (OpenAI Codex) |
| DESIGN-2026-11-06-r1 | 2026-11-06 | Full redesign specification under Leader delegation: visual system, IA, flows, screens, handoff (Claude Code with Impeccable) |
| r1 amendments | 2026-11-07 | Typography (Google Sans Flex, JetBrains Mono, Poppins wordmark), logo palette, Brand and logo section, traced canvas reach (Leader instruction for PR #209) |
| DESIGN-2026-11-08-r7 | 2026-11-08 | Aligned with the delegated technical decisions recorded the same day in `docs/setup.md`, `docs/data-model.md` and `docs/repository-structure.md`, with no visual change (Claude Code, Leader instruction of 8 November 2026): approved route map in the Sitemap (`†` marks approved, unregistered paths) with the activation rule; navigation destinations, active items and Instructor/Administrator start pages (Workspaces, Primary navigation, Top bar, F1); S6 Final scope; S15 message-only rows and F10; Markdown policy (Code and Markdown content, S18, Handoff); S19 replacement-question wording; shared Mastery Map location (Handoff); open questions 2, 5 and 6 resolved; Proposed status wording; a delegated-decisions table; two preview discrepancies recorded. Screen statuses are unchanged and nothing here is implemented. |
| DESIGN-2026-11-08-r6 | 2026-11-08 | Factual and consistency corrections against `main` `882751e`, with no visual change (Claude Code, Leader instruction of 8 November 2026). Changes: current-state text no longer describes the implementation as unmerged branch work; the Learner sign-in destination reads `/home` everywhere (S6 corrected) with the `/profile` return first; the lesson outline token reads 280 px in the theme mapping, matching S8, C14 and the source; S26 separates the absent on-screen controls from the preview account-menu items; open question 9 is marked resolved; quiz-timer visual thresholds are separated from its two screen-reader announcements; the ~90% fidelity is clarified as visual language, not composition; Figtree references are marked historical; `palette.ink`/`onChrome` and `brandMark` match `theme.ts`; graphite replaces "ink" for primary buttons in S3, S4 and F3; the S8 aside is 360/400 px; page-inventory entry points name the sign-in destinations; the README and CONTRIBUTING discrepancies are marked resolved and two new source discrepancies are recorded; the file is tracked in Git. Earlier decisions keep their dates. |
| DESIGN-2026-11-07-r5 | 2026-11-07 | Tile arrows secondary (graphite only for Resume/Continue); canvas reach rebuilt as a mask-cut opening in the header with the canvas extended under the bar (no painted shape) (Claude Code) |
| DESIGN-2026-11-07-r4 | 2026-11-07 | Button system: graphite primary (`chrome-raised`), neutral outlined secondary, Logo Blue tertiary, red destructive; `control` tokens and the `onChrome` button colour; graphite for chosen controls; competing primaries demoted (placement prompt, empty states, error retry) (Claude Code) |
| DESIGN-2026-11-07-r3 | 2026-11-07 | Consistency fixes C1–C14 implemented and recorded: neutral course marks, solid identity blue, Learner sign-in to `/home`, fixed lockup steps and `display` type step, flat map nodes, 4/6 path line, single instructor mention, spinner-free loading, colour and shadow tokens, dashed Proposed pill, current bell, 280 px outline (Claude Code) |
| DESIGN-2026-11-07-r2 | 2026-11-07 | Reconciled with the implemented UI at the Leader's request: Preview status and implementation status; brand fill; header lockup sizes; course marks; account avatar; preview without chrome; landing, Learner home, F2 and Access denied as implemented; component values read from source; page inventory statuses; Leader decisions; design consistency review (Claude Code) |
