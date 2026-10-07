package al.pcmania.web.site;

import al.pcmania.domain.Enums.UseCase;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BuildRequestForm {
    @NotBlank(message = "Shkruani emrin")
    @Size(min = 2, max = 120, message = "Emri duhet të ketë 2–120 karaktere")
    private String customerName;

    @NotBlank(message = "Shkruani numrin e telefonit")
    @Pattern(regexp = CheckoutForm.PHONE_REGEX, message = "Numri i telefonit nuk është i vlefshëm")
    private String customerPhone;

    @NotNull(message = "Shkruani buxhetin")
    @Min(value = 20000, message = "Buxheti minimal është 20.000 Lekë")
    @Max(value = 10000000, message = "Buxheti nuk është i vlefshëm")
    private Integer budgetLek;

    @NotNull(message = "Zgjidhni përdorimin")
    private UseCase useCase;

    @Size(max = 2000)
    private String notes;

    private String website;
}
