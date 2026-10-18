package al.pcmania.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@Setter
public class SpecCatalogEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String categorySlug;
    private String name;
    private String aliases;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> specJson = new LinkedHashMap<>();
    private boolean verified;
    private Long gpuCatalogId;

    public List<String> aliasList() {
        List<String> out = new ArrayList<>();
        if (aliases == null) return out;
        for (String a : aliases.split(",")) {
            String t = a.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
