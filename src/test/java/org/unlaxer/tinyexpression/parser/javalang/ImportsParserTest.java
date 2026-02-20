package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;

public class ImportsParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);

    ImportsParser importsParser = new ImportsParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import java.lang.String as String;")
      .n()
      .n()
      .line("import org.unlaxer.tinyexpression.parser.AdmissionFee as Fee;");

    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    testAllMatch(importsParser, formula);

    
  }

}
