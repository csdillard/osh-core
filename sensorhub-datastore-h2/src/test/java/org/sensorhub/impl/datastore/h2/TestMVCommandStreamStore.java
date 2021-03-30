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
import org.sensorhub.impl.datastore.AbstractTestCommandStreamStore;


public class TestMVCommandStreamStore extends AbstractTestCommandStreamStore<MVCommandStreamStoreImpl>
{
    private static String DB_FILE_PREFIX = "test-mvcmd-";
    protected File dbFile;
    protected MVStore mvStore;
        
    
    protected MVCommandStreamStoreImpl initStore() throws Exception
    {
        dbFile = File.createTempFile(DB_FILE_PREFIX, ".dat");
        dbFile.deleteOnExit();        
        return openMVStore();
    }
    
    
    protected void forceReadBackFromStorage()
    {
        this.cmdStreamStore = openMVStore();
    }
    
    
    private MVCommandStreamStoreImpl openMVStore()
    {
        if (mvStore != null)
            mvStore.close();
        
        mvStore = new MVStore.Builder()
                .fileName(dbFile.getAbsolutePath())
                .autoCommitBufferSize(10)
                .cacheSize(10)
                .open();
        
        var cmdStore = MVCommandStoreImpl.open(mvStore,
            MVDataStoreInfo.builder()
                .withName("CMD_STORE")
                .build());
        
        return (MVCommandStreamStoreImpl)cmdStore.getCommandStreams();
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
