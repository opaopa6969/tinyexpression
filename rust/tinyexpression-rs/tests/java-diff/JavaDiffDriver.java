import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreator;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Evaluates the differential corpus (build_corpus.py output) on the Java side and prints one
 * JSON outcome per row. Used only by regenerate-java-golden.sh; not part of the Maven build.
 *
 * usage: JavaDiffDriver <corpus.jsonl> <out.jsonl> <p4ast|dsljava> [firstRow] [rowCount]
 */
public final class JavaDiffDriver {
  static final String[] EXTERNAL_CLASSES = {
      "org.unlaxer.tinyexpression.Fee",
      "org.unlaxer.tinyexpression.parser.TestSideEffector",
      "org.unlaxer.tinyexpression.parser.AdmissionFee",
      "CheckDigits",
      "sample.v1.CheckAlphabets",
  };
  static final ObjectMapper MAPPER = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    Path corpus = Path.of(args[0]);
    Path out = Path.of(args[1]);
    String backend = args[2];
    int first = args.length > 3 ? Integer.parseInt(args[3]) : 0;
    int count = args.length > 4 ? Integer.parseInt(args[4]) : Integer.MAX_VALUE;
    CalculatorCreator creator = switch (backend) {
      case "p4ast" -> CalculatorCreatorRegistry.p4AstEvaluatorCreator();
      case "dsljava" -> CalculatorCreatorRegistry.dslJavaCodeCreator();
      default -> throw new IllegalArgumentException(backend);
    };
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    Map<String, Object> calculators = new LinkedHashMap<>();
    int index = 0;
    try (BufferedReader reader = Files.newBufferedReader(corpus, StandardCharsets.UTF_8);
        BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (index++ < first) continue;
        if (index - first > count) break;
        JsonNode row = MAPPER.readTree(line);
        String formula = row.get("formula").asText();
        String resultType = row.get("resultType").asText();
        String numberType = row.get("numberType").isNull() ? null : row.get("numberType").asText();
        String key = resultType + "\u0000" + numberType + "\u0000" + formula;
        Object calculator = calculators.get(key);
        if (calculator == null) {
          try {
            calculator = creator.create(new Source(formula), "JavaDiff_" + calculators.size(),
                new SpecifiedExpressionTypes(type(resultType),
                    numberType == null ? ExpressionTypes._float : type(numberType)), loader);
          } catch (Throwable failure) {
            calculator = failure;
          }
          if (calculators.size() > 64) calculators.clear();
          calculators.put(key, calculator);
        }
        ObjectNode result = MAPPER.createObjectNode();
        result.put("id", row.get("id").asText());
        if (calculator instanceof Throwable failure) {
          error(result, "create", failure);
        } else {
          CalculationContext context = CalculationContext.newContext();
          for (JsonNode variable : row.get("vars")) {
            bind(context, variable.get(0).asText(), variable.get(1).asText(),
                variable.get(2).asText(), variable.get(3).asText());
          }
          if (row.get("externals").asBoolean()) {
            for (String className : EXTERNAL_CLASSES) {
              try {
                Class<?> clazz = Class.forName(className, true, loader);
                context.setObject(clazz.getName(), clazz.getDeclaredConstructor().newInstance());
              } catch (ReflectiveOperationException absent) {
                // not loadable: the evaluator reports the class itself
              }
            }
          }
          try {
            value(result, ((Calculator) calculator).apply(context));
          } catch (Throwable failure) {
            error(result, "apply", failure);
          }
        }
        writer.write(MAPPER.writeValueAsString(result));
        writer.write('\n');
      }
    }
  }

  static ExpressionType type(String name) {
    return switch (name) {
      case "float" -> ExpressionTypes._float;
      case "double" -> ExpressionTypes._double;
      case "int" -> ExpressionTypes._int;
      case "long" -> ExpressionTypes._long;
      case "short" -> ExpressionTypes._short;
      case "byte" -> ExpressionTypes._byte;
      case "boolean" -> ExpressionTypes._boolean;
      case "string" -> ExpressionTypes.string;
      case "object" -> ExpressionTypes.object;
      default -> throw new IllegalArgumentException(name);
    };
  }

  static void bind(CalculationContext context, String map, String type, String name, String raw) {
    Object value = switch (type) {
      case "float" -> Float.parseFloat(raw);
      case "double" -> Double.parseDouble(raw);
      case "int" -> Integer.parseInt(raw);
      case "long" -> Long.parseLong(raw);
      case "boolean" -> Boolean.parseBoolean(raw);
      default -> raw;
    };
    switch (map) {
      case "number" -> context.set(name, (Number) value);
      case "string" -> context.set(name, (String) value);
      case "boolean" -> context.set(name, (boolean) (Boolean) value);
      default -> context.setObject(name, value);
    }
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
    } else if (value instanceof BigDecimal || value instanceof BigInteger) {
      result.put("kind", value.getClass().getSimpleName().toLowerCase());
      result.put("text", value.toString());
    } else if (value instanceof Boolean b) {
      result.put("kind", "boolean");
      result.put("text", b.toString());
    } else if (value instanceof String s) {
      result.put("kind", "string");
      String lossy = lossy(s);
      result.put("text", lossy);
      if (!lossy.equals(s)) {
        result.put("loneSurrogate", true);
      }
    } else {
      result.put("kind", "object");
      result.put("text", value.getClass().getName());
    }
  }

  /** UTF-8 cannot carry lone surrogates; both sides compare the U+FFFD-replaced form. */
  static String lossy(String s) {
    StringBuilder builder = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
        builder.append(c).append(s.charAt(++i));
      } else if (Character.isSurrogate(c)) {
        builder.append('\uFFFD');
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  static void error(ObjectNode result, String stage, Throwable failure) {
    result.put("kind", "error");
    result.put("stage", stage);
    result.put("text", failure.getClass().getSimpleName());
    String message = String.valueOf(failure.getMessage());
    Throwable root = failure;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    if (root != failure) {
      message = root.getClass().getSimpleName() + ": " + root.getMessage() + " <- " + message;
    }
    result.put("message", lossy(message.length() > 240 ? message.substring(0, 240) : message));
  }
}
