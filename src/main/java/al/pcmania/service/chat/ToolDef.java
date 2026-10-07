package al.pcmania.service.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record ToolDef(String name, String description, Map<String, ParamSpec> params, java.util.function.Function<JsonNode, Object> fn) {

    public record ParamSpec(String type, String description, List<String> enumValues, boolean required) {
        public static ParamSpec integer(String description) {
            return new ParamSpec("integer", description, null, false);
        }

        public static ParamSpec string(String description) {
            return new ParamSpec("string", description, null, false);
        }

        public static ParamSpec requiredString(String description) {
            return new ParamSpec("string", description, null, true);
        }

        public static ParamSpec oneOf(String description, List<String> values) {
            return new ParamSpec("string", description, values, false);
        }
    }

    public List<String> required() {
        return params.entrySet().stream().filter(e -> e.getValue().required()).map(Map.Entry::getKey).toList();
    }
}
