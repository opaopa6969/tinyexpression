package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class SideEffectName1Parser extends JavaStyleDelimitedLazyChain{

	private static final long serialVersionUID = -5885382161035099103L;
	

	public SideEffectName1Parser() {
		super();
	}
	
	@Override
	public List<Parser> getLazyParsers() {
	  return
      new Parsers(
        Parser.get(()->new WordParser("with")),
        Parser.get(()->new WordParser("side")),
        Parser.get(()->new WordParser("effect"))
      );

	}
	
//	public static class ReturningParser extends WhiteSpaceDelimitedLazyChain{
//	  
//	}
}