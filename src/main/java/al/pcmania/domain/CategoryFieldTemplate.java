package al.pcmania.domain;

import al.pcmania.domain.Enums.FieldType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
public class CategoryFieldTemplate {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Category category;
    private String fieldKey;
    private String labelSq;
    private String labelEn;
    @Enumerated(EnumType.STRING)
    private FieldType dataType = FieldType.TEXT;
    private String unit;
    private String enumOptions;
    @Column(name = "is_required")
    private boolean required;
    private boolean showInListing;
    private boolean showInFilters;
    private int sortOrder;

    public List<String> options() {
        if (enumOptions == null || enumOptions.isBlank()) return List.of();
        try {
            return JSON.readValue(enumOptions, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
