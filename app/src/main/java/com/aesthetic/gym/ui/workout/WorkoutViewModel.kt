package com.aesthetic.gym.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.os.SystemClock
import com.aesthetic.gym.data.db.ProfileEntity
import com.aesthetic.gym.data.db.ExerciseEntity
import com.aesthetic.gym.data.db.RoutineItemEntity
import com.aesthetic.gym.data.db.RoutineItemWithExercise
import com.aesthetic.gym.data.db.SessionWithSets
import com.aesthetic.gym.data.db.SetLogEntity
import com.aesthetic.gym.data.db.WorkoutSessionEntity
import com.aesthetic.gym.data.repo.GymRepository
import com.aesthetic.gym.domain.model.MeasureType
import com.aesthetic.gym.domain.overload.OverloadSuggestion
import com.aesthetic.gym.domain.overload.ProgressiveOverload
import com.aesthetic.gym.domain.comeback.CADENCE_WINDOW
import com.aesthetic.gym.domain.comeback.ComebackBonus
import com.aesthetic.gym.domain.comeback.evaluate
import com.aesthetic.gym.domain.streak.computeWeeklyStreak
import com.aesthetic.gym.domain.streak.weeklyTargetFor
import com.aesthetic.gym.util.PlateCalculator
import com.aesthetic.gym.util.RestBell
import com.aesthetic.gym.util.WorkoutMath
import com.aesthetic.gym.util.epley1RM
import com.aesthetic.gym.util.epochToLocalDate
import com.aesthetic.gym.util.formatKg
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

data class WorkoutSummary(
    val durationMin: Int,
    val kcal: Int,
    val volumeKg: Double,
    val sets: Int,
    val prs: List<String> = emptyList(),
    val comeback: ComebackBonus? = null,
    /** Distinct training days this week (including today) and the weekly target. */
    val weekDone: Int = 0,
    val weekTarget: Int = 0
)

/** A record broken during the session, shown as a celebration banner. */
data class PrEvent(
    val setId: Long,
    val exerciseName: String,
    val text: String,
    val kind: PrKind
)

enum class PrKind { WEIGHT, REPS, E1RM }

class WorkoutViewModel(
    private val repo: GymRepository,
    private val bell: RestBell,
    private val sessionId: Long,
    private val firstSeenAt: Long
) : ViewModel() {

    /** Rest countdown (seconds left, 0 = not resting). */
    var restLeft by mutableIntStateOf(0)
        private set
    var restTotal by mutableIntStateOf(0)
        private set
    private var restJob: Job? = null

    /** Countdown of a timed set (seconds left); [setTimerSetId] says which set is running. */
    var setTimerLeft by mutableIntStateOf(0)
        private set
    var setTimerTotal by mutableIntStateOf(0)
        private set
    var setTimerSetId by mutableStateOf<Long?>(null)
        private set
    private var setTimerJob: Job? = null

    val session: StateFlow<SessionWithSets?> =
        repo.sessionWithSetsFlow(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var plannedItems by mutableStateOf<List<RoutineItemWithExercise>>(emptyList())
        private set

    /** True when this session has no routine behind it and exercises are added by hand. */
    var freeMode by mutableStateOf(false)
        private set

    /** False until the session has been read: tells "still loading" apart from "empty". */
    var loaded by mutableStateOf(false)
        private set

    private var freeOrder by mutableStateOf<List<String>>(emptyList())
    private val freeExercises = mutableMapOf<String, ExerciseEntity>()

    var suggestions by mutableStateOf<Map<String, OverloadSuggestion>>(emptyMap())
        private set

    var summary by mutableStateOf<WorkoutSummary?>(null)
        private set

    /** All-time best set per exercise, shown as "RÉCORD" on the workout card. */
    var records by mutableStateOf<Map<String, SetLogEntity>>(emptyMap())
        private set

    /** User preferences (RIR tracking on/off). */
    val profile = repo.profileHot

    private val soundOn: Boolean get() = profile.value?.soundEnabled != false

    /** Latest record broken, shown as a banner until dismissed. */
    var prEvent by mutableStateOf<PrEvent?>(null)
        private set

    /** Ids of the sets that broke a record in this session (trophy on the row). */
    var prSetIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    private val prLog = mutableListOf<String>()

    init {
        viewModelScope.launch {
            val s = repo.sessionById(sessionId)
            // No routine and no day: this is a free workout, exercises are added as you go.
            freeMode = s == null || s.routineId == null || s.dayId == null
            if (s != null) {
                if (freeMode) initFree() else initRoutine(s)
            }
            loaded = true
        }
    }

    private suspend fun initRoutine(s: WorkoutSessionEntity) {
        val routine = s.routineId?.let { repo.routineWithDays(it) }
        val day = routine?.days?.firstOrNull { it.day.id == s.dayId }
        val items = day?.sortedItems ?: emptyList()
        plannedItems = items

        val map = HashMap<String, OverloadSuggestion>()
        for (it in items) {
            val last = repo.lastSetsForExercise(it.item.exerciseId, sessionId)
            map[it.item.exerciseId] = ProgressiveOverload.suggest(it.item, it.exercise, last)
        }
        suggestions = map

        val recs = HashMap<String, SetLogEntity>()
        for (it in items) {
            repo.bestSetForExercise(it.item.exerciseId)?.let { best -> recs[it.item.exerciseId] = best }
        }
        records = recs

        // First time this session opens: pre-load every planned set (pending confirmation).
        if (repo.setCountForSession(sessionId) == 0) {
            for (item in items) seedSets(item, map[item.item.exerciseId])
        }
    }

    /** Rebuilds a free workout from the sets already stored, so leaving and coming back works. */
    private suspend fun initFree() {
        val existing = repo.setsForSession(sessionId)
        val order = existing.map { it.exerciseId }.distinct()
        val recs = HashMap<String, SetLogEntity>()
        val map = HashMap<String, OverloadSuggestion>()
        for (id in order) {
            val ex = repo.getExercise(id) ?: continue
            freeExercises[id] = ex
            repo.bestSetForExercise(id)?.let { recs[id] = it }
            val last = repo.lastSetsForExercise(id, sessionId)
            map[id] = ProgressiveOverload.suggest(syntheticItem(id, 0).item, ex, last)
        }
        freeOrder = order.filter { freeExercises.containsKey(it) }
        records = recs
        suggestions = map
        rebuildFreeItems()
    }

    /**
     * A routine item that only lives in memory, so the workout screen can treat a free
     * exercise exactly like a planned one. id = 0 is the marker for "no row in the database":
     * [setRest], [seedSets] and [addSet] all check it before writing anything.
     */
    private fun syntheticItem(exerciseId: String, orderIndex: Int): RoutineItemWithExercise {
        val ex = freeExercises[exerciseId]
        return RoutineItemWithExercise(
            item = RoutineItemEntity(
                id = 0L,
                dayId = 0L,
                exerciseId = exerciseId,
                orderIndex = orderIndex,
                targetSets = DEFAULT_FREE_SETS,
                repsMin = 8,
                repsMax = 12,
                targetWeightKg = null,
                restSeconds = null,
                amrap = false,
                rawText = ex?.name ?: exerciseId
            ),
            exercise = ex
        )
    }

    private fun rebuildFreeItems() {
        plannedItems = freeOrder.mapIndexed { index, id -> syntheticItem(id, index) }
    }

    /** Adds an exercise to a free workout, seeding its sets from the last time it was done. */
    fun addExercise(exercise: ExerciseEntity, onSelected: (Int) -> Unit = {}) {
        val existing = freeOrder.indexOf(exercise.id)
        if (existing >= 0) {
            onSelected(existing)
            return
        }
        viewModelScope.launch {
            freeExercises[exercise.id] = exercise
            freeOrder = freeOrder + exercise.id
            // The record has to be known BEFORE seeding: otherwise the first confirmed set
            // celebrates a personal best that is really just the first one ever seen.
            repo.bestSetForExercise(exercise.id)?.let { records = records + (exercise.id to it) }
            val item = syntheticItem(exercise.id, freeOrder.size - 1)
            val last = repo.lastSetsForExercise(exercise.id, sessionId)
            val sugg = ProgressiveOverload.suggest(item.item, exercise, last)
            suggestions = suggestions + (exercise.id to sugg)
            rebuildFreeItems()
            seedSets(item, sugg)
            onSelected(freeOrder.size - 1)
        }
    }

    /** Removes an exercise from a free workout, deleting the sets it had in this session. */
    fun removeExercise(exerciseId: String) {
        viewModelScope.launch {
            val sets = repo.setsForSession(sessionId).filter { it.exerciseId == exerciseId }
            if (sets.any { it.id == setTimerSetId }) stopSetTimer()
            sets.forEach { repo.deleteSet(it.id) }
            freeOrder = freeOrder - exerciseId
            freeExercises.remove(exerciseId)
            suggestions = suggestions - exerciseId
            // Drop the in-memory record too, or a re-added exercise would show a stale
            // "RÉCORD" for a set that was just deleted (set_logs stays the source of truth).
            records = records - exerciseId
            prSetIds = prSetIds - sets.map { it.id }.toSet()
            rebuildFreeItems()
        }
    }

    fun createCustomExercise(rawName: String, onCreated: (ExerciseEntity) -> Unit) {
        viewModelScope.launch { repo.createCustomExercise(rawName)?.let(onCreated) }
    }

    /** Personal note for an exercise, remembered for next time. Patches both modes in memory. */
    fun setExerciseNote(exerciseId: String, note: String) {
        val trimmed = note.trim().take(200).ifBlank { null }
        viewModelScope.launch {
            repo.updateExerciseNotes(exerciseId, trimmed)
            freeExercises[exerciseId]?.let { freeExercises[exerciseId] = it.copy(notes = trimmed) }
            plannedItems = plannedItems.map {
                if (it.item.exerciseId == exerciseId) it.copy(exercise = it.exercise?.copy(notes = trimmed))
                else it
            }
        }
    }

    private suspend fun seedSets(item: RoutineItemWithExercise, sugg: OverloadSuggestion?) {
        val ex = item.exercise
        val measure = ex?.measure ?: MeasureType.REPS
        val free = item.item.id == 0L

        // Restore each set from the corresponding set of the previous session (per set number).
        val previous = repo.previousSetsForExercise(item.item.exerciseId, sessionId)
        val prevByNumber = previous.associateBy { it.setNumber }

        val fallbackWeight = ex?.lastWeightKg ?: sugg?.weightKg ?: item.item.targetWeightKg ?: 20.0
        val fallbackReps = ex?.lastReps ?: if (measure == MeasureType.SECONDS) 30 else when {
            item.item.amrap -> sugg?.repsHigh ?: 10
            item.item.repsMax > 0 -> item.item.repsMax
            else -> 10
        }

        // A free exercise repeats however many sets it had last time; a planned one obeys the routine.
        val total = when {
            !free -> item.item.targetSets.coerceIn(1, 20)
            previous.isEmpty() -> DEFAULT_FREE_SETS
            else -> previous.size.coerceIn(1, 20)
        }

        for (n in 1..total) {
            val ref = prevByNumber[n] ?: previous.lastOrNull()
            repo.addSet(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = item.item.exerciseId,
                    // A synthetic item has no row in routine_items: storing 0 would be a lie.
                    routineItemId = item.item.id.takeIf { it != 0L },
                    setNumber = n,
                    reps = ref?.reps ?: fallbackReps,
                    weightKg = ref?.weightKg ?: fallbackWeight,
                    // Warm-up marking sticks: if set 1 was a warm-up last time, it is again.
                    isWarmup = prevByNumber[n]?.isWarmup ?: false,
                    completed = false,
                    measure = measure
                )
            )
        }
    }

    fun setsFor(exerciseId: String): List<SetLogEntity> =
        session.value?.sets?.filter { it.exerciseId == exerciseId }?.sortedBy { it.setNumber } ?: emptyList()

    fun addSet(item: RoutineItemWithExercise) {
        viewModelScope.launch {
            val current = setsFor(item.item.exerciseId)
            val setNumber = (current.maxOfOrNull { it.setNumber } ?: 0) + 1
            val sugg = suggestions[item.item.exerciseId]
            val measure = current.firstOrNull()?.measure ?: item.exercise?.measure ?: MeasureType.REPS
            val weight = current.maxByOrNull { it.setNumber }?.weightKg
                ?: sugg?.weightKg ?: item.item.targetWeightKg ?: 20.0
            val reps = current.maxByOrNull { it.setNumber }?.reps
                ?: if (measure == MeasureType.SECONDS) 30 else when {
                    item.item.amrap -> sugg?.repsHigh ?: 10
                    item.item.repsMax > 0 -> item.item.repsMax
                    else -> 10
                }
            repo.addSet(
                SetLogEntity(
                    sessionId = sessionId,
                    exerciseId = item.item.exerciseId,
                    routineItemId = item.item.id.takeIf { it != 0L },
                    setNumber = setNumber,
                    reps = reps,
                    weightKg = weight,
                    completed = false,
                    measure = measure
                )
            )
        }
    }

    fun setWeight(set: SetLogEntity, kg: Double) {
        viewModelScope.launch {
            val v = kg.coerceIn(0.0, 2000.0)
            repo.updateSet(set.copy(weightKg = v))
            repo.updateExerciseLastWeight(set.exerciseId, v)
        }
    }

    fun setReps(set: SetLogEntity, reps: Int) {
        viewModelScope.launch {
            val v = reps.coerceIn(0, 1000)
            repo.updateSet(set.copy(reps = v))
            repo.updateExerciseLastReps(set.exerciseId, v)
        }
    }

    fun toggleCompleted(set: SetLogEntity) {
        // Confirming by hand while its countdown runs stops the countdown.
        if (setTimerSetId == set.id) stopSetTimer()
        viewModelScope.launch {
            val nowCompleted = !set.completed
            repo.updateSet(set.copy(completed = nowCompleted))
            if (nowCompleted) {
                if (!set.isWarmup) {
                    repo.updateExerciseLastWeight(set.exerciseId, set.weightKg)
                    repo.updateExerciseLastReps(set.exerciseId, set.reps)
                    checkPersonalRecord(set)
                }
                // Warm-up sets get a short rest instead of the full working-set rest.
                val rest = restSecondsFor(set.exerciseId)
                startRest(if (set.isWarmup) minOf(rest, WARMUP_REST_SECONDS) else rest)
            }
        }
    }

    /** Marks a set as a warm-up (or back to a working set). Warm-ups don't count for volume or records. */
    fun toggleWarmup(set: SetLogEntity) {
        viewModelScope.launch {
            repo.updateSet(set.copy(isWarmup = !set.isWarmup))
            if (!set.isWarmup) prSetIds = prSetIds - set.id
        }
    }

    /** Stores the reps in reserve for a set (null clears it). */
    fun setRir(set: SetLogEntity, rir: Int?) {
        viewModelScope.launch {
            val current = set.rpe?.let { RIR_MAX_RPE - it }?.roundToInt()
            val value = if (current == rir) null else rir?.let { RIR_MAX_RPE - it }
            repo.updateSet(set.copy(rpe = value))
        }
    }

    /**
     * Compares a just-confirmed set with the all-time best and raises a celebration
     * when it beats it on weight, on reps at that weight, or on estimated 1RM.
     */
    private fun checkPersonalRecord(set: SetLogEntity) {
        if (set.measure != MeasureType.REPS || set.weightKg <= 0.0 || set.reps <= 0) return
        val name = plannedItems.firstOrNull { it.item.exerciseId == set.exerciseId }?.exercise?.name
            ?: return
        val best = records[set.exerciseId]
        // First ever set of an exercise: it becomes the baseline, but is not a record.
        // You have only broken a record once you have something to beat.
        if (best == null) {
            records = records + (set.exerciseId to set.copy(completed = true))
            return
        }
        val kind = when {
            set.weightKg > best.weightKg -> PrKind.WEIGHT
            set.weightKg == best.weightKg && set.reps > best.reps -> PrKind.REPS
            epley1RM(set.weightKg, set.reps) > epley1RM(best.weightKg, best.reps) + 0.01 -> PrKind.E1RM
            else -> return
        }
        val weightLabel = "${formatKg(set.weightKg)} kg × ${set.reps}"
        val text = when (kind) {
            PrKind.WEIGHT -> "Peso máximo: $weightLabel"
            PrKind.REPS -> "Más reps a ${formatKg(set.weightKg)} kg: ${set.reps}"
            // The 1RM is an estimate: one decimal, not the three a real load deserves.
            PrKind.E1RM ->
                "Mejor 1RM estimado: " +
                    "${formatKg((epley1RM(set.weightKg, set.reps) * 10).roundToInt() / 10.0)} kg"
        }
        records = records + (set.exerciseId to set.copy(completed = true))
        prSetIds = prSetIds + set.id
        prLog += "$name — $text"
        prEvent = PrEvent(set.id, name, text, kind)
        if (soundOn) bell.celebrate()
    }

    fun dismissPr() {
        prEvent = null
    }

    /**
     * Plates the gym has, stored in the profile as grams.
     * There is no onboarding, so a user who never opened Perfil has no profile row yet:
     * it has to be created here, or the selection would be dropped without a word.
     */
    fun setPlates(gramsSet: Collection<Int>) {
        if (gramsSet.isEmpty()) return // never leave the calculator with nothing to work with
        viewModelScope.launch {
            val current = repo.getProfile() ?: ProfileEntity()
            repo.saveProfile(
                current.copy(id = 1, plateSetGrams = PlateCalculator.formatPlates(gramsSet))
            )
        }
    }

    /**
     * Loading points of an exercise (barbell = 2, four-post press = 4), remembered per exercise.
     * [plannedItems] is a snapshot taken in init, so it has to be patched by hand exactly like
     * [setRest] does — otherwise the dialog would keep showing the old value.
     */
    fun setLoadPoints(exerciseId: String, points: Int) {
        val value = points.coerceIn(1, PlateCalculator.MAX_POINTS)
        viewModelScope.launch {
            repo.updateExerciseLoadPoints(exerciseId, value)
            plannedItems = plannedItems.map {
                if (it.item.exerciseId == exerciseId) it.copy(exercise = it.exercise?.copy(loadPoints = value))
                else it
            }
        }
    }

    /** Configured rest for an exercise (defaults to 90s when the routine doesn't set one). */
    fun restSecondsFor(exerciseId: String): Int {
        val planned = plannedItems.firstOrNull { it.item.exerciseId == exerciseId }
        return planned?.item?.restSeconds
            ?: planned?.exercise?.defaultRestSeconds
            ?: DEFAULT_REST_SECONDS
    }

    /** Persists a new rest time for this routine exercise. */
    fun setRest(item: RoutineItemWithExercise, seconds: Int) {
        val value = seconds.coerceIn(0, 600)
        viewModelScope.launch {
            if (item.item.id == 0L) {
                // Free workout: no routine item to hang it on, so it lives on the exercise.
                val exId = item.item.exerciseId
                repo.updateExerciseRest(exId, value)
                freeExercises[exId]?.let { freeExercises[exId] = it.copy(defaultRestSeconds = value) }
                rebuildFreeItems()
                return@launch
            }
            repo.updateItemRest(item.item.id, value)
            plannedItems = plannedItems.map {
                if (it.item.id == item.item.id) it.copy(item = it.item.copy(restSeconds = value)) else it
            }
        }
    }

    /**
     * Countdown for a set measured in seconds (planks, dead hangs...). When it reaches zero the
     * bell rings and the set is confirmed on its own, so the user never has to touch the phone
     * mid-hold.
     */
    fun startSetTimer(set: SetLogEntity) {
        if (set.measure != MeasureType.SECONDS || set.reps <= 0) return
        skipRest()
        setTimerJob?.cancel()
        setTimerSetId = set.id
        setTimerTotal = set.reps
        setTimerJob = viewModelScope.launch {
            // Counted against the real clock, not by adding up delays: elapsedRealtime keeps
            // running while the device sleeps, so a screen that turns off mid-plank doesn't
            // freeze the countdown (and there is no drift either).
            val endAt = SystemClock.elapsedRealtime() + set.reps * 1000L
            while (true) {
                val remainingMs = endAt - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) break
                setTimerLeft = ceil(remainingMs / 1000.0).toInt()
                delay(minOf(1000L, remainingMs))
            }
            setTimerLeft = 0
            setTimerSetId = null
            // Only if the set still exists: it may have been deleted mid-countdown.
            val current = session.value?.sets?.firstOrNull { it.id == set.id } ?: return@launch
            if (soundOn) bell.ring()
            if (!current.completed) toggleCompleted(current)
        }
    }

    fun stopSetTimer() {
        setTimerJob?.cancel()
        setTimerJob = null
        setTimerSetId = null
        setTimerLeft = 0
    }

    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restJob?.cancel()
        restTotal = seconds
        restJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                restLeft = left
                delay(1000)
                left--
            }
            restLeft = 0
            if (soundOn) bell.ring()
        }
    }

    fun skipRest() {
        restJob?.cancel()
        restLeft = 0
    }

    fun deleteSet(set: SetLogEntity) {
        // A countdown for a set that no longer exists would ring for nothing.
        if (setTimerSetId == set.id) stopSetTimer()
        viewModelScope.launch { repo.deleteSet(set.id) }
    }

    /** Switches an exercise between rep-based and time-based, updating its existing sets. */
    fun setMeasure(exerciseId: String, sets: List<SetLogEntity>, measure: MeasureType) {
        // Switching to reps kills the countdown: there would be no button left to stop it.
        if (sets.any { it.id == setTimerSetId }) stopSetTimer()
        viewModelScope.launch {
            repo.updateExerciseMeasure(exerciseId, measure)
            sets.forEach { repo.updateSet(it.copy(measure = measure)) }
        }
    }

    /** Finishes the session and produces a summary (duration, calories, volume) to show the user. */
    /** True while the session has at least one confirmed set. */
    val hasCompletedSets: Boolean
        get() = session.value?.sets?.any { it.completed } == true

    /**
     * @param onDiscarded called instead of showing a summary when nothing was confirmed:
     *        an empty session would pollute the history, the streak and the comeback detector.
     */
    fun finishWorkout(onDiscarded: () -> Unit = {}) {
        stopSetTimer()
        skipRest()
        viewModelScope.launch {
            val current = session.value
            val sets = current?.sets ?: emptyList()
            if (sets.none { it.completed }) {
                repo.deleteSession(sessionId)
                onDiscarded()
                return@launch
            }
            val bw = repo.getProfile()?.bodyweightKg ?: 75.0
            val now = repo.now()
            repo.finishSession(sessionId)
            val finished = (current?.session ?: repo.sessionById(sessionId))?.copy(finishedAt = now)
            val completed = sets.count { it.completed }
            val startedAt = finished?.startedAt ?: now
            val comeback = detectComeback(startedAt, completed)
            // Process feedback: recognise showing up, not only beating a record. In a plateau
            // there is no PR, and the absence of one should not read as failure.
            val target = weeklyTargetFor(repo.activeRoutine())
            val weekDates = repo.loggedSessionStartsBefore(sessionId, 30).toMutableList().apply { add(startedAt) }
            val weekly = computeWeeklyStreak(weekDates, target)
            summary = WorkoutSummary(
                durationMin = finished?.let { WorkoutMath.durationMinutes(it, completed).roundToInt() } ?: 0,
                kcal = finished?.let { WorkoutMath.caloriesKcal(it, sets, bw) } ?: 0,
                volumeKg = WorkoutMath.volumeKg(sets),
                sets = completed,
                prs = prLog.toList(),
                comeback = comeback,
                weekDone = weekly.doneThisWeek,
                weekTarget = weekly.target
            )
        }
    }

    /**
     * Works out whether this session is a comeback after a gap. Persisted so re-running
     * finish never awards it twice, and so History can show it later.
     */
    private suspend fun detectComeback(startedAt: Long, completedSets: Int): ComebackBonus? {
        if (repo.comebackDays(sessionId) != null) return null
        // The limit counts sessions, not distinct days: ask for more than the cadence window.
        val starts = repo.loggedSessionStartsBefore(sessionId, CADENCE_WINDOW * 2)
        val count = repo.loggedSessionCountBefore(sessionId)
        val bonus = evaluate(
            today = epochToLocalDate(startedAt),
            priorSessionDates = starts.map { epochToLocalDate(it) },
            priorSessionCount = count,
            weeklyTarget = weeklyTargetFor(repo.activeRoutine()),
            completedSetsToday = completedSets,
            firstSeenAtDate = epochToLocalDate(firstSeenAt)
        ) ?: return null
        repo.markComeback(sessionId, bonus.days)
        return bonus
    }

    companion object {
        const val DEFAULT_REST_SECONDS = 90
        const val WARMUP_REST_SECONDS = 45
        const val DEFAULT_FREE_SETS = 3

        /** RIR 0 means RPE 10, RIR 3 means RPE 7, etc. */
        const val RIR_MAX_RPE = 10.0

        fun factory(repo: GymRepository, bell: RestBell, sessionId: Long, firstSeenAt: Long) =
            viewModelFactory { initializer { WorkoutViewModel(repo, bell, sessionId, firstSeenAt) } }
    }
}
