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

import static rapture.common.Scheme.BLOB;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.base.Preconditions;

import rapture.common.BlobContainer;
import rapture.common.CallingContext;
import rapture.common.ContentEnvelope;
import rapture.common.EntitlementSet;
import rapture.common.RaptureFolderInfo;
import rapture.common.RaptureURI;
import rapture.common.Scheme;
import rapture.common.api.BlobApi;
import rapture.common.exception.RaptureException;
import rapture.common.exception.RaptureExceptionFactory;
import rapture.common.impl.jackson.JacksonUtil;
import rapture.common.model.BlobRepoConfig;
import rapture.common.model.BlobRepoConfigStorage;
import rapture.common.shared.blob.DeleteBlobPayload;
import rapture.common.shared.blob.ListBlobsByUriPrefixPayload;
import rapture.kernel.context.ContextValidator;
import rapture.kernel.schemes.RaptureScheme;
import rapture.repo.BlobRepo;

public class BlobApiImpl extends KernelBase implements BlobApi, RaptureScheme {
    public static final String WRITE_TIME = "writeTime";
    public static final String MODIFIED_TIMESTAMP = "modifiedTimestamp";
    public static final String USER = "user";
    public static final String CREATED_TIMESTAMP = "createdTimestamp";
    private static Logger logger = Logger.getLogger(BlobApiImpl.class);

    public BlobApiImpl(Kernel raptureKernel) {
        super(raptureKernel);
    }

    @Override
    public void createBlobRepo(CallingContext context, String blobRepoUri, String config, String metaConfig) {
        checkParameter("Repository URI", blobRepoUri);
        checkParameter("Config", config);
        checkParameter("MetaConfig", metaConfig);

        RaptureURI interimUri = new RaptureURI(blobRepoUri, BLOB);
        String authority = interimUri.getAuthority();
        if ((authority == null) || authority.isEmpty()) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("NoAuthority")); //$NON-NLS-1$
        }
        if (interimUri.hasDocPath()) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("NoDocPath", blobRepoUri)); //$NON-NLS-1$
        }
        if (blobRepoExists(context, blobRepoUri)) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("Exists", blobRepoUri)); //$NON-NLS-1$
        }

        // store repo config
        BlobRepoConfig rbc = new BlobRepoConfig();
        rbc.setConfig(config);
        rbc.setMetaConfig(metaConfig);
        rbc.setAuthority(interimUri.getAuthority());
        BlobRepoConfigStorage.add(rbc, context.getUser(), "create repo");

        // blob repo will be cached when it's accessed the first time
        logger.info("Creating Repository " + blobRepoUri);
    }

    @Override
    public void deleteBlobRepo(CallingContext context, String blobRepoUri) {
        RaptureURI uri = new RaptureURI(blobRepoUri, BLOB);
        try {
            BlobRepo blobRepo = getRepoFromCache(uri.getAuthority());

            // Ensure the repo exists
            if (blobRepo != null) {
                Map<String, RaptureFolderInfo> blobs = this.listBlobsByUriPrefix(context, blobRepoUri, -1);
                for (Entry<String, RaptureFolderInfo> entry : blobs.entrySet()) {
                    if (!entry.getValue().isFolder()) {
                        // Delete all the blobs!
                        RaptureURI buri = new RaptureURI(entry.getKey(), BLOB);
                        if (blobRepo.deleteBlob(context, buri)) {
                            deleteMeta(context, buri);
                        }
                    }
                }
            }
        } catch (RaptureException e) {
            log.info("Unable to delete children; repo definitian may be invalid");
        }

        // delete parent directory

        BlobRepoConfigStorage.deleteByAddress(uri, context.getUser(), "Remove blob repo");
        removeRepoFromCache(uri.getAuthority());
    }

    public void appendToBlobLower(CallingContext context, String blobUri, byte[] content, String contentType) {
        RaptureURI interimUri = new RaptureURI(blobUri, BLOB);
        Preconditions.checkNotNull(interimUri);
        Map<String, String> newMeta = createMetaData(context);
        BlobRepo blobRepo = getRepoFromCache(interimUri.getAuthority());
        if (blobRepo == null) throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST,
                apiMessageCatalog.getMessage("NoSuchRepo", interimUri.toAuthString())); //$NON-NLS-1$
        Map<String, String> meta = getBlobMetaData(ContextFactory.getKernelUser(), blobUri);
        if (meta.isEmpty()) {
            if (contentType != null) {
                meta.put(ContentEnvelope.CONTENT_TYPE_HEADER, contentType);
            }
            meta.put(ContentEnvelope.CONTENT_SIZE, Integer.toString(content.length));
            meta.putAll(newMeta);
            putMeta(meta, interimUri, ContextFactory.getKernelUser());
            blobRepo.storeBlob(context, interimUri, true, new ByteArrayInputStream(content));

        } else {
            addBlobContent(ContextFactory.getKernelUser(), blobUri, content);
        }
    }

    private Map<String, String> createMetaData(CallingContext context) {
        Map<String, String> meta = new HashMap<>();
        Long writeTime = System.currentTimeMillis();
        Date writeTimeDate = new Date(writeTime);
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss.SSS z yyyy");
        meta.put(WRITE_TIME, sdf.format(writeTimeDate));
        meta.put(CREATED_TIMESTAMP, writeTime.toString());
        meta.put(MODIFIED_TIMESTAMP, writeTime.toString());
        meta.put(USER, context.getUser());
        return meta;
    }

    @Override
    public void addBlobContent(CallingContext context, String blobUri, byte[] content) {
        RaptureURI interimUri = new RaptureURI(blobUri, BLOB);
        Preconditions.checkNotNull(interimUri);
        Map<String, String> newMeta = createMetaData(context);

        BlobRepo blobRepo = getRepoFromCache(interimUri.getAuthority());
        if (blobRepo == null) throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST,
                apiMessageCatalog.getMessage("NoSuchRepo", interimUri.toAuthString())); //$NON-NLS-1$

        blobRepo.storeBlob(context, interimUri, true, new ByteArrayInputStream(content));

        Map<String, String> meta = getBlobMetaData(context, blobUri);
        String size = meta.get(ContentEnvelope.CONTENT_SIZE);
        long originalSize = Long.valueOf(size);
        originalSize += content.length;
        meta.put(ContentEnvelope.CONTENT_SIZE, "" + originalSize);
        meta.put(WRITE_TIME, newMeta.get(WRITE_TIME));
        meta.put(MODIFIED_TIMESTAMP, newMeta.get(MODIFIED_TIMESTAMP));
        meta.put(USER, newMeta.get(USER));
        putMeta(meta, interimUri, context);
    }

    private void lowerStoreBlob(CallingContext context, String blobUri, byte[] content, String contentType, boolean append) {
        RaptureURI interimUri = new RaptureURI(blobUri, BLOB);
        Preconditions.checkNotNull(interimUri);
        Map<String, String> newMeta = createMetaData(context);

        if (interimUri.hasAttribute()) {
            logger.debug("interim uri has attribute");
            // This does not work. See RAP-2797
            // Here is the old code:
            // retVal = Kernel.getRepo().getTrusted().putContent(context, interimUri.toString(), content, "Updated Attribute");
            // That was replaced by this:

            putContent(context, interimUri, content, "Updated Attribute");

            // but in testing it gets a UnsupportedOperationException
            // I think we need something more like this:
            // Kernel.getDoc().getTrusted().putContent(context, {document URI based on interimUri} , {something - possibly the attribute} );
        } else {
            BlobRepo blobRepo = getRepoFromCache(interimUri.getAuthority());
            if (blobRepo == null) throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST,
                    apiMessageCatalog.getMessage("NoSuchRepo", interimUri.toAuthString())); //$NON-NLS-1$
            // if file store, pass on docPathWithElement
            // since element contains local file path, which could be used to create sym link
            // A cleaner solution should be in place after RAP-3587
            Map<String, String> attributes = getBlobMetaData(context, blobUri);
            if (attributes.isEmpty()) {
                attributes.put(CREATED_TIMESTAMP, newMeta.get(CREATED_TIMESTAMP));
            }
            boolean isStored = blobRepo.storeBlob(context, interimUri, append, new ByteArrayInputStream(content));

            if (isStored) {
                if (contentType != null) {
                    attributes.put(ContentEnvelope.CONTENT_TYPE_HEADER, contentType);
                }
                attributes.put(WRITE_TIME, newMeta.get(WRITE_TIME));
                attributes.put(MODIFIED_TIMESTAMP, newMeta.get(MODIFIED_TIMESTAMP));
                attributes.put(USER, newMeta.get(USER));
                attributes.put(ContentEnvelope.CONTENT_SIZE, Integer.toString(content.length));
                putMeta(attributes, interimUri, context);
            }
        }
    }

    @Override
    public void putBlob(CallingContext context, String blobUri, byte[] content, String contentType) {
        lowerStoreBlob(context, blobUri, content, contentType, false);
    }

    @Override
    public BlobContainer getBlob(CallingContext context, String blobUri) {

        BlobContainer retVal = new BlobContainer();

        // TODO: Ben - push this up to the wrapper - the this class should
        // implement the Trusted API.
        RaptureURI interimUri = new RaptureURI(blobUri, BLOB);
        if (interimUri.hasAttribute()) {
            // TODO: Ben - This is messy, and shouldn't really be used, throw an
            // exception here?
            ContentEnvelope content = getContent(context, interimUri);
            try {
                retVal.setContent(content.getContent().toString().getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR, apiMessageCatalog.getMessage("ErrorGettingBlob"), e);
            }
            return retVal;
        } else {
            BlobRepo blobRepo = getRepoFromCache(interimUri.getAuthority());
            if (blobRepo == null) {
                throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("NoSuchRepo", interimUri.toAuthString())); //$NON-NLS-1$
            }

            try (InputStream in = blobRepo.getBlob(context, interimUri)) {
                if (in != null) {
                    retVal.setContent(IOUtils.toByteArray(in));
                    Map<String, String> metaData = getBlobMetaData(context, blobUri);
                    Map<String, String> headers = new HashMap<>();
                    headers.put(ContentEnvelope.CONTENT_SIZE, metaData.get(ContentEnvelope.CONTENT_SIZE));
                    headers.put(ContentEnvelope.CONTENT_TYPE_HEADER, metaData.get(ContentEnvelope.CONTENT_TYPE_HEADER));
                    retVal.setHeaders(headers);
                    return retVal;
                } else {
                    return null;
                }
            } catch (IOException e) {
                throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR, apiMessageCatalog.getMessage("ErrorGettingBlob"), e);
            }
        }

    }

    @Override
    public void deleteBlob(CallingContext context, String raptureURIString) {
        RaptureURI blobURI = new RaptureURI(raptureURIString, BLOB);
        BlobRepo blobRepo = getRepoFromCache(blobURI.getAuthority());

        // Has the repo been deleted?
        if (blobRepo != null) {
            blobRepo.deleteBlob(context, blobURI);
            deleteMeta(context, blobURI);
        }
    }

    @Override
    public Long getBlobSize(CallingContext context, String blobUri) {
        Map<String, String> metaData = getBlobMetaData(context, blobUri);
        String sizeString = metaData.get(ContentEnvelope.CONTENT_SIZE);
        if (sizeString != null) {
            return Long.valueOf(sizeString);
        } else {
            return -1L;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> getBlobMetaData(CallingContext context, String blobMetaUri) {
        RaptureURI interimUri = new RaptureURI(blobMetaUri, BLOB);
        BlobRepo repo = getRepoFromCache(interimUri.getAuthority());
        if (repo == null) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("NoSuchRepo", interimUri.toAuthString())); //$NON-NLS-1$
        }
        String attributesString = repo.getMeta(interimUri.getDocPath());
        if (attributesString != null) {
            return JacksonUtil.objectFromJson(attributesString, HashMap.class);
        } else {
            return new HashMap<String, String>();
        }
    }

    @Override
    public ContentEnvelope getContent(CallingContext context, RaptureURI raptureUri) {
        ContentEnvelope retVal = new ContentEnvelope();
        if (raptureUri.hasAttribute()) {
            throw new UnsupportedOperationException("not yet implemented");
        } else if (raptureUri.hasDocPath()) {
            BlobContainer content = getBlob(context, raptureUri.toString());
            if (content != null) {
                retVal.setContent(content);
            }
        } else {
            BlobRepoConfig blobRepoConfig = getBlobRepoConfig(context, raptureUri.toString());
            retVal.setContent(blobRepoConfig);
        }
        return retVal;
    }

    @Override
    public void putContent(CallingContext context, RaptureURI raptureUri, Object content, String comment) {

        // Why not just put attribute in BlobContainer headers map?
        if (raptureUri.hasAttribute()) {
            throw new UnsupportedOperationException("not yet implemented");
        } else if (raptureUri.hasDocPath()) {
            // TODO: Ben -
            if (content instanceof BlobContainer && ((BlobContainer) content).getHeaders() != null) {
                BlobContainer blobContainer = ((BlobContainer) content);
                putBlob(context, raptureUri.toString(), blobContainer.getContent(), blobContainer.getHeaders()
                        .get(ContentEnvelope.CONTENT_TYPE_HEADER));
            } else {
                throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        apiMessageCatalog.getMessage("ErrorGettingBlobType", content.getClass().getCanonicalName()));
            }
        } else {
            if (content instanceof BlobRepoConfig) {
                BlobRepoConfig rbc = (BlobRepoConfig) content;
                createBlobRepo(context, raptureUri.toString(), rbc.getConfig(), rbc.getMetaConfig());
            } else {
                throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_INTERNAL_ERROR,
                        apiMessageCatalog.getMessage("ErrorGettingBlobType", content.getClass().getCanonicalName()));
            }
        }
    }

    @Override
    public void deleteContent(CallingContext context, RaptureURI raptureUri, String comment) {
        if (raptureUri.hasAttribute()) {
            throw new UnsupportedOperationException("not yet implemented");
        } else if (raptureUri.hasDocPath()) {
            deleteBlob(context, raptureUri.toString());
        } else {
            deleteBlobRepo(context, raptureUri.toString());
        }
    }

    // TODO: Ben - should this become part of the API?
    // Package visibility to give access to JarApiImpl
    void putMeta(Map<String, String> attributes, RaptureURI blobMetaUri, CallingContext context) {
        BlobRepo repo = getRepoFromCache(blobMetaUri.getAuthority());
        // TODO: Ben - move this type conversion into it's own thing.
        String attributesAsJSON = JacksonUtil.jsonFromObject(attributes);
        logger.debug("Meta attributes path is " + blobMetaUri.getDocPath());
        repo.putMeta(blobMetaUri.getDocPath(), attributesAsJSON, context.getUser(), null, false);
    }

    private boolean deleteMeta(CallingContext context, RaptureURI blobMetaUri) {
        BlobRepo repo = getRepoFromCache(blobMetaUri.getAuthority());
        return repo.deleteMeta(blobMetaUri.getDocPath(), context.getUser(), "");
    }

    @Override
    public Boolean blobRepoExists(CallingContext context, String blobRepoUri) {
        return (getBlobRepoConfig(context, blobRepoUri) != null);
    }

    @Override
    public BlobRepoConfig getBlobRepoConfig(CallingContext context, String blobRepoUri) {
        RaptureURI uri = new RaptureURI(blobRepoUri, BLOB);
        if (uri.hasDocPath()) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("NoDocPath", blobRepoUri)); //$NON-NLS-1$
        }
        return BlobRepoConfigStorage.readByAddress(uri);
    }

    @Override
    public List<BlobRepoConfig> getBlobRepoConfigs(CallingContext context) {
        return BlobRepoConfigStorage.readAll();
    }

    @Override
    public Boolean blobExists(CallingContext context, String blobUri) {

        // calling getBlob() isn't very efficient; could be very slow for a large blob.
        // But listBlobsByUriPrefix could be slow for a large number of blobs too.

        /***
         * try { 
         *   RaptureURI uri = new RaptureURI(seriesUri, BLOB); 
         *   String respectMah = uri.getAuthority(); 
         *   for (String rfi : listBlobsByUriPrefix (context, respectMah).keySet()) { 
         *     if (rfi.equals(seriesUri)) return true; 
         *   } 
         *   return false; 
         * } catch (Exception e) { return false; }
         ***/
        try {
            return (getBlob(context, blobUri) != null);
        } catch (RaptureException e) {
            return false;
        }
    }

    @Override
    public Map<String, RaptureFolderInfo> listBlobsByUriPrefix(CallingContext context, String uriPrefix, int depth) {
        RaptureURI internalUri = new RaptureURI(uriPrefix, BLOB);
        String authority = internalUri.getAuthority();
        Map<String, RaptureFolderInfo> ret = new HashMap<String, RaptureFolderInfo>();

        // Schema level is special case.
        if (authority.isEmpty()) {
            --depth;
            try {
                List<BlobRepoConfig> configs = getBlobRepoConfigs(context);
                for (BlobRepoConfig config : configs) {
                    authority = config.getAuthority();
                    // NULL or empty string should not exist.
                    if ((authority == null) || authority.isEmpty()) {
                        log.warn("Invalid authority (null or empty string) found for " + JacksonUtil.jsonFromObject(config));
                        continue;
                    }
                    String uri = new RaptureURI(authority, Scheme.BLOB).toString();
                    ret.put(uri, new RaptureFolderInfo(authority, true));
                    if (depth != 0) {
                        ret.putAll(listBlobsByUriPrefix(context, uri, depth));
                    }
                }

            } catch (RaptureException e) {
                // permission denied
                log.debug("No read permission for " + uriPrefix);
            }
            return ret;
        }

        BlobRepo repo = getRepoFromCache(internalUri.getAuthority());
        Boolean getAll = false;
        if (repo == null) {
            throw RaptureExceptionFactory.create(HttpURLConnection.HTTP_BAD_REQUEST, apiMessageCatalog.getMessage("NoSuchRepo", internalUri.toAuthString())); //$NON-NLS-1$
        }
        String parentDocPath = internalUri.getDocPath() == null ? "" : internalUri.getDocPath();
        int startDepth = StringUtils.countMatches(parentDocPath, "/");

        if (log.isDebugEnabled()) {
            log.debug("Loading all children from repo " + internalUri.getAuthority() + " with " + internalUri.getDocPath());
        }
        if (depth <= 0) getAll = true;

        Stack<String> parentsStack = new Stack<String>();
        parentsStack.push(parentDocPath);

        while (!parentsStack.isEmpty()) {
            String currParentDocPath = parentsStack.pop();
            int currDepth = StringUtils.countMatches(currParentDocPath, "/") - startDepth;
            if (!getAll && currDepth >= depth) continue;
            boolean top = currParentDocPath.isEmpty();
            // Make sure that you have permission to read the folder.
            try {
                ListBlobsByUriPrefixPayload requestObj = new ListBlobsByUriPrefixPayload();
                requestObj.setContext(context);
                requestObj.setBlobUri(currParentDocPath);
                ContextValidator.validateContext(context, EntitlementSet.Blob_listBlobsByUriPrefix, requestObj);
            } catch (RaptureException e) {
                // permission denied
                log.debug("No read permission on folder " + currParentDocPath);
                continue;
            }
                        
            // Depending on the Repository it may not be possible to distinguish between an empty directory and a nonexistent directory.
            
            List<RaptureFolderInfo> children = repo.listBlobsByUriPrefix(currParentDocPath);
            if (children != null) {
                for (RaptureFolderInfo child : children) {
                    String name = child.getName();
                    String childDocPath = currParentDocPath + (top ? "" : "/") + name;
                    if (name.isEmpty()) continue;
                    ret.put(RaptureURI.builder(Scheme.BLOB, authority).docPath(childDocPath + (child.isFolder() ? "/" : "")).build().toString(), child);
                    if (child.isFolder()) parentsStack.push(childDocPath);
                }
            }
            if (top) startDepth--; // special case
        }
        return ret;
    }

    private BlobRepo getRepoFromCache(String authority) {
        return Kernel.getRepoCacheManager().getBlobRepo(authority);
    }

    private void removeRepoFromCache(String authority) {
        Kernel.getRepoCacheManager().removeRepo(BLOB.toString(), authority);
    }

    @Override
    public List<String> deleteBlobsByUriPrefix(CallingContext context, String uriPrefix, Boolean recurse) {
        RaptureURI startUri = new RaptureURI(uriPrefix, Scheme.BLOB);
        Map<String, RaptureFolderInfo> docs = listBlobsByUriPrefix(context, uriPrefix, Integer.MAX_VALUE);
        List<String> folders = new ArrayList<>();
        Set<String> notEmpty = new HashSet<>();
        List<String> removed = new ArrayList<>();

        DeleteBlobPayload requestObj = new DeleteBlobPayload();
        requestObj.setContext(context);

        folders.add(uriPrefix.endsWith("/") ? uriPrefix : uriPrefix + "/");
        for (Map.Entry<String, RaptureFolderInfo> entry : docs.entrySet()) {
            String uri = entry.getKey();
            boolean isFolder = entry.getValue().isFolder();
            try {
                requestObj.setBlobUri(uri);
                if (isFolder) {
                    ContextValidator.validateContext(context, EntitlementSet.Blob_deleteBlobsByUriPrefix, requestObj);
                    folders.add(0, uri.substring(0, uri.length() - 1));
                } else {
                    ContextValidator.validateContext(context, EntitlementSet.Blob_deleteBlob, requestObj);
                    deleteBlob(context, uri);
                    removed.add(uri);
                }
            } catch (RaptureException e) {
                // permission denied
                log.debug("Unable to delete " + uri + " : " + e.getMessage());
                int colon = uri.indexOf(":") + 3;
                while (true) {
                    int slash = uri.lastIndexOf('/');
                    if (slash < colon) break;
                    uri = uri.substring(0, slash);
                    notEmpty.add(uri);
                }
            }
        }
        for (String uri : folders) {
            if (notEmpty.contains(uri)) continue;
            try {
                deleteBlob(context, uri);
            } catch (RaptureException e) {
                // Does not exist - that's OK
            }
        }
        
        if (recurse && startUri.hasDocPath()) {
            removed.addAll(recursiveDelete(context, new RaptureURI(uriPrefix, Scheme.BLOB),  new RecursiveHelper() {  
                public Map<String, RaptureFolderInfo> listByPrefix(CallingContext context, String uriPrefix, int depth) {
                    return listBlobsByUriPrefix(context, uriPrefix, depth);
                }
    
                public void removeChild(CallingContext context, RaptureURI uri) {
                    deleteBlob(context, uri.toString());
                }
            }));
        }        
        return removed;
    }
}
