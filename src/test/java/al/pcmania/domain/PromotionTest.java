package al.pcmania.domain;

import al.pcmania.domain.Enums.DiscountType;
import al.pcmania.domain.Enums.PromoState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromotionTest {

    @Test
    void aPercentCutRoundsDownToAWholeHundredSoTheAdvertisedPercentStaysTrue() {
        assertEquals(23_300, Promotion.salePrice(DiscountType.PERCENT, 15, 27_500, null));
        assertEquals(24_000, Promotion.salePrice(DiscountType.PERCENT, 20, 30_000, null));
        assertEquals(850, Promotion.salePrice(DiscountType.PERCENT, 10, 950, null));
    }

    @Test
    void anAmountCutSubtractsLek() {
        assertEquals(23_000, Promotion.salePrice(DiscountType.AMOUNT, 2_000, 25_000, null));
        assertEquals(0, Promotion.salePrice(DiscountType.AMOUNT, 30_000, 25_000, null));
    }

    @Test
    void aFixedPriceWinsOverTheDiscount() {
        assertEquals(21_900, Promotion.salePrice(DiscountType.PERCENT, 50, 25_000, 21_900));
    }

    @Test
    void theStateFollowsTheClock() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 27, 12, 0);
        Promotion p = new Promotion();
        p.setStartsAt(now.minusHours(1));
        p.setEndsAt(now.plusHours(1));
        assertEquals(PromoState.LIVE, p.stateAt(now));
        assertEquals(PromoState.SCHEDULED, p.stateAt(now.minusHours(2)));
        assertEquals(PromoState.ENDED, p.stateAt(now.plusHours(1)));
        p.setActive(false);
        assertEquals(PromoState.PAUSED, p.stateAt(now));
        assertEquals(PromoState.ENDED, p.stateAt(now.plusHours(2)));
    }
}
