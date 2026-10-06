# Death / Destruction Sequence

This is an original implementation inspired by the *presentation language* of the user's visual reference, not a frame-for-frame recreation of the referenced video.

## Sequence

1. Miss Minutes remains fully visible and animated.
2. Glitch regions begin interrupting the character.
3. Glitched regions acquire text/terminal/ASCII contamination.
4. The clock body fades while the corruption becomes increasingly dominant.
5. The final surviving character is a small ASCII-style clock with hands and feet.
6. The clock hands act as the final speaking/animation motif.
7. The ASCII clock collapses into a custom terminal-style fatal screen.
8. The screen fades away.

The fatal screen is deliberately not Windows BSOD branding and does not intentionally crash Android.

## Future renderer

The native renderer should eventually support:
- per-region glitch masks,
- character-to-ASCII transition,
- scanline/noise layers,
- temporal frame tearing,
- eye/pupil corruption,
- clock-hand degradation,
- audio dropout/stutter,
- holographic residue,
- deterministic replay for testing.

The effect must remain a visual state machine so it cannot accidentally become a destructive system operation.
