package org.unlaxer.tinyexpression;

import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public abstract class AbstractCalculationContext implements CalculationContext{
	
	public final int scale;
	public final RoundingMode roundingMode;
	public final Angle angle;
	
	public final Map<String,Number> valueByName = newMap();
	
	public final Map<String,Boolean> booleanByName = newMap();
	
	public final Map<String,String> stringByName = newMap();
	
	public final Map<String,Object> objectByName = newMap();
	
	Random random = new Random();
	
	AbstractCalculationContext(int scale, RoundingMode roundingMode , Angle angle) {
		super();
		this.scale = scale;
		this.roundingMode = roundingMode;
		this.angle = angle;
	}
	
	AbstractCalculationContext() {
		this(10,RoundingMode.HALF_UP , Angle.DEGREE);
	}

	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#set(java.lang.String, java.lang.String)
	 */
	@Override
	public void set(String name,String value) {
		stringByName.put(name, value);
	}
	
	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#getString(java.lang.String)
	 */
	@Override
	public Optional<String> getString(String name){
		return Optional.ofNullable(stringByName.get(name));
	}

	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#set(java.lang.String, float)
	 */
	@Override
	public void set(String name,float value) {
	  
		valueByName.put(name, value);
	}
	
	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#getValue(java.lang.String)
	 */
	@Override
	public Optional<Float> getValue(String name) {

		return Optional.ofNullable((Float)valueByName.get(name));
	}
	
	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#set(java.lang.String, boolean)
	 */
	@Override
	public void set(String name , boolean value) {
		booleanByName.put(name, value);
	}
	
	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#getBoolean(java.lang.String)
	 */
	@Override
	public Optional<Boolean> getBoolean(String name){
		return Optional.ofNullable(booleanByName.get(name));
	}
	
	
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> Optional<T> getObject(String name, Class<T> clazz) {
		return (Optional<T>) Optional.ofNullable(objectByName.get(name));
	}

	@Override
	public void setObject(String name, Object object) {
		objectByName.put(name, object);
	}

	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#isExists(java.lang.String)
	 */
	@Override
	public boolean isExists(String name) {
		return valueByName.get(name) != null ||
				booleanByName.get(name) != null ||
				stringByName.get(name) != null ||
				objectByName.get(name) != null;
	}
	
	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#angle(double)
	 */
	@Override
	public double radianAngle(double angleValue) {
		if(angle == Angle.RADIAN){
			return angleValue;
		}
		return Math.toRadians(angleValue);
	}
	
	/* (non-Javadoc)
	 * @see org.unlaxer.tinyexpression.ICalculationContext#nextRandom()
	 */
	@Override
	public float nextRandom() {
		return random.nextFloat();
	}
	
	@Override
	public Angle angle() {
		return angle;
	}

	@Override
	public int scale() {
		return scale;
	}

	@Override
	public RoundingMode roundingMode() {
		return roundingMode;
	}

	public abstract <T> Map<String,T> newMap();

  @Override
  public void set(String name, Number value) {
    valueByName.put(name, value);
  }

  @Override
  public Optional<? extends Number> getNumber(String name) {
    return Optional.ofNullable(valueByName.get(name));
  }
	
	

}