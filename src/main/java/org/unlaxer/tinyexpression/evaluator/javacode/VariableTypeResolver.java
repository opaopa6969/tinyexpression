package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExclusiveNakedVariableParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.MethodParser;
import org.unlaxer.tinyexpression.parser.TypedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclaration;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationsParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringVariableParser;

public class VariableTypeResolver {

  static final Map<ExpressionType, ? extends VariableParser> variableParserByExpressionType = Map.of(
      ExpressionTypes._boolean, BooleanVariableParser.get(),
      ExpressionTypes.string, StringVariableParser.get(),
      ExpressionTypes.number, NumberVariableParser.get()

  );

  public static Optional<VariableParser> resolveTypedVariable(
      TypedToken<ExclusiveNakedVariableParser> token, Map<String, Token> variableDeclarationByName) {

//    //FIXME!
//    if(true) {
//      return token;
//    }

    // 型推論/型解決を行う
    // 1. 親にMethodParserがあればMethodParameterから解決をする
    // 2. 比較演算の他方の型から解決する。method callや == や -1等
    // 3. methodの実パラメータの場合仮引数の型から解決する
    // 4. not等のunary operatorの型から解決する
    // 5. VariableDeclarationの型から解決する

//		String path = token.getPath();

    String variableName = token.getParser().getVariableName(token);

    // 1. 親にMethodParserがあればMethodParameterから解決をする
    Optional<Token> ancestorAsOptional = token
        .getAncestorAsOptional(TokenPredicators.parserImplements(MethodParser.class));
    if (ancestorAsOptional.isPresent()) {

      TypedToken<MethodParser> methodParserToken = ancestorAsOptional.get().typed(MethodParser.class);
      MethodParser methodParser = methodParserToken.getParser();
      Optional<TypedToken<TypedVariableParser>> typedVariableParser = methodParser
          .typedVariableParser(methodParserToken, variableName);

      if (typedVariableParser.isPresent()) {
        return Optional.of(typedVariableParser.get().getParser());
      }
    }
    // 2. 比較演算の他方の型から解決する。method callや == や -1等
    // 3. methodの実パラメータの場合仮引数の型から解決する
    // 4. not等のunary operatorの型から解決する


    // 5. VariableDeclarationの型から解決する <-これは VariableDeclarationMatchedTokenParserで解決したのでいらない
//    Token declarationToken = variableDeclarationByName.get(variableName);
//
//    if (declarationToken != null) {
//
//      TypedToken<VariableDeclaration> typed = declarationToken.typed(VariableDeclaration.class);
//      Optional<ExpressionType> type = typed.getParser().type();
//
//      return type.map(variableParserByExpressionType::get);
//    }
    return Optional.empty();
  }




  /**
   * rootTokenからすべてを走査してExclusiveNakedVariableParserの型解決を行う
   * ただし、上位Parserが型解決で得られた型と違う場合があるため上位parserの型も書き換えてやらねばならない
   * これはParse時に実行したほうがよさそうだ。
   */
  public static Token resolveVariableType(Token rootToken) {

    // 最初にVariableDeclarationを取得する
    Map<String, Token> variableDeclarationByName = variableDeclarations(rootToken);

    rootToken.flatten().stream().forEach(_token -> {
      if (_token.parser.getClass() == ExclusiveNakedVariableParser.class) {
        TypedToken<ExclusiveNakedVariableParser> typed = _token.typed(ExclusiveNakedVariableParser.class);
        Optional<VariableParser> resolveTypedVariable = VariableTypeResolver
            .resolveTypedVariable(typed, variableDeclarationByName);

        resolveTypedVariable.ifPresent(parser->{
          if (parser.getClass() != ExclusiveNakedVariableParser.class) {
            _token.replace(parser);
            ExpressionType expressionType = parser.typeAsOptional().get();
          }
        });
      }
    });
    return rootToken;
  }

  public static Map<String, Token> variableDeclarations(Token rootToken) {

    Optional<Token> childWithParserAsOptional = rootToken
        .getChildWithParserAsOptional(VariableDeclarationsParser.class);

    List<Token> variableChildren = childWithParserAsOptional
        .map(VariableDeclarationsParser::extractVariables)
        .orElseGet(List::of);

    Map<String, Token> variableDeclarationByVariableName = variableChildren.stream()
        .collect(Collectors.toMap(
            token -> {
              TypedToken<VariableParser> extractVariableParserToOken = VariableDeclarationParser
                  .extractVariableParserToken(token);
              VariableParser parser = extractVariableParserToOken.getParser(VariableParser.class);
              String variableName = parser.getVariableName(extractVariableParserToOken);
              return variableName;
            },
            Function.identity()));

    return variableDeclarationByVariableName;
  }

  public static Optional<ExpressionType> resolveFromVariableParserToken(
      Token token,
      TinyExpressionTokens tinyExpressionTokens) {

    Parser parser = token.getParser();
    if (parser instanceof VariableParser) {

      TypedToken<VariableParser> typed = token.typed(VariableParser.class);
      VariableParser variableParser = typed.getParser();

      Optional<ExpressionType> typeAsOptional = variableParser.typeAsOptional();
      if (typeAsOptional.isPresent()) {
        return typeAsOptional;
      }
      String variableName = variableParser.getVariableName(typed);
      Optional<Token> matchedTypeFromVariableDeclaration = tinyExpressionTokens
          .matchedTypeFromVariableDeclaration(variableName);

      Optional<ExpressionType> expressionType = matchedTypeFromVariableDeclaration
          .map(_token -> _token.typed(VariableDeclaration.class))
          .flatMap(_typedToken->_typedToken.getParser().type(_typedToken));

      return expressionType;
    }
    return Optional.empty();
  }
}