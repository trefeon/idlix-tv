package com.idlix.tv.ui.theme

import androidx.compose.ui.graphics.Color

// B&W-minimalist palette (Wave A): background true black everywhere; primary
// text white; secondary #AAAAAA; surfaces #1F1F1F. RED (#E50914) is fenced to
// the brand wordmark, selected-tab indicator, progress bars, badges, and
// Top-10 numerals ONLY. Everything else is monochrome — token NAMES are
// frozen so Wave B files keep compiling (only values change).
val NetflixRed = Color(0xFFE50914)
val NetflixRedDark = Color(0xFFB81D24)
// True-black OLED background; cards/surfaces stay at minimal elevation grays
// (DarkCard/DarkGray) so depth survives. All scrims/gradients fade from
// transparent to this token, so they keep blending on pure black.
val NetflixBlack = Color(0xFF000000)
val NetflixDarkCard = Color(0xFF1F1F1F)
val NetflixDarkGray = Color(0xFF2A2A2A)
val NetflixLightGray = Color(0xFFAAAAAA)
val NetflixWhite = Color(0xFFFFFFFF)
// Former gold (star ratings): remapped to white per B&W fence. Name kept for
// Wave B source compatibility — do not reintroduce a gold value.
val NetflixGold = Color(0xFFFFFFFF)
val NetflixOverlay = Color(0x99000000)
