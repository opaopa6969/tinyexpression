package org.unlaxer.tinyexpression.p4.ubnfc.generated.api;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
/** Canonical AST field order follows the generated record declaration. */
public final class Json {
    private final String source;
    private int at;
    private Json(String source) { this.source = source; }
    public static Object read(String source) {
        var parser = new Json(source);
        Object value = parser.value(0);
        parser.space();
        if (parser.at != source.length()) throw parser.error("trailing input");
        return value;
    }
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> result.put((String) key, item));
        return result;
    }
    public static List<?> array(Object value) {
        if (value instanceof List<?> list) return list;
        throw new IllegalArgumentException("Expected JSON array");
    }
    private Object value(int depth) {
        if (depth > 512) throw error("nesting exceeds 512");
        space();
        if (at == source.length()) throw error("missing value");
        char ch = source.charAt(at);
        if (ch == '"') return string();
        if (ch == '{') {
            at++;
            var result = new LinkedHashMap<String, Object>();
            space();
            if (take('}')) return result;
            do {
                space();
                if (at == source.length() || source.charAt(at) != '"') throw error("missing key");
                String key = string(); space(); require(':');
                if (result.containsKey(key)) throw error("duplicate key: " + key);
                result.put(key, value(depth + 1)); space();
                if (take('}')) return result;
                require(',');
            } while (true);
        }
        if (ch == '[') {
            at++;
            var result = new ArrayList<Object>(); space();
            if (take(']')) return result;
            do {
                result.add(value(depth + 1)); space();
                if (take(']')) return result;
                require(',');
            } while (true);
        }
        for (String word : List.of("true", "false", "null")) {
            if (source.startsWith(word, at)) {
                at += word.length();
                return word.equals("null") ? null : Boolean.valueOf(word);
            }
        }
        int start = at;
        take('-');
        if (!take('0')) { if (!digit()) throw error("invalid number"); while (digit()) at++; }
        if (take('.')) { if (!digit()) throw error("missing fraction"); while (digit()) at++; }
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            if (!digit()) throw error("missing exponent");
            while (digit()) at++;
        }
        try { return new BigDecimal(source.substring(start, at)); }
        catch (NumberFormatException e) { throw error("invalid number"); }
    }
    private String string() {
        require('"'); var out = new StringBuilder();
        while (at < source.length()) {
            char ch = source.charAt(at++);
            if (ch == '"') return out.toString();
            if (ch < 32) throw error("unescaped control character");
            if (ch == '\\') {
                if (at == source.length()) throw error("unfinished escape");
                char escape = source.charAt(at++);
                ch = switch (escape) {
                    case '"', '\\', '/' -> escape;
                    case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t';
                    case 'u' -> {
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            if (at == source.length()) throw error("unfinished unicode escape");
                            int digit = Character.digit(source.charAt(at++), 16);
                            if (digit < 0) throw error("invalid unicode escape");
                            code = code * 16 + digit;
                        }
                        yield (char) code;
                    }
                    default -> throw error("invalid escape");
                };
            }
            out.append(ch);
        }
        throw error("unfinished string");
    }
    private boolean digit() { return at < source.length() && source.charAt(at) >= '0' && source.charAt(at) <= '9'; }
    private void space() { while (at < source.length() && " \t\r\n".indexOf(source.charAt(at)) >= 0) at++; }
    private boolean take(char ch) { if (at < source.length() && source.charAt(at) == ch) { at++; return true; } return false; }
    private void require(char ch) { if (!take(ch)) throw error("expected " + ch); }
    private IllegalArgumentException error(String text) { return new IllegalArgumentException("JSON at " + at + ": " + text); }

    public static String canonical(ParseResult<?> result) { return write(canonicalValue(result)); }
    public static Object canonicalValue(ParseResult<?> result) { return value(result.ast(), result.nodeSpans()); }
    private static Object value(Object object, Map<Object, Span> spans) {
        if (object instanceof Optional<?> optional) return value(optional.orElse(null), spans);
        if (object instanceof List<?> list) return list.stream().map(v -> value(v, spans)).toList();
        if (object instanceof Enum<?> e) return e.name();
        if (object == null || !object.getClass().isRecord()) return object;
        Span span = spans.get(object);
        if (span == null) throw new IllegalArgumentException("Missing node span");
        var fields = new LinkedHashMap<String, Object>();
        try {
            for (var component : object.getClass().getRecordComponents()) {
                var original = component.getAnnotation(SourceName.class);
                fields.put(original == null ? component.getName() : original.value(), value(component.getAccessor().invoke(object), spans));
            }
        } catch (IllegalAccessException | InvocationTargetException error) { throw new IllegalStateException(error); }
        var node = new LinkedHashMap<String, Object>();
        var original = object.getClass().getAnnotation(SourceName.class);
        node.put("type", original == null ? object.getClass().getSimpleName() : original.value()); node.put("span", List.of(span.start(), span.end())); node.put("fields", fields);
        return node;
    }
    public static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) {
            var out = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                switch (ch) {
                    case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                    default -> { if (ch < 32 || Character.isSurrogate(ch)) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch); }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f))
            throw new IllegalArgumentException("Non-finite JSON number");
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream()
            .map(e -> write(e.getKey()) + ":" + write(e.getValue())).toList()) + "}";
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(Json::write).toList()) + "]";
        throw new IllegalArgumentException("Not JSON: " + value.getClass());
    }
}
