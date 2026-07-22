package com.aesthetic.gym.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.domain.model.Sex
import com.aesthetic.gym.domain.model.WeightUnit
import com.aesthetic.gym.reminder.ReminderCopy
import com.aesthetic.gym.reminder.ReminderScheduler
import com.aesthetic.gym.social.AccountManager
import com.aesthetic.gym.social.SessionState
import com.aesthetic.gym.ui.nav.Routes
import com.aesthetic.gym.ui.rememberRepository
import com.aesthetic.gym.util.Notifications
import com.aesthetic.gym.util.openAppNotificationSettings
import com.aesthetic.gym.ui.theme.Danger
import com.aesthetic.gym.ui.theme.Outline
import com.aesthetic.gym.ui.theme.Surface
import com.aesthetic.gym.ui.theme.SurfaceVariant
import com.aesthetic.gym.ui.theme.TextMuted
import com.aesthetic.gym.ui.theme.TextSecondary
import com.aesthetic.gym.ui.theme.Violet
import com.aesthetic.gym.util.epochToLocalDate
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@Composable
fun ProfileScreen(navController: NavController) {
    val repo = rememberRepository()
    val vm: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(repo))
    val profile by vm.profile.collectAsState()

    var name by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf(Sex.MALE) }
    var weight by remember { mutableStateOf("75") }
    var height by remember { mutableStateOf("175") }
    var unit by remember { mutableStateOf(WeightUnit.KG) }
    var experience by remember { mutableStateOf(1) }
    var showRpe by remember { mutableStateOf(false) }
    var adsTestMode by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val resolver = LocalContext.current.contentResolver
    var exportResult by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val count = vm.exportCsv(resolver, uri)
            exportResult = if (count != null) "Exportadas $count series ✓"
            else "No se pudo exportar"
        }
    }

    LaunchedEffect(profile) {
        val p = profile
        if (!loaded && p != null) {
            name = p.name; sex = p.sex
            weight = trimDouble(p.bodyweightKg); height = trimDouble(p.heightCm)
            unit = p.unit; experience = p.experienceLevel
            showRpe = p.showRpe; adsTestMode = p.adsTestMode; soundEnabled = p.soundEnabled
            loaded = true
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 28.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceVariant)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Perfil", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        // ---------- AVATAR ----------
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Violet.copy(alpha = 0.20f))
                    .border(2.dp, Violet, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = Violet, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                name.ifBlank { "Atleta Zenit" },
                color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Miembro desde ${profile?.createdAt?.takeIf { it > 0 }?.let { epochToLocalDate(it).year } ?: epochToLocalDate(System.currentTimeMillis()).year}",
                color = TextMuted, fontSize = 11.sp
            )
        }

        // ---------- CUENTA (social, opcional) ----------
        Spacer(Modifier.height(22.dp))
        AccountCard(navController)

        // ---------- FORM ----------
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                FieldLabel("NOMBRE")
                DarkField(name, "Tu nombre", KeyboardType.Text) { name = it; saved = false }

                Spacer(Modifier.height(16.dp))
                FieldLabel("SEXO")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Hombre", sex == Sex.MALE, Modifier.weight(1f)) { sex = Sex.MALE; saved = false }
                    Chip("Mujer", sex == Sex.FEMALE, Modifier.weight(1f)) { sex = Sex.FEMALE; saved = false }
                    Chip("Otro", sex == Sex.OTHER, Modifier.weight(1f)) { sex = Sex.OTHER; saved = false }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("PESO (KG)")
                        DarkField(weight, "75", KeyboardType.Decimal) { weight = it; saved = false }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("ALTURA (CM)")
                        DarkField(height, "180", KeyboardType.Decimal) { height = it; saved = false }
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("UNIDAD DE PESO")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("kg", unit == WeightUnit.KG, Modifier.width(74.dp)) { unit = WeightUnit.KG; saved = false }
                    Chip("lb", unit == WeightUnit.LB, Modifier.width(74.dp)) { unit = WeightUnit.LB; saved = false }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("EXPERIENCIA")
                listOf(1 to "Principiante", 2 to "Intermedio", 3 to "Avanzado").forEach { (level, label) ->
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (experience == level) Violet else SurfaceVariant)
                            .clickable { experience = level; saved = false }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (experience == level) Color.White else TextSecondary,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .clickable { showRpe = !showRpe; saved = false }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Registrar RIR por serie",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            "Repeticiones que te sobran al cerrar cada serie",
                            color = TextMuted, fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(50))
                            .background(if (showRpe) Violet else Outline),
                        contentAlignment = if (showRpe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(20.dp)
                                .clip(CircleShape).background(Color.White)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .clickable { soundEnabled = !soundEnabled; saved = false }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Sonido y vibración",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            "La campana de descanso y la celebración de récord",
                            color = TextMuted, fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(50))
                            .background(if (soundEnabled) Violet else Outline),
                        contentAlignment = if (soundEnabled) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(20.dp)
                                .clip(CircleShape).background(Color.White)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Tu peso corporal y sexo se usan para calcular tus rangos de fuerza.",
                        color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Violet)
                        .clickable {
                            // Copy the stored profile instead of rebuilding it: @Upsert replaces
                            // the whole row, so any field not listed here would be wiped.
                            vm.save(
                                (profile ?: ProfileEntity()).copy(
                                    id = 1,
                                    name = name,
                                    sex = sex,
                                    bodyweightKg = weight.replace(',', '.').toDoubleOrNull() ?: 75.0,
                                    heightCm = height.replace(',', '.').toDoubleOrNull() ?: 175.0,
                                    unit = unit,
                                    experienceLevel = experience,
                                    onboarded = true,
                                    createdAt = profile?.createdAt ?: 0L,
                                    showRpe = showRpe,
                                    adsTestMode = adsTestMode,
                                    soundEnabled = soundEnabled
                                )
                            )
                            saved = true
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (saved) "Guardado ✓" else "Guardar perfil",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                    )
                }
            }
        }

        // ---------- BACKUP ----------
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                FieldLabel("TUS DATOS")
                Text(
                    "Exporta todo tu historial de series a un CSV que puedes abrir en Excel " +
                        "o guardar como copia de seguridad.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariant)
                        .clickable {
                            exportResult = null
                            exportLauncher.launch(vm.suggestedFileName())
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Download, null, tint = Violet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Exportar historial (CSV)",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
                exportResult?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Honest privacy note. Precise about the one network exception (the ad SDK),
                // so the "no tracking" claim doesn't become a dark pattern by omission.
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Lock, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Tus entrenos viven solo en este teléfono. Sin cuentas, sin nube: por eso " +
                            "puedes exportarlos y llevártelos cuando quieras. La app no te rastrea; " +
                            "el anuncio usa internet y su proveedor (Google) puede recopilar datos.",
                        color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                    )
                }
            }
        }

        // ---------- REMINDERS ----------
        Spacer(Modifier.height(16.dp))
        ReminderSection(profile) { vm.updateReminders(it) }

        // ---------- ADS ----------
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
                .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
        ) {
            Column {
                FieldLabel("ANUNCIOS")
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .clickable { adsTestMode = !adsTestMode; saved = false }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Anuncios de prueba",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                        Text(
                            "Usa la unidad de prueba de Google, que siempre tiene anuncio, " +
                                "y muestra el error cuando falla. Sirve para comprobar que la " +
                                "integración funciona.",
                            color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(50))
                            .background(if (adsTestMode) Violet else Outline),
                        contentAlignment = if (adsTestMode) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            Modifier.padding(horizontal = 3.dp).size(20.dp)
                                .clip(CircleShape).background(Color.White)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Acuérdate de guardar el perfil para aplicar el cambio.",
                    color = TextMuted, fontSize = 10.sp
                )
            }
        }
    }
}

private val DEFAULT_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
private val DAY_CHIPS = listOf(
    DayOfWeek.MONDAY to "L", DayOfWeek.TUESDAY to "M", DayOfWeek.WEDNESDAY to "X",
    DayOfWeek.THURSDAY to "J", DayOfWeek.FRIDAY to "V", DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "D"
)
private val HOUR_OPTIONS = listOf(6, 7, 8, 9, 12, 17, 18, 19, 20, 21)

@Composable
private fun ReminderSection(profile: ProfileEntity?, onPersist: (ProfileEntity) -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf<DayOfWeek>()) }
    var hour by remember { mutableStateOf(19) }
    var blocked by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        val p = profile
        if (!loaded && p != null) {
            enabled = p.reminderEnabled
            days = ReminderScheduler.parseDays(p.reminderDays)
            hour = p.reminderHour
            loaded = true
        }
    }

    fun persist() {
        val updated = (profile ?: ProfileEntity()).copy(
            id = 1,
            reminderEnabled = enabled,
            reminderDays = ReminderScheduler.formatDays(days),
            reminderHour = hour,
            reminderMinute = 0
        )
        onPersist(updated)
        ReminderScheduler.sync(context.applicationContext, updated)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Two refusals = permanent denial: we don't relaunch, we link to Settings instead.
        if (granted) { enabled = true; blocked = false } else { enabled = false; blocked = true }
        persist()
    }

    fun onToggle(on: Boolean) {
        if (!on) { enabled = false; persist(); return }
        if (days.isEmpty()) days = DEFAULT_DAYS
        if (Notifications.hasRuntimePermission(context)) {
            enabled = true; blocked = false; persist()
        } else {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Column {
            FieldLabel("RECORDATORIOS")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                    .clickable { onToggle(!enabled) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Avísame de entrenar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Un aviso los días y la hora que tú fijes. Puedes apagarlo cuando quieras.",
                        color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.width(46.dp).height(26.dp).clip(RoundedCornerShape(50))
                        .background(if (enabled) Violet else Outline),
                    contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(Modifier.padding(horizontal = 3.dp).size(20.dp).clip(CircleShape).background(Color.White))
                }
            }

            if (blocked) {
                Spacer(Modifier.height(10.dp))
                Text(ReminderCopy.PERM_BLOCKED, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Abrir ajustes de notificaciones", color = Violet, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { openAppNotificationSettings(context) }
                )
            }

            if (enabled) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("DÍAS")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_CHIPS.forEach { (day, label) ->
                        val sel = day in days
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Violet else SurfaceVariant)
                                .clickable {
                                    // Persist even when the last day is removed, or the visible
                                    // state and the scheduled work would diverge (a reminder on a
                                    // day the user just cleared). sync() cancels the work if empty.
                                    days = if (sel) days - day else days + day
                                    persist()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label, color = if (sel) Color.White else TextSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                FieldLabel("HORA")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HOUR_OPTIONS.forEach { h ->
                        val sel = h == hour
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (sel) Violet else SurfaceVariant)
                                .clickable { hour = h; persist() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "%02d:00".format(h), color = if (sel) Color.White else TextSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(ReminderCopy.OEM_WARNING, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun AccountCard(navController: NavController) {
    val session by AccountManager.state.collectAsState()

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Outline, RoundedCornerShape(20.dp)).padding(16.dp)
    ) {
        Column {
            FieldLabel("CUENTA")
            when (val s = session) {
                is SessionState.LoggedIn -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(Violet.copy(alpha = 0.20f))
                                .border(1.dp, Violet, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                s.account.displayName.take(1).uppercase().ifBlank { "Z" },
                                color = Violet, fontWeight = FontWeight.Black, fontSize = 18.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.account.displayName.ifBlank { s.account.username },
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                maxLines = 1
                            )
                            Text("@${s.account.username}", color = Violet, fontSize = 11.sp, maxLines = 1)
                            if (s.account.email.isNotBlank()) {
                                Text(s.account.email, color = TextMuted, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Violet)
                            .clickable { navController.navigate(Routes.FRIENDS) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Amigos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                            .clickable { navController.navigate(Routes.ACCOUNT_SETTINGS) }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ajustes de cuenta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                            .clickable { AccountManager.logout() }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cerrar sesión", color = Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                SessionState.LoggedOut -> {
                    Text(
                        "Crea una cuenta para conectar con amigos y compartir rutinas. " +
                            "Es opcional: la app funciona igual sin ella.",
                        color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
                                .clickable { navController.navigate(Routes.auth("login")) }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Iniciar sesión", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Violet)
                                .clickable { navController.navigate(Routes.auth("register")) }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Crear cuenta", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DarkField(value: String, placeholder: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, color = TextMuted, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(Violet),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) Violet else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextSecondary,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            maxLines = 1, softWrap = false
        )
    }
}

private fun trimDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
