package org.unlaxer.tinyexpression.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * JSON text in the form the Rust {@code tinyexpression-rs} API writes it (issue #221): the
 * same escaping ({@code json_string}), fields in the order the Rust responses use.
 */
final class JsonOut {

  private JsonOut() {}

  /** Rust {@code json_string}: {@code "}, {@code \}, {@code \n}, {@code \r}, {@code \t}, other controls as a 4-digit u-escape. */
  static String string(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder out = new StringBuilder(value.length() + 2);
    out.append('"');
    value.codePoints().forEach(codePoint -> {
      switch (codePoint) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (codePoint < 0x20) {
            out.append(String.format("\\u%04x", codePoint));
          } else {
            out.appendCodePoint(codePoint);
          }
      }
    });
    out.append('"');
    return out.toString();
  }

  static String strings(List<String> values) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(string(values.get(i)));
    }
    return out.append(']').toString();
  }

  /** Inserts {@code ,"name":rawJson} before the closing brace of a JSON object text. */
  static String withField(String objectJson, String name, String rawJson) {
    return objectJson.substring(0, objectJson.length() - 1) + "," + string(name) + ":" + rawJson
        + "}";
  }

  /**
   * The value of a calculator result as Rust {@code Value::canonical_json} writes it. A Java
   * type the Rust runtime has no variant for is {@code {"kind":"object","class":...}}.
   */
  static String value(Object value) {
    if (value == null) {
      return "{\"kind\":\"null\"}";
    }
    if (value instanceof Float) {
      float f = (Float) value;
      return "{\"kind\":\"number\",\"value\":" + string(rustFloatDisplay(f)) + ",\"f32Bits\":\"0x"
          + String.format("%08x", Float.floatToRawIntBits(f)) + "\"}";
    }
    if (value instanceof Double) {
      double d = (Double) value;
      return "{\"kind\":\"double\",\"value\":" + string(Double.toString(d)) + ",\"f64Bits\":\"0x"
          + String.format("%016x", Double.doubleToRawLongBits(d)) + "\"}";
    }
    if (value instanceof Integer) {
      return "{\"kind\":\"int\",\"value\":" + value + "}";
    }
    if (value instanceof Long) {
      return "{\"kind\":\"long\",\"value\":" + value + "}";
    }
    if (value instanceof Short) {
      return "{\"kind\":\"short\",\"value\":" + value + "}";
    }
    if (value instanceof Byte) {
      return "{\"kind\":\"byte\",\"value\":" + value + "}";
    }
    if (value instanceof Boolean) {
      return "{\"kind\":\"boolean\",\"value\":" + value + "}";
    }
    if (value instanceof String) {
      return "{\"kind\":\"string\",\"value\":" + string((String) value) + "}";
    }
    return "{\"kind\":\"object\",\"class\":" + string(value.getClass().getName()) + "}";
  }

  /**
   * Rust's {@code Display} of an {@code f32}: the shortest round-trip digits in plain notation
   * ({@code 1}, {@code 0.1}, {@code 100000000000000000000}), {@code NaN}, {@code inf}. The
   * digits come from {@link Float#toString(float)}, which is the shortest representation from
   * JDK 19 on; on older JDKs a few values can carry one more digit. {@code f32Bits} is exact.
   */
  static String rustFloatDisplay(float value) {
    if (Float.isNaN(value)) {
      return "NaN";
    }
    if (Float.isInfinite(value)) {
      return value > 0 ? "inf" : "-inf";
    }
    if (value == 0f) {
      return Float.floatToRawIntBits(value) < 0 ? "-0" : "0";
    }
    return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
  }
}
