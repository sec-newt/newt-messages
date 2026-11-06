# Newt Messages UI refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Newt Messages meets the UI refresh bar on newt-ui 2.0.1: group chat (mockup D), Settings (4), Group details (7), Color picker (8) and first-run (12).

**Spec:** `newt-android-lib/docs/superpowers/specs/2026-11-04-ui-refresh-design.md`, Messages row; feature change: "Block, Remove and Change color move into the ⋯ menu".

## Global Constraints

- No text under 16sp. Never color alone: a sender's color is a stripe plus their name, and color names are written where colors are picked or managed (Group details and the picker).
- Danger behind ⋯. Merge on green CI.

## Review Focus

1. **A person with no saved color in Group details:** the line says "Automatic · mobile", not a blank. (Task 3.)
2. **Color picker with every color taken:** "who has it" shows on each swatch; nothing overflows at 200%. (Task 4, 200% screenshot.)
3. **A one-to-one chat:** there's no sender name or avatar on each bubble (it's only for groups). (Task 5.)
4. **A long sender name at 200%:** the bubble wraps and doesn't clip. (Task 5, 200% screenshot.)
5. **First run when Newt is already the messaging app:** step 1 shows done. (Task 6.)

### Task 1: newt-ui 2.0.1 (done)
### Task 2: Settings as cards
- Reactions: `NewtPickList` (only the chosen row shows its hint).
- Say who's messaging: `NewtChoice` Off / Always / Headphones.
- Quiet in Do Not Disturb and Read the message too: `NewtSwitchRow`s.
- Test the voice: `NewtButton`. Voice settings: `NewtValueRow`.
### Task 3: Group details
- One card per person: a stripe in their color, the name, then "Yellow · mobile".
- Text and Call are worded buttons, plus ⋯.
- ⋯ opens Change color, Remove from group, and Block (red).
- Rename is a header button.
### Task 4: Color picker
- Where: `NewtChoice` This chat / Every chat.
- Colors: a 3-column swatch grid. Each swatch has its name and who has it. The current one gets a thick ring and ✓.
### Task 5: Group chat bubbles
- Others' messages: an avatar, a stripe in their color, their bold name, 19sp text.
- Your own messages: the gradient fill with dark text.
- The composer is 58dp with a gradient send button.
- The header shows "N people".
### Task 6: First run as numbered steps
- `NewtSteps`: 1 Make it your messaging app (with its button), 2 Allow notifications, 3 Try it.
- A "What stays the same" card.
