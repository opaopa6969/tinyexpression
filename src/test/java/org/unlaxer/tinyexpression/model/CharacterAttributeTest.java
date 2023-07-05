package org.unlaxer.tinyexpression.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CharacterAttributeTest {

	@Test
	public void test() {
		assertEquals(1, CharacterAttribute.blue.code);
		assertEquals(2, CharacterAttribute.red.code);
		assertEquals(4, CharacterAttribute.green.code);
		
		CharacterAttributes attributes = new CharacterAttributes();
		CharacterAttribute[] sets = {
				CharacterAttribute.green,
				CharacterAttribute.secret,
				CharacterAttribute.italic
		};
		
		attributes.setAttributes(sets);
		for(CharacterAttribute characterAttribute : sets){
			assertTrue(attributes.has(characterAttribute));
		}
		assertFalse(attributes.has(CharacterAttribute.blue));
		assertFalse(attributes.has(CharacterAttribute.blink));
		assertFalse(attributes.has(CharacterAttribute.bold));
	}

}
