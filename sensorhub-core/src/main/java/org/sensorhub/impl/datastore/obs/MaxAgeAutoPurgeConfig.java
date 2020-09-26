/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.obs;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.obs.IObsDbAutoPurgePolicy;


/**
 * <p>
 * Configuration for automatic database cleanup based on record age.
 * </p>
 *
 * @author Alex Robin
 * @since Oct 29, 2019
 */
public class MaxAgeAutoPurgeConfig extends HistoricalObsAutoPurgeConfig
{
    
    @DisplayInfo(label="Max Record Age", desc="Maximum age of data to be kept in storage (in seconds)")
    public double maxRecordAge = 7.*24.*3600.;
    
    
    @Override
    public IObsDbAutoPurgePolicy getPolicy()
    {
        return new MaxAgeAutoPurgePolicy(this);
    }
}
