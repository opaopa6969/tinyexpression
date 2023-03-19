package org.unlaxer.tinyexpression.evaluator.javacode;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.ParserTestBase;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.SideEffectStringExpressionParser;

public class SideEffectStringExpressionBuilderTest extends ParserTestBase {

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    String formula =
        "with side effect:org.unlaxer.tinyexpression.parser.TestSideEffector#setBlackList('n'+'i'+'k'+'u')";
    var parser = new SideEffectStringExpressionParser();

    testAllMatch(parser, formula);

    ParseContext parseContext = new ParseContext(new StringSource(formula));
    Parsed parsed = parser.parse(parseContext);
    Token rootToken = parsed.getRootToken();
    Token sideEffectToken = rootToken.flatten().stream()
        .filter(token -> token.parser.getClass().equals(SideEffectStringExpressionParser.class))
        .findFirst().orElseThrow();

    var builder = new SideEffectStringExpressionBuilder();//
    SimpleJavaCodeBuilder builder2 = new SimpleJavaCodeBuilder();
    builder.build(builder2, sideEffectToken);
    System.out.println(builder2.toString());

  }
}
