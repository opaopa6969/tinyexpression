package org.unlaxer.tinyexpression.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.WildCardStringTerninatorParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoParser.Kind;
import org.unlaxer.util.annotation.TokenExtractor;

public class FormulaInfoElementParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(FormulaInfoElementHeaderParser.class),
        new WildCardStringTerninatorParser(true , Parser.get(FormulaInfoElementTerminatorParser.class))
          .addTag(Kind.value.tag())
    );
  }

  @TokenExtractor
  public KeyValue extract(TypedToken<FormulaInfoElementParser> thisParserParsed) {
    TypedToken<FormulaInfoElementHeaderParser> childWithParserTyped = 
        thisParserParsed.getChildWithParserTyped(FormulaInfoElementHeaderParser.class);
    
    String key = childWithParserTyped.getParser().extractKey(childWithParserTyped);
    String value = thisParserParsed.getChild(TokenPredicators.hasTag(Kind.value.tag())).getToken().orElseThrow().stripTrailing();
    return new KeyValue(key, removeInvalids(value));
  }
  
  String removeInvalids(String value) {
    String[] split = value.split("\n");
    List<String> valids = new ArrayList<>();
    for (String string : split) {
      if(string.startsWith("#") || string.strip().equals("")) {
        continue;
      }
      valids.add(string);
    }
    String collect = valids.stream().collect(Collectors.joining("\n"));
    return collect;
  }
  
  
  public static class KeyValue{
    public final String key;
    public final String value;
    public KeyValue(String key, String value) {
      super();
      this.key = key;
      this.value = value.stripTrailing();
    }
    public String getKey() {
      return key;
    }
    public String getValue() {
      return value;
    }
    
  }
}