import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.StringSource;
import org.unlaxer.TokenPredicators;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.ResultType;
import org.unlaxer.tinyexpression.loader.FormulaInfoAdditionalFields;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlockParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoBlocksParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoElementOrCommentParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoElementParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoElementParser.KeyValue;
import org.unlaxer.tinyexpression.loader.FormulaInfoParser.Kind;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeBlockPolicy;

/**
 * Dumps what the hand-written Java FormulaInfo loader makes of each fixture, one JSON line per
 * fixture (issue #180). Used only by regenerate-formula-info-golden.sh; not part of the Maven
 * build. The Rust test tests/formula_info.rs compares its own loader with this golden.
 *
 * <ul>
 *   <li>{@code syntax}: FormulaInfoBlocksParser over the whole input, with the full-consumption
 *       check of FormulaInfoSourceDocument, and every block's KeyValue list (key, normalised
 *       value) as FormulaInfoElementParser.extract returns it.</li>
 *   <li>{@code loader}: FormulaInfoList.parse (siteId as the multi-tenancy attribute, checkKind
 *       or calculatorName as the name, P4_AST_EVALUATOR as the configured default backend so
 *       that compiling a formula applies the P4 semantics tinyexpression-rs implements), and
 *       the parsed fields of every FormulaInfo. Fields the
 *       loader regenerates by compiling (javaCode, byteCode, hashByByteCode) are not dumped.</li>
 *   <li>{@code evaluation}: each loaded formula evaluated once by the P4_AST_EVALUATOR backend
 *       (the semantics tinyexpression-rs runtime mirrors) on an empty context with the test
 *       externals registered.</li>
 * </ul>
 *
 * usage: FormulaInfoParityDriver <repo-root> <out.jsonl> <fixture>...
 */
public final class FormulaInfoParityDriver {
  static final ObjectMapper MAPPER = new ObjectMapper();
  static final String[] EXTERNAL_CLASSES = {"CheckDigits", "sample.v1.CheckAlphabets"};

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    JavaCodeBlockPolicy.setEnabled(true);
    try (BufferedWriter writer = Files.newBufferedWriter(Path.of(args[1]), StandardCharsets.UTF_8)) {
      for (int i = 2; i < args.length; i++) {
        String fixture = args[i];
        String text = Files.readString(root.resolve(fixture), StandardCharsets.UTF_8);
        ObjectNode row = MAPPER.createObjectNode();
        row.put("fixture", fixture);
        row.set("syntax", syntax(text));
        row.set("loader", loader(text));
        writer.write(MAPPER.writeValueAsString(row));
        writer.write('\n');
      }
    }
  }

  static int cp(String text) {
    return text.codePointCount(0, text.length());
  }

  static ObjectNode syntax(String text) {
    ObjectNode node = MAPPER.createObjectNode();
    try {
      FormulaInfoBlocksParser parser = new FormulaInfoBlocksParser();
      ParseContext context = new ParseContext(StringSource.createRootSource(text));
      Parsed parsed;
      try {
        parsed = parser.parse(context);
      } finally {
        context.close();
      }
      int consumed = parsed.isSucceeded() && parsed.getConsumed() != null
          ? cp(parsed.getConsumed().source.sourceAsString()) : -1;
      node.put("succeeded", parsed.isSucceeded());
      node.put("consumed", consumed);
      node.put("length", cp(text));
      boolean accepted = parsed.isSucceeded() && consumed == cp(text);
      node.put("accepted", accepted);
      if (!parsed.isSucceeded()) {
        return node;
      }
      ArrayNode blocks = node.putArray("blocks");
      TypedToken<FormulaInfoBlocksParser> rootToken =
          parsed.getRootToken().typed(FormulaInfoBlocksParser.class);
      for (TypedToken<FormulaInfoBlockParser> block
          : rootToken.getChildrenWithParserAsListTyped(FormulaInfoBlockParser.class)) {
        ObjectNode blockNode = blocks.addObject();
        ArrayNode entries = blockNode.putArray("entries");
        for (Token token : FormulaInfoElementOrCommentParser.elements(block)) {
          if (!(token.parser instanceof FormulaInfoElementParser)) {
            continue;
          }
          TypedToken<FormulaInfoElementParser> element = token.typed(FormulaInfoElementParser.class);
          ObjectNode entry = entries.addObject();
          Token raw = element.getChild(TokenPredicators.hasTag(Kind.value.tag()));
          entry.put("rawValue", raw.getToken().orElse(null));
          try {
            KeyValue keyValue = element.getParser().extract(element);
            entry.put("key", keyValue.getKey());
            entry.put("value", keyValue.getValue());
          } catch (Throwable failure) {
            entry.put("extractError", failure.getClass().getSimpleName());
          }
        }
      }
    } catch (Throwable failure) {
      node.put("exception", failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }
    return node;
  }

  static FormulaInfoAdditionalFields additionalFields() {
    return new FormulaInfoAdditionalFields("siteId", formulaInfo -> {
      String checkKind = formulaInfo.extraValueByKey.get("checkKind");
      return checkKind != null ? checkKind : formulaInfo.calculatorName;
    }).setExecutionBackend(ExecutionBackend.P4_AST_EVALUATOR);
  }

  static ObjectNode loader(String text) {
    ObjectNode node = MAPPER.createObjectNode();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    var parsed = FormulaInfoList.parse(text, additionalFields(), classLoader);
    if (parsed.throwable.isPresent()) {
      Throwable failure = parsed.throwable.get();
      node.put("ok", false);
      node.put("error", failure.getClass().getSimpleName());
      node.put("message", String.valueOf(failure.getMessage()));
      return node;
    }
    node.put("ok", true);
    ArrayNode infos = node.putArray("infos");
    for (FormulaInfo info : parsed.get().get()) {
      ObjectNode out = infos.addObject();
      out.put("name", info.getName());
      out.put("calculatorName", info.calculatorName);
      out.put("description", info.description);
      ArrayNode tags = out.putArray("tags");
      info.tags.forEach(tags::add);
      out.put("periodStartInclusive", info.periodStartInclusive);
      out.put("periodEndExclusive", info.periodEndExclusive);
      out.put("multiTenancyId", info.multiTenancyId.orElse(null));
      out.put("dependsOn", info.dependsOn);
      out.put("resultType", typeName(info.resultType));
      out.put("numberType", typeName(info.numberType));
      out.put("executionBackend", info.executionBackend);
      out.put("formulaText", info.formulaText);
      out.put("hash", info.hash);
      out.put("className", info.className);
      out.put("classNameWithHash", info.classNameWithHash);
      ObjectNode extra = out.putObject("extraValueByKey");
      for (Map.Entry<String, String> entry : info.extraValueByKey.entrySet()) {
        extra.put(entry.getKey(), entry.getValue());
      }
      out.set("evaluation", evaluate(info, classLoader));
    }
    return node;
  }

  static String typeName(Object type) {
    if (type == null) {
      return null;
    }
    return ((ResultType) type).javaType().getTypeName();
  }

  static ObjectNode evaluate(FormulaInfo info, ClassLoader classLoader) {
    ObjectNode result = MAPPER.createObjectNode();
    Calculator calculator;
    try {
      calculator = CalculatorCreatorRegistry.p4AstEvaluatorCreator().create(
          new Source(info.formulaText), "FormulaInfoParity_" + info.getName(),
          new SpecifiedExpressionTypes(info.resultType, info.numberType), classLoader);
    } catch (Throwable failure) {
      error(result, "create", failure);
      return result;
    }
    CalculationContext context = CalculationContext.newContext();
    for (String className : EXTERNAL_CLASSES) {
      try {
        Class<?> clazz = Class.forName(className, true, classLoader);
        context.setObject(clazz.getName(), clazz.getDeclaredConstructor().newInstance());
      } catch (ReflectiveOperationException absent) {
        // not loadable: the evaluator reports the class itself
      }
    }
    try {
      value(result, calculator.apply(context));
    } catch (Throwable failure) {
      error(result, "apply", failure);
    }
    return result;
  }

  static void value(ObjectNode result, Object value) {
    if (value == null) {
      result.put("kind", "null");
    } else if (value instanceof Float f) {
      result.put("kind", "float");
      result.put("bits", String.format("%08x", Float.floatToRawIntBits(f)));
      result.put("text", f.toString());
    } else if (value instanceof Double d) {
      result.put("kind", "double");
      result.put("bits", String.format("%016x", Double.doubleToRawLongBits(d)));
      result.put("text", d.toString());
    } else if (value instanceof Integer || value instanceof Long || value instanceof Short
        || value instanceof Byte) {
      result.put("kind", value.getClass().getSimpleName().toLowerCase().replace("integer", "int"));
      result.put("text", value.toString());
    } else if (value instanceof Boolean b) {
      result.put("kind", "boolean");
      result.put("text", b.toString());
    } else if (value instanceof String s) {
      result.put("kind", "string");
      result.put("text", s);
    } else {
      result.put("kind", "object");
      result.put("text", value.getClass().getName());
    }
  }

  static void error(ObjectNode result, String stage, Throwable failure) {
    result.put("kind", "error");
    result.put("stage", stage);
    result.put("text", failure.getClass().getSimpleName());
  }
}
