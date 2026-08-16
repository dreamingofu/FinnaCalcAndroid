//
// TaxFilingExperience.kt
//
// Port of iOS Features/Taxes/Filing/TaxFilingExperience.swift — the full-screen
// guided estimate. On iOS this is a fullScreenCover from the Taxes tab; here it
// is a full-bleed destination the Taxes tab swaps to, so it takes over the app
// the same way (no bottom bar).
//
// Anatomy, unchanged from iOS:
//   · Persistent top bar: contextual left button, live federal refund/owed pill
//     in the middle, jump menu on the right.
//   · Hub: greeting + progress + a checklist of sections with Start / Continue
//     / Edit states, ending in Review and File rows.
//   · Section player: one question per screen (TaxQuestionPlayer).
//   · Review / File screens.
//
// Answers live in TaxViewModel (auto-persisted), so Save & exit and resume are
// free. Section completion is tracked under its own key, as on iOS.
//

package com.finnacalc.android.features.taxes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finnacalc.android.core.designsystem.FCButton
import com.finnacalc.android.core.designsystem.FCButtonSize
import com.finnacalc.android.core.designsystem.Theme
import com.finnacalc.android.core.designsystem.fcPressable
import com.finnacalc.android.core.designsystem.staggeredAppear
import com.finnacalc.android.core.util.JsonPrefs
import com.finnacalc.android.features.calculators.CalcFormat
import com.finnacalc.android.features.taxes.TaxViewModel
import com.finnacalc.android.features.taxes.engine.AnswerValue
import com.finnacalc.android.features.taxes.engine.LIFE_SITUATIONS
import com.finnacalc.android.features.taxes.engine.LifeSituationOption
import com.finnacalc.android.features.taxes.engine.Section
import com.finnacalc.android.features.taxes.engine.TaxCalculationResult
import com.finnacalc.android.features.taxes.engine.getProgress
import com.finnacalc.android.features.taxes.engine.questionsFor
import com.finnacalc.android.features.taxes.engine.visibleSections
import kotlin.math.abs

// MARK: - Completion persistence

object TaxFilingProgress {
    const val KEY = "finnacalc:taxFiling:completedSections"

    fun load(): Set<String> = JsonPrefs.load<List<String>>(KEY)?.toSet() ?: emptySet()

    fun save(ids: Set<String>) = JsonPrefs.persist(ids.sorted(), KEY)

    fun resetAll() = JsonPrefs.persist(emptyList<String>(), KEY)
}

// MARK: - Route

private sealed class TaxRoute {
    data object Hub : TaxRoute()
    data object Life : TaxRoute()
    data class SectionRoute(val id: String) : TaxRoute()
    data object Review : TaxRoute()
    data object File : TaxRoute()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxFilingExperience(vm: TaxViewModel, onExit: () -> Unit) {
    var route by remember { mutableStateOf<TaxRoute>(TaxRoute.Hub) }
    var completed by rememberSaveable { mutableStateOf(TaxFilingProgress.load()) }
    var showJumpMenu by remember { mutableStateOf(false) }

    val answers by vm.answers.collectAsState()
    val result by vm.result.collectAsState()
    // Derived from the collected answers, not the view model's snapshot, so a
    // life-situation toggle re-runs the section gates on the next frame.
    val sections = visibleSections(answers)

    fun markComplete(id: String) {
        completed = completed + id
        TaxFilingProgress.save(completed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.colors.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (route is TaxRoute.Hub) {
                TopBarAction(Icons.Filled.Close, "Save & exit") { onExit() }
            } else {
                TopBarAction(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Menu") { route = TaxRoute.Hub }
            }

            Spacer(Modifier.weight(1f))

            RefundPill(result)

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .fcPressable { showJumpMenu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "Sections menu",
                    tint = Theme.colors.foreground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        HorizontalDivider(color = Theme.colors.border)

        when (val r = route) {
            is TaxRoute.Hub -> TaxFilingHub(
                vm = vm,
                completed = completed,
                onOpen = { route = it },
            )

            is TaxRoute.Life -> LifeSetupScreen(vm) {
                markComplete("life")
                route = TaxRoute.Hub
            }

            is TaxRoute.SectionRoute -> {
                val section = sections.firstOrNull { it.id == r.id }
                if (section != null) {
                    TaxQuestionPlayer(
                        vm = vm,
                        section = section,
                        onExit = { route = TaxRoute.Hub },
                        onComplete = {
                            markComplete(r.id)
                            route = TaxRoute.Hub
                        },
                    )
                } else {
                    // Section became hidden (answers changed) — fall back.
                    LaunchedEffect(r.id) { route = TaxRoute.Hub }
                }
            }

            is TaxRoute.Review -> ReviewScreen(
                vm = vm,
                onEdit = { route = TaxRoute.SectionRoute(it) },
                onFile = {
                    markComplete("review")
                    route = TaxRoute.File
                },
            )

            is TaxRoute.File -> FilingScreen(vm = vm, onBack = { route = TaxRoute.Review })
        }
    }

    if (showJumpMenu) {
        ModalBottomSheet(
            onDismissRequest = { showJumpMenu = false },
            containerColor = Theme.colors.background,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Your return",
                    style = Theme.sans(Theme.FontSize.lg, FontWeight.Bold),
                    color = Theme.colors.foreground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                JumpRow("Tell us about your year", Icons.Filled.AutoAwesome, completed.contains("life")) {
                    showJumpMenu = false
                    route = TaxRoute.Life
                }
                sections.forEach { section ->
                    JumpRow(
                        section.title,
                        TaxIcons.forLucide(section.icon ?: ""),
                        completed.contains(section.id),
                    ) {
                        showJumpMenu = false
                        route = TaxRoute.SectionRoute(section.id)
                    }
                }
                HorizontalDivider(color = Theme.colors.border, modifier = Modifier.padding(vertical = 8.dp))
                JumpRow("Review", Icons.Filled.Verified, completed.contains("review")) {
                    showJumpMenu = false
                    route = TaxRoute.Review
                }
                JumpRow("Finish", Icons.AutoMirrored.Filled.Send, false) {
                    showJumpMenu = false
                    route = TaxRoute.File
                }
            }
        }
    }
}

@Composable
private fun TopBarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fcPressable(onClick),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Theme.colors.mutedForeground, modifier = Modifier.size(18.dp))
        Text(label, style = Theme.sans(Theme.FontSize.sm, FontWeight.SemiBold), color = Theme.colors.mutedForeground)
    }
}

@Composable
private fun JumpRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fcPressable(onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(22.dp))
        Text(
            title,
            style = Theme.sans(Theme.FontSize.base),
            color = Theme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        if (done) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Theme.colors.positive,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// MARK: - Refund pill (the live estimate tracker)

@Composable
fun RefundPill(result: TaxCalculationResult) {
    val owes = result.owes
    val amount = abs(result.refundOrOwed)
    val tint = if (owes) Theme.colors.negative else Theme.colors.positive
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (owes) "Federal owed" else "Federal refund",
            style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold),
            color = Theme.colors.mutedForeground,
        )
        Text(
            "$" + CalcFormat.fixed(amount, 0),
            style = Theme.figure(Theme.FontSize.base, FontWeight.Bold),
            color = tint,
        )
    }
}

// MARK: - Hub (section checklist)

private enum class RowState { Todo, InProgress, Done }

@Composable
private fun TaxFilingHub(
    vm: TaxViewModel,
    completed: Set<String>,
    onOpen: (TaxRoute) -> Unit,
) {
    val answers by vm.answers.collectAsState()
    val sections = visibleSections(answers)
    val progressPct = getProgress(completed.toList(), answers)

    /** A section is "in progress" when any of its visible questions is answered. */
    fun inProgress(section: Section): Boolean {
        val ids = questionsFor(section.id, answers).map { it.id }.toSet()
        return ids.any { answers.containsKey(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Let's get your taxes done",
                style = Theme.sans(28, FontWeight.Bold),
                color = Theme.colors.foreground,
            )
            Text(
                "Work through each section and your refund updates as you go. Everything saves automatically.",
                style = Theme.sans(Theme.FontSize.sm),
                color = Theme.colors.mutedForeground,
            )
        }

        // Overall progress
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Progress",
                    style = Theme.sans(Theme.FontSize.xs, FontWeight.SemiBold),
                    color = Theme.colors.mutedForeground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$progressPct%",
                    style = Theme.figure(Theme.FontSize.xs, FontWeight.SemiBold),
                    color = Theme.colors.foreground,
                )
            }
            LinearProgressIndicator(
                progress = { progressPct / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Theme.colors.primary,
                trackColor = Theme.colors.secondary,
                drawStopIndicator = {},
            )
        }

        // Section checklist
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HubRow(
                title = "Tell us about your year",
                subtitle = "Pick everything that applies. It shapes your return.",
                icon = Icons.Filled.AutoAwesome,
                state = if (completed.contains("life")) RowState.Done else RowState.Todo,
                index = 0,
            ) { onOpen(TaxRoute.Life) }

            sections.forEachIndexed { index, section ->
                HubRow(
                    title = section.title,
                    subtitle = section.description ?: "",
                    icon = TaxIcons.forLucide(section.icon ?: ""),
                    state = when {
                        completed.contains(section.id) -> RowState.Done
                        inProgress(section) -> RowState.InProgress
                        else -> RowState.Todo
                    },
                    index = index + 1,
                ) { onOpen(TaxRoute.SectionRoute(section.id)) }
            }

            HubRow(
                title = "Review",
                subtitle = "Check your full 1040 summary line by line.",
                icon = Icons.Filled.Verified,
                state = if (completed.contains("review")) RowState.Done else RowState.Todo,
                index = sections.size + 1,
            ) { onOpen(TaxRoute.Review) }

            HubRow(
                title = "File",
                subtitle = "Send your return on its way.",
                icon = Icons.AutoMirrored.Filled.Send,
                state = RowState.Todo,
                index = sections.size + 2,
            ) { onOpen(TaxRoute.File) }
        }
    }
}

@Composable
private fun HubRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    state: RowState,
    index: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredAppear(index)
            .clip(shape)
            .background(Theme.colors.card)
            .border(1.dp, Theme.colors.border, shape)
            .fcPressable(onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Theme.colors.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Theme.colors.primary, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Theme.sans(Theme.FontSize.base, FontWeight.SemiBold), color = Theme.colors.foreground)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = Theme.sans(Theme.FontSize.xs), color = Theme.colors.mutedForeground)
            }
        }

        when (state) {
            RowState.Done -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Theme.colors.positive,
                modifier = Modifier.size(22.dp),
            )

            RowState.InProgress -> Text(
                "Continue",
                style = Theme.sans(Theme.FontSize.xs, FontWeight.Bold),
                color = Theme.colors.primary,
            )

            RowState.Todo -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Theme.colors.mutedForeground,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// MARK: - Life setup ("Tell us about your year")

@Composable
private fun LifeSetupScreen(vm: TaxViewModel, onDone: () -> Unit) {
    val answers by vm.answers.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Tell us about your year",
                    style = Theme.sans(28, FontWeight.Bold),
                    color = Theme.colors.foreground,
                )
                Text(
                    "Select everything that applied to you in 2025. This decides which questions we ask.",
                    style = Theme.sans(Theme.FontSize.sm),
                    color = Theme.colors.mutedForeground,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LIFE_SITUATIONS.forEach { option ->
                    val on = (answers[option.id] as? AnswerValue.Bool)?.value == true
                    LifeTile(option, on) { vm.setBoolean(option.id, !on) }
                }
            }
        }

        FilingFooter {
            FCButton("Continue", size = FCButtonSize.Lg, modifier = Modifier.weight(1f), onClick = onDone)
        }
    }
}

@Composable
private fun LifeTile(option: LifeSituationOption, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Theme.Radius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (on) Theme.colors.primary.copy(alpha = 0.08f) else Theme.colors.card)
            .border(if (on) 1.5.dp else 1.dp, if (on) Theme.colors.primary else Theme.colors.border, shape)
            .fcPressable(onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            TaxIcons.forLucide(option.icon),
            contentDescription = null,
            tint = if (on) Theme.colors.primary else Theme.colors.mutedForeground,
            modifier = Modifier.size(24.dp),
        )
        Text(
            option.label,
            style = Theme.sans(Theme.FontSize.base, if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = Theme.colors.foreground,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (on) Theme.colors.primary else Theme.colors.borderStrong,
            modifier = Modifier.size(24.dp),
        )
    }
}

// MARK: - Shared pinned footer

/** The filing flow's pinned footer (Continue/Back buttons live here). */
@Composable
fun FilingFooter(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.background(Theme.colors.background)) {
        HorizontalDivider(color = Theme.colors.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
