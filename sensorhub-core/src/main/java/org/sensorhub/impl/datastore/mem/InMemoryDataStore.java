/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.mem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


abstract class InMemoryDataStore
{
    
    public String getDatastoreName()
    {
        return getClass().getSimpleName();
    }
    
    
    public void commit()
    {            
    }
    
    
    public void backup(OutputStream is) throws IOException
    {            
    }
    

    public void restore(InputStream os) throws IOException
    {            
    }
    

    public boolean isReadSupported()
    {
        return true;
    }
    

    public boolean isWriteSupported()
    {
        return true;
    }
}