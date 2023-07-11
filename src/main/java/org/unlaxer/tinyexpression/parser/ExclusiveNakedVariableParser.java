package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Not;
import org.unlaxer.tinyexpression.parser.javalang.BooleanTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.NumberTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.StringTypeDeclarationParser;
import org.unlaxer.util.cache.SupplierBoundCache;

public class ExclusiveNakedVariableParser extends NakedVariableParser {//implements Expression , BooleanExpression , StringExpression{
  
  static final SupplierBoundCache<ExclusiveNakedVariableParser> SINGLETON = new SupplierBoundCache<>(ExclusiveNakedVariableParser::new);

  public ExclusiveNakedVariableParser() {
    super();
  }

  public ExclusiveNakedVariableParser(Name name) {
    super(name);
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    
    return 
      new Parsers(
        Parser.get(DollarParser.class),
        Parser.get(IdentifierParser.class),
        new Not(
//              new MatchOnly(
                new Choice(
                    Parser.get(NumberTypeDeclarationParser.class),
                    Parser.get(StringTypeDeclarationParser.class),
                    Parser.get(BooleanTypeDeclarationParser.class)
                )
//              )
        )
      );
  }
  
  public String getVariableName(Token thisParserParsed) {
    return NakedVariableParser.getVariableNameFromNaked(thisParserParsed);
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.empty();
  }
  
  public static ExclusiveNakedVariableParser get() {
    
    return SINGLETON.get();
  }
}