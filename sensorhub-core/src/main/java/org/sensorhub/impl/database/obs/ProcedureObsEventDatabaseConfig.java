/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.database.obs;

import java.util.Set;
import java.util.TreeSet;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.FieldType;
import org.sensorhub.api.config.DisplayInfo.FieldType.Type;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.database.DatabaseConfig;


/**
 * <p>
 * Config class for {@link ProcedureObsEventDatabase}.
 * </p>
 *
 * @author Alex Robin
 * @date Nov 18, 2020
 */
public class ProcedureObsEventDatabaseConfig extends DatabaseConfig
{
    
    @Required
    @DisplayInfo(label="Database Config", desc="Configuration of underlying database")
    public DatabaseConfig dbConfig;
    
    
    @Required
    @FieldType(Type.PROCEDURE_UID)
    @DisplayInfo(label="Procedure UIDs", desc="Unique IDs of streaming data sources managed by this database")
    public Set<String> procedureUIDs = new TreeSet<>();
    
    
    @DisplayInfo(label="Automatic Purge Policy", desc="Policy for automatically purging historical data")
    public HistoricalObsAutoPurgeConfig autoPurgeConfig;
    

    @DisplayInfo(desc="Minimum period between database commits (in ms)")
    public int minCommitPeriod = 10000;
    
    
    public ProcedureObsEventDatabaseConfig()
    {
        this.moduleClass = ProcedureObsEventDatabase.class.getCanonicalName();
    }
}
