package org.unlaxer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.unlaxer.context.ParseContext;

public class TestResult{
	
	public final Parsed parsed;
	public final ParseContext parseContext;
	public final Optional<String> lastToken;
	public final List<Boolean> assertValues;

	
	public TestResult(Parsed parsed, ParseContext parseContext, Optional<String> lastToken  , List<Boolean> assertValues) {
		super();
		this.parsed = parsed;
		this.parseContext = parseContext;
		this.lastToken = lastToken;
		this.assertValues = assertValues;
	}
	
	
	public TestResult(Parsed parsed, ParseContext parseContext, Optional<String> lastToken  , Boolean...  assertValues) {
		super();
		this.parsed = parsed;
		this.parseContext = parseContext;
		this.lastToken = lastToken;
		this.assertValues = List.of(assertValues);
	}
	
	public TestResult(Parsed parsed, ParseContext parseContext, Optional<String> lastToken) {
		super();
		this.parsed = parsed;
		this.parseContext = parseContext;
		this.lastToken = lastToken;
		this.assertValues = new ArrayList<>();
	}
	
	public void add(boolean assertValue) {
		
		this.assertValues.add(assertValue);
	}
	
	public boolean isOK() {
		
		boolean allMatch = assertValues.stream()
			.allMatch(x->x);
		return allMatch;
	}
	
	public boolean  isNG() {
		return false == isOK();
	}
}