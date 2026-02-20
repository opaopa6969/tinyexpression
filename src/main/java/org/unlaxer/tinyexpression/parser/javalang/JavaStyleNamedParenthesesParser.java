package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.util.annotation.TokenExtractor;

public abstract class JavaStyleNamedParenthesesParser extends JavaStyleDelimitedLazyChain{
	

	public JavaStyleNamedParenthesesParser(Name name) {
		super(name);
	}
	
	public JavaStyleNamedParenthesesParser() {
		super();
	}

	public abstract Parser nameParser();
	
	public abstract Parser innerParser();
	
	
	@TokenExtractor
	public static Token getInnerParserParsed(Token thisParserParsed) {
		
		// get next token of LeftParenthesisParser
		int childIndexWithParser = thisParserParsed.getChildIndexWithParser(LeftParenthesisParser.class);
		return thisParserParsed.getAstNodeChildren().get(childIndexWithParser+1);
	}
	
	@Override
	public org.unlaxer.parser.Parsers getLazyParsers() {
		return
			new Parsers(
				nameParser(),
				new LeftParenthesisParser(),
				innerParser(),
				new RightParenthesisParser()
			);

	}
}