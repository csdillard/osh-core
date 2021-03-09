/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.tasking;

import org.sensorhub.api.procedure.ProcedureEvent;


/**
 * <p>
 * Base class for all command stream (i.e. tasking/control input) related events
 * </p>
 *
 * @author Alex Robin
 * @date Nov 23, 2020
 */
public abstract class CommandStreamEvent extends ProcedureEvent
{
    String controlInputName;


    /**
     * @param timeStamp time of event generation (unix time in milliseconds, base 1970)
     * @param procUID Unique ID of parent procedure
     * @param controlInputName Name of control input
     */
    public CommandStreamEvent(long timeStamp, String procUID, String controlInputName)
    {
        super(timeStamp, procUID);
        this.controlInputName = controlInputName;
    }
    
    
    /**
     * Helper constructor that sets the timestamp to current system time
     * @param procUID Unique ID of parent procedure
     * @param controlInputName Name of output producing the datastream
     */
    public CommandStreamEvent(String procUID, String controlInputName)
    {
        super(procUID);
        this.controlInputName = controlInputName;
    }


    /**
     * @return Name of control input
     */
    public String getControlInputName()
    {
        return controlInputName;
    }
}
