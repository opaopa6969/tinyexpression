package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class CaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = -475039384168549876L;


	public CaseFactorParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
	  return 
      new Parsers(
        Parser.get(BooleanClauseParser.class),//0
        new WordParser("->"),
        Parser.get(ExpressionParser.class)//2
      );
	}

  @TokenExtractor
	public static Token getBooleanClause(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(BooleanClauseParser.class); //0
	}

  @TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(ExpressionParser.class);//2
	}

}