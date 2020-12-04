/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.database.registry;

import java.util.Map;
import java.util.TreeMap;
import org.sensorhub.api.database.IProcedureObsDatabase;
import org.sensorhub.api.datastore.obs.DataStreamFilter;
import org.sensorhub.api.datastore.obs.IDataStreamStore;
import org.sensorhub.api.datastore.procedure.IProcedureStore;
import org.sensorhub.api.datastore.procedure.ProcedureFilter;
import org.sensorhub.api.datastore.procedure.IProcedureStore.ProcedureField;
import org.sensorhub.api.procedure.IProcedureWithDesc;
import org.sensorhub.impl.database.registry.DefaultDatabaseRegistry.LocalFilterInfo;


/**
 * <p>
 * Implementation of procedure store that provides federated read-only access
 * to several underlying databases.
 * </p>
 *
 * @author Alex Robin
 * @date Oct 3, 2019
 */
public class FederatedProcedureStore extends FederatedBaseFeatureStore<IProcedureWithDesc, ProcedureField, ProcedureFilter> implements IProcedureStore
{
    
    FederatedProcedureStore(DefaultDatabaseRegistry registry, FederatedObsDatabase db)
    {
        super(registry, db);
    }
    
    
    protected IProcedureStore getFeatureStore(IProcedureObsDatabase db)
    {
        return db.getProcedureStore();
    }
    
    
    protected Map<Integer, LocalFilterInfo> getFilterDispatchMap(ProcedureFilter filter)
    {
        Map<Integer, LocalFilterInfo> dataStreamFilterDispatchMap = null;
        Map<Integer, LocalFilterInfo> parentFilterDispatchMap = null;
        Map<Integer, LocalFilterInfo> procFilterDispatchMap = new TreeMap<>();
        
        if (filter.getInternalIDs() != null)
        {
            var filterDispatchMap = registry.getFilterDispatchMap(filter.getInternalIDs());
            for (var filterInfo: filterDispatchMap.values())
            {
                filterInfo.filter = ProcedureFilter.Builder
                    .from(filter)
                    .withInternalIDs(filterInfo.internalIds)
                    .build();
            }
            
            return filterDispatchMap;
        }
        
        // otherwise get dispatch map for datastreams and parent procedures
        if (filter.getDataStreamFilter() != null)
            dataStreamFilterDispatchMap = db.obsStore.dataStreamStore.getFilterDispatchMap(filter.getDataStreamFilter());
        
        if (filter.getParentFilter() != null)
            parentFilterDispatchMap = getFilterDispatchMap(filter.getParentFilter());
        
        // merge both maps
        if (dataStreamFilterDispatchMap != null)
        {
            for (var entry: dataStreamFilterDispatchMap.entrySet())
            {
                var dataStreamFilterInfo = entry.getValue();
                                
                var builder = ProcedureFilter.Builder
                    .from(filter)
                    .withDataStreams((DataStreamFilter)dataStreamFilterInfo.filter);
                
                var parentfilterInfo = parentFilterDispatchMap != null ? parentFilterDispatchMap.get(entry.getKey()) : null;
                if (parentfilterInfo != null)
                    builder.withParents((ProcedureFilter)parentfilterInfo.filter);
                    
                var filterInfo = new LocalFilterInfo();
                filterInfo.databaseID = dataStreamFilterInfo.databaseID;
                filterInfo.db = dataStreamFilterInfo.db;
                filterInfo.filter = builder.build();
                procFilterDispatchMap.put(entry.getKey(), filterInfo);
            }
        }
        
        if (parentFilterDispatchMap != null)
        {
            for (var entry: parentFilterDispatchMap.entrySet())
            {
                var parentFilterInfo = entry.getValue();
                
                // only process DBs not already processed in first loop above
                if (!procFilterDispatchMap.containsKey(entry.getKey()))
                {
                    var filterInfo = new LocalFilterInfo();
                    filterInfo.databaseID = parentFilterInfo.databaseID;
                    filterInfo.db = parentFilterInfo.db;
                    filterInfo.filter = ProcedureFilter.Builder.from(filter)
                        .withParents((ProcedureFilter)parentFilterInfo.filter)
                        .build();
                    procFilterDispatchMap.put(entry.getKey(), filterInfo);
                }
            }
        }
        
        if (!procFilterDispatchMap.isEmpty())
            return procFilterDispatchMap;
        else
            return null;
    }


    @Override
    public void linkTo(IDataStreamStore dataStreamStore)
    {
        throw new UnsupportedOperationException();        
    }

}