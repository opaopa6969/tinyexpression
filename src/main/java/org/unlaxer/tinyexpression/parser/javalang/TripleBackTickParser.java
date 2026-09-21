package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.elementary.WordParser;

// A fixed WordParser literal is safe to parse again in a fresh context.
public class TripleBackTickParser extends WordParser implements org.unlaxer.context.DiagnosticsAgnostic {
  public TripleBackTickParser() {
    super("```");
  }
}
