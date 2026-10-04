package com.example.gestionmedicamentos

import com.example.gestionmedicamentos.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private val med = HealthRecord(id = "med", kind = Kind.MEDICINE, title = "Prueba", detail = "Dosis prescrita", times = "22:00, 06:00, 14:00", start = "2026-09-01", end = "2026-09-30")
    @Test fun multipleTimesSorted() { assertEquals(listOf("06:00", "14:00", "22:00"), Schedule.onDate(listOf(med), LocalDate.parse("2026-09-27")).map { it.dateTime.toLocalTime().toString() }) }
    @Test fun treatmentBoundsInclusive() {
        assertEquals(3, Schedule.onDate(listOf(med), LocalDate.parse("2026-09-01")).size)
        assertEquals(3, Schedule.onDate(listOf(med), LocalDate.parse("2026-09-30")).size)
        assertTrue(Schedule.onDate(listOf(med), LocalDate.parse("2026-08-31")).isEmpty())
        assertTrue(Schedule.onDate(listOf(med), LocalDate.parse("2026-10-01")).isEmpty())
    }
    @Test fun noDuplicateDoses() { assertEquals(1, Schedule.times("08:00, 08:00").size) }
    @Test fun agendaHidesElapsedTimes() {
        val now = LocalDateTime.parse("2026-09-27T10:00")
        val today = med.copy(start = now.toLocalDate().toString(), end = "", times = "06:00, 10:00, 18:00")
        assertEquals(listOf("18:00"), Schedule.agenda(listOf(today), now).map { it.dateTime.toLocalTime().toString() })
    }
    @Test fun elapsedDoseStaysPendingUntilExplicitlyRecorded() {
        val date = LocalDate.parse("2026-09-27")
        val today = med.copy(start = date.toString(), end = "", times = "08:00, 12:00")
        val elapsed = Schedule.pendingOnDate(listOf(today), date, emptyList())
        assertEquals(listOf("08:00", "12:00"), elapsed.map { it.dateTime.toLocalTime().toString() })

        val recorded = Completion(today.id, elapsed.first().key, Status.TAKEN, 0L)
        assertEquals(listOf("12:00"), Schedule.pendingOnDate(listOf(today), date, listOf(recorded)).map { it.dateTime.toLocalTime().toString() })
    }
    @Test fun adherenceCountsOnlyDosesAlreadyDue() {
        val date = LocalDate.parse("2026-09-27")
        val today = med.copy(start = date.toString(), end = "", times = "08:00, 12:00, 18:00")
        val doses = Schedule.onDate(listOf(today), date)
        val completions = listOf(Completion(today.id, doses[0].key, Status.TAKEN, 0L))
        assertEquals(50, Schedule.adherence(doses, completions, LocalDateTime.parse("2026-09-27T13:00")))
    }
    @Test fun adherenceIsUnknownBeforeFirstDoseIsDue() {
        val date = LocalDate.parse("2026-09-27")
        val today = med.copy(start = date.toString(), end = "", times = "18:00")
        assertNull(Schedule.adherence(Schedule.onDate(listOf(today), date), emptyList(), LocalDateTime.parse("2026-09-27T08:00")))
    }
    @Test fun archiveCancelsFutureTasks() { assertTrue(Schedule.onDate(listOf(med.copy(archived = true)), LocalDate.parse("2026-09-27")).isEmpty()) }
    @Test fun archivedMedicationCanBeShownInHistory() {
        val archived = med.copy(archived = true)
        assertEquals(1, Schedule.onDate(listOf(archived), LocalDate.parse("2026-09-27"), includeArchived = true).size)
    }
    @Test fun appointmentOnlyOnce() {
        val r=med.copy(kind=Kind.APPOINTMENT,times="10:00")
        assertEquals(1,Schedule.onDate(listOf(r),LocalDate.parse("2026-09-01")).size)
        assertEquals(0,Schedule.onDate(listOf(r),LocalDate.parse("2026-09-02")).size)
    }
    @Test fun nutritionRestrictionsDoNotBecomeTasks() { assertTrue(Schedule.onDate(listOf(med.copy(kind=Kind.RESTRICTION)),LocalDate.parse("2026-09-27")).isEmpty()) }
    @Test fun nextDoseCrossesMidnight() { assertEquals("2026-09-28T06:00", Schedule.next(med,LocalDateTime.parse("2026-09-27T23:58"))?.key) }
    @Test fun nextDoseBeyondEndIsAbsent() { assertNull(Schedule.next(med,LocalDateTime.parse("2026-09-30T23:00"))) }
    @Test(expected=IllegalArgumentException::class) fun rejectEmptyDose() { Schedule.validate(med.copy(detail="")) }
    @Test(expected=IllegalArgumentException::class) fun rejectReversedDates() { Schedule.validate(med.copy(end="2026-08-01")) }
    @Test(expected=IllegalArgumentException::class) fun rejectPastAppointment() {
        Schedule.validate(med.copy(kind = Kind.APPOINTMENT, detail = "", times = "10:00", start = LocalDate.now().minusDays(1).toString()))
    }
    @Test fun mealCanStartBeforeToday() {
        val meal = med.copy(kind = Kind.MEAL, detail = "", start = LocalDate.now().minusDays(3).toString(), end = "")
        Schedule.validate(meal)
    }
    @Test fun confirmedDoseConsumesConfiguredStock() {
        assertEquals(3, Schedule.stockAfterDose(med.copy(stockRemaining = 5, unitsPerDose = 2)))
        assertEquals(0, Schedule.stockAfterDose(med.copy(stockRemaining = 1, unitsPerDose = 2)))
        assertNull(Schedule.stockAfterDose(med))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectNegativeStock() {
        Schedule.validate(med.copy(stockRemaining = -1))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectStockAboveConfiguredTotal() {
        Schedule.validate(med.copy(stockRemaining = 11, stockTotal = 10))
    }
    @Test(expected=Exception::class) fun rejectImpossibleTime() { Schedule.validate(med.copy(times="25:00")) }
    @Test(expected=IllegalArgumentException::class) fun rejectAmbiguousTime() { Schedule.validate(med.copy(times="8:00")) }
    @Test fun mixedTimelineOrdered() {
        val meal = med.copy(id="meal",kind=Kind.MEAL,times="07:00")
        assertEquals(listOf("med","meal","med","med"),Schedule.onDate(listOf(meal,med),LocalDate.parse("2026-09-27")).map { it.record.id })
    }
}
