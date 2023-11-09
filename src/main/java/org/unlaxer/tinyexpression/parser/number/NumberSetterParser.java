package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.SetWordParser;
import org.unlaxer.tinyexpression.parser.SetterParser;

public class NumberSetterParser extends WhiteSpaceDelimitedLazyChain/*JavaStyleDelimitedLazyChain*/
  implements NumberExpression , SetterParser{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(SetWordParser.class),
        Parser.get(()->new Optional(Parser.get(IfNotExistsParser.class))),
        Parser.get(()->new Choice(
            Parser.newInstance(StrictTypedNumberExpressionParser.class),
            Parser.get(NumberExpressionParser.class)
          )
        )
    );
  }
  
  
  public static class Tuple{
    
    public static class TupleElement{
      
      ExpressionType expressionType;
      
    }
  }
}