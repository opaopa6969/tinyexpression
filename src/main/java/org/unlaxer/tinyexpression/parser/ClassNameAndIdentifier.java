package org.unlaxer.tinyexpression.parser;

public class ClassNameAndIdentifier{
	
	final String className;
	final String identifier;
	public ClassNameAndIdentifier(String className, String identifier) {
		super();
		this.className = className;
		this.identifier = identifier;
	}
	
	public String getClassName() {
		return className;
	}

	public String getIdentifier() {
		return identifier;
	}
}