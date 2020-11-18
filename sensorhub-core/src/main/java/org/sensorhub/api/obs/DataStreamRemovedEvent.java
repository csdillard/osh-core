/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.obs;


/**
 * <p>
 * Event sent when a datastream (i.e. output) is removed from a procedure
 * </p>
 *
 * @author Alex Robin
 * @date Sep 28, 2020
 */
public class DataStreamRemovedEvent extends DataStreamEvent
{
    
    /**
     * Pass-through to super class constructor
     * @see DataStreamEvent#DataStreamEvent(long, String, String)
     */
    public DataStreamRemovedEvent(long timeStamp, String procUID, String outputName)
    {
        super(timeStamp, procUID, outputName);
    }
    
    
    /**
     * Pass-through to super class constructor
     * @see DataStreamEvent#DataStreamEvent(String, String)
     */
    public DataStreamRemovedEvent(String procUID, String outputName)
    {
        super(procUID, outputName);
    }
}
