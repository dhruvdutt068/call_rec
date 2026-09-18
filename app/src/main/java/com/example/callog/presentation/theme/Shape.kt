package com.example.callog.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Hierarchy for Callog / AllSet CRM.
 * Replaces scattered hardcoded corner radii with semantic tokens.
 */
object CallogShapes {
    /** Circular shape for contact avatars, FABs, and action icon buttons. */
    val avatar = CircleShape

    /** Pill shape for status indicators, filter chips, and segmented indicators. */
    val pill = RoundedCornerShape(percent = 50)

    /** Standard card shape for entity cards, call cards, and GlassyCard. */
    val card = RoundedCornerShape(16.dp)

    /** Large container shape for KPI summary tiles and featured sections. */
    val container = RoundedCornerShape(20.dp)

    /** Interactive controls shape for buttons, segmented tabs, and text fields. */
    val interactive = RoundedCornerShape(12.dp)

    /** Subtle shape for tags, small chips, and inline indicators. */
    val subtle = RoundedCornerShape(6.dp)

    /** Dialog shape for alert dialogs, modal sheets, and wizards. */
    val dialog = RoundedCornerShape(24.dp)

    // Semantic aliases for expressive ergonomics
    val buttonShape = interactive
    val inputShape = interactive
    val cardShape = card
    val badgeShape = subtle
    val pillShape = pill
    val modalShape = dialog
}

