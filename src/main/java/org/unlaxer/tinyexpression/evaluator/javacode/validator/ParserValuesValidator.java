package org.unlaxer.tinyexpression.evaluator.javacode.validator;


public class ParserValuesValidator {

  private static final String MSG_INVALID_TIME_RANGE = "Invalid fromHour or toHour value. Should be a number between 0 - 23";


  public void validateTimeRangeValues(String fromHourStr, String toHourStr) throws ExpressionValidationException {
    float fromHour;
    float toHour;

    try {
      fromHour = Float.parseFloat(fromHourStr);
      toHour = Float.parseFloat(toHourStr);
    } catch (NumberFormatException e) {
      throw new ExpressionValidationException(MSG_INVALID_TIME_RANGE, e);
    }

    if (fromHour < 0 || fromHour > 23 ||
        toHour < 0 || toHour > 23) {
      throw new ExpressionValidationException(MSG_INVALID_TIME_RANGE);
    }
  }


}
