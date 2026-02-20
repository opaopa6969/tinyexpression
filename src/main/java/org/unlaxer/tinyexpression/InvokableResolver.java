package org.unlaxer.tinyexpression;

import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser.MethodAndParameters;

public interface InvokableResolver<INSTANCE,RETURNING>{
	
	 Invokable<INSTANCE,RETURNING> resolve(MethodAndParameters methodAndParameters);
//			 
//			SideEffectExpressionParameterParser paramter , 
//			Class<? extends INSTANCE> instanceClass , 
//			Class<? extends RETURNING> returningClass);
	
	Class<? extends INSTANCE> instanceClass();
	Class<? extends RETURNING> returningClass();
	
	
//	public static class VoidInvokableResolver<T> implements InvokableResolver<T, Void>{
//		
//		final T instance;
//		
//
//		public VoidInvokableResolver(T instance) {
//			super();
//			this.instance = instance;
//		}
//
//		@Override
//		public Invokable<T, Void> resolve(MethodAndParameters methodAndParameters){
//			
//		}
//
//		@SuppressWarnings("unchecked")
//		@Override
//		public Class<? extends T> instanceClass() {
//			return (Class<T>) instance.getClass();
//		}
//
//		@Override
//		public Class<? extends Void> returningClass() {
//			return Void.class;
//		}
//		
//	}

}