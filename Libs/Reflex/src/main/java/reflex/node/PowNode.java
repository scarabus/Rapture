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
package reflex.node;

import java.math.BigDecimal;
import java.math.MathContext;

import reflex.IReflexHandler;
import reflex.Scope;
import reflex.debug.IReflexDebugger;
import reflex.value.ReflexValue;
import reflex.value.internal.ReflexNullValue;

public class PowNode extends BaseNode {

    private ReflexNode lhs;
    private ReflexNode rhs;

    public PowNode(int lineNumber, IReflexHandler handler, Scope s, ReflexNode lhs, ReflexNode rhs) {
        super(lineNumber, handler, s);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public ReflexValue evaluate(IReflexDebugger debugger, Scope scope) {
        debugger.stepStart(this, scope);
        ReflexValue a = lhs.evaluate(debugger, scope);
        ReflexValue b = rhs.evaluate(debugger, scope);

        // number ^ number
        if (a.isNumber() && b.isNumber()) {
        	Integer bInt = b.asInt();
        	ReflexValue retVal;
        	if ((b.isInteger() || (b.asBigDecimal().equals(bInt))) && (bInt >= 0)) {
            	BigDecimal aBig = a.asBigDecimal();
        		retVal = new ReflexValue(aBig.pow(bInt));
        	}
            System.err.println("Expressions with exponents other than positive integers may not be accurate");
            retVal = new ReflexValue(new BigDecimal(Math.pow(a.asDouble(), b.asDouble()), MathContext.DECIMAL64));
            debugger.stepEnd(this, retVal, scope);
            return retVal;
        }
        throwError("Both arguments to an exponent must be numeric", lhs, rhs, a, b);
        return new ReflexNullValue();
    }

    @Override
    public String toString() {
        return String.format("(%s^%s)", lhs, rhs);
    }
}
