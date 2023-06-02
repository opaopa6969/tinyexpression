package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.Name;
import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.elementary.ParenthesesParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class JavaStyleParenthesesParser extends JavaStyleDelimitedLazyChain {

	private static final long serialVersionUID = 6964996290002171327L;
	
	Parser inner;
	

	public JavaStyleParenthesesParser(Name name , Parser inner) {
		super(name);
		this.inner = inner;
	}


	public JavaStyleParenthesesParser(Parser inner) {
		super();
		this.inner = inner;
	}

	
	@TokenExtractor
	public static Token getParenthesesed(Token parenthesesed ){
		if(false == parenthesesed.parser instanceof ParenthesesParser){
			throw new IllegalArgumentException("this token did not generate from " + 
				ParenthesesParser.class.getName());
		}
		Parser contentsParser = JavaStyleParenthesesParser.class.cast(parenthesesed.parser).inner;
		return parenthesesed.getChildWithParser(parser -> parser.equals(contentsParser));
	}
	
	public Parser getParenthesesedParser(){
		return inner;
	}

	@Override
	public List<Parser> getLazyParsers() {
		return 
			new Parsers(
				new LeftParenthesisParser(),
				inner,
				new RightParenthesisParser()
			);

	}

	@TokenExtractor
	public Token getInnerParserParsed(Token thisParserParsed) {
//		return thisParserParsed.filteredChildren.get(1);
		return thisParserParsed.getChildWithParser(parser->parser.equals(inner));
	}
}
