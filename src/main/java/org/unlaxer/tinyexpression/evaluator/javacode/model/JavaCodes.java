package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.List;
import java.util.Optional;

import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.CodeParser;
import org.unlaxer.tinyexpression.parser.javalang.CodesParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public record JavaCodes(TypedToken<CodesParser> token , List<JavaCode> javaCodes){


  @SuppressWarnings("unchecked")
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static JavaCodes extract(TypedToken<TinyExpressionParser> tinyExpressionToken){

    Optional<TypedToken<CodesParser>> childWithParserAsOptionalTyped =
        tinyExpressionToken.getChildWithParserAsOptionalTyped(CodesParser.class);

    if(childWithParserAsOptionalTyped.isEmpty()) {
      return new JavaCodes(
          tinyExpressionToken.newCreatesOf(List.of()).typed(CodesParser.class),  List.of()
      );
    }

    TypedToken<CodesParser> typedToken = childWithParserAsOptionalTyped.get();
    List<JavaCode> list = typedToken.filteredChildren.stream()
      .map(token -> (TypedToken<CodeParser>) token)
      .map(JavaCode::extractCodeBlocksAsModel)
      .toList();
    return new JavaCodes(typedToken, list);
  }
 }
