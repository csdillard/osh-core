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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;
import java.util.function.Predicate;
import org.sensorhub.api.command.ICommandAck;
import org.sensorhub.api.datastore.EmptyFilterIntersection;
import org.sensorhub.api.datastore.IQueryFilter;
import org.sensorhub.api.datastore.TemporalFilter;
import org.sensorhub.api.datastore.procedure.ProcedureFilter;
import org.sensorhub.utils.FilterUtils;
import org.sensorhub.utils.ObjectUtils;
import org.vast.util.BaseBuilder;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Longs;


/**
 * <p>
 * Immutable filter object for commands.<br/>
 * There is an implicit AND between all filter parameters.
 * </p>
 *
 * @author Alex Robin
 * @date Mar 11, 2021
 */
public class CommandFilter implements IQueryFilter, Predicate<ICommandAck>
{
    protected SortedSet<BigInteger> internalIDs;
    protected CommandStreamFilter commandStreamFilter;
    protected TemporalFilter actuationTime;
    protected TemporalFilter issueTime;
    protected SortedSet<String> senderIDs;
    protected Predicate<ICommandAck> valuePredicate;
    protected long limit = Long.MAX_VALUE;
    
    
    /*
     * this class can only be instantiated using builder
     */
    protected CommandFilter() {}


    public SortedSet<BigInteger> getInternalIDs()
    {
        return internalIDs;
    }


    public CommandStreamFilter getCommandStreamFilter()
    {
        return commandStreamFilter;
    }
    
    
    public TemporalFilter getActuationTime()
    {
        return actuationTime;
    }


    public TemporalFilter getIssueTime()
    {
        return issueTime;
    }


    public SortedSet<String> getSenderIDs()
    {
        return senderIDs;
    }


    public Predicate<ICommandAck> getValuePredicate()
    {
        return valuePredicate;
    }


    @Override
    public long getLimit()
    {
        return limit;
    }


    @Override
    public boolean test(ICommandAck cmd)
    {
        return (testActuationTime(cmd) &&
                testIssueTime(cmd) &&
                testValuePredicate(cmd));
    }
    
    
    public boolean testActuationTime(ICommandAck cmd)
    {
        return (actuationTime == null ||
                (cmd.getActuationTime() != null && actuationTime.test(cmd.getActuationTime())));
    }
    
    
    public boolean testIssueTime(ICommandAck cmd)
    {
        return (issueTime == null ||
                issueTime.test(cmd.getIssueTime()));
    }
    
    
    public boolean testSenderID(ICommandAck cmd)
    {
        return (senderIDs == null ||
            senderIDs.contains(cmd.getSenderID()));
    }
    
    
    public boolean testValuePredicate(ICommandAck cmd)
    {
        return (valuePredicate == null ||
                valuePredicate.test(cmd));
    }
    
    
    /**
     * Computes the intersection (logical AND) between this filter and another filter of the same kind
     * @param filter The other filter to AND with
     * @return The new composite filter
     * @throws EmptyFilterIntersection if the intersection doesn't exist
     */
    public CommandFilter intersect(CommandFilter filter) throws EmptyFilterIntersection
    {
        if (filter == null)
            return this;
        
        var builder = new Builder();
        
        var internalIDs = FilterUtils.intersect(this.internalIDs, filter.internalIDs);
        if (internalIDs != null)
            builder.withInternalIDs(internalIDs);
        
        var actuationTime = this.actuationTime != null ? this.actuationTime.intersect(filter.actuationTime) : filter.actuationTime;
        if (actuationTime != null)
            builder.withActuationTime(actuationTime);
        
        var issueTime = this.issueTime != null ? this.issueTime.intersect(filter.issueTime) : filter.issueTime;
        if (issueTime != null)
            builder.withIssueTime(issueTime);
        
        var commandStreamFilter = this.commandStreamFilter != null ? this.commandStreamFilter.intersect(filter.commandStreamFilter) : filter.commandStreamFilter;
        if (commandStreamFilter != null)
            builder.withCommandStreams(commandStreamFilter);
        
        var valuePredicate = this.valuePredicate != null ? this.valuePredicate.and(filter.valuePredicate) : filter.valuePredicate;
        if (valuePredicate != null)
            builder.withValuePredicate(valuePredicate);
        
        var limit = Math.min(this.limit, filter.limit);
        builder.withLimit(limit);
        
        return builder.build();
    }
    
    
    /**
     * Deep clone this filter
     */
    public CommandFilter clone()
    {
        return Builder.from(this).build();
    }


    @Override
    public String toString()
    {
        return ObjectUtils.toString(this, true, true);
    }
    
    
    /*
     * Builder
     */
    public static class Builder extends CommandFilterBuilder<Builder, CommandFilter>
    {
        public Builder()
        {
            this.instance = new CommandFilter();
        }
        
        /**
         * Builds a new filter using the provided filter as a base
         * @param base Filter used as base
         * @return The new builder
         */
        public static Builder from(CommandFilter base)
        {
            return new Builder().copyFrom(base);
        }
    }
    
    
    /*
     * Nested builder for use within another builder
     */
    public static abstract class NestedBuilder<B> extends CommandFilterBuilder<NestedBuilder<B>, CommandFilter>
    {
        B parent;
        
        public NestedBuilder(B parent)
        {
            this.parent = parent;
            this.instance = new CommandFilter();
        }
                
        public abstract B done();
    }


    @SuppressWarnings("unchecked")
    public static abstract class CommandFilterBuilder<
            B extends CommandFilterBuilder<B, T>,
            T extends CommandFilter>
        extends BaseBuilder<T>
    {
        
        protected CommandFilterBuilder()
        {
        }
        
        
        protected B copyFrom(CommandFilter base)
        {
            instance.internalIDs = base.internalIDs;
            instance.actuationTime = base.actuationTime;
            instance.issueTime = base.issueTime;
            instance.commandStreamFilter = base.commandStreamFilter;
            instance.valuePredicate = base.valuePredicate;
            instance.limit = base.limit;
            return (B)this;
        }
        
        
        /**
         * Keep only commands with specific internal IDs.
         * @param ids One or more internal IDs to select
         * @return This builder for chaining
         */
        public B withInternalIDs(BigInteger... ids)
        {
            return withInternalIDs(Arrays.asList(ids));
        }
        
        
        /**
         * Keep only commands with specific internal IDs.
         * @param ids Collection of internal IDs
         * @return This builder for chaining
         */
        public B withInternalIDs(Collection<BigInteger> ids)
        {
            instance.internalIDs = ImmutableSortedSet.copyOf(ids);
            return (B)this;
        }


        /**
         * Keep only commands whose actuation time matches the temporal filter.
         * @param filter Temporal filtering options
         * @return This builder for chaining
         */
        public B withActuationTime(TemporalFilter filter)
        {
            instance.actuationTime = filter;
            return (B)this;
        }

        
        /**
         * Keep only commands whose actuation time matches the temporal filter.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link TemporalFilter} builder for chaining
         */
        public TemporalFilter.NestedBuilder<B> withActuationTime()
        {
            return new TemporalFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    CommandFilterBuilder.this.withActuationTime(build());
                    return (B)CommandFilterBuilder.this;
                }                
            };
        }


        /**
         * Keep only commands whose actuation time is within the given period.
         * @param begin Beginning of desired period
         * @param end End of desired period
         * @return This builder for chaining
         */
        public B withActuationTimeDuring(Instant begin, Instant end)
        {
            return withActuationTime(new TemporalFilter.Builder()
                .withRange(begin, end)
                .build());
        }


        /**
         * Keep only commands whose issue time matches the temporal filter.
         * @param filter Temporal filtering options
         * @return This builder for chaining
         */
        public B withIssueTime(TemporalFilter filter)
        {
            instance.issueTime = filter;
            return (B)this;
        }


        /**
         * Keep only commands whose issue time is within the given period.
         * @param begin Beginning of desired period
         * @param end End of desired period
         * @return This builder for chaining
         */
        public B withIssueTimeDuring(Instant begin, Instant end)
        {
            return withIssueTime(new TemporalFilter.Builder()
                    .withRange(begin, end)
                    .build());
        }
        
        
        /**
         * Keep only commands with the latest issue time.
         * @return This builder for chaining
         */
        public B withLatestIssued()
        {
            return withIssueTime(new TemporalFilter.Builder()
                .withLatestTime().build());
        }
        
        
        /**
         * Keep only commands issued by specific senders.
         * @param ids One or more sender IDs
         * @return This builder for chaining
         */
        public B withSenderIDs(String... ids)
        {
            return withSenderIDs(Arrays.asList(ids));
        }
        
        
        /**
         * Keep only commands issued by specific senders.
         * @param ids Collections of sender IDs
         * @return This builder for chaining
         */
        public B withSenderIDs(Collection<String> ids)
        {
            instance.senderIDs = ImmutableSortedSet.copyOf(ids);
            return (B)this;
        }


        /**
         * Keep only commands from command streams matching the filter.
         * @param filter Filter to select desired command streams
         * @return This builder for chaining
         */
        public B withCommandStreams(CommandStreamFilter filter)
        {
            instance.commandStreamFilter = filter;
            return (B)this;
        }
        
        
        /**
         * Keep only commands from command streams matching the filter.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link DataStreamFilter} builder for chaining
         */
        public CommandStreamFilter.NestedBuilder<B> withCommandStreams()
        {
            return new CommandStreamFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    CommandFilterBuilder.this.withCommandStreams(build());
                    return (B)CommandFilterBuilder.this;
                }                
            };
        }


        /**
         * Keep only commands from specific command streams.
         * @param ids Internal IDs of one or more command streams
         * @return This builder for chaining
         */
        public B withCommandStreams(long... ids)
        {
            return withCommandStreams(Longs.asList(ids));
        }


        /**
         * Keep only commands from specific command streams.
         * @param ids Collection of internal IDs of command streams
         * @return This builder for chaining
         */
        public B withCommandStreams(Collection<Long> ids)
        {
            return withCommandStreams(new CommandStreamFilter.Builder()
                .withInternalIDs(ids)
                .build());
        }


        /**
         * Keep only commands from procedures matching the filter.
         * @param filter Filter to select desired procedures
         * @return This builder for chaining
         */
        public B withProcedures(ProcedureFilter filter)
        {
            return withCommandStreams(new CommandStreamFilter.Builder()
                .withProcedures(filter)
                .build());
        }
        
        
        /**
         * Keep only commands from procedures matching the filter.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link ProcedureFilter} builder for chaining
         */
        public ProcedureFilter.NestedBuilder<B> withProcedures()
        {
            return new ProcedureFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    CommandFilterBuilder.this.withProcedures(build());
                    return (B)CommandFilterBuilder.this;
                }                
            };
        }


        /**
         * Keep only commands from specific procedures (including all tasking inputs).
         * @param procIDs Internal IDs of one or more procedures
         * @return This builder for chaining
         */
        public B withProcedures(long... procIDs)
        {
            return withProcedures(Longs.asList(procIDs));
        }


        /**
         * Keep only commands from specific procedures (including all tasking inputs).
         * @param procIDs Collection of internal IDs of procedures
         * @return This builder for chaining
         */
        public B withProcedures(Collection<Long> procIDs)
        {
            return withCommandStreams(new CommandStreamFilter.Builder()
                .withProcedures(procIDs)
                .build());
        }


        /**
         * Keep only commands from selected tasking inputs of a specific procedure
         * @param procID Internal ID of the procedure
         * @param commandNames Names of one or more command inputs of interest
         * @return This builder for chaining
         */
        public B withProcedure(long procID, String... commandNames)
        {
            return withCommandStreams(new CommandStreamFilter.Builder()
                .withProcedures(procID)
                .withControlInputNames(commandNames)
                .build());
        }


        /**
         * Keep only the commands whose data matches the predicate
         * @param valuePredicate The predicate to test the command data
         * @return This builder for chaining
         */
        public B withValuePredicate(Predicate<ICommandAck> valuePredicate)
        {
            instance.valuePredicate = valuePredicate;
            return (B)this;
        }


        /**
         * Sets the maximum number of commands to retrieve
         * @param limit Max command count
         * @return This builder for chaining
         */
        public B withLimit(long limit)
        {
            instance.limit = limit;
            return (B)this;
        }
    }
}
