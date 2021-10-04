/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.data;

import java.time.Instant;
import java.util.Map;
import org.sensorhub.api.system.SystemId;
import org.vast.util.IResource;
import org.vast.util.TimeExtent;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;


/**
 * <p>
 * Interface for DataStream descriptors
 * </p>
 *
 * @author Alex Robin
 * @date Mar 23, 2020
 */
public interface IDataStreamInfo extends IResource
{

    /**
     * @return The identifier of the system that produces this data stream
     */
    SystemId getSystemID();


    /**
     * @return The name of the system output that is/was the source of
     * this data stream
     */
    String getOutputName();
    

    /**
     * @return The data stream record structure
     */
    DataComponent getRecordStructure();


    /**
     * @return The recommended encoding for the data stream
     */
    DataEncoding getRecordEncoding();
    
    
    /**
     * @return The time of validity of this datastream. This corresponds to the time
     * during which the corresponding system output actually existed.
     */
    TimeExtent getValidTime();
    
    
    /**
     * @return The range of phenomenon times of observations that are part
     * of this datastream, or null if no observations have been recorded yet.
     */
    TimeExtent getPhenomenonTimeRange();
    
    
    /**
     * @return The range of result times of observations that are part
     * of this datastream, or null if no observations have been recorded yet.
     */
    TimeExtent getResultTimeRange();
    
    
    /**
     * @return True if this datastream contains observations acquired for a discrete
     * number of result times (e.g. model runs, test campaigns, etc.) 
     */
    boolean hasDiscreteResultTimes();
    
    
    /**
     * @return A map of discrete result times to the phenomenon time range of all
     * observations whose result was produced at each result time, or an empty map if
     * {@link #hasDiscreteResultTimes()} returns true.
     */
    Map<Instant, TimeExtent> getDiscreteResultTimes();

}