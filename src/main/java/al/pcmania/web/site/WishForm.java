package al.pcmania.web.site;

import al.pcmania.domain.Enums.Condition;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class WishForm {
    @NotBlank(message = "Shkruani çfarë po kërkoni")
    @Size(min = 3, max = 200, message = "Përshkrimi duhet të ketë 3–200 karaktere")
    private String item;

    @Min(value = 1000, message = "Buxheti nuk është i vlefshëm")
    @Max(value = 10000000, message = "Buxheti nuk është i vlefshëm")
    private Integer maxPriceLek;

    /** Null: any condition. */
    private Condition condition;

    @Size(max = 1000)
    private String notes;

    @NotBlank(message = "Shkruani emrin")
    @Size(min = 2, max = 120, message = "Emri duhet të ketë 2–120 karaktere")
    private String customerName;

    @NotBlank(message = "Shkruani numrin e telefonit")
    @Pattern(regexp = CheckoutForm.PHONE_REGEX, message = "Numri i telefonit nuk është i vlefshëm")
    private String customerPhone;

    /** Honeypot. */
    private String website;
}
