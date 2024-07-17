package org.unlaxer.tinyexpression.loader;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyOneOrMore;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.util.annotation.TokenExtractor;

public class FormulaInfoBlocksParser extends LazyOneOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return FormulaInfoBlockParser::new;
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }

  @TokenExtractor
  public FormulaInfoList extract(TypedToken<FormulaInfoBlocksParser> typedToken , 
      FormulaInfoAdditionalFields additionalFields, ClassLoader classLoader) {
    List<TypedToken<FormulaInfoBlockParser>> childrenWithParserAsListTyped = 
        typedToken.getChildrenWithParserAsListTyped(FormulaInfoBlockParser.class);
    
    List<FormulaInfo> collect = childrenWithParserAsListTyped.stream()
      .filter(_typeToken->{
        boolean hasElement = _typeToken.flatten().stream()
            .anyMatch(token->token.parser.getClass() == FormulaInfoElementParser.class);
        return hasElement;
      })
      .map(_typeToken->{
        return FormulaInfoParser.extractFormulaInfo(_typeToken , additionalFields , classLoader);
      })
      .collect(Collectors.toList());
    
    return new FormulaInfoList(collect);
  }

}