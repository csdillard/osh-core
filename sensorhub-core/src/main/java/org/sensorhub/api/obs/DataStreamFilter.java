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

import java.util.Arrays;
import java.util.Collection;
import java.util.SortedSet;
import org.sensorhub.api.datastore.EmptyFilterIntersection;
import org.sensorhub.api.datastore.VersionFilter;
import org.sensorhub.api.procedure.ProcedureFilter;
import org.sensorhub.api.resource.ResourceFilter;
import org.sensorhub.utils.FilterUtils;
import com.google.common.collect.ImmutableSortedSet;
import net.opengis.swe.v20.DataComponent;


/**
 * <p>
 * Immutable filter object for procedure data streams.<br/>
 * There is an implicit AND between all filter parameters.<br/>
 * If internal IDs are used, no other filter options are allowed.
 * </p>
 *
 * @author Alex Robin
 * @date Sep 17, 2019
 */
public class DataStreamFilter extends ResourceFilter<IDataStreamInfo>
{
    protected ProcedureFilter procFilter;
    protected ObsFilter obsFilter;
    protected SortedSet<String> outputNames;
    protected SortedSet<String> observedProperties;
    protected VersionFilter versions;
    
    
    /*
     * this class can only be instantiated using builder
     */
    protected DataStreamFilter() {}


    public ProcedureFilter getProcedureFilter()
    {
        return procFilter;
    }
    
    
    public ObsFilter getObservationFilter()
    {
        return obsFilter;
    }


    public SortedSet<String> getOutputNames()
    {
        return outputNames;
    }


    public SortedSet<String> getObservedProperties()
    {
        return observedProperties;
    }


    public VersionFilter getVersions()
    {
        return versions;
    }
    
    
    public boolean testOutputName(IDataStreamInfo ds)
    {
        return (outputNames == null ||
            outputNames.contains(ds.getName()));
    }
    
    
    public boolean testVersion(IDataStreamInfo ds)
    {
        return (versions == null ||
            versions.test(ds.getRecordVersion()));
    }
    
    
    public boolean testObservedProperty(IDataStreamInfo ds)
    {
        if (observedProperties == null)
            return true;
        
        return hasObservable(ds.getRecordStructure());
    }
    
    
    public boolean hasObservable(DataComponent comp)
    {
        String def = comp.getDefinition();
        if (def != null && observedProperties.contains(def))
            return true;
        
        for (int i = 0; i < comp.getComponentCount(); i++)
        {
            if (hasObservable(comp.getComponent(i)))
                return true;
        }
        
        return false;
    }


    @Override
    public boolean test(IDataStreamInfo ds)
    {
        return (testOutputName(ds) &&
            testVersion(ds) &&
            testObservedProperty(ds) &&
            testValuePredicate(ds));
    }
    
    
    /**
     * Computes the intersection (logical AND) between this filter and another filter of the same kind
     * @param filter The other filter to AND with
     * @return The new composite filter
     * @throws EmptyFilterIntersection if the intersection doesn't exist
     */
    @Override
    public DataStreamFilter intersect(ResourceFilter<IDataStreamInfo> filter) throws EmptyFilterIntersection
    {
        if (filter == null)
            return this;
        
        return intersect((DataStreamFilter)filter, new Builder()).build();
    }
    
    
    protected <B extends DataStreamFilterBuilder<B, DataStreamFilter>> B intersect(DataStreamFilter otherFilter, B builder) throws EmptyFilterIntersection
    {
        super.and(otherFilter, builder);
        
        var procFilter = this.procFilter != null ? this.procFilter.intersect(otherFilter.procFilter) : otherFilter.procFilter;
        if (procFilter != null)
            builder.withProcedures(procFilter);
        
        var obsFilter = this.obsFilter != null ? this.obsFilter.intersect(otherFilter.obsFilter) : otherFilter.obsFilter;
        if (obsFilter != null)
            builder.withObservations(obsFilter);
        
        var outputNames = FilterUtils.intersect(this.outputNames, otherFilter.outputNames);
        if (outputNames != null)
            builder.withNames(outputNames);
        
        var observedProperties = FilterUtils.intersect(this.observedProperties, otherFilter.observedProperties);
        if (observedProperties != null)
            builder.withObservedProperties(observedProperties);
        
        return builder;
    }
    
    
    /**
     * Deep clone this filter
     */
    public DataStreamFilter clone()
    {
        return Builder.from(this).build();
    }
    
    
    /*
     * Builder
     */
    public static class Builder extends DataStreamFilterBuilder<Builder, DataStreamFilter>
    {
        public Builder()
        {
            super(new DataStreamFilter());
        }
        
        /**
         * Builds a new filter using the provided filter as a base
         * @param base Filter used as base
         * @return The new builder
         */
        public static Builder from(DataStreamFilter base)
        {
            return new Builder().copyFrom(base);
        }
    }
    
    
    /*
     * Nested builder for use within another builder
     */
    public static abstract class NestedBuilder<B> extends DataStreamFilterBuilder<NestedBuilder<B>, DataStreamFilter>
    {
        B parent;
        
        public NestedBuilder(B parent)
        {
            super(new DataStreamFilter());
            this.parent = parent;
        }
                
        public abstract B done();
    }


    @SuppressWarnings("unchecked")
    public static abstract class DataStreamFilterBuilder<
            B extends DataStreamFilterBuilder<B, F>,
            F extends DataStreamFilter>
        extends ResourceFilterBuilder<B, IDataStreamInfo, F>
    {
        
        protected DataStreamFilterBuilder(F instance)
        {
            super(instance);
        }
        
        
        protected B copyFrom(F other)
        {
            super.copyFrom(other);
            instance.procFilter = other.procFilter;
            instance.obsFilter = other.obsFilter;
            instance.outputNames = other.outputNames;
            instance.versions = other.versions;
            instance.observedProperties = other.observedProperties;
            return (B)this;
        }


        /**
         * Keep only datastreams generated by the procedures matching the filters.
         * @param filter Filter to select desired procedures
         * @return This builder for chaining
         */
        public B withProcedures(ProcedureFilter filter)
        {
            instance.procFilter = filter;
            return (B)this;
        }

        
        /**
         * Keep only datastreams generated by the procedures matching the filters.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link ProcedureFilter} builder for chaining
         */
        public ProcedureFilter.NestedBuilder<B> withProcedures()
        {
            return new ProcedureFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    DataStreamFilterBuilder.this.withProcedures(build());
                    return (B)DataStreamFilterBuilder.this;
                }                
            };
        }


        /**
         * Keep only datastreams from specific procedures (including all outputs).
         * @param procIDs Internal IDs of one or more procedures
         * @return This builder for chaining
         */
        public B withProcedures(Long... procIDs)
        {
            return withProcedures(Arrays.asList(procIDs));
        }


        /**
         * Keep only datastreams from specific procedures (including all outputs).
         * @param procIDs Internal IDs of one or more procedures
         * @return This builder for chaining
         */
        public B withProcedures(Collection<Long> procIDs)
        {
            withProcedures(new ProcedureFilter.Builder()
                .withInternalIDs(procIDs)
                .build());
            return (B)this;
        }
        

        /**
         * Keep only datastreams that have observations matching the filter
         * @param filter Filter to select desired observations
         * @return This builder for chaining
         */
        public B withObservations(ObsFilter filter)
        {
            instance.obsFilter = filter;
            return (B)this;
        }

        
        /**
         * Keep only datastreams that have observations matching the filter.<br/>
         * Call done() on the nested builder to go back to main builder.
         * @return The {@link ObsFilter} builder for chaining
         */
        public ObsFilter.NestedBuilder<B> withObservations()
        {
            return new ObsFilter.NestedBuilder<B>((B)this) {
                @Override
                public B done()
                {
                    DataStreamFilterBuilder.this.withObservations(build());
                    return (B)DataStreamFilterBuilder.this;
                }                
            };
        }
        
        
        /**
         * Keep only datastreams with the specified names.
         * @param names One or more datastream names
         * @return This builder for chaining
         */
        public B withNames(String... names)
        {
            return withNames(Arrays.asList(names));
        }
        
        
        /**
         * Keep only datastreams with the specified names.
         * @param names Collections of datastream names
         * @return This builder for chaining
         */
        public B withNames(Collection<String> names)
        {
            instance.outputNames = ImmutableSortedSet.copyOf(names);
            return (B)this;
        }
        
        
        /**
         * Keep only datastreams with the specified observed properties.
         * @param uris One or more observable property URIs
         * @return This builder for chaining
         */
        public B withObservedProperties(String... uris)
        {
            return withObservedProperties(Arrays.asList(uris));
        }
        
        
        /**
         * Keep only datastreams with the specified observed properties.
         * @param uris Collection of observable property URIs
         * @return This builder for chaining
         */
        public B withObservedProperties(Collection<String> uris)
        {
            instance.observedProperties = ImmutableSortedSet.copyOf(uris);
            return (B)this;
        }


        /**
         * Keep only datastreams at a specific version
         * @param version Version number
         * @return This builder for chaining
         */
        public B withVersion(int version)
        {
            instance.versions = new VersionFilter.Builder()
                .withSingleValue(version)
                .build();
            return (B)this;
        }


        /**
         * Keep only datastreams within a specific version range
         * @param minVersion Min version number
         * @param maxVersion Max version number
         * @return This builder for chaining
         */
        public B withVersionRange(int minVersion, int maxVersion)
        {
            instance.versions = new VersionFilter.Builder()
                .withRange(minVersion, maxVersion)
                .build();
            return (B)this;
        }


        /**
         * Keep only the latest version of selected datastreams
         * @return This builder for chaining
         */
        public B withAllVersions()
        {
            instance.versions = new VersionFilter.Builder()
                .withAllVersions()
                .build();
            return (B)this;
        }
    }

}
