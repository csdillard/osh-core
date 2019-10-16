/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2018 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.h2;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Comparator;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.DataType;
import org.sensorhub.api.datastore.FeatureKey;


/**
 * <p>
 * H2 DataType implementation for FeatureKey objects
 * </p>
 *
 * @author Alex Robin
 * @date Apr 7, 2018
 */
class MVFeatureKeyDataType implements DataType
{
    private static final int MIN_MEM_SIZE = 10+14+2;
    Comparator<Instant> timeCompare = Comparator.nullsFirst(Comparator.naturalOrder());
    
            
    @Override
    public int compare(Object objA, Object objB)
    {
        FeatureKey a = (FeatureKey)objA;
        FeatureKey b = (FeatureKey)objB;
        
        // first compare internal ID part of the key
        int idComp = Long.compare(a.getInternalID(), b.getInternalID());
        if (idComp != 0)
            return idComp;
        
        // only if IDs are the same, compare valid start time
        return timeCompare.compare(a.getValidStartTime(), b.getValidStartTime());
    }
    

    @Override
    public int getMemory(Object obj)
    {
        FeatureKey key = (FeatureKey)obj;
        return MIN_MEM_SIZE + key.getUniqueID().length();
    }
    

    @Override
    public void write(WriteBuffer wbuf, Object obj)
    {
        FeatureKey key = (FeatureKey)obj;
        wbuf.putVarLong(key.getInternalID());
        H2Utils.writeInstant(wbuf, key.getValidStartTime());
        H2Utils.writeAsciiString(wbuf, key.getUniqueID());
    }
    

    @Override
    public void write(WriteBuffer wbuf, Object[] obj, int len, boolean key)
    {
        for (int i=0; i<len; i++)
            write(wbuf, obj[i]);
    }
    

    @Override
    public Object read(ByteBuffer buff)
    {
        long internalID = DataUtils.readVarLong(buff); 
        Instant validStartTime = H2Utils.readInstant(buff);
        String uid = H2Utils.readAsciiString(buff);
        return new FeatureKey(internalID, uid, validStartTime);
    }
    

    @Override
    public void read(ByteBuffer buff, Object[] obj, int len, boolean key)
    {
        for (int i=0; i<len; i++)
            obj[i] = read(buff);        
    }

}
