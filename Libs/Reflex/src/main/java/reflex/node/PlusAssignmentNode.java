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
import reflex.value.internal.ReflexVoidValue;

public class PlusAssignmentNode extends BaseNode {
    private String identifier;
    private ReflexNode rhs;

    public PlusAssignmentNode(int lineNumber, IReflexHandler handler, Scope s, String i, ReflexNode n) {
        super(lineNumber, handler, s);
        this.identifier = i;
        this.rhs = n;
    }

    @Override
    public String toString() {
        return String.format("(%s += %s)", identifier, rhs);
    }

    @Override
    public ReflexValue evaluate(IReflexDebugger debugger, Scope scope) {
        // This is plus assignment. We want to return the identifier as a value,
        // but the purpose
        // is to "add" the rhs to the identifier, depending on what type of
        // value it is.
        debugger.stepStart(this, scope);
        ReflexValue value = rhs.evaluate(debugger, scope);
        ReflexValue var = scope.resolve(identifier);
        if (var.isList()) {
            var.asList().add(value);
        } else if (var.isNumber()) {
            if (value.isNumber()) {
                var.setValue(var.asDouble().doubleValue() + value.asDouble().doubleValue());
            }
        } else if (var.isString()) {
            var.setValue(var.asString() + value.toString());
        }
        debugger.stepEnd(this, new ReflexVoidValue(lineNumber), scope);
        return new ReflexVoidValue();
    }
}