package al.pcmania.web.site;

import al.pcmania.domain.Enums.DeliveryMethod;
import al.pcmania.domain.Enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CheckoutForm {
    public static final String PHONE_REGEX = "^\\+?[0-9 ()./-]{8,20}$";

    @NotBlank(message = "Shkruani emrin")
    @Size(min = 2, max = 120, message = "Emri duhet të ketë 2–120 karaktere")
    private String customerName;

    @NotBlank(message = "Shkruani numrin e telefonit")
    @Pattern(regexp = PHONE_REGEX, message = "Numri i telefonit nuk është i vlefshëm")
    private String customerPhone;

    @Email(message = "Email-i nuk është i vlefshëm")
    @Size(max = 160)
    private String customerEmail;

    @NotBlank(message = "Shkruani qytetin")
    @Size(max = 80)
    private String city;

    @Size(max = 255)
    private String address;

    @Size(max = 1000)
    private String notes;

    @NotNull
    private DeliveryMethod deliveryMethod = DeliveryMethod.COURIER;

    @NotNull
    private PaymentMethod paymentMethod = PaymentMethod.CASH_ON_DELIVERY;

    @Min(1)
    @Max(10)
    private int quantity = 1;

    /** Honeypot: hidden from humans, bots tend to fill it. */
    private String website;
}
