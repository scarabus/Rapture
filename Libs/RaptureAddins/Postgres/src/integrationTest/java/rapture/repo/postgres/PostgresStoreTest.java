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
package rapture.repo.postgres;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import rapture.common.RaptureFolderInfo;

/**
 * Test the lower level store outside of Rapture
 * @author alanmoore
 *
 */
public class PostgresStoreTest {

	private void enumerateChildren(PostgresDataStore ds, String prefix) {
		List<RaptureFolderInfo> info = ds.getSubKeys(prefix);
        for (RaptureFolderInfo i : info) {
        	System.out.println("Name is " + i.getName() + " folder is " + i.isFolder());
            String innerPrefix = prefix + (prefix.endsWith("/") || prefix.isEmpty() ? "" : "/") + i.getName();
            if (i.isFolder()) {
                enumerateChildren(ds, innerPrefix);
            } else {
                System.out.println("Document is " + innerPrefix);
            }
        }
	}
	@Test
	public void testItAll() {
		PostgresDataStore ds = new PostgresDataStore();
		String key = "one/two/three";
		Map<String, String> config = new HashMap<String, String>();
		config.put("prefix", "test1");
		ds.setConfig(config);
		ds.put(key, "{ \"one\" : 5 }");
		String res = ds.get(key);
		System.out.println(res);
		
		enumerateChildren(ds, "/");
		
		ds.delete(key);
		System.out.println("After delete = " + ds.get(key));
		ds.dropKeyStore();
	}

}