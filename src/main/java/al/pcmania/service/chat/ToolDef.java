package al.pcmania.service.chat;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * One tool the assistant may call, described once and neutrally: every provider translates this into
 * its own schema (Gemini function declarations, Anthropic JSON Schema tools) and nothing else differs.
 *
 * @param name        the function name the model calls
 * @param description what it does, for the model
 * @param params      the parameters, by name, in the order they should be listed
 * @param fn          runs the call; the result is serialised as JSON for the model
 */
public record ToolDef(String name, String description, Map<String, ParamSpec> params, java.util.function.Function<JsonNode, Object> fn) {

    /** One parameter: a JSON type ("string", "integer", "boolean"), optional allowed values, required or not. */
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
