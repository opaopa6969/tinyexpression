package org.unlaxer.tinyexpression;

import java.util.Optional;

public class Errors{
	
	public final Optional<Throwable> raisedException;
	
	public final Optional<String> message;

	public Errors() {
		super();
		raisedException = Optional.empty();
		message=Optional.empty();
	}

	public Errors(Optional<Throwable> raisedException, Optional<String> message) {
		super();
		this.raisedException = raisedException;
		this.message = message;
	}

	public Errors(Exception exception) {
		raisedException = Optional.of(exception);
		message = Optional.ofNullable(exception.getMessage());
	}
}