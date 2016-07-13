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
package rapture.cassandra;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.KsDef;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.TimedOutException;
import org.apache.cassandra.thrift.UnavailableException;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import rapture.common.Messages;
import rapture.common.exception.RaptureExceptionFactory;
import rapture.config.MultiValueConfigLoader;

public class CassandraBase {
    private static Logger log = Logger.getLogger(CassandraBase.class);

    private String cassHost;
    private int cassPort;
    protected String keySpace;
    protected String columnFamily;

    private TTransport tr;
    private TProtocol proto;
    protected Cassandra.Client client;
    protected static final String UTF8 = "UTF-8";

    private ConsistencyLevel readCL = ConsistencyLevel.ONE;
    private ConsistencyLevel writeCL = ConsistencyLevel.ONE;

    public Messages messageCatalog;

    public String getColumnFamily() {
        return columnFamily;
    }

    protected ByteBuffer getByteBuffer(String val) throws UnsupportedEncodingException {
        return ByteBuffer.wrap(val.getBytes(UTF8));
    }

    public CassandraBase(String instance, Map<String, String> config) {
        // The configuration has the following:
        // keyspace
        // columnParent
        // readConsitency (optional)
        // writeConsistency (optional)

        messageCatalog = new Messages("Cassandra");

        // The connection to Cassandra comes from RaptureCASSANDRA.cfg
        // and has host and port
        cassHost = MultiValueConfigLoader.getConfig("CASSANDRA-" + instance + ".host");
        if (cassHost == null) {
            cassHost = "localhost";
        }
        String cassPortString = MultiValueConfigLoader.getConfig("CASSANDRA-" + instance + ".port");
        if (cassPortString == null) {
            cassPortString = "9160";
        }
        cassPort = Integer.valueOf(cassPortString);

        keySpace = config.get(CassandraConstants.KEYSPACECFG);
        columnFamily = config.get(CassandraConstants.CFCFG);

        try {
            getConnection();
        } catch (TTransportException e) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR, messageCatalog.getMessage("DbCommsError"), e);
        }
        ensureAllPresent();

        if (config.containsKey(CassandraConstants.READ_CONSISTENCY)) {
            readCL = ConsistencyLevel.valueOf(config.get(CassandraConstants.READ_CONSISTENCY));
        }

        if (config.containsKey(CassandraConstants.WRITE_CONSISTENCY)) {
            writeCL = ConsistencyLevel.valueOf(config.get(CassandraConstants.WRITE_CONSISTENCY));
        }

    }

    public void ensureStandardCF(String cfName) throws InvalidRequestException, TException, UnavailableException, TimedOutException,
            SchemaDisagreementException {
        KsDef keyspace = getKeyspace(keySpace);
        log.info(String.format("Ensuring standard cf:%s", cfName));
        for (CfDef cfdef : keyspace.getCf_defs()) {
            if (cfdef.getName().equals(cfName)) {
                return;
            }
        }

        String cql = "CREATE TABLE %s (KEY text PRIMARY KEY) WITH comparator=text";
        executeCQL(String.format(cql, cfName));

    }

    private KsDef getKeyspace(String keys) throws InvalidRequestException, TException {
        List<KsDef> keyspaces = client.describe_keyspaces();
        for (KsDef k : keyspaces) {
            if (k.getName().equals(keys)) {
                return k;
            }
        }
        return null;
    }

    private void ensureAllPresent() {
        // Make sure that the keyspace and cf exist with suitable defaults. If
        // you want different defaults
        // set them up outside of Rapture
        log.info(String.format("Ensuring keyspace:%s and cf:%s are present", keySpace, columnFamily));
        try {
            KsDef keyspace = getKeyspace(keySpace);
            if (keyspace == null) {
                String cql = "CREATE keyspace " + keySpace + " WITH strategy_class = 'SimpleStrategy' AND strategy_options:replication_factor = '1'";
                executeCQL(cql);
            }
            client.set_keyspace(keySpace);
            ensureStandardCF(columnFamily);
        } catch (TException e) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR, messageCatalog.getMessage("DbCommsError"), e);
        }
    }

    private void getConnection() throws TTransportException {
        log.info(String.format("Connecting to Cassandra at %s:%d", cassHost, cassPort));
        tr = new TFramedTransport(new TSocket(cassHost, cassPort));
        proto = new TBinaryProtocol(tr);
        client = new Cassandra.Client(proto);
        tr.open();
    }

    protected void executeCQL(String cql) throws InvalidRequestException, UnavailableException, TimedOutException, SchemaDisagreementException, TException {
        try {
            client.execute_cql_query(ByteBuffer.wrap(cql.getBytes("UTF-8")), Compression.NONE);
        } catch (UnsupportedEncodingException e) {
            throw new InvalidRequestException("Argument is not in UTF-8 character set");
        }
    }

    public ConsistencyLevel getReadCL() {
        return readCL;
    }

    public void setReadCL(ConsistencyLevel readCL) {
        this.readCL = readCL;
    }

    public ConsistencyLevel getWriteCL() {
        return writeCL;
    }

    public void setWriteCL(ConsistencyLevel writeCL) {
        this.writeCL = writeCL;
    }
}
