package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.List;
import java.util.Optional;

import org.unlaxer.TypedToken;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportsParser;

public record Imports(TypedToken<ImportsParser> typedToken, List<Import> imports){

  public static Imports extract(TypedToken<TinyExpressionParser> tinyExpressionToken) {
    Optional<TypedToken<ImportsParser>> childWithParserAsOptionalTyped =
        tinyExpressionToken.getChildWithParserAsOptionalTyped(ImportsParser.class);

    if(childWithParserAsOptionalTyped.isEmpty()) {
      return new Imports(
          tinyExpressionToken.newCreatesOf(List.of()).typed(ImportsParser.class),  List.of()
      );
    }


    TypedToken<ImportsParser> typedToken = childWithParserAsOptionalTyped.get();
    @SuppressWarnings("unchecked")
    List<Import> list = typedToken.filteredChildren.stream()
      .map(token -> (TypedToken<ImportParser>) token)
      .map(Import::extract)
      .toList();
    return new Imports(typedToken, list);
  }
}