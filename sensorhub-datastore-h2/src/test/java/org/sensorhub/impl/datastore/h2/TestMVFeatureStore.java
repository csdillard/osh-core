/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.h2;

import java.io.File;
import java.nio.file.Files;
import org.h2.mvstore.MVStore;
import org.junit.After;
import org.sensorhub.impl.datastore.AbstractTestFeatureStore;


public class TestMVFeatureStore extends AbstractTestFeatureStore<MVFeatureStoreImpl>
{
    private static String DB_FILE_PREFIX = "test-mvfeatures-";
    protected File dbFile;
    protected MVStore mvStore;
    
    
    protected MVFeatureStoreImpl initStore() throws Exception
    {
        dbFile = File.createTempFile(DB_FILE_PREFIX, ".dat");
        dbFile.deleteOnExit();
        return openMVStore();
    }
    
    
    protected void forceReadBackFromStorage()
    {
        this.featureStore = openMVStore();
    }
    
    
    private MVFeatureStoreImpl openMVStore()
    {
        if (mvStore != null)
        {
            mvStore.commit();
            mvStore.close();
            System.out.println("MVStore flushed to disk");
        }
        
        mvStore = new MVStore.Builder()
                .fileName(dbFile.getAbsolutePath())
                .autoCommitBufferSize(100)
                .cacheSize(1)
                .open();
        
        return MVFeatureStoreImpl.open(mvStore, MVDataStoreInfo.builder()
            .withName(DATASTORE_NAME)
            .build());
    }
    
    
    @After
    public void cleanup() throws Exception
    {
        if (mvStore != null)
            mvStore.close();
        
        if (dbFile != null) 
        {
            System.out.println("DB file size was " + dbFile.length()/1024 + "KB\n");
            
            Files.list(dbFile.toPath().getParent())
                 .filter(f -> f.getFileName().toString().startsWith(DB_FILE_PREFIX))
                 .forEach(f -> f.toFile().delete());
        }
    }

}
