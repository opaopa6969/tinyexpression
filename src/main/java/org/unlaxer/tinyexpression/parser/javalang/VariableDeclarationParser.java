package org.unlaxer.tinyexpression.parser.javalang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.unlaxer.tinyexpression.parser.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.StringVariableParser;
import org.unlaxer.tinyexpression.parser.TypeHint;
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
  
  public VariableDeclarationParser() {
    super();
    variableDeclarations = new VariableDeclarations();
  }
  
  VariableDeclarations variableDeclarations;
  
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
    TypedToken<TypeHint> typed = thisParserParsed.flatten().stream()
        .filter(TokenPredicators.parserImplements(TypeHint.class))//
        .findFirst().get()
        .typed(TypeHint.class);
        
    ExpressionType expressionType = typed.getParser().type();
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
    
    public VariableParser matchedVariableParser() {
      
      switch (expressionType) {
        case bool:
          return Parser.get(BooleanVariableParser.class);
        case number:
          return Parser.get(NumberVariableParser.class);
        case string:
          return Parser.get(StringVariableParser.class);
        case object:
        default:
          throw new IllegalArgumentException();
      }
      
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
    
    if(false == parser instanceof AbstractVariableDeclarationParser) {
      return ;
    }
    
    //Number or String or boolean VariableDeclararionParser
    Token token = committedTokens.get(0);
    
    VariableInfo variableInfo = extractVariableInfo(token);
    variableDeclarations.set(parseContext, variableInfo);
  }

  @Override
  public void onRollback(ParseContext parseContext, Parser parser, List<Token> rollbackedTokens) {
    
//    if(false == parser instanceof AbstractVariableDeclarationParser) {
//      return ;
//    }
//    
//    Token token = rollbackedTokens.get(0);
//    
//    VariableInfo variableInfo = extractVariableInfo(token);
//    variableDeclarations.remove(parseContext, variableInfo.name);
    
  }

  @Override
  public void onClose(ParseContext parseContext) {
  }
  
  public static class VariableDeclarations{
    
    public static final VariableDeclarations SINGLETON = new VariableDeclarations();
    
    public static final Name STORES = Name.of(VariableDeclarations.class , "Stores");
    
    @SuppressWarnings("unchecked")
    Map<String, VariableInfo> infoByName(ParseContext parseContext){
      Map<Name, Object> globalScopeTreeMap = parseContext.getGlobalScopeTreeMap();
      Map<String, VariableInfo> infoByName = (Map<String, VariableInfo>) globalScopeTreeMap
          .computeIfAbsent(STORES,name->new HashMap<>());
      return infoByName;
      
    }
    
    public void set(ParseContext parseContext , VariableInfo variableInfo) {
      
      infoByName(parseContext).put(variableInfo.name, variableInfo); 
    }
    
    /**
     * @param parseContext
     * @param method name
     * @return VariableInfo removed
     */
    public VariableInfo remove(ParseContext parseContext , String variableName) {
      
      
      return infoByName(parseContext).remove(variableName);
    }
    
    public Optional<VariableInfo> get(ParseContext parseContext , String variableName) {
      
      return Optional.ofNullable(infoByName(parseContext).get(variableName));
    }
  }
  
}