import org.unlaxer.tinyexpression.CalculationContext;

public class NativeDemo {
    private int counter;
    public float add(float a, float b) { return a + b; }
    public String greet(CalculationContext vars, String s) { return vars.getString("prefix").orElse("") + s; }
    public boolean digits(String s) { return !s.isEmpty() && s.chars().allMatch(c -> c >= '0' && c <= '9'); }
    public float scoped(CalculationContext vars) { return vars.getNumber("x").get().floatValue(); }
    public float next() { return ++counter; }
    public float fail() { throw new IllegalStateException("deliberate"); }
    public float floatArg(float v) { return v; }
    public double doubleArg(double v) { return v; }
    public int intArg(int v) { return v; }
    public long longArg(long v) { return v; }
    public short shortArg(short v) { return v; }
    public byte byteArg(byte v) { return v; }
    public boolean boolArg(boolean v) { return v; }
    public String nullable(String v) { return v; }
    public boolean isNull(String v) { return v == null; }
    public Object identity(Object v) { return v; }
    public Object object() { return new NativeObject(); }
    public String objectText(Object v) { return v.toString(); }
}
class NativeObject { public String toString() { return "opaque"; } }
