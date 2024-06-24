package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Not;
import org.unlaxer.tinyexpression.parser.javalang.BooleanTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.NumberTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.StringTypeDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser.VariableDeclarations;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser.VariableInfo;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.cache.SupplierBoundCache;

@SuppressWarnings("serial")
public class ExclusiveNakedVariableParser extends NakedVariableParser {//implements Expression , BooleanExpression , StringExpression{
  
  static final SupplierBoundCache<ExclusiveNakedVariableParser> SINGLETON = new SupplierBoundCache<>(ExclusiveNakedVariableParser::new);

  public ExclusiveNakedVariableParser() {
    super();
  }

  public ExclusiveNakedVariableParser(Name name) {
    super(name);
  }
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    
    return 
      new Parsers(
        Parser.get(DollarParser.class),
        Parser.newInstance(IdentifierParser.class).addTag(VariableParser.variableNameTag),
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
  
  @Override
  public Parsed parse(ParseContext parseContext,TokenKind tokenKind,boolean invertMatch) {

    parseContext.getCurrent().setResetMatchedWithConsumed(false);

    parseContext.startParse(this, parseContext, tokenKind, invertMatch);
    parseContext.begin(this);
    
    Parsers children = getChildren();
    
    String variableName="";

    for (Parser parser : children) {
      Parsed parsed = parser.parse(parseContext,tokenKind,invertMatch);

      if(parsed.isStopped()){
        break;
      }
      if (parsed.isFailed()) {
        parseContext.rollback(this);
        parseContext.endParse(this, Parsed.FAILED , parseContext, tokenKind, invertMatch);
        return Parsed.FAILED;
      }
      if(parser.getClass() == IdentifierParser.class) {
        variableName = parsed.getRootToken().toString();
      }
    }
    Optional<VariableInfo> variableInfo = VariableDeclarations.SINGLETON.get(parseContext, variableName);
    
    VariableParser matchedParser = variableInfo.map(VariableInfo::matchedVariableParser).orElse(this);
    
    Parsed committed = new Parsed(parseContext.commit(matchedParser,tokenKind));
    parseContext.endParse(this, committed, parseContext, tokenKind, invertMatch);
    return committed;
  }
  
  @TokenExtractor
  public Optional<VariableInfo> variableInfoFromDeclaration(ParseContext parseContext , Token thisParserParsed) {
    
    TypedToken<VariableParser> typed = thisParserParsed.typed(VariableParser.class);
    
    String variableName = getVariableName(typed);
    Optional<VariableInfo> optional = VariableDeclarations.SINGLETON.get(parseContext, variableName);
    
    return optional;
  }
  
  public static ExclusiveNakedVariableParser get() {
    
    return SINGLETON.get();
  }
}