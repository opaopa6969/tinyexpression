package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Map;

import org.unlaxer.parser.Parser;

final class ParserDispatch {

  private ParserDispatch() {
  }

  static <H> H findHandler(Map<Class<?>, H> handlers, Parser parser) {
    if (parser == null || handlers.isEmpty()) {
      return null;
    }

    H exact = handlers.get(parser.getClass());
    if (exact != null) {
      return exact;
    }

    for (Map.Entry<Class<?>, H> entry : handlers.entrySet()) {
      if (entry.getKey().isInstance(parser)) {
        return entry.getValue();
      }
    }
    return null;
  }

  static <H> H requireHandler(Map<Class<?>, H> handlers, Parser parser, String owner) {
    if (parser == null) {
      throw new IllegalArgumentException("Unsupported parser for " + owner + ": <null>");
    }
    H handler = findHandler(handlers, parser);
    if (handler == null) {
      throw new IllegalArgumentException("Unsupported parser for " + owner + ": " + parser.getClass().getName());
    }
    return handler;
  }
}
