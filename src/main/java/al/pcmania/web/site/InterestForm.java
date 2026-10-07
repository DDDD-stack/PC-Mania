package al.pcmania.web.site;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterestForm {

    @NotBlank(message = "Shkruani emrin")
    @Size(min = 2, max = 120, message = "Emri duhet të ketë 2–120 karaktere")
    private String customerName;

    @NotBlank(message = "Shkruani numrin e telefonit")
    @Pattern(regexp = CheckoutForm.PHONE_REGEX, message = "Numri i telefonit nuk është i vlefshëm")
    private String customerPhone;

    private String website;
}
