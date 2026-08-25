package com.numenlabs.zerophone.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ZeroPhone brand palette — "calm minimalism".
 *
 * Warm paper-like neutral surfaces carry the UI; a single deep pine-green
 * accent marks interactive/brand moments; a warm ochre tertiary is reserved
 * for contextual highlights (e.g. the emergency window). Colors are hand-set
 * (Material3 tonal-palette rules) so the launcher looks identical on every
 * device instead of following wallpaper tinting.
 */

// ---------- Accent: deep pine green ----------
private val PineLightPrimary = Color(0xFF266A53)
private val MintDarkPrimary = Color(0xFF90D5B8)

// ---------- Light scheme: warm paper ----------
val BrandPrimaryLight = PineLightPrimary
val BrandOnPrimaryLight = Color(0xFFFFFFFF)
val BrandPrimaryContainerLight = Color(0xFFABF2D3)
val BrandOnPrimaryContainerLight = Color(0xFF002115)

val BrandSecondaryLight = Color(0xFF4B635A)
val BrandOnSecondaryLight = Color(0xFFFFFFFF)
val BrandSecondaryContainerLight = Color(0xFFCDE9DB)
val BrandOnSecondaryContainerLight = Color(0xFF07201A)

val BrandTertiaryLight = Color(0xFF6D5D30)
val BrandOnTertiaryLight = Color(0xFFFFFFFF)
val BrandTertiaryContainerLight = Color(0xFFF6E1A5)
val BrandOnTertiaryContainerLight = Color(0xFF231B00)

val BrandErrorLight = Color(0xFFBA1A1A)
val BrandOnErrorLight = Color(0xFFFFFFFF)
val BrandErrorContainerLight = Color(0xFFFFDAD6)
val BrandOnErrorContainerLight = Color(0xFF410002)

val BrandBackgroundLight = Color(0xFFF9F7F0)
val BrandOnBackgroundLight = Color(0xFF191C1A)
val BrandSurfaceLight = Color(0xFFF9F7F0)
val BrandOnSurfaceLight = Color(0xFF191C1A)
val BrandSurfaceVariantLight = Color(0xFFDDE4DC)
val BrandOnSurfaceVariantLight = Color(0xFF404943)

val BrandSurfaceDimLight = Color(0xFFD8D8CF)
val BrandSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val BrandSurfaceContainerLowLight = Color(0xFFF3F1EA)
val BrandSurfaceContainerLight = Color(0xFFEEEDE5)
val BrandSurfaceContainerHighLight = Color(0xFFE8E7DF)
val BrandSurfaceContainerHighestLight = Color(0xFFE2E1DA)
val BrandSurfaceBrightLight = Color(0xFFF9F7F0)

val BrandInverseSurfaceLight = Color(0xFF2E312D)
val BrandInverseOnSurfaceLight = Color(0xFFEFF1E9)
val BrandInversePrimaryLight = MintDarkPrimary

val BrandOutlineLight = Color(0xFF6F7973)
val BrandOutlineVariantLight = Color(0xFFBFC9C1)

// ---------- Dark scheme: warm charcoal ----------
val BrandPrimaryDark = MintDarkPrimary
val BrandOnPrimaryDark = Color(0xFF00382A)
val BrandPrimaryContainerDark = Color(0xFF0A5140)
val BrandOnPrimaryContainerDark = Color(0xFFABF2D3)

val BrandSecondaryDark = Color(0xFFB2CCC0)
val BrandOnSecondaryDark = Color(0xFF1D352B)
val BrandSecondaryContainerDark = Color(0xFF344C41)
val BrandOnSecondaryContainerDark = Color(0xFFCDE9DB)

val BrandTertiaryDark = Color(0xFFD9C58C)
val BrandOnTertiaryDark = Color(0xFF392F00)
val BrandTertiaryContainerDark = Color(0xFF524600)
val BrandOnTertiaryContainerDark = Color(0xFFF6E1A5)

val BrandErrorDark = Color(0xFFFFB4AB)
val BrandOnErrorDark = Color(0xFF690005)
val BrandErrorContainerDark = Color(0xFF93000A)
val BrandOnErrorContainerDark = Color(0xFFFFDAD6)

val BrandBackgroundDark = Color(0xFF111411)
val BrandOnBackgroundDark = Color(0xFFE2E3DB)
val BrandSurfaceDark = Color(0xFF111411)
val BrandOnSurfaceDark = Color(0xFFE2E3DB)
val BrandSurfaceVariantDark = Color(0xFF404943)
val BrandOnSurfaceVariantDark = Color(0xFFBFC9C1)

val BrandSurfaceDimDark = Color(0xFF111411)
val BrandSurfaceContainerLowestDark = Color(0xFF0B0F0C)
val BrandSurfaceContainerLowDark = Color(0xFF191C19)
val BrandSurfaceContainerDark = Color(0xFF1D201D)
val BrandSurfaceContainerHighDark = Color(0xFF282B27)
val BrandSurfaceContainerHighestDark = Color(0xFF333632)
val BrandSurfaceBrightDark = Color(0xFF373A36)

val BrandInverseSurfaceDark = Color(0xFFE2E3DB)
val BrandInverseOnSurfaceDark = Color(0xFF2E312D)
val BrandInversePrimaryDark = PineLightPrimary

val BrandOutlineDark = Color(0xFF89938D)
val BrandOutlineVariantDark = Color(0xFF404943)
