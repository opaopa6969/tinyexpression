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
    /**
     * D-079: 構築済みの AST（{@code Session.remember}）を recipe 自身に持つ。以前は 1 parse ごとの
     * {@code IdentityHashMap<Recipe, Object>} だったが、recipe は 1 parse の中でしか生きないので
     * 表を引く（hash・resize）必要が無い。{@code built == UNBUILT} が未構築。
     */
    static final Object UNBUILT = new Object();
    Object built = UNBUILT;
    /** 自身だけを指す nodes 列と値（{@code Session.rule} の結果・list field の要素）。遅延して 1 回だけ作る。 */
    private List<Recipe> self; private Value value; private List<Value> selfValues;
    private static final String[] NO_NAMES = new String[0];
    @SuppressWarnings("rawtypes") private static final List[] NO_VALUES = new List[0];
    @SuppressWarnings("unchecked")
    public Recipe(String id, int start, int end, Map<String, List<Value>> fields, String fallback) {
        this(id, start, end, fields.isEmpty() ? NO_NAMES : fields.keySet().toArray(new String[0]), fields.isEmpty() ? NO_VALUES : lists(fields.size()), fallback);
        int i = 0;
        for (List<Value> list : fields.values()) values[i++] = list;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Value>[] lists(int size) { return new List[size]; }
    /** names and values are owned by the recipe after construction (callers pass fresh arrays). */
    public Recipe(String id, int start, int end, String[] names, List<Value>[] values, String fallback) {
        this.id = id; this.start = start; this.end = end; this.names = names; this.values = values; this.fallback = fallback;
    }
    /** field を持たない recipe（leaf fallback・recovery marker）。空の名前列と値列は共有する。 */
    @SuppressWarnings("unchecked")
    public static Recipe leaf(String id, int start, int end) { return new Recipe(id, start, end, NO_NAMES, NO_VALUES, null); }
    /** {@code List.of(this)}。 */
    public List<Recipe> self() { List<Recipe> result = self; if (result == null) self = result = List.of(this); return result; }
    /** {@code new Value(start, end, List.of(this))}。 */
    public Value value() { Value result = value; if (result == null) value = result = new Value(start, end, self()); return result; }
    /** {@code List.of(value())}。 */
    public List<Value> values() { List<Value> result = selfValues; if (result == null) selfValues = result = List.of(value()); return result; }
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
