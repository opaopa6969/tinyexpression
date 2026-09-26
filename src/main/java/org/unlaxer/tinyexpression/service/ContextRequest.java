package org.unlaxer.tinyexpression.service;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An evaluation request (issue #221): the Java reading of the request JSON of the Rust
 * {@code te_eval_context} / {@code te_formula_info_context} / {@code te_eval_trace}
 * ({@code rust/tinyexpression-rs/src/request.rs}), with the same defaults and the same error
 * messages. A request error is an {@link IllegalArgumentException} whose message is the Rust
 * one.
 */
final class ContextRequest {

  static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
      .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  /** One {@code variables[]} entry, already converted to its Java value. */
  static final class Variable {
    final String name;
    final String map;
    final Object value;

    Variable(String name, String map, Object value) {
      this.name = name;
      this.map = map;
      this.value = value;
    }
  }

  final JsonNode json;
  final String source;
  final ExpressionType resultType;
  final ExpressionType numberType;
  final Angle angle;
  final long seed;
  final List<Variable> variables;
  final StubExternals externals;

  private ContextRequest(JsonNode json, String source, ExpressionType resultType,
      ExpressionType numberType, Angle angle, long seed, List<Variable> variables,
      StubExternals externals) {
    this.json = json;
    this.source = source;
    this.resultType = resultType;
    this.numberType = numberType;
    this.angle = angle;
    this.seed = seed;
    this.variables = variables;
    this.externals = externals;
  }

  /** Parses the request text; the JSON syntax error message differs from Rust's reader. */
  static JsonNode parseJson(String text) {
    try {
      JsonNode node = MAPPER.readTree(text);
      if (node == null) {
        throw new IllegalArgumentException("invalid JSON: unexpected end of input");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("invalid JSON: " + e.getOriginalMessage());
    }
  }

  /** {@code source_field} is {@code formula} or {@code document}. */
  static ContextRequest read(JsonNode json, String sourceField) {
    JsonNode sourceNode = json.isObject() ? json.get(sourceField) : null;
    if (sourceNode == null || !sourceNode.isTextual()) {
      throw new IllegalArgumentException(
          "the request needs a string field \"" + sourceField + "\"");
    }
    ExpressionType resultType = ExpressionTypes._float;
    JsonNode resultTypeNode = json.get("resultType");
    if (resultTypeNode != null && resultTypeNode.isTextual()) {
      String name = resultTypeNode.textValue();
      resultType = type(name, false);
      if (resultType == null) {
        throw new IllegalArgumentException("unknown resultType \"" + name + "\"");
      }
    }
    ExpressionType numberType = ExpressionTypes._float;
    JsonNode numberTypeNode = json.get("numberType");
    if (numberTypeNode != null && numberTypeNode.isTextual()) {
      String name = numberTypeNode.textValue();
      numberType = type(name, true);
      if (numberType == null) {
        throw new IllegalArgumentException("unknown numberType \"" + name + "\"");
      }
    }
    Angle angle = Angle.DEGREE;
    JsonNode angleNode = json.get("angle");
    if (angleNode != null && angleNode.isTextual()) {
      String name = angleNode.textValue();
      if (name.equals("radian")) {
        angle = Angle.RADIAN;
      } else if (!name.equals("degree")) {
        throw new IllegalArgumentException("unknown angle \"" + name + "\"");
      }
    }
    List<Variable> variables = new ArrayList<>();
    JsonNode variablesNode = json.get("variables");
    if (variablesNode != null) {
      if (!variablesNode.isArray()) {
        throw new IllegalArgumentException("variables must be an array");
      }
      for (JsonNode variable : variablesNode) {
        variables.add(variable(variable));
      }
    }
    List<StubExternals.Stub> stubs = new ArrayList<>();
    JsonNode externalsNode = json.get("externals");
    if (externalsNode != null && !externalsNode.isNull()) {
      if (!externalsNode.isArray()) {
        throw new IllegalArgumentException("externals must be an array");
      }
      for (JsonNode stub : externalsNode) {
        stubs.add(stub(stub));
      }
    }
    long seed = 1;
    JsonNode seedNode = json.get("seed");
    if (seedNode != null && !seedNode.isNull()) {
      if (!seedNode.isNumber()) {
        throw new IllegalArgumentException("seed must be a number");
      }
      String text = seedNode.asText();
      BigInteger parsed = unsigned(text);
      if (parsed == null || parsed.bitLength() > 64) {
        throw new IllegalArgumentException(
            "seed must be a non-negative integer, found " + text);
      }
      seed = parsed.longValue();
    }
    return new ContextRequest(json, sourceNode.textValue(), resultType, numberType, angle, seed,
        variables, new StubExternals(stubs));
  }

  /** A fresh {@code CalculationContext} with the request's angle and variables. */
  CalculationContext newContext() {
    CalculationContext context = CalculationContext.newContext(10, RoundingMode.HALF_UP, angle);
    for (Variable variable : variables) {
      switch (variable.map) {
        case "number":
          context.set(variable.name, (Number) variable.value);
          break;
        case "string":
          context.set(variable.name, (String) variable.value);
          break;
        case "boolean":
          context.set(variable.name, ((Boolean) variable.value).booleanValue());
          break;
        default:
          context.setObject(variable.name, variable.value);
      }
    }
    return context;
  }

  /** Rust {@code ResultType::parse} / {@code NumberType::parse}. */
  static ExpressionType type(String name, boolean numberOnly) {
    ExpressionType type;
    switch (name.strip().toLowerCase(Locale.ROOT)) {
      case "float":
      case "number":
      case "java.lang.float":
        type = ExpressionTypes._float;
        break;
      case "double":
      case "java.lang.double":
        type = ExpressionTypes._double;
        break;
      case "int":
      case "integer":
      case "java.lang.integer":
        type = ExpressionTypes._int;
        break;
      case "long":
      case "java.lang.long":
        type = ExpressionTypes._long;
        break;
      case "short":
      case "java.lang.short":
        type = ExpressionTypes._short;
        break;
      case "byte":
      case "java.lang.byte":
        type = ExpressionTypes._byte;
        break;
      case "boolean":
      case "java.lang.boolean":
        type = ExpressionTypes._boolean;
        break;
      case "string":
      case "java.lang.string":
        type = ExpressionTypes.string;
        break;
      case "object":
      case "java.lang.object":
        type = ExpressionTypes.object;
        break;
      default:
        return null;
    }
    return numberOnly && !type.isNumber() ? null : type;
  }

  private static BigInteger unsigned(String text) {
    if (text.isEmpty()) {
      return null;
    }
    Iterator<Integer> codePoints = text.codePoints().iterator();
    while (codePoints.hasNext()) {
      int codePoint = codePoints.next();
      if (codePoint < '0' || codePoint > '9') {
        return null;
      }
    }
    return new BigInteger(text);
  }

  /** A scalar as text: strings verbatim, numbers as written (as Jackson keeps them), booleans. */
  private static String scalarText(JsonNode node) {
    if (node == null) {
      return null;
    }
    if (node.isTextual()) {
      return node.textValue();
    }
    if (node.isNumber() || node.isBoolean()) {
      return node.asText();
    }
    return null;
  }

  /** Rust {@code typed_value}: {@code {"type": ..., "value": ...}} as a Java value. */
  static Object typedValue(String kind, JsonNode valueNode, String what) {
    if (kind.equals("null")) {
      return null;
    }
    String raw = scalarText(valueNode);
    if (raw == null) {
      throw new IllegalArgumentException(what + ": value must be a string, number or boolean");
    }
    try {
      switch (kind) {
        case "float":
        case "number":
          return Float.parseFloat(raw);
        case "double":
          return Double.parseDouble(raw);
        case "int":
          return Integer.parseInt(raw.strip());
        case "long":
          return Long.parseLong(raw.strip());
        case "short":
          return Short.parseShort(raw.strip());
        case "byte":
          return Byte.parseByte(raw.strip());
        case "boolean":
          if (raw.equals("true")) {
            return Boolean.TRUE;
          }
          if (raw.equals("false")) {
            return Boolean.FALSE;
          }
          throw new NumberFormatException(raw);
        case "string":
          return raw;
        default:
          throw new IllegalArgumentException(what + ": unknown type \"" + kind + "\"");
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          what + ": \"" + raw + "\" is not a valid " + kind);
    }
  }

  private static String textField(JsonNode node, String name) {
    JsonNode field = node.get(name);
    return field != null && field.isTextual() ? field.textValue() : null;
  }

  private static Variable variable(JsonNode node) {
    String name = node.isObject() ? textField(node, "name") : null;
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("variables[]: name is required");
    }
    if (name.startsWith("$")) {
      name = name.substring(1);
    }
    String what = "variable " + name;
    String kind = textField(node, "type");
    if (kind == null) {
      kind = "float";
    }
    Object value = typedValue(kind, node.get("value"), what);
    String defaultMap = value instanceof Boolean ? "boolean"
        : value instanceof String ? "string"
        : value == null ? "object"
        : "number";
    String map = textField(node, "map");
    if (map == null) {
      map = defaultMap;
    }
    boolean fits;
    switch (map) {
      case "number":
        fits = value instanceof Number;
        break;
      case "string":
        fits = value instanceof String;
        break;
      case "boolean":
        fits = value instanceof Boolean;
        break;
      case "object":
        fits = true;
        break;
      default:
        fits = false;
    }
    if (!fits) {
      throw new IllegalArgumentException(
          what + ": a " + kind + " value cannot go into the " + map + " map");
    }
    return new Variable(name, map, value);
  }

  private static StubExternals.Stub stub(JsonNode node) {
    String className = node.isObject() ? textField(node, "class") : null;
    if (className == null || className.isEmpty()) {
      throw new IllegalArgumentException("externals[]: class is required");
    }
    String what = "external " + className;
    Integer arity = null;
    JsonNode arityNode = node.get("arity");
    if (arityNode != null && !arityNode.isNull()) {
      if (!arityNode.isNumber()) {
        throw new IllegalArgumentException(what + ": arity must be a number");
      }
      BigInteger parsed = unsigned(arityNode.asText());
      if (parsed == null || parsed.bitLength() > 31) {
        throw new IllegalArgumentException(what + ": arity must be a non-negative integer");
      }
      arity = parsed.intValue();
    }
    JsonNode registeredNode = node.get("registered");
    boolean registered = !(registeredNode != null && registeredNode.isBoolean()
        && !registeredNode.booleanValue());
    Object result = null;
    JsonNode resultNode = node.get("result");
    if (resultNode != null && !resultNode.isNull()) {
      String kind = textField(resultNode, "type");
      result = typedValue(kind == null ? "float" : kind, resultNode.get("value"), what);
    }
    return new StubExternals.Stub(className, textField(node, "method"), arity, registered,
        result);
  }
}
