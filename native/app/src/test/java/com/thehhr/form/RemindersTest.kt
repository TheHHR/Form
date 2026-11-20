package com.thehhr.form

import com.thehhr.form.data.ActiveWorkout
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.Routine
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.VaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RemindersTest {
    private val friday = LocalDate.of(2026, 11, 20) // dayOfWeek 5
    private val push = Routine("r1", "Push", items = listOf(RoutineItem("0025")))
    private val data = VaultData(routines = listOf(push, Routine("r2", "Empty")), schedule = mapOf(5 to "r1", 6 to "r2"))

    @Test fun remindsOnScheduledDay() = assertEquals(push, Reminders.routineToRemind(data, null, friday))
    @Test fun restDay() = assertNull(Reminders.routineToRemind(data, null, friday.plusDays(2)))
    @Test fun emptyRoutineIsNotWorthANudge() = assertNull(Reminders.routineToRemind(data, null, friday.plusDays(1)))
    @Test fun alreadyTraining() = assertNull(Reminders.routineToRemind(data, ActiveWorkout("2026-11-20", "r1", "Push", 0), friday))
    @Test fun alreadyLoggedToday() = assertNull(Reminders.routineToRemind(
        data.copy(logs = listOf(ProgressLog("1", "0025", "2026-11-20", sets = 1, reps = 5))), null, friday))
}
