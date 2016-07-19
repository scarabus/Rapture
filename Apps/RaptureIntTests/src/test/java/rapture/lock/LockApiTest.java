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
package rapture.lock;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import rapture.common.LockHandle;
import rapture.common.RaptureLockConfig;
import rapture.common.RaptureURI;
import rapture.common.Scheme;
import rapture.common.client.HttpAdminApi;
import rapture.common.client.HttpDocApi;
import rapture.common.client.HttpLockApi;
import rapture.common.client.HttpLoginApi;
import rapture.common.impl.jackson.MD5Utils;
import rapture.helper.IntegrationTestHelper;

public class LockApiTest {
    
    private IntegrationTestHelper helper;
    private HttpLoginApi raptureLogin = null;
    private HttpDocApi docApi = null;
    private HttpLockApi lockApi = null;
    private HttpAdminApi admin = null;

    private static final String user = "User";
    private IntegrationTestHelper helper2;
    private HttpDocApi docApi2 = null;
    private HttpLockApi lockApi2 = null;
    private HttpLoginApi raptureLogin2 = null;
    private RaptureURI repoUri = null;

    @BeforeClass(groups = { "nightly" })
    @Parameters({ "RaptureURL", "RaptureUser", "RapturePassword" })
    public void setUp(@Optional("http://localhost:8665/rapture") String url, @Optional("rapture") String username, @Optional("rapture") String password) {


        helper = new IntegrationTestHelper(url, username, password);
        raptureLogin = helper.getRaptureLogin();
        docApi = helper.getDocApi();
        lockApi = helper.getLockApi();
        admin = helper.getAdminApi();
        if (!admin.doesUserExist(user)) {
            admin.addUser(user, "Another User", MD5Utils.hash16(user), "user@incapture.net");
        }

        helper2 = new IntegrationTestHelper(url, user, user);
        docApi2 = helper2.getDocApi();
        lockApi2 = helper2.getLockApi();

        repoUri = helper.getRandomAuthority(Scheme.DOCUMENT);
        helper.configureTestRepo(repoUri, "MONGODB"); // TODO Make this configurable
    }

    @AfterClass(groups = { "nightly" })
    public void tearDown() {
    }
        
    @Test(groups = { "nightly" }, enabled = true)
    public void testLock() throws InterruptedException {

        // Player 1 acquires a lock
        RaptureURI lockUri = RaptureURI.builder(helper.getRandomAuthority(Scheme.DOCUMENT)).docPath("foo/bar").build();
        RaptureLockConfig lockConfig = lockApi.createLockManager(lockUri.toString(), "LOCKING USING MONGODB {}", "");
        assertNotNull(lockConfig);
        LockHandle lockHandle = lockApi.acquireLock(lockUri.toString(), lockConfig.getName(), 1, 60);
        assertNotNull(lockHandle);
        Thread.sleep(100);

        // Meanwhile elsewhere Player 2 tries to acquire the lock
        RaptureLockConfig lockConfig2 = lockApi.getLockManagerConfig(lockUri.toString());
        assertNotNull(lockConfig2);
        LockHandle lockHandle2 = lockApi2.acquireLock(lockUri.toString(), lockConfig2.getName(), 1, 60);
        // but fails
        assertNull(lockHandle2);

        // Eventually player1 releases the lock
        Thread.sleep(100);
        lockApi.releaseLock(lockUri.toString(), lockConfig.getName(), lockHandle);

        // and now Player 2 can acquire it
        lockHandle2 = lockApi2.acquireLock(lockUri.toString(), lockConfig2.getName(), 1, 60);
        assertNotNull(lockHandle2);
        lockApi2.releaseLock(lockUri.toString(), lockConfig2.getName(), lockHandle2);

        assertNotNull(lockApi.getLockManagerConfig(lockUri.toString()));
        assertTrue(lockApi.lockManagerExists(lockUri.toString()));
        lockApi.deleteLockManager(lockUri.toString());
        assertFalse(lockApi.lockManagerExists(lockUri.toString()));
        assertNull(lockApi.getLockManagerConfig(lockUri.toString()));
        System.out.println(lockUri.toString());

        // Map<String, RaptureFolderInfo> dox = docApi.listDocsByUriPrefix("document://sys.RaptureConfig", 99);
        // System.out.println(JacksonUtil.jsonFromObject(dox));
    }
}