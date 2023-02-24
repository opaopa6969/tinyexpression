package org.unlaxer.tinyexpression.model;

public enum CharacterAttribute{
	//color
	blue,red,green,highlight,
	//like μPD3301
	blink,underline,upperline,reverse,secret,//,semiGraphic
	//font
	italic,bold,
	//backColor
	blueBack,redBack,greenBack,highlightBack
	;
	public int code;

	private CharacterAttribute() {
		this.code = (int)Math.pow(2,ordinal());
		if(ordinal()>31){
			throw new IllegalStateException("attributes too many");
		}
	}
}