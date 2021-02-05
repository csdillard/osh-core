/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.procedure;

import java.util.Map;
import java.util.function.Consumer;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.feature.FeatureId;
import org.sensorhub.api.obs.IDataStreamInfo;
import org.sensorhub.api.obs.IObsData;
import org.sensorhub.api.obs.ObsData;
import org.sensorhub.api.utils.OshAsserts;
import org.sensorhub.utils.SWEDataUtils;
import org.vast.swe.SWEHelper;
import org.vast.swe.ScalarIndexer;
import org.vast.util.Asserts;
import net.opengis.swe.v20.DataBlock;


/**
 * <p>
 * Transitional utility class to convert DataEvent to IObsData objects
 * until procedures generate ObsEvent directly 
 * </p>
 *
 * @author Alex Robin
 * @date Feb 5, 2021
 */
public class DataEventToObsConverter
{
    protected long dsID;
    protected IDataStreamInfo dsInfo;
    protected Map<String, FeatureId> foiIdMap;
    protected ScalarIndexer timeStampIndexer;
    
    
    public DataEventToObsConverter(long dsID, IDataStreamInfo dsInfo, Map<String, FeatureId> foiIdMap)
    {
        this.dsID = OshAsserts.checkValidInternalID(dsID);
        this.dsInfo = Asserts.checkNotNull(dsInfo, IDataStreamInfo.class);
        this.foiIdMap = Asserts.checkNotNull(foiIdMap, "foiIdMap");
        this.timeStampIndexer = SWEHelper.getTimeStampIndexer(dsInfo.getRecordStructure());
    }
    
    
    public void toObs(DataEvent e, Consumer<IObsData> obsSink)
    {
        // if event carries an FOI UID, try to fetch the full Id object
        FeatureId foiId;
        String foiUID = e.getFoiUID();
        if (foiUID != null)
        {
            var fid = foiIdMap.get(foiUID);
            if (fid != null)
                foiId = fid;
            else
                throw new IllegalStateException("Unknown FOI: " + foiUID);
        }
        
        // else use the single FOI if there is one
        else if (foiIdMap.size() == 1)
        {
            foiId = foiIdMap.values().iterator().next();
        }
        
        // else don't associate to any FOI
        else
            foiId = ObsData.NO_FOI;
        
        // process all records
        for (DataBlock record: e.getRecords())
        {
            // get time stamp
            double time;
            if (timeStampIndexer != null)
                time = timeStampIndexer.getDoubleValue(record);
            else
                time = e.getTimeStamp() / 1000.;
        
            // store record with proper key
            ObsData obs = new ObsData.Builder()
                .withDataStream(dsID)
                .withFoi(foiId)
                .withPhenomenonTime(SWEDataUtils.toInstant(time))
                .withResult(record)
                .build();
            
            obsSink.accept(obs);
        }
    }
}
