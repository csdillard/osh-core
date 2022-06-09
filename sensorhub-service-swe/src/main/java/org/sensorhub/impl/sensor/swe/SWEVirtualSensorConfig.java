/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.swe;

import java.util.ArrayList;
import java.util.List;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.sensor.SensorConfig;
import org.sensorhub.impl.comm.HTTPConfig;
import org.sensorhub.impl.module.RobustConnectionConfig;


/**
 * <p>
 * Configuration for a SWE Virtual Sensor.
 * </p>
 *
 * @author Alex Robin
 * @since Sep 5, 2015
 */
public class SWEVirtualSensorConfig extends SensorConfig
{
    @DisplayInfo(label="Sensor UID", desc="Unique ID of sensor to connect to on SOS and SPS servers")
    public String sensorUID;
    
    @DisplayInfo(label="SOS Endpoint", desc="SOS endpoint to fetch data from")
    public HTTPConfig sosEndpoint;
    
    @DisplayInfo(label="SPS Endpoint", desc="SPS endpoint to send commands to")
    public HTTPConfig spsEndpoint;
    
    @DisplayInfo(label="Observed Properties", desc="List of observed properties URI to make available as outputs")
    public List<String> observedProperties = new ArrayList<>();
    
    @DisplayInfo(label="Use WebSockets for SOS", desc="Set if WebSocket protocol should be used to get streaming data from SOS")
    public boolean sosUseWebsockets = false;
    
    //@DisplayInfo(label="Use WebSockets for SPS", desc="Set if websockets protocol should be used to send commands to SPS")
    //public boolean spsUseWebsockets = false;
    
    @DisplayInfo(label="Connection Settings")
    public RobustConnectionConfig connectionConfig = new RobustConnectionConfig();
    
    public SWEVirtualSensorConfig()
    {
        this.moduleClass = SWEVirtualSensor.class.getCanonicalName();
        // Set a longer connection timeout than the default offered by RobustConnectionConfig.
        // This will allow us to wait longer for observations, in cases where observations are
        // infrequent.
        connectionConfig.connectTimeout = 30000;
        // Set a shorter time between reconnection attempts, so that data is less likely to be
        // lost during the outage.
        connectionConfig.reconnectPeriod = 1000;
        // Set the default to allow infinitely many reconnection attempts.
        connectionConfig.reconnectAttempts = -1;
    }

}
