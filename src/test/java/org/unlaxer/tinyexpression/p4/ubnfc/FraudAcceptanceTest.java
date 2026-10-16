package org.unlaxer.tinyexpression.p4.ubnfc;

import static org.junit.Assert.*;
import org.junit.Test;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SourceText;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class FraudAcceptanceTest {
  @Test public void nullPartialAndRejectedResultsCannotCountAsFastSuccess() {
    assertThrows(AssertionError.class, () -> FraudAcceptance.validated(null, "x"));
    assertThrows(AssertionError.class, () -> FraudAcceptance.validated(
        new P4PreferredAstMapper.ParsedAst(null, "invalid", P4SourceText.lexicalOnly()), "x"));
    var source = "if(true){1}else{0}";
    var parsed = P4ParserEngine.with(P4ParserEngine.UBNFC,
        () -> P4PreferredAstMapper.parseDetailed(source, ExpressionTypes._float));
    FraudAcceptance.validated(parsed, source);
    assertThrows(AssertionError.class, () -> FraudAcceptance.validated(parsed, source + "@"));
    assertThrows(RuntimeException.class, () -> P4ParserEngine.with(P4ParserEngine.UBNFC,
        () -> P4PreferredAstMapper.parseDetailed(source + "@", ExpressionTypes._float)));
  }
}
