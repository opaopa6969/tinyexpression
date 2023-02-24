package org.unlaxer.parser.posix;

import org.unlaxer.Name;
import org.unlaxer.parser.StaticParser;
import org.unlaxer.parser.elementary.MappedSingleCharacterParser;

public class AlphabetUnderScoreParser extends MappedSingleCharacterParser implements StaticParser{

	private static final long serialVersionUID = 1820592725022624691L;

	public AlphabetUnderScoreParser() {
		this(null);
	}
	
	public AlphabetUnderScoreParser(Name name) {
		super("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_");
	}


}