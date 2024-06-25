package org.unlaxer.tinyexpression.loader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyOneOrMore;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.function.Unchecked;

import jp.caulis.fraud.model.CheckKinds;
import jp.caulis.fraud.model.SiteId;
import jp.caulis.fraud.model.calc.FormulaInfo;
import jp.caulis.fraud.model.calc.parser.FormulaInfoElementParser.KeyValue;

public class FormulaInfoParser extends LazyOneOrMore{
  
  public enum Kind{
    key,
    value
    ;
    public Tag tag() {
      return new Tag(this);
    }
  }
  
  @Override
  public Supplier<Parser> getLazyParser() {
    return FormulaInfoElementOrCommentParser::new;
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
//    return Optional.of(Parser.get(EndOfPartParser.class));
  }

  
  @TokenExtractor
  public static FormulaInfo extractFormulaInfo(TypedToken<FormulaInfoBlockParser> thisParserParsed , ClassLoader classLoader){
    
    FormulaInfo formulaInfo = new FormulaInfo();
    List<Token> elements = FormulaInfoElementOrCommentParser.elements(thisParserParsed);
    
    AtomicBoolean hasByteCode = new AtomicBoolean(false);
    for (Token token : elements) {

      String text = token.getToken().orElse("");
      formulaInfo.text.add(text);
      Parser parser = token.parser;
      if(parser instanceof FormulaInfoElementParser) {
        TypedToken<FormulaInfoElementParser> typed = token.typed(FormulaInfoElementParser.class);
        FormulaInfoElementParser formulaInfoElementParser = typed.getParser();
        KeyValue keyValue = formulaInfoElementParser.extract(typed);
        
        boolean match = false;
        match |= set(keyValue, "tags", (value)->formulaInfo.tags.addAll(List.of(value.split(","))));
        match |= set(keyValue, "description", (value)->formulaInfo.description = value);
        match |= set(keyValue, "periodStartInclusive", (value)->formulaInfo.periodStartInclusive = value);
        match |= set(keyValue, "periodEndExclusive", (value)->formulaInfo.periodEndExclusive = value);
        match |= set(keyValue, "siteId", (value)->formulaInfo.siteId = SiteId.of(value));
        match |= set(keyValue, "checkKind", (value)->formulaInfo.checkKind = CheckKinds.ofWithDynamicIfNotMatched(value));
        match |= set(keyValue, "hash", (value)->formulaInfo.hash = value);
        match |= set(keyValue, "hashByByteCode", (value)->formulaInfo.hashByByteCode = value);
        match |= set(keyValue, "formula", (value)->formulaInfo.formulaText = value);
        match |= set(keyValue, "javaCode", (value)->formulaInfo.javaCodeText = value);
        match |= set(keyValue, "byteCode", (value)->{
          formulaInfo.byteCodeAsHex = value;
          formulaInfo.byteCode = Unchecked.supplier(()->Hex.decodeHex(value.toCharArray())).get();
          hasByteCode.set(true);
        });
        if(match == false ) {
          formulaInfo.extraValueByKey.put(keyValue.getKey(), keyValue.getValue());
        }
      }
    }
    boolean formulaExists = StringUtils.isNoneBlank(formulaInfo.formulaText);
    boolean needsUpdate = formulaInfo.needsUpdate();
    if(needsUpdate && formulaExists) {
      formulaInfo.updateHash();
    }
    formulaInfo.updateClassName();
    
    if((false == hasByteCode.get() || needsUpdate) && formulaExists) {
      formulaInfo.updateCalculatorFromFormula(classLoader);
    }else {
//      System.out.println(new String(formulaInfo.byteCode));
      formulaInfo.updateCalculatorWithByteCode(classLoader);
    }
    return formulaInfo;
  }
  
  static boolean set(KeyValue keyValue , String targetKey , Consumer<String> valueConsumer) {
    if(keyValue.getKey().equals(targetKey)) {
      valueConsumer.accept(keyValue.getValue());
      return true;
    }
    return false;
  }
  
  @TokenExtractor
  public Map<String,String> extract(TypedToken<FormulaInfoParser> thisParserParsed){
    Map<String, String> valueByKey = thisParserParsed.flatten().stream()
        .filter(TokenPredicators.parsers(FormulaInfoElementParser.class))
        .map(token->token.typed(FormulaInfoElementParser.class))
        .map(token->token.getParser().extract(token))
        .collect(Collectors.toMap(KeyValue::getKey,KeyValue::getValue));
    
    return valueByKey;
  }

}
