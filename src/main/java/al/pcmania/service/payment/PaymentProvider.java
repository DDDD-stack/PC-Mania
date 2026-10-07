package al.pcmania.service.payment;

import org.springframework.stereotype.Component;

/**
 * Card payment on the site. Deliberately empty: the shop is waiting for its bank's API, and the shape
 * of a payment call - a redirect, a hosted form, a direct charge, what a callback carries - is the
 * bank's to dictate, not ours to guess. Writing that shape now would almost certainly be rebuilt.
 *
 * What exists here is the seam. {@link Disabled} answers false, so checkout shows the card option
 * greyed out and refuses it if anything posts it anyway. When the bank sends their documentation the
 * work is one class implementing this interface, and this one is deleted.
 */
public interface PaymentProvider {

    /** Whether cards can actually be charged right now. False until the bank's integration exists. */
    boolean isEnabled();

    /** The only implementation today: no bank, no charges. */
    @Component
    class Disabled implements PaymentProvider {
        @Override
        public boolean isEnabled() {
            return false;
        }
    }
}
