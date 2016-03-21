package reflex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.antlr.runtime.RecognitionException;
import org.apache.log4j.Logger;
import org.junit.Test;

public class MathsTest extends AbstractReflexScriptTest {
	private static Logger log = Logger.getLogger(MathsTest.class);

	@Test
	public void testDivision1() throws RecognitionException {
		String program = "i = 20;\n" +
			"println(i/2); \n";

		String output = runScript(program, null);
		assertEquals("10", output.trim());
	}
	
	@Test
	public void testDivision2() throws RecognitionException {
		String program = "i = 21;\n" +
			"println(i/2); \n";

		String output = runScript(program, null);
		assertEquals("10.5", output.trim());
	}
	
	@Test
	public void testEqualsAndAssert() throws RecognitionException {
		String program = 
		"       //Number manipulation \n" +
		"       y = 2.65; //float \n" +
		"       x = 12; //integer \n" +

		"       z = x*y; \n" +
		"       println('value of z is :' + z); \n" +
		"       println('type of z is  :' + typeof(z)); \n" +

		"       assert('its all gone dave tong',z == 31.8); \n" +
		"       assert(z == 31.80); \n" +

		"       a=12.123; \n" +
		"       b=871938; \n" +
		"       c=a*b; \n" +
		"       println('value of c is :' + c); \n" +
		"       println('type of c is  :' + typeof(c)); \n" +
		"       //10570504.374 \n" +
		"       assert(c == 10570504.374); \n";
		String[] output = runScript(program, null).split("\n");
		assertEquals("value of z is :31.80", output[0]);
		assertEquals("type of z is  :number", output[1]);
		assertEquals("value of c is :10570504.374", output[2]);
		assertEquals("type of c is  :number", output[3]);
	}
	
}
