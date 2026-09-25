package org.unlaxer.tinyexpression.lsp.p4;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4LanguageServer.CatalogEntry;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4LanguageServer.CatalogResolver;

/**
 * The tinyexpression language catalog (issue #201): {@code catalog/tinyexpression-catalog.json}
 * at the repository root, bundled into the server jar as {@value #RESOURCE}. It is the single
 * source of the user-facing language knowledge the server shows: variable descriptions (hover,
 * completion, TE022), function / keyword documentation and snippets (completion, hover), and
 * the TE / FI diagnostic texts with the rules that pick a TE code for a parse failure.
 *
 * <p>An override catalog (the VS Code setting {@code tinyExpressionP4Lsp.catalog.overridePath},
 * initialization option {@code catalogOverridePath}) may contain any subset of the sections;
 * its entries replace bundled entries with the same key (error code, function or keyword name,
 * variable group + name) and new entries are added. The playground edits and exports the same
 * file format.
 */
public final class CatalogProvider {

  /** Classpath location of the bundled catalog. */
  public static final String RESOURCE = "/tinyexpression-catalog.json";

  /** Localized text; {@link #text()} prefers Japanese, as the VSIX has always shown. */
  public record Text(String ja, String en) {
    public String text() {
      if (ja != null && !ja.isBlank()) return ja;
      return en == null ? "" : en;
    }

    static final Text EMPTY = new Text(null, null);
  }

  public record Variable(String name, String match, String separator, int minSuffixLength,
      String type, Text description, String context, String group) {

    boolean matches(String bareName) {
      if (!"prefixWithSuffix".equals(match)) return name.equals(bareName);
      String head = name + (separator == null ? "" : separator);
      return bareName.startsWith(head) && bareName.length() - head.length() >= minSuffixLength;
    }
  }

  public record FunctionDoc(String name, String kind, String signature, String returns,
      String snippet, Text description, List<String> examples) {}

  public record Keyword(String name, String snippet, Text description) {}

  public record NamedValue(String name, String type, Text description) {}

  public record ErrorCode(String code, String severity, Text message, Text fix,
      Map<String, Text> templates) {

    /** {@code [TE006] 文末のセミコロンが必要です。 修正例: 文の末尾に ; を追加}. */
    public String fullMessage() {
      return "[" + code + "] " + message.text() + " 修正例: " + fix.text();
    }

    /** The template {@code name} with {@code {placeholders}} filled in, or null. */
    public String template(String name, Map<String, String> values) {
      Text template = templates.get(name);
      if (template == null) return null;
      String out = template.text();
      for (Map.Entry<String, String> e : values.entrySet()) {
        out = out.replace("{" + e.getKey() + "}", e.getValue());
      }
      return out;
    }
  }

  /** One entry of {@code diagnosticRules}; see the catalog schema. */
  public record DiagnosticRule(String code, List<String> expected, List<String> hintContains,
      String snippetStartsWith, String leadingEndsWith, String snippetMatches,
      List<String> consumers) {

    boolean appliesToLsp() {
      return consumers.isEmpty() || consumers.contains("lsp");
    }
  }

  private static volatile CatalogProvider bundled;

  private final JsonObject json;
  private final List<Variable> variables;
  private final Map<String, FunctionDoc> functions;
  private final Map<String, Keyword> keywords;
  private final List<NamedValue> values;
  private final Map<String, ErrorCode> errorCodes;
  private final List<DiagnosticRule> diagnosticRules;
  private final String defaultErrorCode;
  private final String origin;

  private CatalogProvider(JsonObject json, String origin) {
    this.json = json;
    this.origin = origin;
    this.variables = List.copyOf(mapArray(json, "variables", CatalogProvider::variable));
    this.functions = index(mapArray(json, "functions", CatalogProvider::function), FunctionDoc::name);
    this.keywords = index(mapArray(json, "keywords", CatalogProvider::keyword), Keyword::name);
    this.values = List.copyOf(mapArray(json, "values", o -> new NamedValue(
        str(o, "name"), str(o, "type"), text(o, "description"))));
    this.errorCodes = index(mapArray(json, "errorCodes", CatalogProvider::errorCode), ErrorCode::code);
    this.diagnosticRules = List.copyOf(mapArray(json, "diagnosticRules", CatalogProvider::rule));
    String fallback = str(json, "defaultErrorCode");
    this.defaultErrorCode = fallback == null ? "TE020" : fallback;
  }

  // ── loading ──

  /** The catalog bundled into the server jar (cached). */
  public static CatalogProvider bundled() {
    CatalogProvider provider = bundled;
    if (provider == null) {
      synchronized (CatalogProvider.class) {
        provider = bundled;
        if (provider == null) {
          try (InputStream in = CatalogProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
              throw new IllegalStateException("bundled catalog " + RESOURCE + " is missing");
            }
            provider = new CatalogProvider(parse(new InputStreamReader(in, StandardCharsets.UTF_8)),
                "bundled:" + RESOURCE);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          bundled = provider;
        }
      }
    }
    return provider;
  }

  /** A complete catalog file. */
  public static CatalogProvider load(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return new CatalogProvider(parse(reader), path.toString());
    }
  }

  /** This catalog with the entries of the (possibly partial) catalog at {@code path} merged in. */
  public CatalogProvider withOverride(Path path) throws IOException {
    JsonObject override;
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      override = parse(reader);
    }
    return withOverride(override, path.toString());
  }

  CatalogProvider withOverride(JsonObject override, String overrideOrigin) {
    JsonObject merged = json.deepCopy();
    mergeArray(merged, override, "variables",
        o -> str(o, "group") + "/" + str(o, "match") + "/" + str(o, "name"));
    mergeArray(merged, override, "variableGroups", o -> str(o, "id"));
    mergeArray(merged, override, "functions", o -> str(o, "name"));
    mergeArray(merged, override, "keywords", o -> str(o, "name"));
    mergeArray(merged, override, "values", o -> str(o, "name"));
    mergeArray(merged, override, "externals", o -> str(o, "class") + "#" + str(o, "method")
        + "/" + (o.has("params") ? o.getAsJsonArray("params").size() : 0));
    mergeArray(merged, override, "errorCodes", o -> str(o, "code"));
    mergeArray(merged, override, "runtimeErrors", o -> str(o, "kind"));
    mergeArray(merged, override, "settings", o -> str(o, "scope") + "/" + str(o, "name"));
    if (override.has("diagnosticRules")) {
      merged.add("diagnosticRules", override.get("diagnosticRules").deepCopy());
    }
    if (override.has("defaultErrorCode")) {
      merged.add("defaultErrorCode", override.get("defaultErrorCode").deepCopy());
    }
    return new CatalogProvider(merged, origin + " + " + overrideOrigin);
  }

  private static JsonObject parse(Reader reader) {
    JsonElement element = JsonParser.parseReader(reader);
    if (!element.isJsonObject()) {
      throw new IllegalArgumentException("a catalog must be a JSON object");
    }
    return element.getAsJsonObject();
  }

  private static void mergeArray(JsonObject target, JsonObject override, String key,
      Function<JsonObject, String> keyOf) {
    if (!override.has(key) || !override.get(key).isJsonArray()) return;
    Map<String, JsonElement> byKey = new LinkedHashMap<>();
    if (target.has(key) && target.get(key).isJsonArray()) {
      for (JsonElement e : target.getAsJsonArray(key)) {
        if (e.isJsonObject()) byKey.put(keyOf.apply(e.getAsJsonObject()), e);
      }
    }
    for (JsonElement e : override.getAsJsonArray(key)) {
      if (e.isJsonObject()) byKey.put(keyOf.apply(e.getAsJsonObject()), e.deepCopy());
    }
    JsonArray out = new JsonArray();
    byKey.values().forEach(out::add);
    target.add(key, out);
  }

  // ── accessors ──

  public String origin() { return origin; }

  public List<Variable> variables() { return variables; }

  public Map<String, FunctionDoc> functions() { return functions; }

  public Map<String, Keyword> keywords() { return keywords; }

  public List<NamedValue> values() { return values; }

  public Map<String, ErrorCode> errorCodes() { return errorCodes; }

  public List<DiagnosticRule> diagnosticRules() { return diagnosticRules; }

  public String defaultErrorCode() { return defaultErrorCode; }

  /** Number of entries per top-level array section (for tests and diagnostics). */
  public Map<String, Integer> counts() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> e : json.entrySet()) {
      if (e.getValue().isJsonArray()) counts.put(e.getKey(), e.getValue().getAsJsonArray().size());
    }
    return counts;
  }

  /** The error code, or the default code's entry when {@code code} is unknown. */
  public ErrorCode errorCode(String code) {
    ErrorCode entry = errorCodes.get(code);
    return entry != null ? entry : errorCodes.get(defaultErrorCode);
  }

  /** Exact variable first, then a prefixWithSuffix variable whose prefix matches. */
  public Variable lookupVariable(String name) {
    String bare = name.startsWith("$") ? name.substring(1) : name;
    for (Variable v : variables) {
      if (!"prefixWithSuffix".equals(v.match()) && v.name().equals(bare)) return v;
    }
    for (Variable v : variables) {
      if ("prefixWithSuffix".equals(v.match()) && v.matches(bare)) return v;
    }
    return null;
  }

  /** Function by name; {@code .name} finds the dot-form method. */
  public FunctionDoc function(String name) {
    return functions.get(name);
  }

  /**
   * The TE code for a parse failure (LSP form): {@code hint} is the first display hint of the
   * parser ({@code Expected ';'} ...), {@code snippet} the stripped text at the failure,
   * {@code leading} the text before it.
   */
  public String resolveCode(String hint, String snippet, String leading) {
    for (DiagnosticRule rule : diagnosticRules) {
      if (!rule.appliesToLsp()) continue;
      if (!rule.expected().isEmpty()) {
        for (String token : rule.expected()) {
          if (hint.contains("'" + token + "'")) return rule.code();
        }
      } else if (!rule.hintContains().isEmpty()) {
        for (String token : rule.hintContains()) {
          if (hint.contains(token)) return rule.code();
        }
      } else if (rule.snippetStartsWith() != null) {
        if (snippet.startsWith(rule.snippetStartsWith())) return rule.code();
      } else if (rule.leadingEndsWith() != null) {
        if (leading.stripTrailing().endsWith(rule.leadingEndsWith())) return rule.code();
      } else if (rule.snippetMatches() != null) {
        if (snippet.matches(rule.snippetMatches())) return rule.code();
      }
    }
    return defaultErrorCode;
  }

  /** The catalog's variables as the generated server's {@link CatalogResolver}. */
  public CatalogResolver variableResolver() {
    List<CatalogEntry> entries = new ArrayList<>(variables.size());
    for (Variable v : variables) {
      entries.add(new CatalogEntry(v.name(), v.description().text(), v.context(), origin + "#" + v.group()));
    }
    List<CatalogEntry> view = Collections.unmodifiableList(entries);
    return new CatalogResolver() {
      @Override public List<CatalogEntry> listAll() { return view; }

      @Override public CatalogEntry lookup(String name) {
        Variable v = lookupVariable(name);
        if (v == null) return null;
        return new CatalogEntry(name, v.description().text(), v.context(), origin + "#" + v.group());
      }
    };
  }

  // ── JSON mapping ──

  private static <T> List<T> mapArray(JsonObject json, String key, Function<JsonObject, T> mapper) {
    List<T> out = new ArrayList<>();
    if (json.has(key) && json.get(key).isJsonArray()) {
      for (JsonElement e : json.getAsJsonArray(key)) {
        if (e.isJsonObject()) out.add(mapper.apply(e.getAsJsonObject()));
      }
    }
    return out;
  }

  private static <T> Map<String, T> index(List<T> items, Function<T, String> keyOf) {
    Map<String, T> out = new LinkedHashMap<>();
    for (T item : items) out.put(keyOf.apply(item), item);
    return Collections.unmodifiableMap(out);
  }

  private static String str(JsonObject o, String key) {
    JsonElement e = o.get(key);
    return e == null || e.isJsonNull() ? null : e.getAsString();
  }

  private static List<String> strings(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || e.isJsonNull()) return List.of();
    if (e.isJsonPrimitive()) return List.of(e.getAsString());
    List<String> out = new ArrayList<>();
    for (JsonElement item : e.getAsJsonArray()) out.add(item.getAsString());
    return List.copyOf(out);
  }

  private static Text text(JsonObject o, String key) {
    JsonElement e = o.get(key);
    if (e == null || e.isJsonNull()) return Text.EMPTY;
    if (e.isJsonPrimitive()) return new Text(e.getAsString(), null);
    JsonObject t = e.getAsJsonObject();
    return new Text(str(t, "ja"), str(t, "en"));
  }

  private static Variable variable(JsonObject o) {
    return new Variable(str(o, "name"), str(o, "match") == null ? "exact" : str(o, "match"),
        str(o, "separator"), o.has("minSuffixLength") ? o.get("minSuffixLength").getAsInt() : 0,
        str(o, "type"), text(o, "description"), str(o, "context") == null ? "" : str(o, "context"),
        str(o, "group"));
  }

  private static FunctionDoc function(JsonObject o) {
    return new FunctionDoc(str(o, "name"), str(o, "kind"), str(o, "signature"), str(o, "returns"),
        str(o, "snippet"), text(o, "description"), strings(o, "examples"));
  }

  private static Keyword keyword(JsonObject o) {
    return new Keyword(str(o, "name"), str(o, "snippet"), text(o, "description"));
  }

  private static ErrorCode errorCode(JsonObject o) {
    Map<String, Text> templates = new LinkedHashMap<>();
    if (o.has("templates") && o.get("templates").isJsonObject()) {
      for (String name : o.getAsJsonObject("templates").keySet()) {
        templates.put(name, text(o.getAsJsonObject("templates"), name));
      }
    }
    return new ErrorCode(str(o, "code"), str(o, "severity"), text(o, "message"), text(o, "fix"),
        Collections.unmodifiableMap(templates));
  }

  private static DiagnosticRule rule(JsonObject o) {
    return new DiagnosticRule(str(o, "code"), strings(o, "expected"), strings(o, "hintContains"),
        str(o, "snippetStartsWith"), str(o, "leadingEndsWith"), str(o, "snippetMatches"),
        strings(o, "consumers"));
  }
}
