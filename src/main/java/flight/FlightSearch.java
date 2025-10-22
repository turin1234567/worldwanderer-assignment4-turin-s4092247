package flight;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * WorldWanderer - FlightSearch
 * ------------------------------------------------------------
 * This class validates a proposed flight search against the
 * assignment's business rules. If and only if every rule passes,
 * we initialise this object's attributes from the parameters and
 * return true. If any rule fails, we leave the existing state
 * alone and return false.
 *
 * Notes (aligns with the updated brief):
 *  - Condition 10: Only economy class seating can have an emergency
 *    row. All classes may be non-emergency.
 *  - Note 7: Tests must assert the return value and also that state
 *    is initialised on success and left unchanged on failure.
 *
 * Design choice:
 *  We keep the validation logic in small helper methods to make
 *  intent obvious. We validate first without mutating fields, then
 *  commit the values at the end in one step.
 */
public class FlightSearch {

    //  Attributes (only written when validation succeeds) 
    private String  departureDate;           // dd/MM/yyyy
    private String  departureAirportCode;    // e.g. "syd"
    private boolean emergencyRowSeating;
    private String  returnDate;              // dd/MM/yyyy
    private String  destinationAirportCode;  // e.g. "mel"
    private String  seatingClass;            // "economy", "premium economy", "business", "first"
    private int     adultPassengerCount;
    private int     childPassengerCount;
    private int     infantPassengerCount;

    // ---- Allowed values ----
    private static final Set<String> VALID_AIRPORTS = new HashSet<>(
            Arrays.asList("syd", "mel", "lax", "cdg", "del", "pvg", "doh"));
    private static final Set<String> VALID_CLASSES = new HashSet<>(
            Arrays.asList("economy", "premium economy", "business", "first"));

    private static final DateTimeFormatter STRICT_DDMMYYYY =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
                             .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Validate all inputs against Conditions 1-11. No state is changed
     * unless every check passes.
     */
    public boolean runFlightSearch(String departureDate,
                                   String departureAirportCode,
                                   boolean emergencyRowSeating,
                                   String returnDate,
                                   String destinationAirportCode,
                                   String seatingClass,
                                   int adultPassengerCount,
                                   int childPassengerCount,
                                   int infantPassengerCount) {

        // 1. Passengers and ratios (Conditions 1, 4, 5)
        if (!validatePassengerCounts(adultPassengerCount, childPassengerCount, infantPassengerCount)) {
            return false;
        }

        // 2. Seating class (Condition 9)
        if (!isValidClass(seatingClass)) {
            return false;
        }

        // 3. Airports (Condition 11)
        if (!areValidAirports(departureAirportCode, destinationAirportCode)) {
            return false;
        }

        // 4. Dates (Conditions 6, 7, 8) - parse strictly first, then compare
        final LocalDate dep, ret;
        try {
            dep = LocalDate.parse(departureDate, STRICT_DDMMYYYY);
            ret = LocalDate.parse(returnDate,     STRICT_DDMMYYYY);
        } catch (DateTimeParseException ex) {
            return false; // fails strict format/validity (e.g. 29/02 on non-leap year)
        }
        if (!areValidDates(dep, ret)) {
            return false;
        }

        // 5. Seating rules by class and emergency row (Conditions 2, 3, 10)
        if (!validateSeatingRules(seatingClass, emergencyRowSeating, adultPassengerCount, childPassengerCount, infantPassengerCount)) {
            return false;
        }

        // All good, commit the values (initialise attributes)
        this.departureDate = departureDate;
        this.departureAirportCode = departureAirportCode;
        this.emergencyRowSeating = emergencyRowSeating;
        this.returnDate = returnDate;
        this.destinationAirportCode = destinationAirportCode;
        this.seatingClass = seatingClass;
        this.adultPassengerCount = adultPassengerCount;
        this.childPassengerCount = childPassengerCount;
        this.infantPassengerCount = infantPassengerCount;

        return true;
    }

    //  Validation helpers 

    // Conditions 1, 4, 5
    private boolean validatePassengerCounts(int adults, int children, int infants) {
        if (adults < 0 || children < 0 || infants < 0) return false;

        int total = adults + children + infants;
        if (total < 1 || total > 9) return false;      // Condition 1

        if (children > adults * 2) return false;       // Condition 4: ≤ 2 children per adult
        if (infants > adults) return false;            // Condition 5: ≤ 1 infant per adult
        return true;
    }

    // Condition 9
    private boolean isValidClass(String seatingClass) {
        return seatingClass != null && VALID_CLASSES.contains(seatingClass);
    }

    // Condition 11
    private boolean areValidAirports(String dep, String dest) {
        if (dep == null || dest == null) return false;
        if (!VALID_AIRPORTS.contains(dep) || !VALID_AIRPORTS.contains(dest)) return false;
        if (dep.equals(dest)) return false; // must be different
        return true;
    }

    // Conditions 6, 7, 8
    private boolean areValidDates(LocalDate dep, LocalDate ret) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (dep.isBefore(today)) return false; // Condition 6
        if (ret.isBefore(dep)) return false;   // Condition 8 (same-day return allowed)
        return true;
    }

    // Conditions 2, 3, 10
    // Emergency rows are a special case: only economy can request them.
    // If emergencyRow is true and class isn't economy, the request is invalid.
    private boolean validateSeatingRules(String seatingClass,
                                         boolean emergencyRow,
                                         int adults, int children, int infants) {

        // Condition 10: emergency rows only available in economy
        if (emergencyRow && !"economy".equals(seatingClass)) return false;

        // Condition 2: children restrictions
        // Ratios:
        // - up to 2 children per adult
        // - up to 1 infant per adult
        // These are simple counts, not seat allocation. Keep it clear and strict.
        if (children > 0) {
            if (emergencyRow) return false;          // children cannot sit in emergency row
            if ("first".equals(seatingClass)) return false; // children cannot be in first class
        }

        // Condition 3: infant restrictions
        if (infants > 0) {
            if (emergencyRow) return false;            // infants cannot be in emergency row
            if ("business".equals(seatingClass)) return false; // infants cannot be in business
        }

        return true;
    }

    //  Getters used by tests 
    public String getDepartureDate() { return departureDate; }
    public String getDepartureAirportCode() { return departureAirportCode; }
    public boolean isEmergencyRowSeating() { return emergencyRowSeating; }
    public String getReturnDate() { return returnDate; }
    public String getDestinationAirportCode() { return destinationAirportCode; }
    public String getSeatingClass() { return seatingClass; }
    public int getAdultPassengerCount() { return adultPassengerCount; }
    public int getChildPassengerCount() { return childPassengerCount; }
    public int getInfantPassengerCount() { return infantPassengerCount; }
}
