package flight;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 tests for FlightSearch.
 */
public class FlightSearchTest {

    private FlightSearch fs;
    private final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private String depDatePlus(int days) { return LocalDate.now().plusDays(days).format(F); }
    private String depValid() { return depDatePlus(21); }
    private String retValid() { return depDatePlus(28); }

    @BeforeEach
    void setUp() {
        fs = new FlightSearch();
        // Prime the object with a known good state so we can assert "no change on failure".
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "economy",
                2, 2, 0);
        assertTrue(ok, "Priming the state should succeed");
    }

    //  All-valid scenarios (four variants) 
    @Test @DisplayName("All valid: economy, adults only, emergency row allowed")
    void economy_adults_only_can_use_exit_row() {
        boolean ok = fs.runFlightSearch(
                depValid(), "mel", true,
                retValid(), "pvg", "economy",
                3, 0, 0);
        assertTrue(ok);
        assertCommitted("mel", "pvg", "economy", true, 3, 0, 0);
    }

    @Test @DisplayName("All valid: economy family within child ratio, non-emergency")
    void valid_familyEconomyNonEmergency() {
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "lax", "economy",
                2, 4, 0);
        assertTrue(ok);
        assertCommitted("syd", "lax", "economy", false, 2, 4, 0);
    }

    @Test @DisplayName("All valid: premium economy with single adult, non-emergency")
    void valid_premiumEconomySingleAdult() {
        boolean ok = fs.runFlightSearch(
                depValid(), "cdg", false,
                retValid(), "doh", "premium economy",
                1, 0, 0);
        assertTrue(ok);
        assertCommitted("cdg", "doh", "premium economy", false, 1, 0, 0);
    }

    @Test @DisplayName("All valid: first class, adults only, non-emergency")
    void valid_firstClassAdultsOnly() {
        boolean ok = fs.runFlightSearch(
                depValid(), "mel", false,
                retValid(), "cdg", "first",
                2, 0, 0);
        assertTrue(ok);
        assertCommitted("mel", "cdg", "first", false, 2, 0, 0);
    }

    //  Condition 1: total passengers between 1 and 9 
    @Test @DisplayName("Invalid: total passengers below minimum")
    void invalid_totalBelowMin() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "economy",
                0, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    @Test @DisplayName("Invalid: total passengers above maximum")
    void invalid_totalAboveMax() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "economy",
                5, 5, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 2: children not in exit row or first class 
    @Test @DisplayName("Invalid: child present with emergency row selected")
    void invalid_childInExitRow() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", true,
                retValid(), "mel", "economy",
                2, 1, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    @Test @DisplayName("Invalid: child present in first class")
    void invalid_childInFirstClass() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "first",
                2, 1, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 3: infants not in exit row or business class 
    @Test @DisplayName("Invalid: infant present with emergency row selected")
    void invalid_infantInExitRow() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", true,
                retValid(), "mel", "economy",
                2, 0, 1);
        assertFalse(ok);
        assertUnchanged(before);
    }

    @Test @DisplayName("Invalid: infant present in business class")
    void invalid_infantInBusiness() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "business",
                2, 0, 1);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 4: at most two children per adult 
    @Test @DisplayName("Invalid: children exceed ratio of 2 per adult")
    void children_more_than_two_per_adult_is_rejected() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "economy",
                1, 3, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 5: at most one infant per adult 
    @Test @DisplayName("Invalid: infants exceed ratio of 1 per adult")
    void more_than_one_infant_per_adult_is_rejected() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "economy",
                1, 0, 2);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 6: departure not in the past 
    @Test @DisplayName("Invalid: departure date is in the past")
    void invalid_departureInPast() {
        var before = snapshot();
        String past = LocalDate.now().minusDays(1).format(F);
        boolean ok = fs.runFlightSearch(
                past, "syd", false,
                retValid(), "mel", "economy",
                1, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 7: strict date validation 
    @Test @DisplayName("Invalid: non-existent date fails strict parsing")
    void invalid_nonexistentDate() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                "29/02/2100", "syd", false, // 2100 is not a leap year
                "05/03/2100", "mel", "economy",
                1, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 8: return is not before departure 
    @Test @DisplayName("Invalid: return date before departure")
    void invalid_returnBeforeDeparture() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                "20/01/2100", "syd", false,
                "18/01/2100", "mel", "economy",
                1, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 9: class is one of allowed values 
    @Test @DisplayName("Invalid: unsupported seating class")
    void invalid_unsupportedClass() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "mel", "luxury",
                1, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    //  Condition 10: only economy can be emergency row 
    @Test @DisplayName("Invalid: emergency row selected with non-economy class")
    void invalid_exitRowNonEconomy() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", true,
                retValid(), "mel", "premium economy",
                2, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    // Condition 11: airport codes must be valid and different 
    @Test @DisplayName("Invalid: unsupported airport code")
    void invalid_airportUnsupported() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "jfk", false,
                retValid(), "mel", "economy",
                1, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    @Test @DisplayName("Invalid: same code for departure and destination")
    void invalid_sameAirport() {
        var before = snapshot();
        boolean ok = fs.runFlightSearch(
                depValid(), "syd", false,
                retValid(), "syd", "economy",
                1, 0, 0);
        assertFalse(ok);
        assertUnchanged(before);
    }

    // Helper snapshots and assertions 
    private record Snap(String depDate, String depCode, boolean exit,
                        String retDate, String destCode, String cls,
                        int a, int c, int i) {}

    private Snap snapshot() {
        return new Snap(
            fs.getDepartureDate(),
            fs.getDepartureAirportCode(),
            fs.isEmergencyRowSeating(),
            fs.getReturnDate(),
            fs.getDestinationAirportCode(),
            fs.getSeatingClass(),
            fs.getAdultPassengerCount(),
            fs.getChildPassengerCount(),
            fs.getInfantPassengerCount()
        );
    }

    private void assertUnchanged(Snap before) {
        assertEquals(before.depDate(), fs.getDepartureDate());
        assertEquals(before.depCode(), fs.getDepartureAirportCode());
        assertEquals(before.exit(), fs.isEmergencyRowSeating());
        assertEquals(before.retDate(), fs.getReturnDate());
        assertEquals(before.destCode(), fs.getDestinationAirportCode());
        assertEquals(before.cls(), fs.getSeatingClass());
        assertEquals(before.a(), fs.getAdultPassengerCount());
        assertEquals(before.c(), fs.getChildPassengerCount());
        assertEquals(before.i(), fs.getInfantPassengerCount());
    }

    private void assertCommitted(String depCode, String destCode, String cls, boolean exit,
                                 int a, int c, int i) {
        assertEquals(depCode, fs.getDepartureAirportCode());
        assertEquals(destCode, fs.getDestinationAirportCode());
        assertEquals(cls, fs.getSeatingClass());
        assertEquals(exit, fs.isEmergencyRowSeating());
        assertEquals(a, fs.getAdultPassengerCount());
        assertEquals(c, fs.getChildPassengerCount());
        assertEquals(i, fs.getInfantPassengerCount());
    }
}
