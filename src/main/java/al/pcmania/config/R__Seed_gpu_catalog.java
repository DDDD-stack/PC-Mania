package al.pcmania.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Loads {@code data/gpu-catalog.json} into {@code gpu_catalog}, upserting on slug, as a repeatable
 * Flyway migration. Spring Boot hands every {@code JavaMigration} bean to Flyway, and a repeatable
 * migration runs again whenever its checksum changes: the checksum here is the JSON's, so editing
 * the file and deploying updates the rows, while rows the file does not mention (added in the admin)
 * are left alone. Columns the file leaves out are reset to the file's values on each run, which is
 * the point: the file is the reference, the admin is for corrections between releases.
 */
@Component
public class R__Seed_gpu_catalog extends BaseJavaMigration {

    static final String RESOURCE = "data/gpu-catalog.json";

    private static final List<String> COLUMNS = List.of("slug", "name", "vendor", "aliases", "release_year",
            "architecture", "vram_gb", "memory_type", "memory_bus_bits", "tdp_watts", "psu_min_watts", "pcie_connectors",
            "length_mm", "tier", "supports_dlss", "frame_generation", "supports_fsr", "ray_tracing", "driver_status",
            "mining_risk", "fps_esports_1080p", "fps_aaa_1080p", "fps_aaa_1440p", "notes_sq");
    private static final List<String> FIELDS = List.of("slug", "name", "vendor", "aliases", "releaseYear",
            "architecture", "vramGb", "memoryType", "memoryBusBits", "tdpWatts", "psuMinWatts", "pcieConnectors",
            "lengthMm", "tier", "supportsDlss", "frameGeneration", "supportsFsr", "rayTracing", "driverStatus",
            "miningRisk", "fpsEsports1080p", "fpsAaa1080p", "fpsAaa1440p", "notesSq");

    private final byte[] json;

    public R__Seed_gpu_catalog() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            json = in.readAllBytes();
        }
    }

    @Override
    public Integer getChecksum() {
        CRC32 crc = new CRC32();
        crc.update(json);
        return (int) crc.getValue();
    }

    @Override
    public void migrate(Context context) throws Exception {
        JsonNode rows = new ObjectMapper().readTree(json);
        if (!rows.isArray()) throw new IllegalStateException(RESOURCE + " must hold a JSON array of catalogue rows");
        Connection c = context.getConnection();
        boolean postgres = c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres");
        try (PreparedStatement ps = c.prepareStatement(upsertSql(postgres))) {
            for (JsonNode row : rows) {
                for (int i = 0; i < FIELDS.size(); i++) bind(ps, i + 1, row.get(FIELDS.get(i)));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    static String upsertSql(boolean postgres) {
        String cols = String.join(", ", COLUMNS);
        String marks = String.join(", ", COLUMNS.stream().map(x -> "?").toList());
        StringBuilder sql = new StringBuilder("INSERT INTO gpu_catalog (").append(cols).append(") VALUES (").append(marks).append(")");
        if (postgres) {
            sql.append(" ON CONFLICT (slug) DO UPDATE SET ");
            sql.append(String.join(", ", COLUMNS.stream().skip(1).map(x -> x + " = EXCLUDED." + x).toList()));
        } else {
            sql.append(" ON DUPLICATE KEY UPDATE ");
            sql.append(String.join(", ", COLUMNS.stream().skip(1).map(x -> x + " = VALUES(" + x + ")").toList()));
        }
        return sql.toString();
    }

    private static void bind(PreparedStatement ps, int index, JsonNode v) throws java.sql.SQLException {
        if (v == null || v.isNull()) ps.setNull(index, Types.NULL);
        else if (v.isBoolean()) ps.setBoolean(index, v.booleanValue());
        else if (v.isInt()) ps.setInt(index, v.intValue());
        else ps.setString(index, v.asText());
    }
}
