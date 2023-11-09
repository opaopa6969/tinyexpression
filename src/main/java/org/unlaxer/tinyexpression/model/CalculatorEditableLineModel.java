package org.unlaxer.tinyexpression.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.unlaxer.CodePointIndex;
import org.unlaxer.EnclosureDirection;
import org.unlaxer.Range;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenEnclosureUtil;
import org.unlaxer.parser.ParsersSpecifier;
import org.unlaxer.tinyexpression.CalculateResult;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.number.NumberTermParser;
import org.unlaxer.util.SourceUtil;

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
		this(calculator , calculateContext , StringSource.createRootSource(""));
	}
	
	public CalculatorEditableLineModel(Calculator<?> calculator , CalculationContext calculateContext , Source formula) {
		super();
		this.calculateContext = calculateContext;
		this.calculator = calculator;
		CalculateResult calculateResult = calculator.calculate(calculateContext,formula);
		EditHistory editHistory = 
				new EditHistory(EditAction.of(ActionType.initialized), 0 , calculateContext, calculateResult);

		edits.add(editHistory);
		
		editHistory = 
				new EditHistory(new EditAction(formula.sourceAsString()) ,
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
	  
	  StringSource partSource = StringSource.createRootSource(part);
	  
		
		Source formulaString = getFormulaString();
		CodePointIndex position = new CodePointIndex(getPosition());
		
		Optional<Token> selection = getSelection();
		Range tokenRange = selection.isPresent() ?
				selection.get().getSource().cursorRange().toRange() :
					new Range(position , position.newWithAdd(1));
		Source deleted = SourceUtil.newWithDeleteAndInsert(formulaString, tokenRange , partSource);
		EditHistory createInsertEditHistory = createInsertEditHistory(deleted , tokenRange, position, partSource);
		edits.add(createInsertEditHistory);
		return true;
	}

	boolean backspace(CodePointIndex position){
		return delete(ActionType.backSpace , position.newWithMinus(1));
	}
	boolean delete(CodePointIndex position){
		return delete(ActionType.delete, position);
	}

	boolean delete(ActionType actionType , CodePointIndex position){
		
		Source formulaString = getFormulaString();
		
		Optional<Token> selection = getSelection();
		if(selection.isPresent()){
			Range tokenRange = selection.get().getSource().cursorRange().toRange();
			Source deleted = SourceUtil.newWithDelete(formulaString, tokenRange);
			return addDeleteAction(actionType, position, formulaString , deleted , Optional.of(tokenRange));
			
		}else{
			Source deleted = SourceUtil.newWithDelete(formulaString , position);
			return addDeleteAction(actionType, position, formulaString, deleted , Optional.empty());
		}
	}

	private boolean addDeleteAction(
			ActionType actionType, CodePointIndex position, Source formulaString, Source deleted ,Optional<Range> effectedRange) {
		
		boolean success = deleted.length() != formulaString.length();
		if(success){
			Range range = effectedRange.orElse(new Range(position , position.newWithPlus(1)));
			EditHistory createEditHistory = createEditHistory(actionType , deleted, range , position);
			edits.add(createEditHistory);
		}
		return success;
	}
	
	
	EditHistory createEditHistory(ActionType actionType , Source formula ,Range range ,CodePointIndex position){
		
		CalculateResult calculateResult = calculator.calculate(calculateContext,formula);
		EditHistory editHistory = 
				new EditHistory(new EditAction(actionType , range), position.value() , calculateContext, calculateResult);
		
		return editHistory;
	}
	
	EditHistory createInsertEditHistory(Source formula ,Range range ,CodePointIndex position , Source insertion){
		
		CalculateResult calculateResult = calculator.calculate(calculateContext,formula);
		
		EditHistory editHistory = 
				new EditHistory(new EditAction(insertion.sourceAsString(),range), position.value() , calculateContext, calculateResult);
		
		return editHistory;
	}
	
	//
	// position
	//
	
	@Override
	public boolean cursorRight() {
		Optional<Token> currentSelection = getSelection();
		if(currentSelection.isPresent()){
			updatePosition(ActionType.cursorRight , 
			    currentSelection.get().getSource().cursorRange().endIndexExclusive.getPosition());
			return true;
		}
		Source formulaString = getFormulaString();
		CodePointIndex position = getPosition();
		boolean movable = formulaString.codePointLength().gt(position);
		position = movable ? position.newWithIncrements() : position;
		updatePosition(ActionType.cursorRight , position);
		return movable;
	}
	
	
	@Override
	public boolean cursorLeft() {
		Optional<Token> currentSelection = getSelection();
		if(currentSelection.isPresent()){
			CodePointIndex position = new CodePointIndex(			    
			    Math.max(0, currentSelection.get().getSource().cursorRange().startIndexInclusive().getPosition().newWithMinus(1).value())
	    );
			updatePosition(ActionType.cursorLeft , position);
			return true;
		}
		CodePointIndex position = getPosition();
		boolean movable = position.isGreaterThanZero();
		position = movable ? position.newWithDecrements() : position;
		updatePosition(ActionType.cursorLeft, position);
		return movable;
	}
	
	@Override
	public boolean home() {
		CodePointIndex position = getPosition();
		boolean movable = position.isGreaterThanZero();
		position = new CodePointIndex(0);
		updatePosition(ActionType.home , position);
		return movable;
	}

	@Override
	public boolean end() {
		Source formulaString = getFormulaString();
		CodePointIndex position = getPosition();
		boolean movable = formulaString.codePointLength().gt(position);
		position = new CodePointIndex(formulaString.codePointLength());
		updatePosition(ActionType.end, position);
		return movable;
	}
	
	void updatePosition(ActionType actionType , CodePointIndex position){
		EditHistory updated = getCurrent().clone(EditAction.of(actionType));
		updated.caretPosition = position.value();
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
		CodePointIndex position = getPosition();
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
	
	Source getFormulaString(){
		return getCurrent().calculateResult.parseContext.source;
	}
	
	CodePointIndex getPosition(){
		return new CodePointIndex(getCurrent().caretPosition);
	}

}