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
package rapture.kernel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import rapture.common.CallingContext;
import rapture.common.RaptureConstants;
import rapture.common.RaptureFolderInfo;
import rapture.common.RaptureURI;
import rapture.common.Scheme;
import rapture.common.exception.RaptureException;
import rapture.common.impl.jackson.JacksonUtil;
import rapture.common.model.DocumentRepoConfig;

public class DocApiFileTest extends AbstractFileTest {

    private static final Logger log = Logger.getLogger(DocApiFileTest.class);
    private static final String REPO_USING_FILE = "REP {} USING FILE {prefix=\"/tmp/" + auth + "\"}";
    private static final String GET_ALL_BASE = "document://getAll";
    private static final String docContent = "{\"content\":\"Cold and misty morning I had heard a warning borne in the air\"}";
    private static final String docAuthorityURI = "document://" + auth;
    private static final String docURI = docAuthorityURI + "/brain/salad/surgery";

    private static CallingContext callingContext;
    private static DocApiImpl docImpl;

    @BeforeClass
    static public void setUp() {
        AbstractFileTest.setUp();
        config.RaptureRepo = REPO_USING_FILE;
        config.InitSysConfig = "NREP {} USING FILE { prefix=\"/tmp/" + auth + "/sys.config\"}";
        // config.DefaultPipelineTaskStatus = "TABLE {} USING FILE {prefix=\"/tmp/" + auth + "\"}";

        callingContext = new CallingContext();
        callingContext.setUser("dummy");

        Kernel.initBootstrap();
        callingContext = ContextFactory.getKernelUser();

        Kernel.INSTANCE.clearRepoCache(false);
        Kernel.getAudit().createAuditLog(ContextFactory.getKernelUser(), new RaptureURI(RaptureConstants.DEFAULT_AUDIT_URI, Scheme.LOG).getAuthority(),
                "LOG {} using FILE {prefix=\"/tmp/" + auth + "\"}");
        Kernel.getLock().createLockManager(ContextFactory.getKernelUser(), "lock://kernel", "LOCKING USING DUMMY {}", "");
        docImpl = new DocApiImpl(Kernel.INSTANCE);
    }

    @Test
    public void testThatWhichShouldNotBe() {
        String dummyAuthorityURI = "document://dummy"; //$NON-NLS-1$
        String dummyURI = dummyAuthorityURI + "/dummy"; //$NON-NLS-1$
        try {
            docImpl.createDocRepo(callingContext, dummyAuthorityURI, "NREP {} USING FILE { }");
            String doc = docImpl.getDoc(callingContext, dummyURI);
            fail("You can't create a repo without a prefix");
        } catch (RaptureException e) {
//            assertEquals("Repository document://dummy does not exist", e.getMessage());
        }

        // because the config gets stored even though it's not valid
        try {
            docImpl.deleteDocRepo(callingContext, dummyAuthorityURI);
        } catch (Exception e1) {
        }

        try {
            docImpl.createDocRepo(callingContext, dummyAuthorityURI, "NREP {} USING FILE { prefix=\"\" }");
            String doc = docImpl.getDoc(callingContext, dummyURI);
            fail("You can't create a repo without a valid prefix");
        } catch (RaptureException e) {
        }

        // because the config gets stored even though it's not valid
        try {
            docImpl.deleteDocRepo(callingContext, dummyAuthorityURI);
        } catch (Exception e1) {
        }

        Map<String, String> hashMap = new HashMap<>();
        try {
            docImpl.createDocRepo(callingContext, dummyAuthorityURI, "NREP {} USING FILE {}");
            String doc = docImpl.getDoc(callingContext, dummyURI);
            fail("You can't create a repo without a prefix");
        } catch (RaptureException e) {
        }

        // because the config gets stored even though it's not valid
        try {
            docImpl.deleteDocRepo(callingContext, dummyAuthorityURI);
        } catch (Exception e1) {
        }

        hashMap.put("prefix", "  ");
        try {
            docImpl.createDocRepo(callingContext, dummyAuthorityURI, "NREP {} USING FILE { prefix=\"  \" }");
            String doc = docImpl.getDoc(callingContext, dummyURI);
            fail("You can't create a repo without a valid prefix");
        } catch (RaptureException e) {
        }

    }

    @Test
    public void testValidDocStore() {
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("prefix", "/tmp/foo");
        docImpl.createDocRepo(callingContext, "document://dummy2", "NREP {} USING FILE { prefix=\"/tmp/foo\"");
    }

    @Test
    public void testPutContentWithNoRepo() {
        String randomDoc = "document://" + UUID.randomUUID().toString() + "/JUNK/ForTestingOnly";
        String randomJson = "{\"Foo\" : \"Bar\"}";

        docImpl.putDoc(ContextFactory.getKernelUser(), randomDoc, randomJson);
        assertEquals(randomJson, docImpl.getDoc(ContextFactory.getKernelUser(), randomDoc));
    }


    @Test
    public void testDeleteRepo() {
        testPutAndGetDoc();
        assertTrue(docImpl.docExists(callingContext, docURI));
        String doc = docImpl.getDoc(callingContext, docURI);
        docImpl.deleteDocRepo(callingContext, docAuthorityURI);
        assertFalse(docImpl.docRepoExists(callingContext, docAuthorityURI));
        assertFalse(docImpl.docExists(callingContext, docURI));
        testCreateAndGetRepo();
        assertTrue(docImpl.docRepoExists(callingContext, docAuthorityURI));
        assertFalse(docImpl.docExists(callingContext, docURI));
        testPutAndGetDoc();
        assertTrue(docImpl.docExists(callingContext, docURI));
    }


    @Test
    public void testlistDocsByUriPrefix() {
        if (docImpl.docRepoExists(callingContext, GET_ALL_BASE)) {
            docImpl.deleteDocRepo(callingContext, GET_ALL_BASE);

        } else {
            docImpl.createDocRepo(callingContext, GET_ALL_BASE, "REP {} USING FILE {prefix=\"/tmp/" + auth + "-1\"}");
        }
        docImpl.putDoc(callingContext, GET_ALL_BASE + "/uncle", "{\"magic\": \"Drunk Uncle\"}");
        docImpl.putDoc(callingContext, GET_ALL_BASE + "/dad/kid1", "{\"magic\": \"Awesome Child\"}");
        docImpl.putDoc(callingContext, GET_ALL_BASE + "/dad/kid2", "{\"magic\": \"Stellar Child\"}");
        docImpl.putDoc(callingContext, GET_ALL_BASE + "/daddywarbucks/fakeKid", "{\"magic\": \"Fake Child\"}");
        Map<String, RaptureFolderInfo> allDocs = docImpl.listDocsByUriPrefix(callingContext, GET_ALL_BASE, 0);
        // listDocsByUriPrefix also returns both the folders /dad and /daddywarbucks
        assertEquals(6, allDocs.size());
        Assert.assertFalse(allDocs.values().toArray(new RaptureFolderInfo[6])[0].getName().startsWith(GET_ALL_BASE + "//"));
        Map<String, RaptureFolderInfo> dadDocs = docImpl.listDocsByUriPrefix(callingContext, GET_ALL_BASE + "/dad", 0);
        assertEquals(2, dadDocs.size());
        // ordering is not guaranteed, we could get either kid1 or kid2, but should not get fakeKid nor uncle
        String s = dadDocs.keySet().toArray(new String[2])[0];
        assertEquals(GET_ALL_BASE + "/dad/kidX != " + s, (GET_ALL_BASE + "/dad/kidX").length(), s.length());
    }

    static boolean firstTime = true;

    @Test
    public void testCreateAndGetRepo() {
        if (!firstTime && docImpl.docRepoExists(callingContext, docAuthorityURI)) return;
        firstTime = false;
        docImpl.createDocRepo(callingContext, docAuthorityURI, REPO_USING_FILE);
        DocumentRepoConfig docRepoConfig = docImpl.getDocRepoConfig(callingContext, docAuthorityURI);
        assertNotNull(docRepoConfig);
        assertEquals(REPO_USING_FILE, docRepoConfig.getDocumentRepo().getConfig());
        assertEquals(auth, docRepoConfig.getAuthority());
    }

    @Test
    public void testGetDocumentRepositories() {
        testCreateAndGetRepo();
        List<DocumentRepoConfig> docRepositories = docImpl.getDocRepoConfigs(callingContext);
        docImpl.createDocRepo(callingContext, "document://somewhereelse/", REPO_USING_FILE);
        List<DocumentRepoConfig> docRepositoriesNow = docImpl.getDocRepoConfigs(callingContext);
        assertEquals(JacksonUtil.jsonFromObject(docRepositoriesNow), docRepositories.size() + 1, docRepositoriesNow.size());
    }

    @Test
    public void testPutAndGetDoc() {
        testCreateAndGetRepo();
        docImpl.putDoc(callingContext, docURI, docContent);
        String doc = docImpl.getDoc(callingContext, docURI);
        assertEquals(docContent, doc);
    }

    @Test
    public void testPutAndGetDocWithAttribute() {
        testCreateAndGetRepo();
        // Hmm if you use Bar not meta/Bar then it causes problems later - shouldn't that get caught by URI builder?
        RaptureURI docURIWithAttribute = new RaptureURI.Builder(Scheme.DOCUMENT, auth).docPath("Foo").attribute("meta/Bar").build();
        docImpl.putDoc(callingContext, docURIWithAttribute.toString(), docContent);
        String doc = docImpl.getDoc(callingContext, docURIWithAttribute.toString());
        assertEquals(docContent, doc);
    }

    @Test
    public void testDeleteDoc() {
        testPutAndGetDoc();
        assertTrue("Doc should exist", docImpl.docExists(callingContext, docURI));
        String doc = docImpl.getDoc(callingContext, docURI);
        assertNotNull("Document should exist but data not present", doc);

        docImpl.deleteDoc(callingContext, docURI);
        assertFalse("Doc should have been deleted", docImpl.docExists(callingContext, docURI));
        doc = docImpl.getDoc(callingContext, docURI);
        assertNull("Document should have been deleted but data still present", doc);
    }

    @Test()
    public void testDeleteDocRepo() {
        testPutAndGetDoc();
        String data = docImpl.getDoc(callingContext, docURI);
        assertNotNull("Document not found", data);
        List<DocumentRepoConfig> docRepositories = docImpl.getDocRepoConfigs(callingContext);
        assertTrue("Doc should exist", docImpl.docExists(callingContext, docURI));

        Assert.assertTrue(docImpl.docRepoExists(callingContext, docAuthorityURI));
        docImpl.deleteDocRepo(callingContext, docAuthorityURI);
        Assert.assertFalse(docImpl.docRepoExists(callingContext, docAuthorityURI));
        DocumentRepoConfig docRepoConfig = docImpl.getDocRepoConfig(callingContext, docAuthorityURI);
        assertNull("Repository should have been deleted", docRepoConfig);

        // Don't do this; it quietly creates a REP USING MEMEORY config (should it?)
        //        String zombie = docImpl.getDoc(callingContext, docURI);
        //        assertNull("Repository should have been deleted but data still present", zombie);

        List<DocumentRepoConfig> docRepositoriesAfter = docImpl.getDocRepoConfigs(callingContext);

        assertEquals("Should be one less now", docRepositories.size() - 1, docRepositoriesAfter.size());

        assertFalse("Doc should have been deleted", docImpl.docExists(callingContext, docURI));
        String doc = docImpl.getDoc(callingContext, docURI);
        assertNull("Document should have been deleted but data still present", doc);
    }

    @Test

    public void deleteDocsByUriPrefix() {
        String json = "{\"key123\":\"value123\"}";
        testCreateAndGetRepo();

        /* DocumentRepoConfig dr = */
        docImpl.getDocRepoConfig(callingContext, docAuthorityURI);
        assertTrue(docImpl.docRepoExists(callingContext, docAuthorityURI));

        docImpl.putDoc(callingContext, docAuthorityURI + "/folder1/folder2/file3", json);
        assertTrue(docImpl.docExists(callingContext, docAuthorityURI + "/folder1/folder2/file3"));
        String content = docImpl.getDoc(callingContext, docAuthorityURI + "/folder1/folder2/file3");
        assertEquals(json, content);

        docImpl.putDoc(callingContext, docAuthorityURI + "/folder1/file2", json);
        List<String> removedUris = docImpl.deleteDocsByUriPrefix(callingContext, docAuthorityURI + "/folder1/folder2", true);
        assertFalse(docImpl.docExists(callingContext, docAuthorityURI + "/folder1/folder2/file3"));

        File meta = new File("/tmp" + docAuthorityURI + "_meta/folder1/folder2/file3-3f-2d1.txt");
        String data = null;
        if (meta.exists()) {
            log.warn("Although the file has been deleted its metadata still exists");
            try {
                data = FileUtils.readFileToString(meta);
                log.warn(data);
                Map<String, Object> map = JacksonUtil.getMapFromJson(data);
                String deleted = map.get("deleted").toString();
                if (deleted.equals("false")) log.warn("and it's marked as not deleted");
            } catch (IOException e) {
            }
        }

        docImpl.putDoc(callingContext, docAuthorityURI + "/folder1/folder2", json);
        assertTrue(docImpl.docExists(callingContext, docAuthorityURI + "/folder1/folder2"));

        docImpl.putDoc(callingContext, docAuthorityURI + "/folder1/folder2/file3", json);
        assertTrue(docImpl.docExists(callingContext, docAuthorityURI + "/folder1/folder2/file3"));

        try {
            String data2 = FileUtils.readFileToString(meta);
            System.out.println("Was " + data + " now " + data2);
            Map<String, Object> map = JacksonUtil.getMapFromJson(data);
            String deleted = map.get("deleted").toString();
            if (deleted.equals("false")) log.warn("and it's marked as not deleted");
        } catch (IOException e) {
        }

        // But you can't do this
        //        docImpl.putDoc(callingContext, repoUri + "/folder1/folder2.txt/file3", json);
        //        assertTrue(docImpl.docExists(callingContext, repoUri + "/folder1/folder2.txt/file3"));

    }

    @Test
    public void removeVREPFolder() {
        String repoUri = "//testRepoUri_" + System.currentTimeMillis();
        File f1 = new File("/tmp"+repoUri.substring(1));
        f1.mkdir();
        f1.deleteOnExit();
        File f2 = new File("/tmp"+repoUri.substring(1)+"_cache");
        f2.mkdir();
        f2.deleteOnExit();
        
        String json = "{\"key123\":\"value123\"}";
        docImpl.createDocRepo(callingContext, repoUri, "VREP {} USING FILE {prefix=\"/tmp/" + repoUri + "\"}");

        /* DocumentRepoConfig dr = */
        docImpl.getDocRepoConfig(callingContext, repoUri);
        assertTrue(docImpl.docRepoExists(callingContext, repoUri));

        docImpl.putDoc(callingContext, repoUri + "/folder1/folder2/file3", json);
        assertTrue(docImpl.docExists(callingContext, repoUri + "/folder1/folder2/file3"));
        String content = docImpl.getDoc(callingContext, repoUri + "/folder1/folder2/file3");
        assertEquals(json, content);

        docImpl.putDoc(callingContext, repoUri + "/folder1/file2", json);

        // If you delete a folder from a versioned repo the file still remains
        assertTrue(docImpl.docExists(callingContext, repoUri + "/folder1/folder2/file3"));

        File meta = new File("/tmp" + repoUri + "_meta/folder1/folder2/file3-3f-2d1.txt");
        String data = null;
        if (meta.exists()) {
            log.warn("Although the file has been deleted its metadata still exists");
            try {
                data = FileUtils.readFileToString(meta);
                log.warn(data);
                Map<String, Object> map = JacksonUtil.getMapFromJson(data);
                String deleted = map.get("deleted").toString();
                if (deleted.equals("false")) log.warn("and it's marked as not deleted");
            } catch (IOException e) {
            }
        }

        docImpl.putDoc(callingContext, repoUri + "/folder1/folder2", json);
        assertTrue(docImpl.docExists(callingContext, repoUri + "/folder1/folder2"));

        docImpl.putDoc(callingContext, repoUri + "/folder1/folder2/file3", json);
        assertTrue(docImpl.docExists(callingContext, repoUri + "/folder1/folder2/file3"));

        try {
            String data2 = FileUtils.readFileToString(meta);
            System.out.println("Was " + data + " now " + data2);
            Map<String, Object> map = JacksonUtil.getMapFromJson(data);
            String deleted = map.get("deleted").toString();
            if (deleted.equals("false")) log.warn("and it's marked as not deleted");
        } catch (IOException e) {
        }

        // But you can't do this
        //        docImpl.putDoc(callingContext, repoUri + "/folder1/folder2.txt/file3", json);
        //        assertTrue(docImpl.docExists(callingContext, repoUri + "/folder1/folder2.txt/file3"));
        
        try {
            FileUtils.deleteDirectory(f1);
        } catch (IOException e) {
        }
        try {
            FileUtils.deleteDirectory(f2);
        } catch (IOException e) {
        }
    }

    @Test
    public void testRap3532() {
        //create document repo
        long t = System.currentTimeMillis();
        testCreateAndGetRepo();
        DocumentRepoConfig dr = docImpl.getDocRepoConfig(callingContext, docAuthorityURI);
        //create document and putDoc
        String uri = docAuthorityURI + "/file1";
        docImpl.putDoc(callingContext, uri, "{\"key\":\"value\"}");
        //get the docImpl
        Assert.assertTrue("does doc exist should return true.", docImpl.docExists(callingContext, uri));
        String content = docImpl.getDoc(callingContext, uri);
        Assert.assertEquals("Document written to file is not same as expected.", content, "{\"key\":\"value\"}");
        //delete the document
        Assert.assertTrue(docImpl.deleteDoc(callingContext, uri));
        Assert.assertFalse("does doc exist should return false.", docImpl.docExists(callingContext, uri));
        Assert.assertNull("no doc so getDoc should return null.", docImpl.getDoc(callingContext, uri));

        Assert.assertTrue(docImpl.docRepoExists(callingContext, docAuthorityURI));
        docImpl.deleteDocRepo(callingContext, docAuthorityURI);
        Assert.assertFalse(docImpl.docRepoExists(callingContext, docAuthorityURI));
    }
    
    @Test
    public void testGetDocRepoStatus() {
        Map<String, String> ret = docImpl.getDocRepoStatus(callingContext, docAuthorityURI);
        assertNotEquals(0, ret.size());
    }

    // deleteDocsByUriPrefix called on a non-existent doc deletes all existing docs in folder
    @Test
    public void testRap4002() {
        testCreateAndGetRepo();
        String clydeUri = docAuthorityURI + "/PacMan/Wocka/Wocka/Wocka/Inky/Pinky/Blinky/Clyde";
        String sueUri = docAuthorityURI + "/PacMan/Wocka/Wocka/Wocka/Sue";
        
        String content = "{\"foo\" : \"bar\" }";
        
        docImpl.putDoc(callingContext, clydeUri, content);
        docImpl.putDoc(callingContext, sueUri, content);
        String doc = docImpl.getDoc(callingContext, clydeUri);
        assertNotNull(doc);
        assertEquals(content, doc);
        
        Map<String, RaptureFolderInfo> byPrefix;

        byPrefix = docImpl.listDocsByUriPrefix(callingContext, docAuthorityURI+"/PacMan", 10);
        assertEquals(8, byPrefix.size());
        
        byPrefix = docImpl.listDocsByUriPrefix(callingContext, docAuthorityURI+"/PacMan/Wocka/Wocka/Wocka/Inky/Pinky/Blinky", 10);
        assertEquals(1, byPrefix.size());
        assertEquals(clydeUri, byPrefix.keySet().toArray(new String[1])[0]);
        
        byPrefix = docImpl.listDocsByUriPrefix(callingContext, clydeUri, 10);
        assertEquals(1, byPrefix.size());
        assertEquals(clydeUri, byPrefix.keySet().toArray(new String[1])[0]);

        
        docImpl.deleteDocsByUriPrefix(callingContext, clydeUri, false);
        
        try {
            doc = docImpl.getDoc(callingContext, clydeUri);
            // SHOULD FAIL OR DO NOTHING
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Doc or doc folder "+clydeUri+" does not exist"));
        }
        
        Map<String, RaptureFolderInfo> docs = docImpl.listDocsByUriPrefix(callingContext, docAuthorityURI + "/PacMan", -1);
        assertEquals(7, docs.size());
        for (RaptureFolderInfo rfi : docs.values()) {
            assertTrue(rfi.isFolder() || rfi.getName().equals("Sue"));
        }
        
        
        docImpl.putDoc(callingContext, clydeUri, content);
        doc = docImpl.getDoc(callingContext, clydeUri);
        assertNotNull(doc);
        assertEquals(content, doc);
        
        List<String> deleted = docImpl.deleteDocsByUriPrefix(callingContext, clydeUri, true);
        assertEquals(4, deleted.size());
        
        try {
            doc = docImpl.getDoc(callingContext, clydeUri);
            // SHOULD FAIL OR DO NOTHING
        } catch (Exception e) {
            assertTrue(e.getMessage().equals("Doc or doc folder "+clydeUri+" does not exist"));
        }
        
        docs = docImpl.listDocsByUriPrefix(callingContext, docAuthorityURI + "/PacMan", -1);
        assertEquals(4, docs.size());
    }
}
