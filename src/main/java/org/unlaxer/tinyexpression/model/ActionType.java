package org.unlaxer.tinyexpression.model;

import org.unlaxer.EnclosureDirection;

public enum ActionType{
	// modify formula
	backSpace,
	delete,
	insert,
	// position
	cursorRight,
	cursorLeft,
	home,
	end,
	// select
	selectEnclosureOuter,
	selectEnclosureInner,
	// history 
	initialized,
	addCalculateHistory
	;

	public static ActionType of(EnclosureDirection enclosureDirection) {
		return enclosureDirection.isInner() ? selectEnclosureInner : selectEnclosureOuter;
	}
}