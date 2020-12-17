/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.ui;

import java.util.concurrent.Callable;
import org.sensorhub.api.ISensorHub;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.database.DatabaseConfig;
import org.sensorhub.api.database.IProcedureObsDatabase;
import org.sensorhub.api.database.IProcedureObsDatabaseModule;
import org.sensorhub.api.datastore.feature.IFoiStore;
import org.sensorhub.api.datastore.obs.IDataStreamStore;
import org.sensorhub.api.datastore.obs.IObsStore;
import org.sensorhub.api.datastore.procedure.IProcedureStore;
import org.sensorhub.api.event.IEventListener;
import org.sensorhub.api.event.IEventSourceInfo;
import org.sensorhub.api.module.IModuleStateManager;
import org.sensorhub.api.module.ModuleEvent.ModuleState;


public class FederatedDbModuleAdapter implements IProcedureObsDatabaseModule<DatabaseConfig>
{
    IProcedureObsDatabase delegate;
    
    
    public FederatedDbModuleAdapter(IProcedureObsDatabase db)
    {
        this.delegate = db;
    }
    

    public Integer getDatabaseNum()
    {
        return delegate.getDatabaseNum();
    }


    public <T> T executeTransaction(Callable<T> transaction) throws Exception
    {
        return delegate.executeTransaction(transaction);
    }


    public IProcedureStore getProcedureStore()
    {
        return delegate.getProcedureStore();
    }


    public IFoiStore getFoiStore()
    {
        return delegate.getFoiStore();
    }


    public void commit()
    {
        delegate.commit();
    }


    public IObsStore getObservationStore()
    {
        return delegate.getObservationStore();
    }


    public boolean isOpen()
    {
        return delegate.isOpen();
    }


    public IDataStreamStore getDataStreamStore()
    {
        return delegate.getDataStreamStore();
    }


    public boolean isReadOnly()
    {
        return delegate.isReadOnly();
    }


    @Override
    public void setParentHub(ISensorHub hub)
    {                
    }


    @Override
    public ISensorHub getParentHub()
    {
        return null;
    }


    @Override
    public void setConfiguration(DatabaseConfig config)
    {
    }


    @Override
    public DatabaseConfig getConfiguration()
    {
        return new DatabaseConfig();
    }


    @Override
    public String getName()
    {
        return "Federated Database";
    }


    @Override
    public String getDescription()
    {
        return "The federated database that provides access to all observation data and procedure metadata on this hub";
    }


    @Override
    public String getLocalID()
    {
        return "$$FEDERATED_DB";
    }


    @Override
    public boolean isInitialized()
    {
        return true;
    }


    @Override
    public boolean isStarted()
    {
        return true;
    }


    @Override
    public ModuleState getCurrentState()
    {
        return ModuleState.STARTED;
    }


    @Override
    public boolean waitForState(ModuleState state, long timeout)
    {
        return false;
    }


    @Override
    public String getStatusMessage()
    {
        return null;
    }


    @Override
    public Throwable getCurrentError()
    {
        return null;
    }


    @Override
    public void requestInit(boolean force) throws SensorHubException
    {        
    }


    @Override
    public void init() throws SensorHubException
    {        
    }


    @Override
    public void init(DatabaseConfig config) throws SensorHubException
    {        
    }


    @Override
    public void updateConfig(DatabaseConfig config) throws SensorHubException
    {        
    }


    @Override
    public void requestStart() throws SensorHubException
    {        
    }


    @Override
    public void start() throws SensorHubException
    {        
    }


    @Override
    public void requestStop() throws SensorHubException
    {        
    }


    @Override
    public void stop() throws SensorHubException
    {        
    }


    @Override
    public void saveState(IModuleStateManager saver) throws SensorHubException
    {        
    }


    @Override
    public void loadState(IModuleStateManager loader) throws SensorHubException
    {        
    }


    @Override
    public void cleanup() throws SensorHubException
    {        
    }


    @Override
    public void registerListener(IEventListener listener)
    {        
    }


    @Override
    public void unregisterListener(IEventListener listener)
    {        
    }


    @Override
    public IEventSourceInfo getEventSourceInfo()
    {
        return null;
    }
}