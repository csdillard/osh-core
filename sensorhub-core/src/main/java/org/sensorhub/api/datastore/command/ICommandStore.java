/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.datastore.command;

import java.math.BigInteger;
import java.util.stream.Stream;
import org.sensorhub.api.command.ICommandDataWithAck;
import org.sensorhub.api.datastore.IDataStore;
import org.sensorhub.api.datastore.ValueField;
import org.sensorhub.api.datastore.command.ICommandStore.CommandField;


/**
 * <p>
 * Generic interface for data stores containing commands.
 * </p><p>
 * Commands are organized into command streams. Each command stream contains
 * commands sharing the same schema (i.e. record structure).
 * </p><p>
 * Commands retrieved by select methods are sorted by actuation time.
 * </p>
 *
 * @author Alex Robin
 * @date Mar 11, 2021
 */
public interface ICommandStore extends IDataStore<BigInteger, ICommandDataWithAck, CommandField, CommandFilter>
{
    public static class CommandField extends ValueField
    {
        public static final CommandField COMMANDSTREAM_ID = new CommandField("commandStreamID");
        public static final CommandField ISSUE_TIME = new CommandField("issueTime");
        public static final CommandField ACTUATION_TIME  = new CommandField("actuationTime");
        public static final CommandField PARAMETERS = new CommandField("params");
        
        public CommandField(String name)
        {
            super(name);
        }
    }
    
    
    /**
     * @return Associated command streams store
     */
    ICommandStreamStore getCommandStreams();
    
    
    /**
     * Add an observation to the datastore
     * @param obs
     * @return The auto-generated ID
     */
    public BigInteger add(ICommandDataWithAck obs);
    
    
    /**
     * Select statistics for commands matching the query
     * @param query filter to select desired procedures and time range
     * @return stream of statistics buckets. Each item represents statistics for
     * commands recorded for a given procedure
     */
    public Stream<CommandStats> getStatistics(CommandStatsQuery query);


    /**
     * @return A builder for a filter compatible with this datastore
     */
    public default CommandFilter.Builder filterBuilder()
    {
        return new CommandFilter.Builder();
    }
    
    
    @Override
    public default CommandFilter selectAllFilter()
    {
        return filterBuilder().build();
    }
    
}
