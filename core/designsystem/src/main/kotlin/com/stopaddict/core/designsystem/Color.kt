package com.stopaddict.core.designsystem

import androidx.compose.ui.graphics.Color

// Identité StopAddict : vert profond en couleur principale ; blanc, gris clair, noir.
val GreenDeep = Color(0xFF0E3B2E)      // vert profond principal
val GreenPrimary = Color(0xFF1B5E20)   // vert d'action
val GreenAccent = Color(0xFF2E7D55)    // vert secondaire
val GreenSoft = Color(0xFFA5D6B7)      // vert clair (états, conteneurs)

val NeutralWhite = Color(0xFFFFFFFF)
val NeutralLight = Color(0xFFF2F4F3)   // gris clair
val NeutralDark = Color(0xFF111413)    // presque noir
val NeutralGrey = Color(0xFF5E6663)
val SurfaceDark = Color(0xFF161A18)    // surface en thème sombre (évite le noir pur)

// États sémantiques (statut de protection honnête).
val StatusOk = Color(0xFF2E7D55)       // protégé
val StatusWarn = Color(0xFFB26A00)     // dégradé / sync en attente
val StatusDanger = Color(0xFFB3261E)   // non protégé
