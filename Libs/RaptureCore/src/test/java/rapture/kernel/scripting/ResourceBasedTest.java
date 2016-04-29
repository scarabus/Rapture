/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2016 Incapture Technologies LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package rapture.kernel.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;

import rapture.common.api.ScriptingApi;
import rapture.common.client.HttpLoginApi;
import rapture.common.client.ScriptClient;
import rapture.common.client.SimpleCredentialsProvider;
import rapture.common.impl.jackson.JacksonUtil;
import reflex.IReflexDataHandler;
import reflex.IReflexHandler;
import reflex.IReflexOutputHandler;
import reflex.IReflexScriptHandler;
import reflex.ReflexLexer;
import reflex.ReflexParser;
import reflex.ReflexScriptDataHandler;
import reflex.ReflexTreeWalker;
import reflex.debug.NullDebugger;
import reflex.debug.ReflexPrintingDebugger;
import reflex.node.ReflexNode;
import reflex.util.InstrumentDebugger;
import reflex.value.ReflexValue;
import reflex.value.internal.ReflexNullValue;

public class ResourceBasedTest {

    public static ScriptingApi scriptApi = null;

    public static String getResourceAsString(Object context, String path) {
        InputStream is = (context == null ? ResourceBasedTest.class : context.getClass()).getResourceAsStream(path);

        // Can't open as a resource - try as a file
        if (is == null) {
            File f = new File("src/test/resources" + path);
            String s = f.getAbsolutePath();
            System.out.println(s);
            try {
                is = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                System.out.println("Problem loading file " + e.getMessage());
            }
        }

        if (is != null) {
            Writer writer = new StringWriter();
            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return writer.toString();
        }
        // Can't open. Return null so that the caller doesn't assume we
        // succeeded.
        return null;
    }

    protected void runSuspendTestFor(String fileName) throws RecognitionException {
        ReflexLexer lexer = new ReflexLexer();
        lexer.setCharStream(new ANTLRStringStream(getResourceAsString(this, fileName)));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ReflexParser parser = new ReflexParser(tokens);
        CommonTree tree = (CommonTree) parser.parse().getTree();

        /*
         * DOTTreeGenerator gen = new DOTTreeGenerator(); StringTemplate st =
         * gen.toDOT(tree); System.out.println(st);
         */

        CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
        ReflexTreeWalker walker = new ReflexTreeWalker(nodes, parser.languageRegistry);
        ReflexNode returned = walker.walk();
        ReflexValue value = returned.evaluateWithoutScope(new NullDebugger());
        while (value.getValue() == ReflexValue.Internal.SUSPEND) {
            System.out.println("Oooh it suspended...");
            System.out.println("Resuming script");
            value = returned.evaluateWithResume(new NullDebugger(), returned.getScope());
        }
        System.out.println(value);
    }

    protected String runTestForWithScriptHandler(String fileName, IReflexScriptHandler scriptHandler) throws RecognitionException {
        ReflexLexer lexer = new ReflexLexer();
        String program = getResourceAsString(this, fileName);
        lexer.setCharStream(new ANTLRStringStream(program));
        lexer.dataHandler = scriptHandler;
        return runTestForWithLexer(fileName, lexer, program, null);
    }

    protected String runTestWithString(String program) throws RecognitionException {
        ReflexLexer lexer = new ReflexLexer();
        lexer.setCharStream(new ANTLRStringStream(program));
        return runTestForWithLexer("", lexer, program, null);

    }

    protected String runTestFor(String fileName) throws RecognitionException {
        ReflexLexer lexer = new ReflexLexer();
        String program = getResourceAsString(this, fileName);
        if ((program == null) || program.isEmpty()) throw new RuntimeException("No programme to run");
        lexer.setCharStream(new ANTLRStringStream(program));
        return runTestForWithLexer(fileName, lexer, program, null);
    }

    protected String runTestFor(String fileName, Map<String, Object> params) throws RecognitionException {
        ReflexLexer lexer = new ReflexLexer();
        String program = getResourceAsString(this, fileName);
        lexer.setCharStream(new ANTLRStringStream(program));
        return runTestForWithLexer(fileName, lexer, program, params);
    }

    private String runTestForWithLexer(String fileName, ReflexLexer lexer, String program, Map<String, Object> injectedVars) throws RecognitionException {
        if ((program == null) || program.isEmpty()) throw new RuntimeException("No programme to run");
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ReflexParser parser = new ReflexParser(tokens);

        try {
            CommonTree tree = (CommonTree) parser.parse().getTree();
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
            ReflexTreeWalker walker = createReflexTreeWalker(nodes, parser);

            // Add Script API here otherwise it's null.

            walker.getReflexHandler().setApi(scriptApi);
            if (injectedVars != null && !injectedVars.isEmpty()) {
                for (Map.Entry<String, Object> kv : injectedVars.entrySet()) {
                    walker.currentScope.assign(kv.getKey(), kv.getValue() == null ? new ReflexNullValue() : new ReflexValue(kv.getValue()));
                }
            }

            final StringBuilder sb = new StringBuilder();
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

            ReflexNode returned = walker.walk();
            InstrumentDebugger instrument = new InstrumentDebugger();
            instrument.setProgram(program);
            ReflexValue retVal;
            if (returned == null) {
                retVal = null;
                System.out.println("null");
            } else {
                retVal = returned.evaluateWithoutScope(instrument);
                sb.append("--RETURNS--").append(JacksonUtil.jsonFromObject(retVal));
            }
            instrument.getInstrumenter().log();
            System.out.println(sb.toString());
            return sb.toString();
        } catch (RecognitionException e) {
            String hdr = parser.getErrorHeader(e);
            String msg = parser.getErrorMessage(e, ReflexParser.tokenNames);

            StringBuilder sb = new StringBuilder();
            CommonToken token = (CommonToken) e.token;

            String[] lines = token.getInputStream().substring(0, token.getInputStream().size() - 1).split("\n");

            sb.append("Error at token ").append(token.getText()).append(" on line ").append(e.line).append(" while parsing: \n");

            int start = Math.max(0, token.getLine() - 5);
            int end = Math.min(lines.length, token.getLine() + 5);
            int badline = token.getLine() - 1;

            for (int i = start; i < end; i++) {
                sb.append(String.format("%5d: %s\n", i + 1, lines[i]));
                if (i == badline) {
                    for (int j = 0; j < e.charPositionInLine + 7; j++)
                        sb.append(" ");
                    for (int j = 0; j < token.getText().length(); j++)
                        sb.append("^");
                    sb.append("\n");
                }
            }

            System.out.println("Captured an error - " + hdr + " - " + msg);
            System.out.println(sb.toString());
            throw e;
        }
    }

    protected ReflexTreeWalker createReflexTreeWalker(CommonTreeNodeStream nodes, ReflexParser parser) {
        return new ReflexTreeWalker(nodes, parser.languageRegistry);
    }

    protected void runTestForWithApi(String fileName) throws RecognitionException {

        HttpLoginApi loginApi = new HttpLoginApi("http://localhost:8665/rapture", new SimpleCredentialsProvider("rapture", "rapture"));
        loginApi.login();

        ScriptingApi api = new ScriptClient(loginApi);
        runTestForWithApi(fileName, api, new ReflexScriptDataHandler(api));
    }

    protected void runTestForWithApi(String fileName, ScriptingApi api, IReflexDataHandler dataHandler) throws RecognitionException {
        ReflexLexer lexer = new ReflexLexer();
        lexer.setCharStream(new ANTLRStringStream(getResourceAsString(this, fileName)));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ReflexParser parser = new ReflexParser(tokens);
        CommonTree tree = (CommonTree) parser.parse().getTree();

        CommonTreeNodeStream nodes = new CommonTreeNodeStream(tree);
        ReflexTreeWalker walker = new ReflexTreeWalker(nodes, parser.languageRegistry);

        walker.getReflexHandler().setApi(api);
        walker.getReflexHandler().setDataHandler(dataHandler);

        ReflexNode returned = walker.walk();
        System.out.println(returned == null ? "null" : returned.evaluateWithoutScope(new ReflexPrintingDebugger()));
    }
}
