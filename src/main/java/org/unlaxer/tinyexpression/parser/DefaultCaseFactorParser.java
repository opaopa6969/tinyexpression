package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class DefaultCaseFactorParser extends JavaStyleDelimitedLazyChain{
	
	private static final long serialVersionUID = -955174558962757636L;


	public DefaultCaseFactorParser() {
		super();
	}

	@Override
	public List<Parser> getLazyParsers() {
		return
	    new Parsers(
        new WordParser(","),
        new WordParser("default"),
        new WordParser("->"),
        Parser.get(ExpressionParser.class)//3
      );
	}
	
  @TokenExtractor
	public static Token getExpression(Token thisParserParsed) {
		return thisParserParsed.getChildWithParser(ExpressionParser.class); //3
	}

}