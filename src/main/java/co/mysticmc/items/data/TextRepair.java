package co.mysticmc.items.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;

/** Repairs UTF-8 text that the site has already decoded as Windows-1252. */
public final class TextRepair {
    private TextRepair() { }

    public static String fix(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length();) {
            int lead = byteValue(input.charAt(i));
            int length = lead >= 0xC2 && lead <= 0xDF ? 2 :
                    lead >= 0xE0 && lead <= 0xEF ? 3 :
                    lead >= 0xF0 && lead <= 0xF4 ? 4 : 0;
            if (length > 0 && i + length <= input.length()) {
                byte[] bytes = new byte[length];
                bytes[0] = (byte) lead;
                boolean valid = true;
                for (int n = 1; n < length; n++) {
                    int value = byteValue(input.charAt(i + n));
                    if (value < 0x80 || value > 0xBF) { valid = false; break; }
                    bytes[n] = (byte) value;
                }
                if (valid) {
                    String decoded = new String(bytes, StandardCharsets.UTF_8);
                    if (decoded.indexOf('\uFFFD') < 0) {
                        out.append(decoded);
                        i += length;
                        continue;
                    }
                }
            }
            out.append(input.charAt(i++));
        }
        return out.toString();
    }

    public static JsonElement fixJson(JsonElement element) {
        if (element == null || element.isJsonNull()) return element;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            return primitive.isString() ? new JsonPrimitive(fix(primitive.getAsString())) : element;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) result.add(fixJson(child));
            return result;
        }
        JsonObject result = new JsonObject();
        for (var field : element.getAsJsonObject().entrySet()) result.add(field.getKey(), fixJson(field.getValue()));
        return result;
    }

    /** Lore in the API can be JSON serialized a second time, including the blank line "". */
    public static JsonElement decodeComponentString(String value) {
        if (value.startsWith("{") || value.startsWith("[") || value.startsWith("\"")) {
            try { return JsonParser.parseString(value); }
            catch (RuntimeException ignored) { }
        }
        return new JsonPrimitive(value);
    }

    private static int byteValue(char value) {
        if (value <= 0xFF) return value;
        return switch (value) {
            case '\u20AC' -> 0x80; case '\u201A' -> 0x82; case '\u0192' -> 0x83;
            case '\u201E' -> 0x84; case '\u2026' -> 0x85; case '\u2020' -> 0x86;
            case '\u2021' -> 0x87; case '\u02C6' -> 0x88; case '\u2030' -> 0x89;
            case '\u0160' -> 0x8A; case '\u2039' -> 0x8B; case '\u0152' -> 0x8C;
            case '\u017D' -> 0x8E; case '\u2018' -> 0x91; case '\u2019' -> 0x92;
            case '\u201C' -> 0x93; case '\u201D' -> 0x94; case '\u2022' -> 0x95;
            case '\u2013' -> 0x96; case '\u2014' -> 0x97; case '\u02DC' -> 0x98;
            case '\u2122' -> 0x99; case '\u0161' -> 0x9A; case '\u203A' -> 0x9B;
            case '\u0153' -> 0x9C; case '\u017E' -> 0x9E; case '\u0178' -> 0x9F;
            default -> -1;
        };
    }
}
