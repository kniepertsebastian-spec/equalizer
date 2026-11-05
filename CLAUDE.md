# CLAUDE.md — HardBass EQ

## Project Overview

HardBass EQ is an audio equalizer project focused on precise sound control, a powerful visual identity, and reliable audio processing.

The main application is a native Android app built with Kotlin and Jetpack Compose. The repository may also contain desktop functionality for managing or exporting presets to tools such as Equalizer APO and EasyEffects.

**Primary goal:** Build a premium, technical, responsive audio-control experience that makes complex sound adjustments feel precise, understandable, and immediate.

## 1. Inspect Before Changing Anything

Before implementing changes:

1. Inspect the repository structure and identify all modules.
2. Read the Gradle configuration, dependency versions, application entry points, navigation, themes, and existing UI components.
3. Identify the audio engine, DSP implementation, equalizer bands, preset storage, and platform-specific integrations.
4. Determine which functionality is implemented and which parts are planned or incomplete.
5. Inspect existing tests and available build commands.
6. Reuse the existing architecture and conventions whenever possible.

For substantial visual redesigns, first provide a short analysis and implementation plan. **Do not modify production code until the user explicitly approves the plan.**

Do not assume that a feature exists simply because it is described in the project concept.

## 2. Design Direction: Sonic Control

The interface should feel:

- Technical, precise, and premium.
- Dark, focused, and performance-oriented.
- Inspired by professional audio equipment and studio tools.
- Modern without looking like a generic music player.
- Powerful without unnecessary visual noise.
- Clear enough for beginners while offering useful control for advanced users.

The equalizer curve should be the visual centerpiece. The interface should communicate signal state, frequency response, gain, and audio control clearly.

Avoid generic music-player layouts, excessive decorative cards, oversized gradients, and visual effects that compete with the equalizer.

## 3. Visual System

Use the existing design tokens if available. Extend the current theme rather than introducing a separate, competing design system.

Suggested visual direction:

- Background: deep graphite or near-black.
- Secondary surfaces: dark charcoal with subtle tonal separation.
- Primary accent: warm amber or electric orange.
- Secondary accent: restrained signal green for active signal or positive states.
- Text: high-contrast off-white.
- Secondary text: cool gray.
- Borders: subtle graphite.
- Errors: clear red, distinct from normal signal indicators.

### Visual Rules

- Use amber for the primary interactive accent and selected controls.
- Use green for meaningful signal or active-processing states, not as decoration.
- Make active, inactive, bypassed, and unavailable states visually distinct.
- Use gradients sparingly.
- Avoid excessive glow, neon outlines, and glassmorphism.
- Use consistent iconography and restrained corner radii.
- Maintain readable contrast in dark environments.
- Do not use color as the only way to communicate a state.

## 4. Equalizer Visualization

The equalizer curve is a core product feature, not decorative artwork.

- Render frequency response clearly and accurately.
- Make frequency and gain information easy to understand.
- Use consistent axis labels, ticks, units, and grid lines.
- Distinguish the active response curve from the grid and reference lines.
- Make draggable control points visually recognizable.
- Provide clear selected, focused, and disabled states.
- Avoid cluttering the graph with unnecessary labels.

