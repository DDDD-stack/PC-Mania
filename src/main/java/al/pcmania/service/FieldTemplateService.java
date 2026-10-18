package al.pcmania.service;

import al.pcmania.config.CacheConfig;
import al.pcmania.domain.Category;
import al.pcmania.domain.CategoryFieldTemplate;
import al.pcmania.domain.Enums.FieldType;
import al.pcmania.repo.CategoryRepository;
import al.pcmania.repo.FieldTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FieldTemplateService {

    public record Field(Long id, String key, String labelSq, String labelEn, FieldType type, String unit,
                        List<String> options, boolean required, boolean showInListing, boolean showInFilters, int sortOrder) {

        static Field of(CategoryFieldTemplate t) {
            return new Field(t.getId(), t.getFieldKey(), t.getLabelSq(), t.getLabelEn(), t.getDataType(), t.getUnit(),
                    t.options(), t.isRequired(), t.isShowInListing(), t.isShowInFilters(), t.getSortOrder());
        }

        public String display(String raw) {
            return FieldTemplateService.display(this, raw);
        }
    }

    public record Template(String categorySlug, String categoryName, String catalogKey, boolean prebuilt, List<Field> fields) {

        public Optional<Field> field(String key) {
            return fields.stream().filter(f -> f.key().equals(key)).findFirst();
        }

        public String label(String key) {
            return field(key).map(Field::labelSq).orElse(key);
        }

        public String display(String key, String raw) {
            return field(key).map(f -> f.display(raw)).orElse(raw);
        }
    }

    public record Spec(String key, String value) {}

    public record Cleaned(List<Spec> specs, List<String> errors) {}

    public record FieldForm(String fieldKey, String labelSq, String labelEn, FieldType dataType, String unit,
                            String enumOptions, boolean required, boolean showInListing, boolean showInFilters, Integer sortOrder) {}

    static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,59}$");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d+$");
    private static final Pattern GROUPED = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final int MAX_VALUE = 160;

    private final FieldTemplateRepository repo;
    private final CategoryRepository categories;
    private final ObjectMapper json;

    @Lazy
    @Autowired
    private FieldTemplateService self;

    @Cacheable(CacheConfig.FIELD_TEMPLATES)
    @Transactional(readOnly = true)
    public Map<String, Template> all() {
        Map<String, List<Field>> bySlug = new LinkedHashMap<>();
        for (CategoryFieldTemplate t : repo.findAllWithCategory()) {
            bySlug.computeIfAbsent(t.getCategory().getSlug(), k -> new ArrayList<>()).add(Field.of(t));
        }
        Map<String, Template> out = new LinkedHashMap<>();
        for (Category c : categories.findAllByOrderBySortOrderAsc()) {
            out.put(c.getSlug(), new Template(c.getSlug(), c.getNameSq(), c.catalogKey(), c.isPrebuilt(), List.copyOf(bySlug.getOrDefault(c.getSlug(), List.of()))));
        }
        return Collections.unmodifiableMap(out);
    }

    public Template forCategory(String categorySlug) {
        Template t = categorySlug == null ? null : self.all().get(categorySlug);
        return t != null ? t : new Template(categorySlug, categorySlug, categorySlug, false, List.of());
    }

    public String json() {
        try {
            return json.writeValueAsString(self.all()).replace("</", "<\\/");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Cleaned clean(String categorySlug, List<String> keys, List<String> values, boolean enforceRequired) {
        Template template = forCategory(categorySlug);
        Map<String, String> templateValues = new HashMap<>();
        List<Spec> extra = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = trimToNull(keys.get(i));
            String value = i < values.size() ? trimToNull(values.get(i)) : null;
            if (key == null || value == null || !seen.add(key)) continue;
            Optional<Field> field = template.field(key);
            if (field.isEmpty()) {
                if (key.length() > 80) {
                    errors.add("Çelësi \"" + key.substring(0, 20) + "…\" është shumë i gjatë.");
                    continue;
                }
                extra.add(new Spec(key, truncate(value)));
                continue;
            }
            try {
                templateValues.put(key, normalize(field.get(), value));
            } catch (IllegalArgumentException e) {
                errors.add(field.get().labelSq() + ": " + e.getMessage());
            }
        }
        List<Spec> out = new ArrayList<>();
        for (Field f : template.fields()) {
            String v = templateValues.get(f.key());
            if (v != null) out.add(new Spec(f.key(), v));
            else if (enforceRequired && f.required()) errors.add(f.labelSq() + " është e detyrueshme.");
        }
        out.addAll(extra);
        return new Cleaned(out, errors);
    }

    static String normalize(Field f, String raw) {
        String v = withoutUnit(f, raw.trim());
        return switch (f.type()) {
            case TEXT -> truncate(v);
            case INTEGER -> {
                String n = v.replace(" ", "");
                if (GROUPED.matcher(n).matches()) n = n.replace(".", "");
                if (!INTEGER.matcher(n).matches()) throw new IllegalArgumentException("shkruani një numër të plotë.");
                yield String.valueOf(Long.parseLong(n));
            }
            case DECIMAL -> {
                String n = v.replace(" ", "").replace(',', '.');
                if (!DECIMAL.matcher(n).matches()) throw new IllegalArgumentException("shkruani një numër (p.sh. 3,5).");
                yield n;
            }
            case BOOLEAN -> switch (v.toLowerCase(Locale.ROOT)) {
                case "true", "po", "1", "yes" -> "true";
                case "false", "jo", "0", "no" -> "false";
                default -> throw new IllegalArgumentException("zgjidhni Po ose Jo.");
            };
            case ENUM -> f.options().stream().filter(o -> o.equalsIgnoreCase(v)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("zgjidhni një nga vlerat e listës."));
        };
    }

    private static String withoutUnit(Field f, String v) {
        if (f.unit() == null || f.type() == FieldType.TEXT || f.type() == FieldType.ENUM) return v;
        String unit = f.unit().toLowerCase(Locale.ROOT);
        return v.toLowerCase(Locale.ROOT).endsWith(unit) ? v.substring(0, v.length() - unit.length()).trim() : v;
    }

    static String display(Field f, String raw) {
        if (raw == null) return null;
        String v = switch (f.type()) {
            case BOOLEAN -> "true".equals(raw) ? "Po" : "false".equals(raw) ? "Jo" : raw;
            case DECIMAL -> raw.replace('.', ',');
            case INTEGER -> INTEGER.matcher(raw).matches() && raw.length() > 4 ? al.pcmania.web.Fmt.grouped(Long.parseLong(raw)) : raw;
            default -> raw;
        };
        boolean withUnit = f.unit() != null && !f.unit().isBlank() && f.type() != FieldType.BOOLEAN && f.type() != FieldType.ENUM;
        return withUnit ? v + " " + f.unit() : v;
    }

    @CacheEvict(cacheNames = CacheConfig.FIELD_TEMPLATES, allEntries = true)
    @Transactional
    public CategoryFieldTemplate add(Long categoryId, FieldForm form) {
        Category c = categories.findById(categoryId).orElseThrow(NotFoundException::new);
        String key = form.fieldKey() == null ? "" : form.fieldKey().trim().toLowerCase(Locale.ROOT);
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Çelësi duhet të fillojë me shkronjë dhe të ketë vetëm a–z, 0–9 dhe _ (p.sh. vram_gb).");
        }
        if (repo.existsByCategoryIdAndFieldKey(categoryId, key)) throw new IllegalArgumentException("Kjo kategori e ka tashmë fushën \"" + key + "\".");
        CategoryFieldTemplate t = new CategoryFieldTemplate();
        t.setCategory(c);
        t.setFieldKey(key);
        apply(t, form);
        if (form.sortOrder() == null) {
            t.setSortOrder(repo.findByCategory(categoryId).stream().mapToInt(CategoryFieldTemplate::getSortOrder).max().orElse(0) + 1);
        }
        return repo.save(t);
    }

    @CacheEvict(cacheNames = CacheConfig.FIELD_TEMPLATES, allEntries = true)
    @Transactional
    public CategoryFieldTemplate update(Long id, FieldForm form) {
        CategoryFieldTemplate t = repo.findById(id).orElseThrow(NotFoundException::new);
        apply(t, form);
        return t;
    }

    @CacheEvict(cacheNames = CacheConfig.FIELD_TEMPLATES, allEntries = true)
    @Transactional
    public Long delete(Long id) {
        CategoryFieldTemplate t = repo.findById(id).orElseThrow(NotFoundException::new);
        Long categoryId = t.getCategory().getId();
        repo.delete(t);
        return categoryId;
    }

    @Transactional(readOnly = true)
    public List<CategoryFieldTemplate> rows(Long categoryId) {
        return repo.findByCategory(categoryId);
    }

    private void apply(CategoryFieldTemplate t, FieldForm form) {
        if (!StringUtils.hasText(form.labelSq())) throw new IllegalArgumentException("Shkruani emrin e fushës në shqip.");
        if (form.dataType() == null) throw new IllegalArgumentException("Zgjidhni llojin e fushës.");
        t.setLabelSq(form.labelSq().trim());
        t.setLabelEn(trimToNull(form.labelEn()));
        t.setDataType(form.dataType());
        t.setUnit(trimToNull(form.unit()));
        if (form.dataType() == FieldType.ENUM) {
            List<String> options = Arrays.stream((form.enumOptions() == null ? "" : form.enumOptions()).split("[,\\n]"))
                    .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
            if (options.size() < 2) throw new IllegalArgumentException("Një listë zgjedhjesh ka nevojë për të paktën dy vlera, të ndara me presje.");
            try {
                t.setEnumOptions(json.writeValueAsString(options));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            t.setEnumOptions(null);
        }
        t.setRequired(form.required());
        t.setShowInListing(form.showInListing());
        t.setShowInFilters(form.showInFilters());
        if (form.sortOrder() != null) t.setSortOrder(form.sortOrder());
    }

    private static String truncate(String s) {
        return s.length() > MAX_VALUE ? s.substring(0, MAX_VALUE) : s;
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
