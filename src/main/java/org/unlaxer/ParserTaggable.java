package org.unlaxer;

import java.util.Set;

import org.unlaxer.ast.ASTNodeKind;
import org.unlaxer.parser.Parser;

public interface ParserTaggable extends ParserFinder{

	public default Parser addTag(Tag... addings){

		Set<Tag> tags = getTags();
		for(Tag tag : addings){
			tags.add(tag);
		}
		return getThisParser();
	}

	public default boolean hasTag(Tag tag){
		return getTags().contains(tag);
	}

	public default Parser removeTag(Tag... removes){
		for(Tag remove : removes){
			getTags().remove(remove);
		}
		return getThisParser();
	}

	public Set<Tag> getTags();

	public default Parser setASTNodeKind(ASTNodeKind astNodeKind){
		return getThisParser();
	}

	public default ASTNodeKind astNodeKind(){
		return ASTNodeKind.NotSpecified;
	}

}
