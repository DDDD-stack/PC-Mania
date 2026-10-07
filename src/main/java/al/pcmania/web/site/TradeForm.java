package al.pcmania.web.site;

import al.pcmania.domain.Enums.ContactMethod;
import al.pcmania.domain.Enums.TradeItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TradeForm {
    @NotBlank(message = "Shkruani emrin")
    @Size(min = 2, max = 120, message = "Emri duhet të ketë 2–120 karaktere")
    private String customerName;

    @NotNull(message = "Zgjidhni si t'ju kontaktojmë")
    private ContactMethod contactMethod = ContactMethod.PHONE;

    @Size(max = 30)
    private String customerPhone;

    @Size(max = 160)
    private String customerEmail;

    @NotNull(message = "Zgjidhni llojin e pjesës")
    private TradeItemType itemType;

    @NotBlank(message = "Shkruani prodhuesin")
    @Size(max = 80)
    private String manufacturer;

    @NotBlank(message = "Shkruani modelin")
    @Size(max = 120)
    private String model;

    @Size(max = 1000)
    private String extraNotes;

    private boolean whatsappInstead;

    private String website;
}
