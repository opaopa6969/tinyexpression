package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.PlusParser;

public class ParserDispatchTest {

  @Test
  public void testFindHandlerPrefersExactClassOverAssignableMatch() {
    Parser parser = Parser.get(PlusParser.class);

    Map<Class<?>, String> handlers = new LinkedHashMap<>();
    handlers.put(Parser.class, "generic");
    handlers.put(PlusParser.class, "plus");

    String handler = ParserDispatch.findHandler(handlers, parser);
    assertEquals("plus", handler);
  }

  @Test
  public void testFindHandlerFallsBackToAssignableMatch() {
    Parser parser = Parser.get(PlusParser.class);

    Map<Class<?>, String> handlers = new LinkedHashMap<>();
    handlers.put(Parser.class, "generic");

    String handler = ParserDispatch.findHandler(handlers, parser);
    assertEquals("generic", handler);
  }

  @Test
  public void testRequireHandlerRejectsNullParser() {
    try {
      ParserDispatch.requireHandler(Map.of(), null, "SampleBuilder");
      fail("expected null parser failure");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("<null>"));
    }
  }
}
