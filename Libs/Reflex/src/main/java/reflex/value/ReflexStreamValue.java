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
package reflex.value;

import java.io.InputStream;

import com.google.common.net.MediaType;

import reflex.file.DummyFileReadAdapter;
import reflex.file.FileReadAdapterFactory;
import reflex.node.io.FileReadAdapter;

/**
 * A Reflex Stream Value is abstract - it's like a file but we return a stream
 * rather than explicitly being a file.
 * 
 * @author amkimian
 * 
 */
public abstract class ReflexStreamValue {
    private FileReadAdapter fileReadAdapter = new DummyFileReadAdapter();

    public FileReadAdapter getFileReadAdapter() {
        return fileReadAdapter;
    }

    public void setFileReadAdapter(FileReadAdapter fileReadAdapter) {
        this.fileReadAdapter = fileReadAdapter;
    }

    public MediaType getFileType() {
        return fileReadAdapter.getMimeType();
    }

    public void setFileType(ReflexValue type, String param1, String param2) {
        setFileReadAdapter(FileReadAdapterFactory.create(type.asString(), param1, param2));
    }

    public abstract InputStream getInputStream();
    public abstract String getEncoding();
}
