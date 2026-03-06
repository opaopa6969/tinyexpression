package org.unlaxer.tinyexpression.model;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.CalculateResult;
import org.unlaxer.tinyexpression.CalculationContext;

public class EditHistory implements Cloneable{
	
	public final EditAction editAction;
	public final CalculationContext calculateContext;
	public final CalculateResult calculateResult;
	//TODO attach attribute to token before rendering
//	public final Map<Token,CharacterAttributes> attributesByToken;
	/**
	 * initial position is 0
	 * after first character inserted, position is 1 and it position's element is empty
	 */
	public int caretPosition;
	public Optional<Token> select = Optional.empty();
	
	public EditHistory(EditAction editAction , int caretPosition, 
			CalculationContext calculateContext, 
			CalculateResult calculateResult){
		super();
		this.editAction = editAction;
		this.caretPosition = caretPosition;
		this.calculateContext = calculateContext;
		this.calculateResult = calculateResult;
	}

	public EditHistory clone(EditAction editAction)  {
		return new EditHistory(editAction , caretPosition, calculateContext, calculateResult);//, 
	}
}