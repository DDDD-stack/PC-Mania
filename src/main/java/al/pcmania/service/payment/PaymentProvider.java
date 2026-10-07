package al.pcmania.service.payment;

import org.springframework.stereotype.Component;

public interface PaymentProvider {

    boolean isEnabled();

    @Component
    class Disabled implements PaymentProvider {
        @Override
        public boolean isEnabled() {
            return false;
        }
    }
}
