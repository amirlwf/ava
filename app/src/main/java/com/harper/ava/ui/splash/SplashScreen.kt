package com.harper.ava.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harper.ava.R
import com.harper.ava.ui.common.AvaMotion
import com.harper.ava.ui.theme.SplashDedicationStyle
import com.harper.ava.ui.theme.AvaTokens
import kotlinx.coroutines.delay

/**
 * Launch intro (0.2, Harper 2026-11-13): a ~1-second intro on every
 * cold start — the app's logo (the new purple squircle artwork) and
 * the app name ease in softly, and at the bottom, small, the
 * dedication «تقدیم به آوا رحمانی راد» types itself out character by
 * character in the Almas handwritten face.
 *
 * Flow: this screen is shown while the persisted settings load
 * (`ready == false`) and for ~2 s after they arrive; then
 * [onIntroFinished] hands over to the graph (onboarding on first run,
 * Home afterwards). The entrance plays on first composition, the
 * typewriter starts when `ready` becomes true, so a slow settings
 * load never eats the animation and a fast one never skips it.
 *
 * One orchestrated moment, per the design skill: the logo + name
 * fade/scale in on the shared [AvaMotion] family (350 ms cross-fade,
 * soft 0.96x → 1x spring), the dedication types at ~70 ms per
 * character (~1600 ms for the 23-character line), then a 400 ms hold
 * before the hand-over — about two seconds in total. The dedication
 * line is the ONLY place Almas is used (see `Type.kt`: DigiFont free
 * download, not OFL — never Dana / Manshoor / Yadgar).
 *
 * The logo is the launcher foreground asset, clipped to a rounded
 * square: the asset is a full-bleed black square (the squircle art
 * sits on it), so an unclipped draw would show a black tile on light
 * themes; the 30 dp radius follows the artwork's own squircle.
 */
@Composable
fun SplashScreen(
    ready: Boolean,
    onIntroFinished: () -> Unit,
) {
    val dedication = stringResource(R.string.splash_dedication)
    var typedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        val perCharMs = 1600L / dedication.length.coerceAtLeast(1)
        for (i in 1..dedication.length) {
            typedCount = i
            delay(perCharMs)
        }
        delay(400)
        onIntroFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(AvaMotion.crossFade) +
                scaleIn(AvaMotion.coverScaleSpring, initialScale = 0.96f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(30.dp)),
                )
                Spacer(modifier = Modifier.height(AvaTokens.space16))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            text = dedication.take(typedCount),
            style = SplashDedicationStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        )
    }
}
