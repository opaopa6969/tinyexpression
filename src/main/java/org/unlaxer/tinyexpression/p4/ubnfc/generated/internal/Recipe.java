package org.unlaxer.tinyexpression.p4.ubnfc.generated.internal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Immutable construction commands. No public AST or numeric conversion during recognition. Fields are few, so a parallel array beats a map. */
public final class Recipe {
    private final String id, fallback;
    private final int start, end;
    private final String[] names;
    private final List<Value>[] values;
    public Recipe(String id, int start, int end, Map<String, List<Value>> fields, String fallback) {
        this(id, start, end, fields.keySet().toArray(new String[0]), lists(fields.size()), fallback);
        int i = 0;
        for (List<Value> list : fields.values()) values[i++] = list;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Value>[] lists(int size) { return new List[size]; }
    /** names and values are owned by the recipe after construction (callers pass fresh arrays). */
    public Recipe(String id, int start, int end, String[] names, List<Value>[] values, String fallback) {
        this.id = id; this.start = start; this.end = end; this.names = names; this.values = values; this.fallback = fallback;
    }
    public String id() { return id; }
    public int start() { return start; }
    public int end() { return end; }
    public String fallback() { return fallback; }
    public Map<String, List<Value>> fields() {
        var map = new LinkedHashMap<String, List<Value>>();
        for (int i = 0; i < names.length; i++) map.put(names[i], values[i]);
        return Map.copyOf(map);
    }
    /** Values of a field in insertion order; an unmapped field is empty. */
    public List<Value> field(String name) {
        for (int i = 0; i < names.length; i++) if (names[i] == name || names[i].equals(name)) return values[i];
        return List.of();
    }
    public int fieldCount() { return names.length; }
    public List<Value> fieldValues(int index) { return values[index]; }
    /** elements retains transparent helper values separately from the enclosing capture extent. */
    public record Value(int start, int end, List<Recipe> nodes, String text, List<Value> elements) {
        public Value(int start, int end, List<Recipe> nodes, String text) { this(start, end, nodes, text, null); }
        // nodes / elements は Session の中だけで組み立てられ、構築後は変更しない（Match の list と
        // 同じ約束）。防御的複製は 1 parse あたり数千回の全複写になるので行わない。
        public Value(int start, int end, List<Recipe> nodes) { this(start, end, nodes, null); }
    }
}
