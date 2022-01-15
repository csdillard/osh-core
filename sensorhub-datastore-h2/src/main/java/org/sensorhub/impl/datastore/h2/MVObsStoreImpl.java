/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore.h2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVBTreeMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.RangeCursor;
import org.h2.mvstore.WriteBuffer;
import org.sensorhub.api.data.IDataStreamInfo;
import org.sensorhub.api.data.IObsData;
import org.sensorhub.api.datastore.IdProvider;
import org.sensorhub.api.datastore.feature.IFoiStore;
import org.sensorhub.api.datastore.obs.DataStreamKey;
import org.sensorhub.api.datastore.obs.IDataStreamStore;
import org.sensorhub.api.datastore.obs.IObsStore;
import org.sensorhub.api.datastore.obs.ObsFilter;
import org.sensorhub.api.datastore.obs.ObsStats;
import org.sensorhub.api.datastore.obs.ObsStatsQuery;
import org.sensorhub.api.feature.FeatureId;
import org.sensorhub.impl.datastore.DataStoreUtils;
import org.sensorhub.impl.datastore.MergeSortSpliterator;
import org.sensorhub.impl.datastore.h2.MVDatabaseConfig.IdProviderType;
import org.vast.util.Asserts;
import org.vast.util.TimeExtent;
import com.google.common.collect.Range;
import com.google.common.hash.Hashing;
import net.opengis.swe.v20.DataBlock;


/**
 * <p>
 * Implementation of obs store based on H2 MVStore.
 * </p><p>
 * Several instances of this store can be contained in the same MVStore
 * as long as they have different names.
 * </p>
 *
 * @author Alex Robin
 * @date Apr 7, 2018
 */
public class MVObsStoreImpl implements IObsStore
{
    private static final String OBS_RECORDS_MAP_NAME = "obs_records";
    private static final String OBS_SERIES_MAP_NAME = "obs_series";
    private static final String OBS_SERIES_FOI_MAP_NAME = "obs_series_foi";
    
    protected MVStore mvStore;
    protected MVDataStoreInfo dataStoreInfo;
    protected MVDataStreamStoreImpl dataStreamStore;
    protected MVBTreeMap<MVTimeSeriesRecordKey, IObsData> obsRecordsIndex;
    protected MVBTreeMap<MVTimeSeriesKey, MVTimeSeriesInfo> obsSeriesMainIndex;
    protected MVBTreeMap<MVTimeSeriesKey, Boolean> obsSeriesByFoiIndex;
    
    protected IFoiStore foiStore;
    protected int maxSelectedSeriesOnJoin = 10000;
    
    
    static class TimeParams
    {
        Range<Instant> phenomenonTimeRange;
        Range<Instant> resultTimeRange;
        boolean currentTimeOnly;
        boolean latestResultOnly;
        
        
        TimeParams(ObsFilter filter)
        {
            // get phenomenon time range
            phenomenonTimeRange = filter.getPhenomenonTime() != null ?
                filter.getPhenomenonTime().getRange() : H2Utils.ALL_TIMES_RANGE;
            
            // get result time range
            resultTimeRange = filter.getResultTime() != null ?
                filter.getResultTime().getRange() : H2Utils.ALL_TIMES_RANGE;
                
            latestResultOnly = filter.getResultTime() != null && filter.getResultTime().isLatestTime();
            currentTimeOnly = filter.getPhenomenonTime() != null && filter.getPhenomenonTime().isCurrentTime();
        }
    }
    
    
    private MVObsStoreImpl()
    {
    }


    /**
     * Opens an existing obs store or create a new one with the specified name
     * @param mvStore MVStore instance containing the required maps
     * @param systemStore associated system descriptions data store
     * @param foiStore associated FOIs data store
     * @param idProviderType Type of ID provider to use to generate new datastream IDs
     * @param newStoreInfo Data store info to use if a new store needs to be created
     * @return The existing datastore instance 
     */
    public static MVObsStoreImpl open(MVStore mvStore, IdProviderType idProviderType, MVDataStoreInfo newStoreInfo)
    {
        var dataStoreInfo = H2Utils.getDataStoreInfo(mvStore, newStoreInfo.getName());
        if (dataStoreInfo == null)
        {
            dataStoreInfo = newStoreInfo;
            H2Utils.addDataStoreInfo(mvStore, dataStoreInfo);
        }
        
        // create ID provider
        IdProvider<IDataStreamInfo> idProvider = null;
        if (idProviderType == IdProviderType.UID_HASH)
        {
            var hashFunc = Hashing.murmur3_128(741532149);
            idProvider = dsInfo -> {
                var hasher = hashFunc.newHasher();
                hasher.putLong(dsInfo.getSystemID().getInternalID());
                hasher.putUnencodedChars(dsInfo.getOutputName());
                hasher.putLong(dsInfo.getValidTime().begin().toEpochMilli());
                return hasher.hash().asLong() & 0xFFFFFFFFFFFFL; // keep only 48 bits
            };
        }
        
        return new MVObsStoreImpl().init(mvStore, dataStoreInfo, idProvider);
    }
    
    
    private MVObsStoreImpl init(MVStore mvStore, MVDataStoreInfo dataStoreInfo, IdProvider<IDataStreamInfo> dsIdProvider)
    {
        this.mvStore = Asserts.checkNotNull(mvStore, MVStore.class);
        this.dataStoreInfo = Asserts.checkNotNull(dataStoreInfo, MVDataStoreInfo.class);
        this.dataStreamStore = new MVDataStreamStoreImpl(this, dsIdProvider);
        
        // persistent class mappings for Kryo
        var kryoClassMap = mvStore.openMap(MVObsSystemDatabase.KRYO_CLASS_MAP_NAME, new MVBTreeMap.Builder<String, Integer>());
        
        // open observation map
        String mapName = dataStoreInfo.getName() + ":" + OBS_RECORDS_MAP_NAME;
        this.obsRecordsIndex = mvStore.openMap(mapName, new MVBTreeMap.Builder<MVTimeSeriesRecordKey, IObsData>()
                .keyType(new MVTimeSeriesRecordKeyDataType())
                .valueType(new ObsDataType(kryoClassMap)));
        
        // open observation series map
        mapName = dataStoreInfo.getName() + ":" + OBS_SERIES_MAP_NAME;
        this.obsSeriesMainIndex = mvStore.openMap(mapName, new MVBTreeMap.Builder<MVTimeSeriesKey, MVTimeSeriesInfo>()
                .keyType(new MVObsSeriesKeyByDataStreamDataType())
                .valueType(new MVTimeSeriesInfoDataType()));
        
        mapName = dataStoreInfo.getName() + ":" + OBS_SERIES_FOI_MAP_NAME;
        this.obsSeriesByFoiIndex = mvStore.openMap(mapName, new MVBTreeMap.Builder<MVTimeSeriesKey, Boolean>()
                .keyType(new MVObsSeriesKeyByFoiDataType())
                .valueType(new MVVoidDataType()));
        
        return this;
    }


    @Override
    public String getDatastoreName()
    {
        return dataStoreInfo.getName();
    }
    

    @Override
    public IDataStreamStore getDataStreams()
    {
        return dataStreamStore;
    }


    @Override
    public long getNumRecords()
    {
        return obsRecordsIndex.sizeAsLong();
    }
    
    
    Stream<MVTimeSeriesInfo> getAllObsSeries(Range<Instant> resultTimeRange)
    {
        MVTimeSeriesKey first = new MVTimeSeriesKey(0, 0, resultTimeRange.lowerEndpoint());
        MVTimeSeriesKey last = new MVTimeSeriesKey(Long.MAX_VALUE, Long.MAX_VALUE, resultTimeRange.upperEndpoint());
        RangeCursor<MVTimeSeriesKey, MVTimeSeriesInfo> cursor = new RangeCursor<>(obsSeriesMainIndex, first, last);
        
        return cursor.entryStream()
            .filter(e -> resultTimeRange.contains(e.getKey().resultTime))
            .map(e -> {
                e.getValue().key = e.getKey();
                return e.getValue();
            });
    }
    
    
    Stream<MVTimeSeriesInfo> getObsSeriesByDataStream(long dataStreamID, Range<Instant> resultTimeRange, boolean latestResultOnly)
    {
        // special case when latest result is requested
        if (latestResultOnly)
        {
            MVTimeSeriesKey key = new MVTimeSeriesKey(dataStreamID, Long.MAX_VALUE, Instant.MAX);
            MVTimeSeriesKey lastKey = obsSeriesMainIndex.floorKey(key);
            if (lastKey == null || lastKey.dataStreamID != dataStreamID)
                return null;
            resultTimeRange = Range.singleton(lastKey.resultTime);
        }
       
        // scan series for all FOIs of the selected system and result times
        MVTimeSeriesKey first = new MVTimeSeriesKey(dataStreamID, 0, Instant.MIN);
        MVTimeSeriesKey last = new MVTimeSeriesKey(dataStreamID, Long.MAX_VALUE, resultTimeRange.upperEndpoint());
        RangeCursor<MVTimeSeriesKey, MVTimeSeriesInfo> cursor = new RangeCursor<>(obsSeriesMainIndex, first, last);
        
        final Range<Instant> finalResultTimeRange = resultTimeRange;
        return cursor.entryStream()
            .filter(e -> {
                // filter out series with result time not matching filter
                // but always select series that have multiple result times (e.g. result time = phenomenonTime)
                var resultTime = e.getKey().resultTime;
                return resultTime == Instant.MIN || finalResultTimeRange.contains(resultTime);
            })
            .map(e -> {
                MVTimeSeriesInfo series = e.getValue();
                series.key = e.getKey();
                return series;
            });
    }
    
    
    Stream<MVTimeSeriesInfo> getObsSeriesByFoi(long foiID, Range<Instant> resultTimeRange, boolean latestResultOnly)
    {
        // special case when latest result is requested
        if (latestResultOnly)
        {
            MVTimeSeriesKey key = new MVTimeSeriesKey(Long.MAX_VALUE, foiID, Instant.MAX);
            MVTimeSeriesKey lastKey = obsSeriesByFoiIndex.floorKey(key);
            if (lastKey == null || lastKey.foiID != foiID)
                return null;
            resultTimeRange = Range.singleton(lastKey.resultTime);
        }
       
        // scan series for all systems that produced observations of the selected FOI
        MVTimeSeriesKey first = new MVTimeSeriesKey(0, foiID, Instant.MIN);
        MVTimeSeriesKey last = new MVTimeSeriesKey(Long.MAX_VALUE, foiID, resultTimeRange.upperEndpoint());
        RangeCursor<MVTimeSeriesKey, Boolean> cursor = new RangeCursor<>(obsSeriesByFoiIndex, first, last);
        
        final Range<Instant> finalResultTimeRange = resultTimeRange;
        return cursor.keyStream()
            .filter(k -> {
                // filter out series with result time not matching filter
                // but always select series that have multiple result times (e.g. result time = phenomenonTime)
                var resultTime = k.resultTime;
                return resultTime == Instant.MIN || finalResultTimeRange.contains(resultTime);
            })
            .map(k -> {
                MVTimeSeriesInfo series = obsSeriesMainIndex.get(k);
                series.key = k;
                return series;
            });
    }
    
    
    RangeCursor<MVTimeSeriesRecordKey, IObsData> getObsCursor(long seriesID, Range<Instant> phenomenonTimeRange)
    {
        MVTimeSeriesRecordKey first = new MVTimeSeriesRecordKey(seriesID, phenomenonTimeRange.lowerEndpoint());
        MVTimeSeriesRecordKey last = new MVTimeSeriesRecordKey(seriesID, phenomenonTimeRange.upperEndpoint());
        return new RangeCursor<>(obsRecordsIndex, first, last);
    }
    
    
    Stream<Entry<BigInteger, IObsData>> getObsStream(MVTimeSeriesInfo series, Range<Instant> resultTimeRange, Range<Instant> phenomenonTimeRange, boolean currentTimeOnly, boolean latestResultOnly)
    {
        // if series is a special case where all obs have resultTime = phenomenonTime
        if (series.key.resultTime == Instant.MIN)
        {
            // if request is for current time only, get only the obs with
            // phenomenon time right before current time
            if (currentTimeOnly)
            {
                MVTimeSeriesRecordKey maxKey = new MVTimeSeriesRecordKey(series.id, Instant.now());
                Entry<MVTimeSeriesRecordKey, IObsData> e = obsRecordsIndex.floorEntry(maxKey);
                if (e != null && e.getKey().seriesID == series.id)
                    return Stream.of(mapToPublicEntry(e));
                else
                    return Stream.empty();
            }
            
            // if request if for latest result only, get only the latest obs in series
            if (latestResultOnly)
            {
                MVTimeSeriesRecordKey maxKey = new MVTimeSeriesRecordKey(series.id, Instant.MAX);
                Entry<MVTimeSeriesRecordKey, IObsData> e = obsRecordsIndex.floorEntry(maxKey);
                if (e != null && e.getKey().seriesID == series.id)
                    return Stream.of(mapToPublicEntry(e));
                else
                    return Stream.empty();
            }
            
            // else further restrict the requested time range using result time filter
            phenomenonTimeRange = resultTimeRange.intersection(phenomenonTimeRange);
        }
        
        // scan using a cursor on main obs index
        // recreating full entries in the process
        RangeCursor<MVTimeSeriesRecordKey, IObsData> cursor = getObsCursor(series.id, phenomenonTimeRange);
        return cursor.entryStream()
            .map(e -> {
                return mapToPublicEntry(e);
            });
    }
    
    
    BigInteger mapToPublicKey(MVTimeSeriesRecordKey internalKey)
    {
        // compute internal ID
        WriteBuffer buf = new WriteBuffer(24); // seriesID + timestamp seconds + nanos
        DataUtils.writeVarLong(buf.getBuffer(), internalKey.getSeriesID());
        H2Utils.writeInstant(buf, internalKey.getTimeStamp());
        return new BigInteger(buf.getBuffer().array(), 0, buf.position());
    }
    
    
    MVTimeSeriesRecordKey mapToInternalKey(Object keyObj)
    {
        Asserts.checkArgument(keyObj instanceof BigInteger, "key must be a BigInteger");
        BigInteger key = (BigInteger)keyObj;

        try
        {
            // parse from BigInt
            ByteBuffer buf = ByteBuffer.wrap(key.toByteArray());
            long seriesID = DataUtils.readVarLong(buf);
            Instant phenomenonTime = H2Utils.readInstant(buf);
            
            return new MVTimeSeriesRecordKey(seriesID, phenomenonTime);
        }
        catch (Exception e)
        {
            // invalid bigint key
            return null;
        }
    }
    
    
    Entry<BigInteger, IObsData> mapToPublicEntry(Entry<MVTimeSeriesRecordKey, IObsData> internalEntry)
    {
        BigInteger obsID = mapToPublicKey(internalEntry.getKey());
        return new DataUtils.MapEntry<>(obsID, internalEntry.getValue());
    }
    
    
    /*
     * Select all obs series matching the filter
     */
    protected Stream<MVTimeSeriesInfo> selectObsSeries(ObsFilter filter, TimeParams timeParams)
    {
        // otherwise prepare stream of matching obs series
        Stream<MVTimeSeriesInfo> obsSeries = null;
        
        // if no datastream nor FOI filter used, scan all obs
        if (filter.getDataStreamFilter() == null && filter.getFoiFilter() == null)
        {
            obsSeries = getAllObsSeries(timeParams.resultTimeRange);
        }
        
        // only datastream filter used
        else if (filter.getDataStreamFilter() != null && filter.getFoiFilter() == null)
        {
            // stream directly from list of selected datastreams
            obsSeries = DataStoreUtils.selectDataStreamIDs(dataStreamStore, filter.getDataStreamFilter())
                .flatMap(id -> getObsSeriesByDataStream(id, timeParams.resultTimeRange, timeParams.latestResultOnly));
        }
        
        // only FOI filter used
        else if (filter.getFoiFilter() != null && filter.getDataStreamFilter() == null)
        {
            // stream directly from list of selected fois
            obsSeries = DataStoreUtils.selectFeatureIDs(foiStore, filter.getFoiFilter())
                .flatMap(id -> getObsSeriesByFoi(id, timeParams.resultTimeRange, timeParams.latestResultOnly));
        }
        
        // both datastream and FOI filters used
        else
        {
            // create set of selected datastreams
            AtomicInteger counter = new AtomicInteger();
            Set<Long> dataStreamIDs = DataStoreUtils.selectDataStreamIDs(dataStreamStore, filter.getDataStreamFilter())
                .peek(s -> {
                    // make sure set size cannot go over a threshold
                    if (counter.incrementAndGet() >= 100*maxSelectedSeriesOnJoin)
                        throw new IllegalStateException("Too many datastreams selected. Please refine your filter");
                })
                .collect(Collectors.toSet());

            if (dataStreamIDs.isEmpty())
                return Stream.empty();
            
            // stream from fois and filter on datastream IDs
            obsSeries = DataStoreUtils.selectFeatureIDs(foiStore, filter.getFoiFilter())
                .flatMap(id -> {
                    return getObsSeriesByFoi(id, timeParams.resultTimeRange, timeParams.latestResultOnly)
                        .filter(s -> dataStreamIDs.contains(s.key.dataStreamID));
                });
        }
        
        return obsSeries;
    }


    @Override
    public Stream<Entry<BigInteger, IObsData>> selectEntries(ObsFilter filter, Set<ObsField> fields)
    {        
        // stream obs directly in case of filtering by internal IDs
        if (filter.getInternalIDs() != null)
        {
            var obsStream = filter.getInternalIDs().stream()
                .map(k -> mapToInternalKey(k))
                .map(k -> obsRecordsIndex.getEntry(k))
                .filter(Objects::nonNull)
                .map(e -> mapToPublicEntry(e));
            
            return getPostFilteredResultStream(obsStream, filter);
        }
        
        // select obs series matching the filter
        var timeParams = new TimeParams(filter);
        var obsSeries = selectObsSeries(filter, timeParams);
        
        // create obs streams for each selected series
        // and keep all spliterators in array list
        final var obsStreams = new ArrayList<Stream<Entry<BigInteger, IObsData>>>(100);
        obsSeries.peek(s -> {
                // make sure list size cannot go over a threshold
                if (obsStreams.size() >= maxSelectedSeriesOnJoin)
                    throw new IllegalStateException("Too many datastreams or features of interest selected. Please refine your filter");
            })
            .forEach(series -> {
                Stream<Entry<BigInteger, IObsData>> obsStream = getObsStream(series, 
                    timeParams.resultTimeRange,
                    timeParams.phenomenonTimeRange,
                    timeParams.currentTimeOnly,
                    timeParams.latestResultOnly);
                if (obsStream != null)
                    obsStreams.add(getPostFilteredResultStream(obsStream, filter));
            });
        
        if (obsStreams.isEmpty())
            return Stream.empty();
        
        // TODO group by result time when series with different result times are selected
        
        // stream and merge obs from all selected datastreams and time periods
        MergeSortSpliterator<Entry<BigInteger, IObsData>> mergeSortIt = new MergeSortSpliterator<>(obsStreams,
                (e1, e2) -> e1.getValue().getPhenomenonTime().compareTo(e2.getValue().getPhenomenonTime()));
        
        // stream output of merge sort iterator + apply limit
        return StreamSupport.stream(mergeSortIt, false)
            .limit(filter.getLimit())
            .onClose(() -> mergeSortIt.close());
    }
    
    
    Stream<Entry<BigInteger, IObsData>> getPostFilteredResultStream(Stream<Entry<BigInteger, IObsData>> resultStream, ObsFilter filter)
    {
        if (filter.getValuePredicate() != null)
            resultStream = resultStream.filter(e -> filter.testValuePredicate(e.getValue()));
        
        return resultStream;
    }
    
    
    @Override
    public Stream<Long> selectObservedFois(ObsFilter filter)
    {
        var timeParams = new TimeParams(filter);
        
        if (filter.getDataStreamFilter() != null)
        {
            return DataStoreUtils.selectDataStreamIDs(dataStreamStore, filter.getDataStreamFilter())
                .flatMap(dsID -> {
                    return getObsSeriesByDataStream(dsID, timeParams.resultTimeRange, timeParams.latestResultOnly)
                        .filter(s -> {
                            var timeRange = getObsSeriesPhenomenonTimeRange(s.id);
                            return timeRange != null && timeRange.isConnected(timeParams.phenomenonTimeRange);
                        })
                        .map(s -> s.key.foiID)
                        .distinct();
                });
        }
        
        return IObsStore.super.selectObservedFois(filter);
    }
    

    @Override
    public Stream<DataBlock> selectResults(ObsFilter filter)
    {
        return select(filter).map(obs -> obs.getResult());
    }
        
    
    TimeExtent getDataStreamResultTimeRange(long dataStreamID)
    {
        MVTimeSeriesKey firstKey = obsSeriesMainIndex.ceilingKey(new MVTimeSeriesKey(dataStreamID, 0, Instant.MIN));
        MVTimeSeriesKey lastKey = obsSeriesMainIndex.floorKey(new MVTimeSeriesKey(dataStreamID, Long.MAX_VALUE, Instant.MAX));
        
        if (firstKey == null || lastKey == null)
            return null;
        else if (firstKey.resultTime == Instant.MIN)
            return getDataStreamPhenomenonTimeRange(dataStreamID);
        else            
            return TimeExtent.period(firstKey.resultTime, lastKey.resultTime);
    }
    
    
    TimeExtent getDataStreamPhenomenonTimeRange(long dataStreamID)
    {
        Instant[] timeRange = new Instant[] {Instant.MAX, Instant.MIN};
        getObsSeriesByDataStream(dataStreamID, H2Utils.ALL_TIMES_RANGE, false)
            .forEach(s -> {
                var seriesTimeRange = getObsSeriesPhenomenonTimeRange(s.id);
                if (seriesTimeRange == null)
                    return;
                
                if (timeRange[0].isAfter(seriesTimeRange.lowerEndpoint()))
                    timeRange[0] = seriesTimeRange.lowerEndpoint();
                if (timeRange[1].isBefore(seriesTimeRange.upperEndpoint()))
                    timeRange[1] = seriesTimeRange.upperEndpoint();
            });
        
        if (timeRange[0] == Instant.MAX || timeRange[1] == Instant.MIN)
            return null;
        else
            return TimeExtent.period(timeRange[0], timeRange[1]);
    }
    
    
    Range<Instant> getObsSeriesPhenomenonTimeRange(long seriesID)
    {
        MVTimeSeriesRecordKey firstKey = obsRecordsIndex.ceilingKey(new MVTimeSeriesRecordKey(seriesID, Instant.MIN));
        MVTimeSeriesRecordKey lastKey = obsRecordsIndex.floorKey(new MVTimeSeriesRecordKey(seriesID, Instant.MAX));
        
        if (firstKey == null || lastKey == null ||
            firstKey.seriesID != seriesID || lastKey.seriesID != seriesID)
            return null;
        else
            return Range.closed(firstKey.timeStamp, lastKey.timeStamp);
    }
    
    
    long getObsSeriesCount(long seriesID, Range<Instant> phenomenonTimeRange)
    {
        MVTimeSeriesRecordKey firstKey = obsRecordsIndex.ceilingKey(new MVTimeSeriesRecordKey(seriesID, phenomenonTimeRange.lowerEndpoint()));
        MVTimeSeriesRecordKey lastKey = obsRecordsIndex.floorKey(new MVTimeSeriesRecordKey(seriesID, phenomenonTimeRange.upperEndpoint()));
        
        if (firstKey == null || lastKey == null ||
            firstKey.seriesID != seriesID || lastKey.seriesID != seriesID)
            return 0;
        else
            return obsRecordsIndex.getKeyIndex(lastKey) - obsRecordsIndex.getKeyIndex(firstKey) + 1;
    }
    
    
    int[] getObsSeriesHistogram(long seriesID, Range<Instant> phenomenonTimeRange, Duration binSize)
    {
        long start = phenomenonTimeRange.lowerEndpoint().getEpochSecond();
        long end = phenomenonTimeRange.upperEndpoint().getEpochSecond();
        long dt = binSize.getSeconds();
        long t = start;
        int numBins = (int)Math.ceil((double)(end - start)/dt);
        int[] counts = new int[numBins];
        
        for (int i = 0; i < counts.length; i++)
        {
            var beginBin = Instant.ofEpochSecond(t);
            MVTimeSeriesRecordKey k1 = obsRecordsIndex.ceilingKey(new MVTimeSeriesRecordKey(seriesID, beginBin));
            
            t += dt;
            var endBin = Instant.ofEpochSecond(t);
            MVTimeSeriesRecordKey k2 = obsRecordsIndex.floorKey(new MVTimeSeriesRecordKey(seriesID, endBin));
            
            if (k1 != null && k2 != null && k1.seriesID == seriesID && k2.seriesID == seriesID)
            {
                long idx1 = obsRecordsIndex.getKeyIndex(k1);
                long idx2 = obsRecordsIndex.getKeyIndex(k2);
                
                // only compute count if key2 is after key1
                // otherwise it means there was no matching key inside this bin
                if (idx2 >= idx1)
                {
                    int count = (int)(idx2-idx1);
                    
                    // need to add one unless end of bin falls exactly on a key 
                    if (!endBin.equals(k2.timeStamp))
                        count++;
                    
                    counts[i] = count;
                }
            }
        }
        
        return counts;
    }


    @Override
    public Stream<ObsStats> getStatistics(ObsStatsQuery query)
    {
        var filter = query.getObsFilter();
        var timeParams = new TimeParams(filter);
        
        /*if (query.isAggregateFois())
        {
            return null;
        }
        else*/
        {
            return selectObsSeries(filter, timeParams)
                .map(series -> {
                   var dsID = series.key.dataStreamID;
                   var foiID = series.key.foiID > 0 ?
                       new FeatureId(series.key.foiID, "urn:foi:unknown") :
                       FeatureId.NULL_FEATURE;
                   
                   var seriesTimeRange = getObsSeriesPhenomenonTimeRange(series.id);
                   
                   // skip if requested phenomenon time range doesn't intersect series time range
                   var statsTimeRange = timeParams.phenomenonTimeRange;
                   if (seriesTimeRange == null || !statsTimeRange.isConnected(seriesTimeRange))
                       return null;
                   
                   statsTimeRange = seriesTimeRange.intersection(statsTimeRange);
                   
                   var resultTimeRange = series.key.resultTime != Instant.MIN ?
                       Range.singleton(series.key.resultTime) : statsTimeRange;
                   
                   var obsCount = getObsSeriesCount(series.id, statsTimeRange);
                       
                   var obsStats = new ObsStats.Builder()
                       .withDataStreamID(dsID)
                       .withFoiID(foiID)
                       .withPhenomenonTimeRange(TimeExtent.period(statsTimeRange))
                       .withResultTimeRange(TimeExtent.period(resultTimeRange))
                       .withTotalObsCount(obsCount);
                   
                   // compute histogram
                   if (query.getHistogramBinSize() != null)
                   {
                       var histogramTimeRange = timeParams.phenomenonTimeRange;
                       if (histogramTimeRange.lowerEndpoint() == Instant.MIN || histogramTimeRange.upperEndpoint() == Instant.MAX)
                           histogramTimeRange = seriesTimeRange;
                       
                       obsStats.withObsCountByTime(getObsSeriesHistogram(series.id,
                           histogramTimeRange, query.getHistogramBinSize()));
                   }
                   
                   return obsStats.build();
                })
                .filter(Objects::nonNull);
        }
    }


    @Override
    public long countMatchingEntries(ObsFilter filter)
    {
        var timeParams = new TimeParams(filter);
        
        // if no predicate or spatial query is used, we can optimize
        // by scanning only observation series
        if (filter.getValuePredicate() == null && filter.getPhenomenonLocation() == null)
        {
            // special case to count per series
            return selectObsSeries(filter, timeParams)
                .mapToLong(series -> {
                    return getObsSeriesCount(series.id, timeParams.phenomenonTimeRange);
                })
                .sum();
        }
        
        // else use full select and count items
        else
            return selectKeys(filter).limit(filter.getLimit()).count();
    }


    @Override
    public void clear()
    {
        // synchronize on MVStore to avoid autocommit in the middle of things
        synchronized (mvStore)
        {
            long currentVersion = mvStore.getCurrentVersion();
            
            try
            {
                obsRecordsIndex.clear();
                obsSeriesByFoiIndex.clear();
                obsSeriesMainIndex.clear();
            }
            catch (Exception e)
            {
                mvStore.rollbackTo(currentVersion);
                throw e;
            }
        }
    }


    @Override
    public boolean containsKey(Object key)
    {
        MVTimeSeriesRecordKey obsKey = mapToInternalKey(key);
        return obsKey == null ? false : obsRecordsIndex.containsKey(obsKey);
    }


    @Override
    public boolean containsValue(Object value)
    {
        return obsRecordsIndex.containsValue(value);
    }


    @Override
    public IObsData get(Object key)
    {
        MVTimeSeriesRecordKey obsKey = mapToInternalKey(key);
        return obsKey == null ? null : obsRecordsIndex.get(obsKey);
    }


    @Override
    public boolean isEmpty()
    {
        return obsRecordsIndex.isEmpty();
    }


    @Override
    public Set<Entry<BigInteger, IObsData>> entrySet()
    {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<BigInteger, IObsData>> iterator() {
                return getAllObsSeries(H2Utils.ALL_TIMES_RANGE)
                    .flatMap(series -> {
                        RangeCursor<MVTimeSeriesRecordKey, IObsData> cursor = getObsCursor(series.id, H2Utils.ALL_TIMES_RANGE);
                        return cursor.entryStream().map(e -> {
                            return mapToPublicEntry(e);
                        });
                    }).iterator();
            }

            @Override
            public int size() {
                return obsRecordsIndex.size();
            }

            @Override
            public boolean contains(Object o) {
                return MVObsStoreImpl.this.containsKey(o);
            }
        };
    }


    @Override
    public Set<BigInteger> keySet()
    {
        return new AbstractSet<>() {
            @Override
            public Iterator<BigInteger> iterator() {
                return getAllObsSeries(H2Utils.ALL_TIMES_RANGE)
                    .flatMap(series -> {
                        RangeCursor<MVTimeSeriesRecordKey, IObsData> cursor = getObsCursor(series.id, H2Utils.ALL_TIMES_RANGE);
                        return cursor.keyStream().map(e -> {
                            return mapToPublicKey(e);
                        });
                    }).iterator();
            }

            @Override
            public int size() {
                return obsRecordsIndex.size();
            }

            @Override
            public boolean contains(Object o) {
                return MVObsStoreImpl.this.containsKey(o);
            }
        };
    }
    
    
    @Override
    public BigInteger add(IObsData obs)
    {
        // check that datastream exists
        if (!dataStreamStore.containsKey(new DataStreamKey(obs.getDataStreamID())))
            throw new IllegalStateException("Unknown datastream: " + obs.getDataStreamID());
            
        // synchronize on MVStore to avoid autocommit in the middle of things
        synchronized (mvStore)
        {
            long currentVersion = mvStore.getCurrentVersion();
            
            try
            {
                MVTimeSeriesKey seriesKey = new MVTimeSeriesKey(
                    obs.getDataStreamID(),
                    obs.getFoiID(),
                    obs.getResultTime().equals(obs.getPhenomenonTime()) ? Instant.MIN : obs.getResultTime());
                
                MVTimeSeriesInfo series = obsSeriesMainIndex.computeIfAbsent(seriesKey, k -> {
                    // also update the FOI to series mapping if needed
                    obsSeriesByFoiIndex.putIfAbsent(seriesKey, Boolean.TRUE);
                    
                    return new MVTimeSeriesInfo(
                        obsRecordsIndex.isEmpty() ? 1 : obsRecordsIndex.lastKey().seriesID + 1);
                });
                
                // add to main obs index
                MVTimeSeriesRecordKey obsKey = new MVTimeSeriesRecordKey(series.id, obs.getPhenomenonTime());
                obsRecordsIndex.put(obsKey, obs);
                
                return mapToPublicKey(obsKey);
            }
            catch (Exception e)
            {
                mvStore.rollbackTo(currentVersion);
                throw e;
            }
        }
    }


    @Override
    public IObsData put(BigInteger key, IObsData obs)
    {
        // synchronize on MVStore to avoid autocommit in the middle of things
        synchronized (mvStore)
        {
            long currentVersion = mvStore.getCurrentVersion();
            
            try
            {
                MVTimeSeriesRecordKey obsKey = mapToInternalKey(key);
                IObsData oldObs = obsRecordsIndex.replace(obsKey, obs);
                if (oldObs == null)
                    throw new UnsupportedOperationException("put can only be used to update existing entries");
                return oldObs;
            }
            catch (Exception e)
            {
                mvStore.rollbackTo(currentVersion);
                throw e;
            }
        }
    }


    @Override
    public IObsData remove(Object keyObj)
    {
        // synchronize on MVStore to avoid autocommit in the middle of things
        synchronized (mvStore)
        {
            long currentVersion = mvStore.getCurrentVersion();
            
            try
            {
                MVTimeSeriesRecordKey key = mapToInternalKey(keyObj);
                IObsData oldObs = obsRecordsIndex.remove(key);
                
                // don't check and remove empty obs series here since in many cases they will be reused.
                // it can be done automatically during cleanup/compaction phase or with specific method.
                
                return oldObs;
            }
            catch (Exception e)
            {
                mvStore.rollbackTo(currentVersion);
                throw e;
            }
        }        
    }
    

    protected void removeAllObsAndSeries(long datastreamID)
    {
        // remove all series and obs
        MVTimeSeriesKey first = new MVTimeSeriesKey(datastreamID, 0, Instant.MIN);
        MVTimeSeriesKey last = new MVTimeSeriesKey(datastreamID, Long.MAX_VALUE, Instant.MAX);
        
        new RangeCursor<>(obsSeriesMainIndex, first, last).entryStream().forEach(entry -> {

            // remove all obs in series
            var seriesId = entry.getValue().id;
            MVTimeSeriesRecordKey k1 = new MVTimeSeriesRecordKey(seriesId, Instant.MIN);
            MVTimeSeriesRecordKey k2 = new MVTimeSeriesRecordKey(seriesId, Instant.MAX);
            new RangeCursor<>(obsRecordsIndex, k1, k2).keyStream().forEach(k -> {
                obsRecordsIndex.remove(k);
            });
            
            // remove series from index
            obsSeriesMainIndex.remove(entry.getKey());
            obsSeriesByFoiIndex.remove(entry.getKey());
        });
    }


    @Override
    public int size()
    {
        return obsRecordsIndex.size();
    }


    @Override
    public Collection<IObsData> values()
    {
        return obsRecordsIndex.values();
    }


    @Override
    public void commit()
    {
        obsRecordsIndex.getStore().commit();
        obsRecordsIndex.getStore().sync();
    }


    @Override
    public void backup(OutputStream output) throws IOException
    {
        // TODO Auto-generated method stub
    }


    @Override
    public void restore(InputStream input) throws IOException
    {
        // TODO Auto-generated method stub
    }


    @Override
    public boolean isReadOnly()
    {
        return mvStore.isReadOnly();
    }
    
    
    @Override
    public void linkTo(IFoiStore foiStore)
    {
        this.foiStore = Asserts.checkNotNull(foiStore, IFoiStore.class);
    }
}
