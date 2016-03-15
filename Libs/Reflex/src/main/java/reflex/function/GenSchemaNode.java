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
package reflex.function;

import rapture.common.impl.jackson.JacksonUtil;
import reflex.IReflexHandler;
import reflex.ReflexException;
import reflex.Scope;
import reflex.debug.IReflexDebugger;
import reflex.node.BaseNode;
import reflex.node.ReflexNode;
import reflex.schema.SchemaDocument;
import reflex.structure.Structure;
import reflex.util.function.LanguageRegistry;
import reflex.util.function.StructureFactory;
import reflex.util.function.StructureKey;
import reflex.value.ReflexValue;
import reflex.value.internal.ReflexNullValue;

/**
 * Cast one value type to another
 * 
 * @author amkimian
 * 
 */
public class GenSchemaNode extends BaseNode {

    private ReflexNode name;
    private LanguageRegistry registry;
    private String namespacePrefix;

    public GenSchemaNode(int lineNumber, IReflexHandler handler, Scope scope, ReflexNode name, LanguageRegistry registry, String namespacePrefix) {
        super(lineNumber, handler, scope);
        this.name = name;
        this.registry = registry;
        this.namespacePrefix = namespacePrefix;
    }

    @Override
    public ReflexValue evaluate(IReflexDebugger debugger, Scope scope) {
        debugger.stepStart(this, scope);
        ReflexValue structureName = name.evaluate(debugger, scope);
        ReflexValue retVal = new ReflexNullValue();
        if (structureName.isString()) {
            String name = structureName.asString();
                StructureKey key = namespacePrefix == null ? StructureFactory.createStructureKey(name) : StructureFactory.createStructureKey(namespacePrefix, name);
                
                Structure s = registry.getStructure(key);
                if (s != null) {
                    // Generate schema document for this structure,
                    // convert it to a json string
                    SchemaDocument doc = s.getSchemaDocument();
                    retVal = new ReflexValue(JacksonUtil.jsonFromObject(doc));
                } else {
                    throw new ReflexException(lineNumber, "Cannot find structure named " + name);
                }
                
        } else {
            throw new ReflexException(lineNumber, "Name of structure must be a string");
        }
        debugger.stepEnd(this, retVal, scope);
        return retVal;
    }

    @Override
    public String toString() {
        return super.toString() + " - " + String.format("new(%s)", name);
    }
}
