/**
 * Copyright (C) 2011-2015 Incapture Technologies LLC
 *
 * This is an autogenerated license statement. When copyright notices appear below
 * this one that copyright supercedes this statement.
 *
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 *
 * Unless explicit permission obtained in writing this software cannot be distributed.
 */
package rapture.ftp.common;

import java.io.Serializable;

import org.apache.log4j.Logger;

import rapture.object.Debugable;

public class FTPConnectionConfig implements Serializable, Debugable {
    
    private static final Logger log = Logger.getLogger(FTPConnectionConfig.class);

    private static final long serialVersionUID = 1L;
    public static final int DEFAULT_TIMEOUT = 60;
    public static final int MAX_RETRIES = 5;
    public static final int RETRY_WAIT = 15000;

    private String loginId;
    private String password;
    private String privateKey;
    private String address;
    private boolean useSFTP = false;
    private int port = 23;
    private int retryCount = MAX_RETRIES;
    private int retryWait = RETRY_WAIT;
    private int timeout = DEFAULT_TIMEOUT;

    public String getLoginId() {
        return loginId;
    }

    public FTPConnectionConfig setLoginId(String loginId) {
        this.loginId = loginId;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public FTPConnectionConfig setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public FTPConnectionConfig setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public FTPConnectionConfig setAddress(String address) {
        this.address = address;
        return this;
    }

    public int getPort() {
        return port;
    }

    public FTPConnectionConfig setPort(int port) {
        this.port = port;
        return this;
    }

    public boolean isUseSFTP() {
        return useSFTP;
    }

    public FTPConnectionConfig setUseSFTP(boolean useSFTP) {
        this.useSFTP = useSFTP;
        return this;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public FTPConnectionConfig setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public int getRetryWait() {
        return retryWait;
    }

    public FTPConnectionConfig setRetryWait(int retryWait) {
        if (retryWait > 1000) {
            log.warn("retryWait should be specified in seconds. Is "+Math.abs(retryWait/60)+" minutes really correct?");
        }
        this.retryWait = retryWait * 1000;
        return this;
    }

    public int getTimeout() {
        return timeout;
    }

    public FTPConnectionConfig setTimeout(int timeout) {
        if (timeout > 1000) {
            log.warn("timeout should be specified in seconds. Is " + Math.abs(timeout / 60) + " minutes really correct?");
        }
        this.timeout = timeout * 1000;
        return this;
    }

    @Override
    public String debug() {
        return toString();
    }
}