package org.unlaxer.tinyexpression.parser.javalang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.util.annotation.TokenExtractor;

@SuppressWarnings("serial")
public class VariableDeclarationParser extends LazyChoice implements TransactionListener{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(NumberVariableDeclarationParser.class),
        Parser.get(StringVariableDeclarationParser.class),
        Parser.get(BooleanVariableDeclarationParser.class)
    );
  }
  
  @TokenExtractor
  public static TypedToken<VariableParser> extractVariableParserToken(Token thisParserParsed) {
    
    Token choiced = thisParserParsed;
    Parser parser = thisParserParsed.getParser();
    if(parser instanceof Choice) {
      
      choiced = ChoiceInterface.choiced(thisParserParsed);
    }
    
    parser = choiced.getParser();
      
    if(parser instanceof AbstractVariableDeclarationParser) {
      
      return choiced.getChild(TokenPredicators.parserImplements(VariableParser.class))
          .typed(VariableParser.class);
    }
    throw new IllegalArgumentException();
  }
  
  @TokenExtractor
  public static VariableInfo extractVariableInfo(Token thisParserParsed) {
    
    TypedToken<VariableParser> variableParserToken = extractVariableParserToken(thisParserParsed);
    VariableParser parser = variableParserToken.getParser();
    ExpressionType expressionType = parser.typeAsOptional().get();
    String variableName = parser.getVariableName(variableParserToken);
    return new VariableInfo(expressionType, variableName);
  }
  
  public static class VariableInfo{
    
    public final ExpressionType expressionType;
    public String name;
    public VariableInfo(ExpressionType expressionType, String name) {
      super();
      this.expressionType = expressionType;
      this.name = name;
    }
  }

  @Override
  public void setLevel(OutputLevel level) {
  }

  @Override
  public void onOpen(ParseContext parseContext) {
  }

  @Override
  public void onBegin(ParseContext parseContext, Parser parser) {
  }

  @Override
  public void onCommit(ParseContext parseContext, Parser parser, List<Token> committedTokens) {
    
    
    //Number or String or boolean VariableDeclararionParser
    Token token = committedTokens.get(0);
    
    VariableInfo variableInfo = extractVariableInfo(token);
    VariableDeclarations.set(parseContext, variableInfo);
    
  }

  @Override
  public void onRollback(ParseContext parseContext, Parser parser, List<Token> rollbackedTokens) {
  }

  @Override
  public void onClose(ParseContext parseContext) {
  }
  
  public static class VariableDeclarations{
    
    public static Name STORES = Name.of(VariableDeclarations.class , "Stores");
    
    @SuppressWarnings("unchecked")
    public static void set(ParseContext parseContext , VariableInfo variableInfo) {
      
      Map<Name, Object> globalScopeTreeMap = parseContext.getGlobalScopeTreeMap();

      ((Map<Name,VariableInfo>)
          globalScopeTreeMap.computeIfAbsent(STORES,key->new HashMap<>()))
            .put(Name.of(variableInfo.name ), variableInfo); 
    }
    
    @SuppressWarnings("unchecked")
    public VariableInfo get(ParseContext parseContext , Name name) {
      
      Map<Name, Object> globalScopeTreeMap = parseContext.getGlobalScopeTreeMap();
      
      Map<Name , VariableInfo> infoByName = (Map<Name, VariableInfo>) globalScopeTreeMap.get(STORES);
      
      return infoByName.get(name);
    }
  }
  
}