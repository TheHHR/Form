package com.thehhr.form

import com.thehhr.form.data.Prefs
import com.thehhr.form.data.Routine
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.WorkoutLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineLengthTest {
    private val prefs = Prefs(restBetweenSets = 60.0, restBetweenExercises = 90.0)

    @Test fun restBetweenSetsAndExercisesPlusTimedSets() {
        // A: 3 sets -> 2 rests; B: 2 x 5 min timed -> 1 rest + 10 min; one change of exercise.
        val r = Routine("r", "R", items = listOf(RoutineItem("a", sets = 3), RoutineItem("b", sets = 2, reps = 5, timed = true, unit = "min")))
        assertEquals((2 * 60 + 60 + 90 + 600) / 60, WorkoutLogic.minMinutes(r, prefs))
    }

    @Test fun supersetRestsOncePerRound() {
        val r = Routine("r", "R", items = listOf(RoutineItem("a", sets = 4, superset = "s"), RoutineItem("b", sets = 4, superset = "s")))
        assertEquals(3, WorkoutLogic.minMinutes(r, prefs)) // 3 rounds of 60 s, no exercise change
        assertEquals(0, WorkoutLogic.minMinutes(r, prefs.copy(restEnabled = false)))
    }
}
