package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class ImportParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);

    ImportParser importParser = new ImportParser();
    
    testAllMatch(importParser, "import java.lang.String as String ;");
    testAllMatch(importParser, "import org.unlaxer.tinyexpression.parser.AdmissionFee as Fee ;");

    
  }

}
