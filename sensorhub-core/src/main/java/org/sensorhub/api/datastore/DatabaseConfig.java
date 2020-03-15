/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.datastore;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.config.DisplayInfo.Required;
import org.sensorhub.api.module.ModuleConfig;


/**
 * <p>
 * BAse config class for all database modules
 * </p>
 *
 * @author Alex Robin
 * @date Oct 12, 2019
 */
public class DatabaseConfig extends ModuleConfig
{
    @Required
    @DisplayInfo(desc="Numerical identifier of the database. Each database "
        + "must have a unique ID on the sensor hub")
    public int databaseID = 1;
}