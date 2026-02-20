package org.unlaxer.tinyexpression.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.Range;

public class EditAction{
	
	public final ActionType actionType;
	public final Optional<String> input;
	public final Optional<Range> effectedRange;
	public EditAction(ActionType actionType) {
		super();
		this.actionType = actionType;
		this.input = Optional.empty();
		this.effectedRange = Optional.empty();
	}
	
	public EditAction(String input) {
		super();
		this.actionType = ActionType.insert;
		this.input = Optional.of(input);
		effectedRange = Optional.empty();
	}
	
	public EditAction(String input , Range range) {
		super();
		this.actionType = ActionType.insert;
		this.input = Optional.of(input);
		effectedRange = Optional.of(range);
	}

	
	public EditAction(ActionType actionType , Range range) {
		super();
		this.actionType = ActionType.insert;
		effectedRange = Optional.of(range);
		this.input = Optional.empty();
	}
	
	static final Map<ActionType , EditAction> editActionByActionType = new HashMap<ActionType , EditAction>();
	
	public static EditAction of(ActionType actionType){
		if(actionType == ActionType.insert){
			throw new UnsupportedOperationException("you have to specify inputString. create with constructor!");
		}
		synchronized (actionType) {
			EditAction editAction = editActionByActionType.get(actionType);
			if(editAction == null){
				editAction = new EditAction(actionType);
				editActionByActionType.put(actionType, editAction);
			}
			return editAction;
		}
	}
}