package org.unlaxer.tinyexpression.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.unlaxer.EnclosureDirection;
import org.unlaxer.Range;
import org.unlaxer.Token;
import org.unlaxer.TokenEnclosureUtil;
import org.unlaxer.parser.ParsersSpecifier;
import org.unlaxer.tinyexpression.CalculateResult;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.number.NumberTermParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.util.StringUtil;

public class CalculatorEditableLineModel implements EditableLineModel{
	
	public static final ParsersSpecifier enclosureMatchers=
		new ParsersSpecifier(
			NumberExpressionParser.class,
			NumberTermParser.class,
			NumberFactorParser.class
		);
	
	final Calculator<?> calculator;
	CalculationContext calculateContext;
	
	public final Deque<EditHistory> calculations = new ArrayDeque<EditHistory>();
	public final Deque<EditHistory> edits = new ArrayDeque<EditHistory>();


	public CalculatorEditableLineModel(Calculator<?> calculator , CalculationContext calculateContext) {
		this(calculator , calculateContext , "");
	}
	
	public CalculatorEditableLineModel(Calculator<?> calculator , CalculationContext calculateContext , String formula) {
		super();
		this.calculateContext = calculateContext;
		this.calculator = calculator;
		CalculateResult calculateResult = calculator.calculate(calculateContext,formula);
		EditHistory editHistory = 
				new EditHistory(EditAction.of(ActionType.initialized), 0 , calculateContext, calculateResult);

		edits.add(editHistory);
		
		editHistory = 
				new EditHistory(new EditAction(formula) ,
						formula.length(), calculateContext, calculateResult); 
		edits.add(editHistory);
	}

	//
	// modify formula
	//
	
	@Override
	public DeleteBehavior backSpace() {
		// first implementation -> delete force
		// TODO second implementation -> if ast was broken when delete token then suggests operation(change function , delete , etc)
		boolean success = backspace(getPosition());
		DeleteBehavior deleteBehavior = new DeleteBehavior();
		deleteBehavior.deleted = success;
		return deleteBehavior;
	}
	@Override
	public DeleteBehavior delete() {
		// first implementation -> delete force
		// TODO second implementation -> if ast was broken when delete token then suggests operation(change function , delete , etc)  
		boolean success = delete(getPosition());
		DeleteBehavior deleteBehavior = new DeleteBehavior();
		deleteBehavior.deleted = success;
		return deleteBehavior;
	}
	

	
	@Override
	public boolean insert(String part) {
		
		//first implementation -> do simple !
		
		String formulaString = getFormulaString();
		int position = getPosition();
		
		Optional<Token> selection = getSelection();
		Range tokenRange = selection.isPresent() ?
				selection.get().tokenRange :
					new Range(position , position+1);
		String deleted = StringUtil.deleteAndInsert(formulaString, tokenRange , part);
		EditHistory createInsertEditHistory = createInsertEditHistory(deleted , tokenRange, position, part);
		edits.add(createInsertEditHistory);
		return true;
	}

	boolean backspace(int position){
		return delete(ActionType.backSpace , position-1);
	}
	boolean delete(int position){
		return delete(ActionType.delete, position);
	}

	boolean delete(ActionType actionType , int position){
		
		String formulaString = getFormulaString();
		
		Optional<Token> selection = getSelection();
		if(selection.isPresent()){
			Range tokenRange = selection.get().tokenRange;
			String deleted = StringUtil.delete(formulaString, tokenRange);
			return addDeleteAction(actionType, position, formulaString , deleted , Optional.of(tokenRange));
			
		}else{
			String deleted = StringUtil.delete(formulaString , position);
			return addDeleteAction(actionType, position, formulaString, deleted , Optional.empty());
		}
	}

	private boolean addDeleteAction(
			ActionType actionType, int position, String formulaString, String deleted ,Optional<Range> effectedRange) {
		
		boolean success = deleted.length() != formulaString.length();
		if(success){
			Range range = effectedRange.orElse(new Range(position , position +1));
			EditHistory createEditHistory = createEditHistory(actionType , deleted, range , position);
			edits.add(createEditHistory);
		}
		return success;
	}
	
	
	EditHistory createEditHistory(ActionType actionType , String formula ,Range range ,int  position){
		
		CalculateResult calculateResult = calculator.calculate(calculateContext,formula);
		EditHistory editHistory = 
				new EditHistory(new EditAction(actionType , range), position , calculateContext, calculateResult);
		
		return editHistory;
	}
	
	EditHistory createInsertEditHistory(String formula ,Range range ,int  position , String insertion){
		
		CalculateResult calculateResult = calculator.calculate(calculateContext,formula);
		
		EditHistory editHistory = 
				new EditHistory(new EditAction(insertion,range), position , calculateContext, calculateResult);
		
		return editHistory;
	}
	
	//
	// position
	//
	
	@Override
	public boolean cursorRight() {
		Optional<Token> currentSelection = getSelection();
		if(currentSelection.isPresent()){
			updatePosition(ActionType.cursorRight , currentSelection.get().tokenRange.endIndexExclusive);
			return true;
		}
		String formulaString = getFormulaString();
		int position = getPosition();
		boolean movable = formulaString.length() > position;
		position = movable ? ++position : position;
		updatePosition(ActionType.cursorRight , position);
		return movable;
	}
	
	
	@Override
	public boolean cursorLeft() {
		Optional<Token> currentSelection = getSelection();
		if(currentSelection.isPresent()){
			int position = Math.max(0, currentSelection.get().tokenRange.startIndexInclusive-1);
			updatePosition(ActionType.cursorLeft , position);
			return true;
		}
		int position = getPosition();
		boolean movable = 0 < position;
		position = movable ? --position : position;
		updatePosition(ActionType.cursorLeft, position);
		return movable;
	}
	
	@Override
	public boolean home() {
		int position = getPosition();
		boolean movable = 0 < position;
		position = 0;
		updatePosition(ActionType.home , position);
		return movable;
	}

	@Override
	public boolean end() {
		String formulaString = getFormulaString();
		int position = getPosition();
		boolean movable = formulaString.length() > position;
		position = formulaString.length();
		updatePosition(ActionType.end, position);
		return movable;
	}
	
	void updatePosition(ActionType actionType , int position){
		EditHistory updated = getCurrent().clone(EditAction.of(actionType));
		updated.caretPosition = position;
		edits.push(updated);
	}
	
	//
	// select
	//
	
	@Override
	public Optional<Token> selectEnclosure(EnclosureDirection enclosureDirection) {
		Optional<Token> rootToken = getCurrent().calculateResult.tokenAst;
		if(false == rootToken.isPresent()){
			return Optional.empty();
		}
		int position = getPosition();
		Optional<Token> selected = 
				TokenEnclosureUtil.getEnclosureWithToken(rootToken.get(),
						enclosureDirection, position, getSelection(), enclosureMatchers);
		
		if(selected.isPresent()){
			EditHistory clone = getCurrent().clone(EditAction.of(ActionType.of(enclosureDirection)));
			clone.select = selected;
			edits.add(clone);
			return selected;
		}
		return Optional.empty();
	}

	@Override
	public Optional<Token> getSelection() {
		return getCurrent().select;
	}
	
	//
	// history 
	//
	
	@Override
	public boolean addCalculateHistory() {
		EditHistory current = getCurrent();
		if(current.calculateResult.success){
			calculations.add(current);
		}
		return false;
	}
	
	EditHistory getCurrent(){
		return edits.peekFirst();
	}
	
	String getFormulaString(){
		return getCurrent().calculateResult.parseContext.source.toString();
	}
	
	int getPosition(){
		return getCurrent().caretPosition;
	}

}