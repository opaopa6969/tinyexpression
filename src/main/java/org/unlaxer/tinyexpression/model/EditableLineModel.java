package org.unlaxer.tinyexpression.model;

import java.util.Optional;

import org.unlaxer.EnclosureDirection;
import org.unlaxer.Token;

public interface EditableLineModel {
	
	// modify formula
	public DeleteBehavior backSpace();
	public DeleteBehavior delete();
	public boolean insert(String part);

	// position
	public boolean cursorRight();
	public boolean cursorLeft();
	public boolean home();
	public boolean end();
	
	// select
	public Optional<Token> selectEnclosure(EnclosureDirection enclosureDirection);
	public Optional<Token> getSelection();
	
	// history 
	public boolean addCalculateHistory();

	//TODO implementation 
//	public boolean escape();
//	public void selectStart();
//	public void selectEnd();
//	public Range copy();
//	public Range cut();
//	public void paste();
//	public boolean historyUp();
//	public boolean historyDown();
//	public boolean redo();
//	public boolean undo();
	
	public static class Cursor{
		public int caretPosition=0;
		public int topLevelTokenIndex=0;
		public Token topLevelToken;
		public Token bottomLevelToken;
	}
}
