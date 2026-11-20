package al.pcmania.web.view;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DealsTest {

    private static final LocalDateTime FRIDAY_NOON = LocalDateTime.of(2026, 11, 27, 12, 0);

    @Test
    void aSaleEndingAtMidnightTonightEndsToday() {
        assertEquals("Mbaron sot", Deals.urgency(FRIDAY_NOON.toLocalDate().plusDays(1).atStartOfDay(), FRIDAY_NOON));
        assertEquals("Mbaron sot", Deals.urgency(FRIDAY_NOON.withHour(23).withMinute(59), FRIDAY_NOON));
    }

    @Test
    void theNextDaysAreCountedAndLaterOnesStayQuiet() {
        assertEquals("Mbaron nesër", Deals.urgency(FRIDAY_NOON.plusDays(1).withHour(23).withMinute(59), FRIDAY_NOON));
        assertEquals("Edhe 3 ditë", Deals.urgency(FRIDAY_NOON.plusDays(3).withHour(23).withMinute(59), FRIDAY_NOON));
        assertNull(Deals.urgency(FRIDAY_NOON.plusDays(4).withHour(23).withMinute(59), FRIDAY_NOON));
        assertNull(Deals.urgency(FRIDAY_NOON.minusMinutes(1), FRIDAY_NOON));
    }

    @Test
    void percentIsRoundedToTheNearestWholeNumber() {
        assertEquals(15, Deals.percent(27_500, 23_300));
        assertEquals(0, Deals.percent(0, 0));
    }
}
