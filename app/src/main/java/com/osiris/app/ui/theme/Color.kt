package com.osiris.app.ui.theme

import androidx.compose.ui.graphics.Color

// Pulled directly from osirisai.live's own CSS custom properties (:root), not guessed — its
// actual brand/border accent is gold, with cyan reserved for live data readouts (clock, status
// figures) rather than UI chrome. Verified live 2026-09 via getComputedStyle on the site itself.
val OsirisBackground = Color(0xFF04040A) // --bg-void
val OsirisSurface = Color(0xFF0C0E1A) // --bg-secondary
val OsirisSurfaceVariant = Color(0xFF121628) // --bg-tertiary
val OsirisAccent = Color(0xFFD4AF37) // --gold-primary — primary/border/active-state accent
val OsirisAccentLight = Color(0xFFF0D060) // --gold-light — active toggle/button state
val OsirisAccentDim = Color(0xFF00E5FF) // --cyan-primary — secondary accent, live data readouts
val OsirisWarning = Color(0xFFFF9500) // --alert-orange
val OsirisDanger = Color(0xFFFF3D3D) // --alert-red
val OsirisTextPrimary = Color(0xFFE8E6E0) // --text-primary
val OsirisTextSecondary = Color(0xFF9B978E) // --text-secondary
