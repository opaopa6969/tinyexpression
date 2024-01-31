package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.parser.ExclusiveNakedVariableParser;
import org.unlaxer.tinyexpression.parser.MethodParser;
import org.unlaxer.tinyexpression.parser.TypedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;

public class VariableTypeResolver{
  
  public static TypedToken<? extends VariableParser> resolveTypedVariable(TypedToken<ExclusiveNakedVariableParser> token) {
    
//    //FIXME!
//    if(true) {
//      return token;
//    }
    
    // 型推論/型解決を行う
    //1. 親にMethodParserがあればMethodParameterから解決をする
    //2. 比較演算の他方の型から解決する。method callや == や -1等
    //3. methodの実パラメータの場合仮引数の型から解決する
    //4. not等のunary operatorの型から解決する
    //5. VariableDeclarationの型から解決する
    
//    String path = token.getPath();

    String variableName = token.getParser().getVariableName(token);
    
    //1. 親にMethodParserがあればMethodParameterから解決をする
    Optional<Token> ancestorAsOptional = token.getAncestorAsOptional(TokenPredicators.parserImplements(MethodParser.class));
    if(ancestorAsOptional.isPresent()) {
      
      TypedToken<MethodParser> methodParserToken = ancestorAsOptional.get().typed(MethodParser.class);
      MethodParser methodParser = methodParserToken.getParser();
      Optional<TypedToken<TypedVariableParser>> typedVariableParser = 
          methodParser.typedVariableParser(methodParserToken, variableName);
      
      if(typedVariableParser.isPresent()) {
        return typedVariableParser.get();
      }
    }
    //2. 比較演算の他方の型から解決する。method callや == や -1等
    //3. methodの実パラメータの場合仮引数の型から解決する
    //4. not等のunary operatorの型から解決する
    //5. VariableDeclarationの型から解決する
    
    return token;
  }
  
  public static Token resolveVariableType(Token token) {
    token.flatten().stream()
      .forEach(_token->{
        if(_token.parser.getClass() == ExclusiveNakedVariableParser.class) {
          TypedToken<ExclusiveNakedVariableParser> typed = 
              _token.typed(ExclusiveNakedVariableParser.class);
          TypedToken<? extends VariableParser> resolveTypedVariable = 
              VariableTypeResolver.resolveTypedVariable(typed);
          
          VariableParser parser = resolveTypedVariable.getParser();
          if(parser.getClass() != ExclusiveNakedVariableParser.class) {
            _token.replace(parser);
//            String path = _token.getPath();
          }
        }
      });
    return token;
  }
  
}