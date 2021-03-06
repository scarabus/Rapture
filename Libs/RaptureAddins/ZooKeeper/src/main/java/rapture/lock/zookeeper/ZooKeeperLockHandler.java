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
package rapture.lock.zookeeper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.log4j.Logger;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import rapture.common.LockHandle;
import rapture.common.exception.ExceptionToString;
import rapture.config.MultiValueConfigLoader;
import rapture.lock.ILockingHandler;

/**
 * An implementation of a lock strategy on ZK. { name = lockName, locked=true, freeTime=(some long time) }
 * 
 * @author James Howe
 * 
 */
public class ZooKeeperLockHandler implements ILockingHandler {

    @Override
    public String toString() {
        return "ZooKeeperLockHandler [locks=" + locks + ", client=" + client + ", instanceName=" + instanceName + ", config=" + config + "]";
    }

    static Logger logger = Logger.getLogger(ZooKeeperLockHandler.class);
    private static final int TIME_TO_WAIT_DEFAULT = 3;
    private static final String LOCK_BASE = "/rapture_locking";

    private Map<String, InterProcessSemaphoreMutex> locks = new HashMap<>();

    // TODO put this mutex in a map with the key being lockName...
    // InterProcessSemaphoreMutex lock;

    CuratorFramework client;
    String instanceName = "dunno";
    Map<String, String> config = null;

    public ZooKeeperLockHandler(String connectionString) {
        client = makeClient(connectionString, 0);
    }

    public ZooKeeperLockHandler() {
        String zkHost = MultiValueConfigLoader.getConfig("ZOOKEEPER-serverHost");
        String zkPort = MultiValueConfigLoader.getConfig("ZOOKEEPER-serverPort");

        if (zkHost == null) zkHost = "localhost";
        if (zkPort == null) zkPort = "2181";

        String connectionString = zkHost + ":" + zkPort;
        int timeToWait = TIME_TO_WAIT_DEFAULT;

        String ttws = MultiValueConfigLoader.getConfig("ZOOKEEPER-timeToWait");
        if (ttws != null) try {
            int ttw = Integer.parseInt(ttws);
            timeToWait = ttw;
        } catch (NumberFormatException nfe) {
            logger.warn("invalid time to wait value " + ttws);
        }

        logger.debug("connectionString is " + connectionString);
        logger.debug("timeToWait is " + timeToWait);

        client = makeClient(connectionString, timeToWait);
    }

    static CuratorFramework makeClient(String connectionString, int timeToWait) {
        CuratorFramework client = CuratorFrameworkFactory.newClient(connectionString, new RetryUntilElapsed(timeToWait, timeToWait / 4));

        client.getConnectionStateListenable().addListener(new ConnectionStateListener() {
            @Override
            public void stateChanged(CuratorFramework client, ConnectionState newState) {
                if (newState == ConnectionState.LOST) {
                    logger.error("lost connection to zookeeper server");
                    // TODO connection CAN'T be trusted anymore. throw a runtime telling the client app to reconnect.
                } else if (newState == ConnectionState.SUSPENDED) {
                    logger.error(" connection suspended to zookeeper server");
                    // TODO we can wait for state to move back to CONNECTED. if not time out with exception
                }
            }
        });
        client.start();
        return client;
    }

    @Override
    public LockHandle acquireLock(String lockHolder, String lockName, long secondsToWait, long secondsToHold) {
        String lockPath = createLockPath(lockName);

        InterProcessSemaphoreMutex lock = locks.get(lockName);
        // TODO put this mutex in a map with the key being lockName...
        if (lock == null) {
            lock = new InterProcessSemaphoreMutex(client, lockPath);
        }

        try {
            // Curator uses ephemeral nodes under the "lockPath" for actual lock creation.
            Stat exists = client.checkExists().forPath(lockPath);
            if (exists == null || exists.getCtime() < 1) {
                try {
                    logger.info(lockName + " doesn't exist. creating in zookeeper");
                    client.create().creatingParentsIfNeeded().forPath(lockPath);
                } catch (KeeperException.NodeExistsException e) {
                    // ignore as someone else created it at the same time
                    logger.debug("hey! someone else created the node: " + lockPath);
                }
            }

            boolean success = lock.acquire(secondsToWait, TimeUnit.SECONDS);

            if (!success) {
                logger.debug("lock not obtained");
                return null;
            }
        } catch (Exception e) {
            logger.error("error trying to obtain lock", e);
            throw new RuntimeException("Could not obtain lock", e);
        }

        locks.put(lockName, lock);
        logger.debug("lock obtained");

        LockHandle lh = new LockHandle();
        lh.setLockHolder(lockHolder);
        lh.setLockName(lockName);
        return lh;
    }

    @Override
    public Boolean releaseLock(String lockHolder, String lockName, LockHandle lockHandle) {
        InterProcessSemaphoreMutex lock = locks.get(lockName);
        if (lock == null) {
            logger.debug(lockName + " lock was never obtained");
            // TODO maybe ignore as we dont really care
            throw new IllegalStateException("Cannot release " + lockName + " since lock does not exist (was never obtained?)");
        }

        try {
            logger.debug("releasing lock " + lockName);
            lock.release();
            return true;
        } catch (Exception e) {
            logger.debug("exception swallowed while trying to release lock " + lockName, e);
            logger.trace(ExceptionToString.format(e));
        }
        return false;
    }

    String createLockPath(String lockName) {
        if (lockName == null) {
            // no lockName, no lock...
            throw new RuntimeException("lockName should not be null");
        } else {
            if (lockName.startsWith("/")) {
                return LOCK_BASE + lockName;
            } else {
                return LOCK_BASE + "/" + lockName;
            }
        }
    }

    @Override
    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    @Override
    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    @Override
    public Boolean forceReleaseLock(String lockName) {
        // TODO maybe this should create a client that just removes the lock node ? ...
        return this.releaseLock(null, lockName, null);
    }
}
