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

import reflex.IReflexHandler;
import reflex.Scope;
import reflex.debug.IReflexDebugger;
import reflex.value.ReflexValue;
import reflex.value.internal.ReflexNullValue;

public class GTNode extends BaseNode {

    private ReflexNode lhs;
    private ReflexNode rhs;

    public GTNode(int lineNumber, IReflexHandler handler, Scope scope, ReflexNode lhs, ReflexNode rhs) {
        super(lineNumber, handler, scope);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public ReflexValue evaluate(IReflexDebugger debugger, Scope scope) {
        debugger.stepStart(this, scope);
        ReflexValue a = lhs.evaluate(debugger, scope);
        ReflexValue b = rhs.evaluate(debugger, scope);
        ReflexValue retVal = new ReflexNullValue(lineNumber);;
        if (a.isNumber() && b.isNumber()) {
            retVal = new ReflexValue(a.compareTo(b) > 0);	// returns > 0 if A>B
        } else if (a.isString() && b.isString()) {
            retVal = new ReflexValue(a.asString().compareTo(b.asString()) > 0);
        } else if (a.isTime() && b.isTime()) {
            retVal = new ReflexValue(a.asTime().greaterThan(b.asTime()));
        } else if (a.isDate() && b.isDate()) {
            retVal = new ReflexValue(a.asDate().greaterThan(b.asDate()));
        } else {
            throwError("both must be of same type: numeric, date, time or string", lhs, rhs, a, b);
        }
        debugger.stepEnd(this, retVal, scope);
        return retVal;
    }

    @Override
    public String toString() {
        return String.format("(%s > %s)", lhs, rhs);
    }
}
