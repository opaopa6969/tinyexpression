package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.util.annotation.TokenExtractor;

public interface MethodParser extends Parser{
  
  public abstract Class<? extends TypeHint> returningParser();
  public abstract Class<? extends ExpressionInterface> expressionParser();

  @TokenExtractor
  public default TypedToken<TypeHint> returning(TypedToken<MethodParser> thisParserParsed) {
    
    checkTokenParsedByThisParser(thisParserParsed);
    
    TypedToken<TypeHint> typedWithInterface = thisParserParsed.getChild(TokenPredicators.parsers(returningParser()))
        .typedWithInterface(TypeHint.class);
    return typedWithInterface;
  }
  
  @TokenExtractor
  public default TypedToken<IdentifierParser> methodName(TypedToken<MethodParser> thisParserParsed) {
    
    checkTokenParsedByThisParser(thisParserParsed);
    
    return thisParserParsed.getChild(TokenPredicators.parsers(IdentifierParser.class))
        .typed(IdentifierParser.class);
  }
  
  @TokenExtractor
  public default TypedToken<MethodParametersParser> methodParameters(TypedToken<MethodParser> thisParserParsed) {
    
    checkTokenParsedByThisParser(thisParserParsed);
    
     return thisParserParsed.getChild(TokenPredicators.parsers(MethodParametersParser.class))
        .typed(MethodParametersParser.class);
  }
  
  @TokenExtractor
  public default TypedVariableParser expressionType(TypedToken<MethodParser> thisParserParsed) {
    
    TypedToken<MethodParametersParser> methodParameters = methodParameters(thisParserParsed);
    
  }
  
}