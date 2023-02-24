package org.unlaxer.tinyexpression.model;

public class CharacterAttributes{
	
	public int attributres;
	public void setAttributes(CharacterAttribute...characterAttributes){
		for (CharacterAttribute characterAttribute : characterAttributes) {
			if(has(characterAttribute)){
				continue;
			}
			attributres += characterAttribute.code;
		}
	}
	public void resetAttributes(CharacterAttribute...characterAttributes){
		for (CharacterAttribute characterAttribute : characterAttributes) {
			if(has(characterAttribute)){
				attributres -= characterAttribute.code;
			}
		}
	}
	public boolean has(CharacterAttribute characterAttribute){
		return (attributres & characterAttribute.code) != 0;
	}
}