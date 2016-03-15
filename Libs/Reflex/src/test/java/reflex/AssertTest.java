package reflex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Map;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

import rapture.common.api.ScriptingApi;
import reflex.node.MatchNode;
import reflex.node.ReflexNode;
import reflex.util.InstrumentDebugger;
import reflex.value.ReflexValue;
import reflex.value.internal.ReflexNullValue;

public class AssertTest extends AbstractReflexScriptTest {
	private static Logger log = Logger.getLogger(AssertTest.class);

	@Test
	public void happyPathAssertTrue() throws RecognitionException {
		String program = "assert(true); \n println('Success');\n";
		String output = runScript(program, null);
		assertEquals("Success\n", output);
	}

	@Test
	public void happyPathAssertFalse() throws RecognitionException {
		String program = "assert(false); \n println('Fail');\n";
		try {
			String output = this.runScript(program, null);
			fail("Expected AssertionError");
		} catch (AssertionError e) {
			assertEquals("false", e.getMessage());
		}
	}
	
	@Test
	public void happyPathAssertWithArgTrue() throws RecognitionException {
		String program = "assert('Fail', true); \n println('Success');\n";
		String output = runScript(program, null);
		assertEquals("Success\n", output);
	}
	

	@Test
	public void happyPathAssertWithArgFalse() throws RecognitionException {
		String program = "assert('Success', false); \n println('Fail');\n";
		try {
			String output = this.runScript(program, null);
			fail("Expected AssertionError");
		} catch (AssertionError e) {
			assertEquals("Success", e.getMessage());
		}
	}
	
	@Test
	public void happyPathAssertWithArgFalse2() throws RecognitionException {
		String program = "foo='Success'; \n assert(\"${foo}\", false); \n println('Fail');\n";
		try {
			String output = this.runScript(program, null);
			fail("Expected AssertionError");
		} catch (AssertionError e) {
			assertEquals("Success", e.getMessage());
		}
	}
}