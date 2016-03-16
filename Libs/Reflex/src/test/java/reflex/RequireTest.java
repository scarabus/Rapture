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
package reflex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.antlr.runtime.RecognitionException;
import org.junit.Test;

import reflex.handlers.TestScriptHandler;
import reflex.value.ReflexValue;

/**
 * Unit test for the 'require' module functionality of reflex.
 * 
 * @author dukenguyen
 *
 */
public class RequireTest extends ResourceBasedTest {
    
    @Test
    public void testStandalone() throws RecognitionException {
        String ret = runTestForWithScriptHandler("/require/module.rfx", new TestScriptHandler(this, "require"));
        assertEquals("VOID", ret.split("--RETURNS--")[1]);
    }

    @Test
    public void testRequire() throws RecognitionException {
        String ret = runTestForWithScriptHandler("/require/main.rfx", new TestScriptHandler(this, "require"));
        assertEquals("4", ret.split("--RETURNS--")[1]);
    }
    
}