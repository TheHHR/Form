package com.thehhr.form

import com.thehhr.form.data.SetRow
import com.thehhr.form.data.WorkoutLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class CarryForwardTest {
    @Test fun editCopiesToLaterPendingSetsOnly() {
        val rows = listOf(SetRow(10, 50.0, done = true), SetRow(10, 50.0), SetRow(10, 50.0), SetRow(8, 40.0, done = true))
        val out = WorkoutLogic.carryForward(rows, 1, SetRow(10, 55.0))
        assertEquals(listOf(50.0, 55.0, 55.0, 40.0), out.map { it.weight }) // earlier and done sets untouched
        assertEquals(listOf(10, 10, 10, 8), out.map { it.reps }) // unchanged field not copied
    }

    @Test fun checkingASetChangesNothingElse() {
        val rows = listOf(SetRow(10, 50.0), SetRow(12, 45.0))
        assertEquals(rows[1], WorkoutLogic.carryForward(rows, 0, rows[0].copy(done = true))[1])
    }
}
