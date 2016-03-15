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

public class AssertTest {
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
	

		
	public String runScript(String program, Map<String, Object> injectedVars)
			throws RecognitionException, ReflexParseException {
		StringBuilder sb = new StringBuilder();

		ReflexLexer lexer = new ReflexLexer();
		lexer.setCharStream(new ANTLRStringStream(program));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ReflexParser parser = new ReflexParser(tokens);

		CommonTree tree = (CommonTree) parser.parse().getTree();

		CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
		ReflexTreeWalker walker = new ReflexTreeWalker(nodes, parser.languageRegistry);

		IReflexHandler handler = walker.getReflexHandler();
		handler.setOutputHandler(new IReflexOutputHandler() {

			@Override
			public boolean hasCapability() {
				return false;
			}

			@Override
			public void printLog(String text) {
				sb.append(text);
			}

			@Override
			public void printOutput(String text) {
				sb.append(text);
			}

			@Override
			public void setApi(ScriptingApi api) {
			}
		});

		if (injectedVars != null && !injectedVars.isEmpty()) {
			for (Map.Entry<String, Object> kv : injectedVars.entrySet()) {
				walker.currentScope.assign(kv.getKey(),
						kv.getValue() == null ? new ReflexNullValue() : new ReflexValue(kv.getValue()));
			}
		}

		@SuppressWarnings("unused")
		ReflexNode returned = walker.walk();
		InstrumentDebugger instrument = new InstrumentDebugger();
		instrument.setProgram(program);
		ReflexValue retVal = (returned == null) ? null : returned.evaluateWithoutScope(instrument);
		instrument.getInstrumenter().log();
		return sb.toString();
	}

	public String runScriptCatchingExceptions(String program, Map<String, Object> injectedVars) {
		StringBuilder sb = new StringBuilder();
		StringBuilder lexerError = new StringBuilder();
		StringBuilder parserError = new StringBuilder();
		StringBuilder logs = new StringBuilder();
		
		Logger.getLogger(MatchNode.class).addAppender(new AppenderSkeleton() {
			@Override
			public void close() {				
			}

			@Override
			public boolean requiresLayout() {
				return false;
			}

			@Override
			protected void append(LoggingEvent event) {
				logs.append(event.getMessage().toString());
			};
		});

		try {
			ReflexLexer lexer = new ReflexLexer() {
				@Override
				public void emitErrorMessage(String msg) {
					lexerError.append(msg);
				}
			};
			lexer.setCharStream(new ANTLRStringStream(program));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			ReflexParser parser = new ReflexParser(tokens) {
				@Override
				public void emitErrorMessage(String msg) {
					parserError.append(msg);
				}
			};

			CommonTree tree = (CommonTree) parser.parse().getTree();

			CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
			ReflexTreeWalker walker = new ReflexTreeWalker(nodes, parser.languageRegistry);

			IReflexHandler handler = walker.getReflexHandler();
			handler.setOutputHandler(new IReflexOutputHandler() {

				@Override
				public boolean hasCapability() {
					return false;
				}

				@Override
				public void printLog(String text) {
					sb.append(text);
				}

				@Override
				public void printOutput(String text) {
					sb.append(text);
				}

				@Override
				public void setApi(ScriptingApi api) {
				}
			});

			if (injectedVars != null && !injectedVars.isEmpty()) {
				for (Map.Entry<String, Object> kv : injectedVars.entrySet()) {
					walker.currentScope.assign(kv.getKey(),
							kv.getValue() == null ? new ReflexNullValue() : new ReflexValue(kv.getValue()));
				}
			}

			@SuppressWarnings("unused")
			ReflexNode returned = walker.walk();
			InstrumentDebugger instrument = new InstrumentDebugger();
			instrument.setProgram(program);
			ReflexValue retVal = (returned == null) ? null : returned.evaluateWithoutScope(instrument);
			instrument.getInstrumenter().log();
		} catch (Exception e) {
			sb.append(e.getMessage()).append("\n");
		}
		sb.append("-----\n");
		sb.append(lexerError.toString()).append("\n");
		sb.append("-----\n");
		sb.append(parserError.toString()).append("\n");
		sb.append("-----\n");
		sb.append(logs.toString()).append("\n");
		return sb.toString();
	}
}