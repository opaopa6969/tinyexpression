package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.DollarParser;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.cache.SupplierBoundCache;

public class BooleanVariableParser extends LazyChoice implements VariableParser , BooleanExpression{

  private static final long serialVersionUID = -60484510350410L;

  static final SupplierBoundCache<BooleanVariableParser> SINGLETON = new SupplierBoundCache<>(BooleanVariableParser::new);


  public BooleanVariableParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
      new Parsers(//
          Parser.get(BooleanVariableMatchedWithVariableDeclarationParser.class),
          Parser.get(BooleanPrefixedVariableParser.class),
          Parser.get(BooleanSuffixedVariableParser.class)
      );
  }


  public static class BooleanVariableMatchedWithVariableDeclarationParser extends LazyChain implements BooleanExpression {

    public BooleanVariableMatchedWithVariableDeclarationParser() {
      super();
    }

    @Override
    public org.unlaxer.parser.Parsers getLazyParsers() {
      return new Parsers(//
          Parser.get(DollarParser.class), //0
          Parser.get(BooleanVariableDeclarationMatchedTokenParser.class)//1
      );
    }

    @TokenExtractor
    static Token getVariableNameToken(Token thisParserParsed) {
      Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
      return token;
    }

  }

  public static BooleanVariableParser get() {
    return SINGLETON.get();
  }

//  @Override
//  public Class<? extends RootVariableParser> rootOfTypedVariableParser() {
//    return BooleanVariableParser.class;
//  }
//
//  @Override
//  public Class<? extends VariableParser> oneOfTypedVariableParser() {
//    return BooleanPrefixedVariableParser.class;
//  }
//
//  @Override
//  public Class<? extends TypeHintVariableParser> typeHintVariableParser() {
//    return BooleanTypeHintPrefixParser.class;
//  }
}