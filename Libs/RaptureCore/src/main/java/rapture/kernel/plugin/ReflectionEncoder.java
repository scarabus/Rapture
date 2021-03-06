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
package rapture.kernel.plugin;

import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

import rapture.common.CallingContext;
import rapture.common.PluginTransportItem;
import rapture.common.exception.RaptureExceptionFactory;
import rapture.common.impl.jackson.JacksonUtil;

/**
 * This is extracts the common code from encoders that use the JacksonUtil codec
 * of a single representative object for the entity being described.
 *
 * @author mel
 */
public abstract class ReflectionEncoder implements RaptureEncoder {

    static Logger log = Logger.getLogger(ReflectionEncoder.class);

    @Override
    public PluginTransportItem encode(CallingContext ctx, String uri) {
        try {
            Object o = getReflectionObject(ctx, uri);
            MessageDigest md = MessageDigest.getInstance("MD5");
            PluginTransportItem item = new PluginTransportItem();
            item.setContent(JacksonUtil.bytesJsonFromObject(o));
            md.update(item.getContent());
            item.setHash(Hex.encodeHexString(md.digest()));
            item.setUri(uri);
            return item;
        } catch (NoSuchAlgorithmException e) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR, "Error encoding item", e);
        }
    }

    public abstract Object getReflectionObject(CallingContext ctx, String uri);
}
