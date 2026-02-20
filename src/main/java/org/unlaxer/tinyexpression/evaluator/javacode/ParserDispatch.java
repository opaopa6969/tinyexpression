package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Map;

import org.unlaxer.parser.Parser;

final class ParserDispatch {

  private ParserDispatch() {
  }

  static <H> H findHandler(Map<Class<?>, H> handlers, Parser parser) {
    for (Map.Entry<Class<?>, H> entry : handlers.entrySet()) {
      if (entry.getKey().isInstance(parser)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
