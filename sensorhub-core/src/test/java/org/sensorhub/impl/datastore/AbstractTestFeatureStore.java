/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.datastore;

import static org.junit.Assert.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;
import net.opengis.gml.v32.AbstractFeature;
import net.opengis.gml.v32.Envelope;
import net.opengis.gml.v32.LinearRing;
import net.opengis.gml.v32.Point;
import net.opengis.gml.v32.Polygon;
import net.opengis.gml.v32.impl.GMLFactory;
import org.junit.Before;
import org.junit.Test;
import org.sensorhub.api.datastore.FeatureFilter;
import org.sensorhub.api.datastore.FeatureId;
import org.sensorhub.api.datastore.FeatureKey;
import org.sensorhub.api.datastore.IFeatureStore;
import org.vast.ogc.gml.GMLUtils;
import org.vast.ogc.gml.GenericFeatureImpl;
import org.vast.ogc.gml.GenericTemporalFeatureImpl;
import org.vast.ogc.gml.ITemporalFeature;
import org.vast.ogc.om.SamplingPoint;
import org.vast.util.Bbox;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;


/**
 * <p>
 * Abstract base for testing implementations of IFeatureStore.
 * </p>
 *
 * @author Alex Robin
 * @param <StoreType> type of datastore under test
 * @since Apr 14, 2018
 */
public abstract class AbstractTestFeatureStore<StoreType extends IFeatureStore<FeatureKey, AbstractFeature>>
{
    protected static String DATASTORE_NAME = "test-features";
    static String UID_PREFIX = "urn:domain:features:";
    static int NUM_TIME_ENTRIES_PER_FEATURE = 5;
        
    protected StoreType featureStore;
    GMLFactory gmlFac = new GMLFactory(true);
    Map<FeatureKey, AbstractFeature> allFeatures = new LinkedHashMap<>();
    boolean useAdd, useAddVersion;
    
    
    protected abstract StoreType initStore(ZoneOffset timeZone) throws Exception;
    protected abstract void forceReadBackFromStorage() throws Exception;
    
    
    @Before
    public void init() throws Exception
    {
        this.featureStore = initStore(ZoneOffset.UTC);
    }
    
    
    protected void setCommonFeatureProperties(AbstractFeature f, int num)
    {
        String idPrefix = "F";
        if (f instanceof ITemporalFeature)
            idPrefix += "T";        
        if (f.isSetGeometry())
            idPrefix += "G";
        
        f.setId(idPrefix + num);
        f.setUniqueIdentifier(UID_PREFIX + idPrefix + num);
        f.setName("Feature " + num);
        f.setDescription("This is feature #" + num);
    }
    
    
    protected FeatureKey getKey(AbstractFeature f)
    {
        Instant validStartTime = (f instanceof ITemporalFeature) ? 
                ((ITemporalFeature)f).getValidTime().lowerEndpoint() :
                Instant.MIN;
        
        return new FeatureKey(
            Long.parseLong(f.getId().replaceAll("(F|G|T)*", ""))+1,
            f.getUniqueIdentifier(),
            validStartTime);
    }
    
    
    protected void addOrPutFeature(AbstractFeature f)
    {
        //System.out.println("Adding " + f.getId());        
        FeatureKey key;
        
        if (useAdd) {
            key = featureStore.add(f);
        }
        else if (useAddVersion) {
            key = featureStore.addVersion(f);
        }
        else {
            key = getKey(f);
            featureStore.put(key, f);
        }            
        
        //System.out.println(key);
        allFeatures.put(key, f);        
    }
    
    
    protected void addNonGeoFeatures(int startIndex, int numFeatures) throws Exception
    {
        QName fType = new QName("http://mydomain/features", "MyFeature");
        
        for (int i = startIndex; i < startIndex+numFeatures; i++)
        {
            AbstractFeature f = new GenericFeatureImpl(fType);
            setCommonFeatureProperties(f, i);
            addOrPutFeature(f);
        }
    }
    
    
    protected void addGeoFeaturesPoint2D(int startIndex, int numFeatures) throws Exception
    {
        QName fType = new QName("http://mydomain/features", "MyPointFeature");
        
        for (int i = startIndex; i < startIndex+numFeatures; i++)
        {
            AbstractFeature f = new GenericFeatureImpl(fType);
            Point p = gmlFac.newPoint();
            p.setPos(new double[] {i, i, 0.0});
            f.setGeometry(p);
            setCommonFeatureProperties(f, i);
            addOrPutFeature(f);
        }
    }
    
    
    protected void addSamplingPoints2D(int startIndex, int numFeatures) throws Exception
    {
        for (int i = startIndex; i < startIndex+numFeatures; i++)
        {
            SamplingPoint sp = new SamplingPoint();
            Point p = gmlFac.newPoint();
            p.setPos(new double[] {0.5*i, 1.5*i, 0.0});
            sp.setShape(p);
            setCommonFeatureProperties(sp, i);
            addOrPutFeature(sp);
        }
    }
    
    
    protected void addTemporalFeatures(int startIndex, int numFeatures) throws Exception
    {
        QName fType = new QName("http://mydomain/features", "MyTimeFeature");
        
        for (int i = startIndex; i < startIndex+numFeatures; i++)
        {
            // add feature with 5 different time periods
            for (int j = 0; j < NUM_TIME_ENTRIES_PER_FEATURE; j++)
            {
                GenericTemporalFeatureImpl f = new GenericTemporalFeatureImpl(fType);
                OffsetDateTime beginTime = OffsetDateTime.parse("2000-01-01T00:00:00Z").plus(j*30, ChronoUnit.DAYS).plus(i, ChronoUnit.HOURS);
                OffsetDateTime endTime = beginTime.plus(30, ChronoUnit.DAYS);
                f.setValidTimePeriod(beginTime, endTime);
                setCommonFeatureProperties(f, i);
                addOrPutFeature(f);
            }
        }
    }
    
    
    protected void addTemporalGeoFeatures(int startIndex, int numFeatures) throws Exception
    {
        QName fType = new QName("http://mydomain/features", "MyGeoTimeFeature");
        
        for (int i = startIndex; i < startIndex+numFeatures; i++)
        {
            // add feature with 5 different time periods
            for (int j = 0; j < NUM_TIME_ENTRIES_PER_FEATURE; j++)
            {
                GenericTemporalFeatureImpl f = new GenericTemporalFeatureImpl(fType);
                OffsetDateTime beginTime = OffsetDateTime.parse("2000-01-01T00:00:00Z").plus(j*30, ChronoUnit.DAYS).plus(i, ChronoUnit.HOURS);
                OffsetDateTime endTime = beginTime.plus(30, ChronoUnit.DAYS);
                f.setValidTimePeriod(beginTime, endTime);
                LinearRing ring = gmlFac.newLinearRing();
                double minx = i*10;
                double miny = j*10;
                ring.setPosList(new double[] {minx, miny, minx+2, miny, minx+2, miny+2, minx, miny+2});
                Polygon shape = gmlFac.newPolygon();
                shape.setExterior(ring);
                f.setGeometry(shape);
                setCommonFeatureProperties(f, i);
                addOrPutFeature(f);
            }
        }
    }
    
    
    @Test
    public void testGetDatastoreName() throws Exception
    {
        assertEquals(DATASTORE_NAME, featureStore.getDatastoreName());
        
        forceReadBackFromStorage();
        assertEquals(DATASTORE_NAME, featureStore.getDatastoreName());
    }
    
    
    @Test
    public void testGetTimeZone() throws Exception
    {
        assertEquals(ZoneOffset.UTC, featureStore.getTimeZone());
        
        ZoneOffset timeZone = ZoneOffset.ofHours(-7);
        this.featureStore = initStore(timeZone);
        assertEquals(timeZone, featureStore.getTimeZone());
        
        forceReadBackFromStorage();
        assertEquals(timeZone, featureStore.getTimeZone());
    }
    
    
    @Test
    public void testPutAndGetNumRecords() throws Exception
    {
        int totalFeatures = 0;
        
        int numFeatures = 100;
        addNonGeoFeatures(1, numFeatures);
        assertEquals(totalFeatures += numFeatures, featureStore.getNumRecords());
        
        numFeatures = 120;
        addGeoFeaturesPoint2D(1000, numFeatures);
        forceReadBackFromStorage();
        assertEquals(totalFeatures += numFeatures, featureStore.getNumRecords());
        
        numFeatures = 40;
        addTemporalFeatures(2000, numFeatures);
        assertEquals(totalFeatures += numFeatures*NUM_TIME_ENTRIES_PER_FEATURE, featureStore.getNumRecords());
    }
    
    
    @Test
    public void testPutAndGetNumFeatures() throws Exception
    {
        int totalFeatures = 0;
        
        int numFeatures = 100;
        addNonGeoFeatures(1, numFeatures);
        assertEquals(totalFeatures += numFeatures, featureStore.getNumFeatures());
        
        numFeatures = 120;
        addGeoFeaturesPoint2D(1000, numFeatures);
        forceReadBackFromStorage();
        assertEquals(totalFeatures += numFeatures, featureStore.getNumFeatures());
        
        numFeatures = 40;
        addTemporalFeatures(2000, numFeatures);
        assertEquals(totalFeatures += numFeatures, featureStore.getNumFeatures());
    }
    
    
    private void checkFeatureIDs(Stream<FeatureId> featureIds)
    {
        Set<FeatureId> datastoreIds = featureIds.collect(Collectors.toSet());
        Set<FeatureId> expectedIds = allFeatures.keySet().stream()
            .map(k -> new FeatureId(k.getInternalID(), k.getUniqueID()))
            .collect(Collectors.toSet());
        assertEquals(expectedIds.size(), datastoreIds.size());
        expectedIds.forEach(uid -> assertTrue(datastoreIds.contains(uid)));
    }
    
    
    @Test
    public void testPutAndGetFeatureIDs() throws Exception
    {
        addNonGeoFeatures(1, 40);
        checkFeatureIDs(featureStore.getAllFeatureIDs());
        forceReadBackFromStorage();
        
        addGeoFeaturesPoint2D(100, 25);
        checkFeatureIDs(featureStore.getAllFeatureIDs());
        
        addTemporalFeatures(2000, 100);
        forceReadBackFromStorage();
        checkFeatureIDs(featureStore.getAllFeatureIDs());
    }
    
    
    private void checkFeaturesBbox(Bbox bbox)
    {
        Bbox expectedBbox = new Bbox();
        for (AbstractFeature f: allFeatures.values())
        {
            if (f.isSetGeometry())
            {
                Envelope env = f.getGeometry().getGeomEnvelope();
                expectedBbox.add(GMLUtils.envelopeToBbox(env));
            }
        }
        
        assertTrue(bbox.contains(expectedBbox));
        assertEquals(expectedBbox.getMinX(), bbox.getMinX(), 1e-4);
        assertEquals(expectedBbox.getMinY(), bbox.getMinY(), 1e-4);
        assertEquals(expectedBbox.getMaxX(), bbox.getMaxX(), 1e-4);
        assertEquals(expectedBbox.getMaxY(), bbox.getMaxY(), 1e-4);
    }
    
    
    @Test
    public void testGetFeaturesBbox() throws Exception
    {
        //addNonGeoFeatures(1, 40);
        //checkFeaturesBbox(featureStore.getFeaturesBbox());
        
        addGeoFeaturesPoint2D(100, 25);
        checkFeaturesBbox(featureStore.getFeaturesBbox());
        forceReadBackFromStorage();
        
        addSamplingPoints2D(200, 50);
        checkFeaturesBbox(featureStore.getFeaturesBbox());
        forceReadBackFromStorage();
        checkFeaturesBbox(featureStore.getFeaturesBbox());
    }
    
    
    private void checkMapKeySet(Set<FeatureKey> keySet)
    {
        keySet.forEach(k -> {
            if (!allFeatures.containsKey(k))
                fail("No matching key in reference list: " + k);
        });
        
        allFeatures.keySet().forEach(k -> {
            if (!keySet.contains(k))
                fail("No matching key in datastore: " + k);
        });
    }
    
    
    @Test
    public void testMapKeySet() throws Exception
    {
        addGeoFeaturesPoint2D(100, 25);
        checkMapKeySet(featureStore.keySet());
        
        addTemporalFeatures(200, 30);
        checkMapKeySet(featureStore.keySet());
    }
    
    
    private void checkMapValues(Collection<AbstractFeature> mapValues)
    {
        mapValues.forEach(f1 -> {
            AbstractFeature f2 = allFeatures.get(getKey(f1));
            if (f2 == null || !f2.getUniqueIdentifier().equals(f1.getUniqueIdentifier()))
                fail("No matching feature in reference list: " + f1);
        });
    }
    
    
    @Test
    public void testMapValues() throws Exception
    {
        addGeoFeaturesPoint2D(100, 25);
        checkMapValues(featureStore.values());
    }
    
    
    private void checkFeaturesEqual(AbstractFeature f1, AbstractFeature f2)
    {
        assertEquals(f1.getClass(), f2.getClass());
        assertEquals(f1.getUniqueIdentifier(), f2.getUniqueIdentifier());
        
        if (f1 instanceof ITemporalFeature)
            assertEquals(((ITemporalFeature)f1).getValidTime(), ((ITemporalFeature)f2).getValidTime());
        
        if (f1.isSetGeometry())
            assertEquals(f1.getGeometry().getClass(), f2.getGeometry().getClass());
    }
    
    
    private void getAndCheckFeatures() throws Exception
    {
        long t0 = System.currentTimeMillis();
        allFeatures.forEach((k, f1) -> {
            AbstractFeature f2 = featureStore.get(k);
            assertTrue("Feature " + k + " not found in datastore", f2 != null);
            checkFeaturesEqual(f1, f2);
        });
        System.out.println(String.format("%d features fetched in %d ms", allFeatures.size(), System.currentTimeMillis()-t0));
    }
    
    
    @Test
    public void testGetByKey() throws Exception
    {
        addTemporalFeatures(50, 63);
        addGeoFeaturesPoint2D(700, 33);
        forceReadBackFromStorage();
        getAndCheckFeatures();
        
        addTemporalFeatures(50, 22);
        getAndCheckFeatures();
        addNonGeoFeatures(1, 40);
        getAndCheckFeatures();
        
        forceReadBackFromStorage();
        getAndCheckFeatures();
    }
    
    
    @Test
    public void testAddAndGet() throws Exception
    {
        useAdd = true;
        
        addGeoFeaturesPoint2D(10, 33);
        assertEquals(allFeatures.size(), featureStore.size());
        getAndCheckFeatures();
        
        forceReadBackFromStorage();
        addNonGeoFeatures(50, 22);
        forceReadBackFromStorage();
        getAndCheckFeatures();
    }
    
    
    @Test
    public void testAddVersionAndGet() throws Exception
    {
        useAddVersion = true;
        
        addGeoFeaturesPoint2D(10, 33);
        assertEquals(allFeatures.size(), featureStore.size());
        getAndCheckFeatures();
        
        forceReadBackFromStorage();
        addTemporalFeatures(50, 22);
        forceReadBackFromStorage();
        getAndCheckFeatures();
    }
    
    
    private void testRemoveAllKeys()
    {
        long t0 = System.currentTimeMillis();
        allFeatures.forEach((k, f) -> {
            featureStore.remove(k);
            assertFalse(featureStore.containsKey(k));
            assertTrue(featureStore.get(k) == null);
        });
        System.out.println(String.format("%d features removed in %d ms", allFeatures.size(), System.currentTimeMillis()-t0));
        
        assertTrue(featureStore.isEmpty());
        assertTrue(featureStore.getNumRecords() == 0);
        assertTrue(featureStore.getNumFeatures() == 0);
        allFeatures.clear();
    }
    
    
    @Test
    public void testRemoveByKey() throws Exception
    {
        addGeoFeaturesPoint2D(100, 25);
        testRemoveAllKeys();
        
        addTemporalFeatures(200, 30);
        forceReadBackFromStorage();
        testRemoveAllKeys();
        
        forceReadBackFromStorage();
        addNonGeoFeatures(1, 40);
        testRemoveAllKeys();
        
        forceReadBackFromStorage();
        testRemoveAllKeys();
    }
    
    
    private void checkSelectedEntries(Stream<Entry<FeatureKey, AbstractFeature>> resultStream, Set<String> expectedIds, Range<Instant> timeRange)
    {
        boolean lastVersion = timeRange.lowerEndpoint() == Instant.MAX && timeRange.upperEndpoint() == Instant.MAX;
        System.out.println("\nSelect " + expectedIds + " within " +  (lastVersion ? "LATEST" : timeRange));
        
        Map<FeatureKey, AbstractFeature> resultMap = resultStream
                .peek(e -> System.out.println(e.getKey()))
                .collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
        
        resultMap.forEach((k, v) -> {
            if (!expectedIds.contains(k.getUniqueID()) ||
                (v instanceof ITemporalFeature) && !lastVersion && !timeRange.isConnected(((ITemporalFeature)v).getValidTime()))
                fail("Result contains unexpected feature: " + k);
        });
        
        if (!lastVersion)
        {
            allFeatures.entrySet().stream()
                .filter(e -> expectedIds.contains(e.getKey().getUniqueID()))
                .filter(e -> !(e.getValue() instanceof ITemporalFeature) ||
                             timeRange.isConnected(((ITemporalFeature)e.getValue()).getValidTime()))
                .forEach(e -> {
                    if (!resultMap.containsKey(e.getKey()))
                        fail("Result is missing feature: " + e.getKey());
                });
        }
    }
    
    
    @Test
    public void testSelectFeaturesByInternalID() throws Exception
    {
        Stream<Entry<FeatureKey, AbstractFeature>> resultStream;
        Range<Instant> timeRange;
        Long[] ids;
        
        addNonGeoFeatures(0, 50);
        
        // correct IDs and all times
        ids = new Long[] {3L, 24L};
        Set<String> uids = Arrays.stream(ids).map(i -> UID_PREFIX+"F"+(i-1)).collect(Collectors.toSet());
        timeRange = Range.closed(Instant.MIN, Instant.MAX);
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
            .withInternalIDs(ids)
            .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
            .build());
        checkSelectedEntries(resultStream, uids, timeRange);
    }
    
    
    @Test
    public void testSelectFeaturesByUIDAndTime() throws Exception
    {
        Stream<Entry<FeatureKey, AbstractFeature>> resultStream;
        Set<String> uids;
        Range<Instant> timeRange;
        
        addNonGeoFeatures(0, 50);
        
        // correct UIDs and all times
        uids = Sets.newHashSet(UID_PREFIX+"F10", UID_PREFIX+"F31");
        timeRange = Range.closed(Instant.MIN, Instant.MAX);
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(uids.toArray(new String[0]))
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, uids, timeRange);
        
        // correct UIDs and time range
        uids = Sets.newHashSet(UID_PREFIX+"F25", UID_PREFIX+"F49");
        timeRange = Range.closed(Instant.parse("2000-04-08T08:59:59Z"), Instant.parse("2000-06-08T07:59:59Z"));
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(uids.toArray(new String[0]))
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, uids, timeRange);
    }
    
    
    @Test
    public void testSelectTemporalFeaturesByUIDAndTime() throws Exception
    {
        Stream<Entry<FeatureKey, AbstractFeature>> resultStream;
        Set<String> ids;
        Range<Instant> timeRange;
        
        addTemporalFeatures(200, 30);
        
        // correct IDs and all times
        ids = Sets.newHashSet(UID_PREFIX+"FT200", UID_PREFIX+"FT201");
        timeRange = Range.closed(Instant.MIN, Instant.MAX);
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(ids.toArray(new String[0]))
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, ids, timeRange);
        
        // correct IDs and time range
        ids = Sets.newHashSet(UID_PREFIX+"FT200", UID_PREFIX+"FT201");
        timeRange = Range.closed(Instant.parse("2000-04-08T08:59:59Z"), Instant.parse("2000-06-08T07:59:59Z"));
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(ids.toArray(new String[0]))
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, ids, timeRange);
        
        // correct IDs and future time
        ids = Sets.newHashSet(UID_PREFIX+"FT200", UID_PREFIX+"FT201");
        timeRange = Range.singleton(Instant.parse("2001-04-08T07:59:59Z"));
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(ids.toArray(new String[0]))
                .validAtTime(timeRange.lowerEndpoint())
                .build());
        checkSelectedEntries(resultStream, ids, timeRange);
        
        // mix of correct and wrong IDs
        ids = Sets.newHashSet(UID_PREFIX+"FT300", UID_PREFIX+"FT201");
        timeRange = Range.singleton(Instant.MAX);
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(ids.toArray(new String[0]))
                .withLatestVersion()
                .build());
        checkSelectedEntries(resultStream, ids, timeRange);
        
        // only wrong IDs
        ids = Sets.newHashSet(UID_PREFIX+"FT300", UID_PREFIX+"FT301");
        timeRange = Range.singleton(Instant.MAX);
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withUniqueIDs(ids.toArray(new String[0]))
                .withLatestVersion()
                .build());
        checkSelectedEntries(resultStream, ids, timeRange);
    }
    
    
    private void checkSelectedEntries(Stream<Entry<FeatureKey, AbstractFeature>> resultStream, Geometry roi, Range<Instant> timeRange)
    {
        System.out.println("\nSelect " + roi + " within " + timeRange);
        
        Map<FeatureKey, AbstractFeature> resultMap = resultStream
                .peek(e -> System.out.println(e.getKey()))
                .collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
        
        resultMap.forEach((k, v) -> {
            if (!v.isSetGeometry() || !((Geometry)v.getGeometry()).intersects(roi) ||
                (v instanceof ITemporalFeature) && !timeRange.isConnected(((ITemporalFeature)v).getValidTime()))
                fail("Result contains unexpected feature: " + k + ", geom=" + v.getGeometry());
        });
        
        allFeatures.entrySet().stream()
            .filter(e -> e.getValue().isSetGeometry() && ((Geometry)e.getValue().getGeometry()).intersects(roi))
            .filter(e -> !(e.getValue() instanceof ITemporalFeature) ||
                         timeRange.isConnected(((ITemporalFeature)e.getValue()).getValidTime()))
            .forEach(e -> {
                if (!resultMap.containsKey(e.getKey()))
                    fail("Result is missing feature: " + e.getKey());
            });
    }
    
    
    @Test
    public void testSelectEntriesByRoi() throws Exception
    {
        Stream<Entry<FeatureKey, AbstractFeature>> resultStream;
        Geometry roi;
        Range<Instant> timeRange;
        
        addTemporalGeoFeatures(0, 30);
        addSamplingPoints2D(0, 30);
        
        // containing polygon and all times
        roi = new GeometryFactory().createPolygon(new Coordinate[] {
            new Coordinate(0, 0),
            new Coordinate(500, 0),
            new Coordinate(500, 100),
            new Coordinate(0, 100),
            new Coordinate(0, 0)
        });
        timeRange = Range.closed(Instant.MIN, Instant.MAX);        
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withLocationIntersecting((com.vividsolutions.jts.geom.Polygon)roi)
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, roi, timeRange);
        
        // containing polygon and time range
        timeRange = Range.closed(Instant.parse("2000-02-28T09:59:59Z"), Instant.parse("2000-04-08T10:00:00Z"));
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withLocationIntersecting((com.vividsolutions.jts.geom.Polygon)roi)
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, roi, timeRange);
        
        // smaller polygon and all times
        roi = new GeometryFactory().createPolygon(new Coordinate[] {
                new Coordinate(0, 0),
                new Coordinate(200, 0),
                new Coordinate(200, 29.9),
                new Coordinate(0, 29.9),
                new Coordinate(0, 0)
            });
            timeRange = Range.closed(Instant.MIN, Instant.MAX);        
            resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                    .withLocationIntersecting((com.vividsolutions.jts.geom.Polygon)roi)
                    .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                    .build());
            checkSelectedEntries(resultStream, roi, timeRange);
        
        // smaller polygon and time range
        timeRange = Range.closed(Instant.parse("2000-02-28T09:59:59Z"), Instant.parse("2000-04-08T10:00:00Z"));
        resultStream = featureStore.selectEntries(new FeatureFilter.Builder()
                .withLocationIntersecting((com.vividsolutions.jts.geom.Polygon)roi)
                .withValidTimeDuring(timeRange.lowerEndpoint(), timeRange.upperEndpoint())
                .build());
        checkSelectedEntries(resultStream, roi, timeRange);
    }
    
    
    ///////////////////////
    // Performance Tests //
    ///////////////////////
    
    @Test
    public void testPutThroughput() throws Exception
    {
        System.out.println("Write Throughput (put operations)");
        
        int numFeatures = 100000;
        long t0 = System.currentTimeMillis();
        //addSamplingPoints2D(0, numFeatures);
        addNonGeoFeatures(0, numFeatures);
        //addTemporalGeoFeatures(0, numFeatures);
        featureStore.commit();
        double dt = System.currentTimeMillis() - t0;
        int throughPut = (int)(numFeatures/dt*1000);
        System.out.println(String.format("Simple Features: %d writes/s", throughPut));
        assertTrue(throughPut > 50000);        
        
        numFeatures = 10000;
        t0 = System.currentTimeMillis();
        addSamplingPoints2D(0, numFeatures);
        featureStore.commit();
        dt = System.currentTimeMillis() - t0;
        throughPut = (int)(numFeatures/dt*1000);
        System.out.println(String.format("Geo Features: %d writes/s", throughPut));
        assertTrue(throughPut > 10000);
        
        numFeatures = 10000;
        t0 = System.currentTimeMillis();
        addTemporalGeoFeatures(0, numFeatures);
        featureStore.commit();
        dt = System.currentTimeMillis() - t0;
        throughPut = (int)(numFeatures/dt*1000*NUM_TIME_ENTRIES_PER_FEATURE);
        System.out.println(String.format("Spatio-temporal features: %d writes/s", throughPut));
        assertTrue(throughPut > 10000);
    }
    
    
    @Test
    public void testGetThroughput() throws Exception
    {
        System.out.println("Read Throughput (get operations)");
        
        int numFeatures = 100000;
        addNonGeoFeatures(0, numFeatures);
        
        // sequential reads
        int numReads = numFeatures;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < numReads; i++)
        {
            String uid = UID_PREFIX + "F" + i;
            var key = new FeatureKey(uid);            
            var f = featureStore.get(key);
            assertEquals(uid, f.getUniqueIdentifier());
        }
        double dt = System.currentTimeMillis() - t0;
        int throughPut = (int)(numReads/dt*1000);
        System.out.println(String.format("Sequential Reads: %d reads/s", throughPut));
        assertTrue(throughPut > 100000);
        
        // random reads
        numReads = 10000;
        t0 = System.currentTimeMillis();
        for (int i = 0; i < numReads; i++)
        {
            String uid = UID_PREFIX + "F" + (int)(Math.random()*(numFeatures-1));
            var key = new FeatureKey(uid);            
            var f = featureStore.get(key);
            assertEquals(uid, f.getUniqueIdentifier());
        }
        dt = System.currentTimeMillis() - t0;
        throughPut = (int)(numReads/dt*1000);
        System.out.println(String.format("Random Reads: %d reads/s", throughPut));
        assertTrue(throughPut > 10000);
    }
    
    
    @Test
    public void testScanThroughput() throws Exception
    {
        System.out.println("Scan Throughput (cursor iteration)");
        
        int numFeatures = 100000;
        addNonGeoFeatures(0, numFeatures);
        
        // warm up
        long count = featureStore.keySet().stream().count();
        
        // key scan
        long t0 = System.currentTimeMillis();
        count = featureStore.keySet().stream().count();
        double dt = System.currentTimeMillis() - t0;
        int throughPut = (int)(count/dt*1000);
        System.out.println(String.format("Key Scan: %d reads/s", throughPut));
        assertTrue(throughPut > 200000);
        
        // entry scan
        t0 = System.currentTimeMillis();
        count = featureStore.entrySet().stream().count();
        dt = System.currentTimeMillis() - t0;
        throughPut = (int)(count/dt*1000);
        System.out.println(String.format("Entry Scan: %d reads/s", throughPut));
        assertTrue(throughPut > 200000);
    }
    
    
    @Test
    public void testTemporalFilterThroughput() throws Exception
    {
        System.out.println("Temporal Query Throughput (select operation w/ temporal filter)");
        
        int numFeatures = 20000;
        addTemporalFeatures(0, numFeatures);
                
        // spatial filter with all features
        Instant date0 = featureStore.keySet().iterator().next().getValidStartTime();
        FeatureFilter filter = new FeatureFilter.Builder()
                .withValidTimeDuring(date0, date0.plus(numFeatures+NUM_TIME_ENTRIES_PER_FEATURE*30*24, ChronoUnit.HOURS))
                .build();
        
        long t0 = System.currentTimeMillis();
        long count = featureStore.selectEntries(filter).count();//.forEach(entry -> count.incrementAndGet());        
        double dt = System.currentTimeMillis() - t0;
        int throughPut = (int)(count/dt*1000);
        System.out.println(String.format("Entry Stream: %d reads/s", throughPut));
        assertTrue(throughPut > 50000);
        assertEquals(numFeatures*NUM_TIME_ENTRIES_PER_FEATURE, count);
    }
        
    
    @Test
    public void testSpatialFilterThroughput() throws Exception
    {
        System.out.println("Geo Query Throughput (select operation w/ spatial filter)");
        
        int numFeatures = 100000;
        addSamplingPoints2D(0, numFeatures);
        
        // spatial filter with all features
        FeatureFilter filter = new FeatureFilter.Builder()
                .withLocationWithin(featureStore.getFeaturesBbox())
                .build();
        
        long t0 = System.currentTimeMillis();
        long count = featureStore.selectEntries(filter).count();
        double dt = System.currentTimeMillis() - t0;
        int throughPut = (int)(count/dt*1000);
        System.out.println(String.format("Entry Stream: %d reads/s", throughPut));
        assertTrue(throughPut > 50000);
        assertEquals(numFeatures, count);
        
        // with geo temporal features
        int numFeatures2 = 20000;
        addTemporalGeoFeatures(1000, numFeatures2);
          
        // spatial filter with all features
        filter = new FeatureFilter.Builder()
                .withValidTimeDuring(Instant.MIN, Instant.MAX)
                .withLocationWithin(featureStore.getFeaturesBbox())
                .build();
        
        t0 = System.currentTimeMillis();
        count = featureStore.selectEntries(filter).count();
        dt = System.currentTimeMillis() - t0;
        throughPut = (int)(count/dt*1000);
        System.out.println(String.format("Entry Stream: %d reads/s", throughPut));
        assertTrue(throughPut > 50000);
        assertEquals(numFeatures+numFeatures2*NUM_TIME_ENTRIES_PER_FEATURE, count);
        
        // many requests with small random bbox
        Bbox bboxAll = featureStore.getFeaturesBbox();
        Bbox selectBbox = new Bbox();
        int numRequests = 1000;
        t0 = System.currentTimeMillis();
        for (int i = 0; i < numRequests; i++)
        {        
            selectBbox.setMinX(bboxAll.getMinX() + bboxAll.getSizeX()*Math.random());
            selectBbox.setMaxX(selectBbox.getMinX() + 1);
            selectBbox.setMinY(bboxAll.getMinY() + bboxAll.getSizeY()*Math.random());
            selectBbox.setMaxY(selectBbox.getMinY() + 1);
            filter = new FeatureFilter.Builder()
                    .withLocationWithin(selectBbox)
                    .build();
            featureStore.selectEntries(filter).count();
        }
                
        dt = System.currentTimeMillis() - t0;
        throughPut = (int)(numRequests/dt*1000);
        System.out.println(String.format("Random Reads: %d reads/s", throughPut));
        //assertTrue(throughPut > 50000);        
    }
    
    
    
    ///////////////////////
    // Concurrency Tests //
    ///////////////////////
    
    /*long refTime;
    int numWrittenMetadataObj;
    int numWrittenRecords;
    volatile int numWriteThreadsRunning;
    
    protected void startWriteRecordsThreads(final ExecutorService exec, 
                                            final int numWriteThreads,
                                            final DataComponent recordDef,
                                            final double timeStep,
                                            final int testDurationMs,
                                            final Collection<Throwable> errors)
    {
        numWriteThreadsRunning = numWriteThreads;
                
        for (int i=0; i<numWriteThreads; i++)
        {
            final int count = i;
            exec.submit(new Runnable() {
                @Override
                public void run()
                {
                    long startTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("Begin Write Records Thread %d @ %dms\n", Thread.currentThread().getId(), startTimeOffset);
                    
                    try
                    {
                        List<DataBlock> dataList = writeRecords(recordDef, count*10000., timeStep, Integer.MAX_VALUE, testDurationMs);
                        synchronized(AbstractTestFeatureStore.this) {
                            numWrittenRecords += dataList.size();
                        }
                    }
                    catch (Throwable e)
                    {
                        errors.add(e);
                        //exec.shutdownNow();
                    }
                    
                    synchronized(AbstractTestFeatureStore.this) {
                        numWriteThreadsRunning--;
                    }
                    
                    long stopTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("End Write Records Thread %d @ %dms\n", Thread.currentThread().getId(), stopTimeOffset);
                }
            });
        }
    }
    
    
    protected void startReadRecordsThreads(final ExecutorService exec, 
                                           final int numReadThreads,
                                           final DataComponent recordDef,
                                           final double timeStep,
                                           final Collection<Throwable> errors)
    {
        for (int i=0; i<numReadThreads; i++)
        {
            exec.submit(new Runnable() {
                @Override
                public void run()
                {
                    long tid = Thread.currentThread().getId();
                    long startTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("Begin Read Records Thread %d @ %dms\n", tid, startTimeOffset);
                    int readCount = 0;
                    
                    try
                    {
                        while (numWriteThreadsRunning > 0 && !Thread.interrupted())
                        {
                            //System.out.println(numWriteThreadsRunning);
                            double[] timeRange = storage.getRecordsTimeRange(recordDef.getName());
                            if (Double.isNaN(timeRange[0]))
                                continue;
                            //double[] timeRange = new double[] {0.0, 110000.0};
                            
                            //System.out.format("Read Thread %d, Loop %d\n", Thread.currentThread().getId(), j+1);
                            final double begin = timeRange[0] + Math.random() * (timeRange[1] - timeRange[0]);
                            final double end = begin + Math.max(timeStep*100., Math.random() * (timeRange[1] - begin));
                            
                            // prepare filter
                            IDataFilter filter = new DataFilter(recordDef.getName()) {
                                @Override
                                public double[] getTimeStampRange() { return new double[] {begin, end}; }
                            };
                        
                            // retrieve records
                            Iterator<? extends IDataRecord> it = storage.getRecordIterator(filter);                            
                            
                            // check records time stamps and order
                            //System.out.format("Read Thread %d, [%f-%f]\n", Thread.currentThread().getId(), begin, end);
                            double lastTimeStamp = Double.NEGATIVE_INFINITY;
                            while (it.hasNext())
                            {
                                IDataRecord rec = it.next();
                                double timeStamp = rec.getKey().timeStamp;
                                
                                //System.out.format("Read Thread %d, %f\n", Thread.currentThread().getId(), timeStamp);
                                assertTrue(tid + ": Time steps are not increasing: " + timeStamp + "<" + lastTimeStamp , timeStamp > lastTimeStamp);
                                assertTrue(tid + ": Time stamp lower than begin: " + timeStamp + "<" + begin , timeStamp >= begin);
                                assertTrue(tid + ": Time stamp higher than end: " + timeStamp + ">" + end, timeStamp <= end);
                                lastTimeStamp = timeStamp;
                                readCount++;
                            }
                            
                            Thread.sleep(1);
                        }
                    }
                    catch (Throwable e)
                    {
                        errors.add(e);
                        //exec.shutdownNow();
                    }
                    
                    long stopTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("End Read Records Thread %d @%dms - %d read ops\n", Thread.currentThread().getId(), stopTimeOffset, readCount);
                }
            });
        }
    }
    
    
    protected void startWriteMetadataThreads(final ExecutorService exec, 
                                             final int numWriteThreads,
                                             final Collection<Throwable> errors)
    {
        for (int i=0; i<numWriteThreads; i++)
        {
            final int startCount = i*1000000;
            exec.submit(new Runnable() {
                @Override
                public void run()
                {
                    long startTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("Begin Write Desc Thread %d @%dms\n", Thread.currentThread().getId(), startTimeOffset);
                    
                    try
                    {
                        int count = startCount;
                        while (numWriteThreadsRunning > 0 && !Thread.interrupted())
                        {
                            // create description
                            //SWEHelper helper = new SWEHelper();
                            SMLFactory smlFac = new SMLFactory();
                            GMLFactory gmlFac = new GMLFactory();
                            
                            PhysicalSystem system = new PhysicalSystemImpl();
                            system.setUniqueIdentifier("TEST" + count++);
                            system.setName("blablabla");
                            system.setDescription("this is the description of my sensor that can be pretty long");
                            
                            IdentifierList identifierList = smlFac.newIdentifierList();
                            system.addIdentification(identifierList);
                            
                            Term term;            
                            term = smlFac.newTerm();
                            term.setDefinition(SWEHelper.getPropertyUri("Manufacturer"));
                            term.setLabel("Manufacturer Name");
                            term.setValue("My manufacturer");
                            identifierList.addIdentifier2(term);
                            
                            term = smlFac.newTerm();
                            term.setDefinition(SWEHelper.getPropertyUri("ModelNumber"));
                            term.setLabel("Model Number");
                            term.setValue("SENSOR_2365");
                            identifierList.addIdentifier2(term);
                            
                            term = smlFac.newTerm();
                            term.setDefinition(SWEHelper.getPropertyUri("SerialNumber"));
                            term.setLabel("Serial Number");
                            term.setValue("FZEFZE154618989");
                            identifierList.addIdentifier2(term);
                            
                            // generate unique time stamp
                            TimePosition timePos = gmlFac.newTimePosition(startCount + System.currentTimeMillis()/1000.);
                            TimeInstant validTime = gmlFac.newTimeInstant(timePos);
                            system.addValidTimeAsTimeInstant(validTime);
                            
                            // add to storage
                            storage.storeDataSourceDescription(system);
                            //storage.commit();
                            
                            synchronized(AbstractTestFeatureStore.this) {
                                numWrittenMetadataObj++;
                            }
                            
                            Thread.sleep(5);
                        }
                    }
                    catch (Throwable e)
                    {
                        errors.add(e);
                        //exec.shutdownNow();
                    }
                    
                    long stopTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("End Write Desc Thread %d @%dms\n", Thread.currentThread().getId(), stopTimeOffset);
                }
            });
        }
    }
    
    
    protected void startReadMetadataThreads(final ExecutorService exec, 
            final int numReadThreads,
            final Collection<Throwable> errors)
    {
        for (int i = 0; i < numReadThreads; i++)
        {
            exec.submit(new Runnable()
            {
                @Override
                public void run()
                {
                    long tid = Thread.currentThread().getId();
                    long startTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("Begin Read Desc Thread %d @ %dms\n", tid, startTimeOffset);
                    int readCount = 0;

                    try
                    {
                        while (numWriteThreadsRunning > 0 && !Thread.interrupted())
                        {
                            AbstractProcess p = storage.getLatestDataSourceDescription();
                            if (p != null)
                                assertTrue("Missing valid time", p.getValidTimeList().size() > 0);
                            readCount++;
                            Thread.sleep(1);
                        }
                    }
                    catch (Throwable e)
                    {
                        errors.add(e);
                        //exec.shutdownNow();
                    }

                    long stopTimeOffset = System.currentTimeMillis() - refTime;
                    System.out.format("End Read Desc Thread %d @%dms - %d read ops\n", Thread.currentThread().getId(), stopTimeOffset, readCount);
                }
            });
        }
    }   

    
    protected void checkForAsyncErrors(Collection<Throwable> errors) throws Throwable
    {
        // report errors
        System.out.println(errors.size() + " error(s)");
        for (Throwable e: errors)
            e.printStackTrace();
        if (!errors.isEmpty())
            throw errors.iterator().next();
    }
    
    
    protected void checkRecordsInStorage(final DataComponent recordDef) throws Throwable
    {
        System.out.println(numWrittenRecords + " records written");
        
        // check number of records        
        int recordCount = storage.getNumRecords(recordDef.getName());
        assertEquals("Wrong number of records in storage", numWrittenRecords, recordCount);
        
        // check number of records returned by iterator
        recordCount = 0;
        Iterator<?> it = storage.getRecordIterator(new DataFilter(recordDef.getName()));
        while (it.hasNext())
        {
            it.next();
            recordCount++;
        }
        assertEquals("Wrong number of records returned by iterator", numWrittenRecords, recordCount);
    }
    
    
    protected void checkMetadataInStorage() throws Throwable
    {
        System.out.println(numWrittenMetadataObj + " metadata objects written");
        
        int descCount = 0;
        List<AbstractProcess> descList = storage.getDataSourceDescriptionHistory(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        for (AbstractProcess desc: descList)
        {
            assertTrue(desc instanceof PhysicalSystem);
            assertEquals("blablabla", desc.getName());
            assertTrue(desc.getUniqueIdentifier().startsWith("TEST"));
            descCount++;
        }        
        assertEquals("Wrong number of metadata objects in storage", numWrittenMetadataObj, descCount);
        
        AbstractProcess desc = storage.getLatestDataSourceDescription();
        assertTrue(desc instanceof PhysicalSystem);
    }
    
    
    @Test
    public void testConcurrentWriteRecords() throws Throwable
    {
        final DataComponent recordDef = createDs2();
        ExecutorService exec = Executors.newCachedThreadPool();
        final Collection<Throwable> errors = Collections.synchronizedCollection(new ArrayList<Throwable>());
        
        int numWriteThreads = 10;
        int testDurationMs = 2000;
        refTime = System.currentTimeMillis();
        
        startWriteRecordsThreads(exec, numWriteThreads, recordDef, 0.1, testDurationMs, errors);
        
        exec.shutdown();
        exec.awaitTermination(testDurationMs*2, TimeUnit.MILLISECONDS);
        
        forceReadBackFromStorage();
        checkRecordsInStorage(recordDef);
        checkForAsyncErrors(errors);
    }
    
    
    @Test
    public void testConcurrentWriteMetadata() throws Throwable
    {
        ExecutorService exec = Executors.newCachedThreadPool();
        final Collection<Throwable> errors = Collections.synchronizedCollection(new ArrayList<Throwable>());
        
        int numWriteThreads = 10;
        int testDurationMs = 2000;
        refTime = System.currentTimeMillis();
        
        numWriteThreadsRunning = 1;
        startWriteMetadataThreads(exec, numWriteThreads, errors);
      
        Thread.sleep(testDurationMs);
        numWriteThreadsRunning = 0;
        
        exec.shutdown();
        exec.awaitTermination(testDurationMs*2, TimeUnit.MILLISECONDS);
        
        forceReadBackFromStorage();
        checkMetadataInStorage();
        checkForAsyncErrors(errors);
    }
    
    
    @Test
    public void testConcurrentWriteThenReadRecords() throws Throwable
    {
        DataComponent recordDef = createDs2();
        ExecutorService exec = Executors.newCachedThreadPool();
        Collection<Throwable> errors = Collections.synchronizedCollection(new ArrayList<Throwable>());        
        
        int numWriteThreads = 10;
        int numReadThreads = 10;
        int testDurationMs = 1000;
        double timeStep = 0.1;
        refTime = System.currentTimeMillis();
        
        startWriteRecordsThreads(exec, numWriteThreads, recordDef, timeStep, testDurationMs, errors);
        
        exec.shutdown();
        exec.awaitTermination(testDurationMs*2, TimeUnit.MILLISECONDS);
        exec = Executors.newCachedThreadPool();
        numWriteThreadsRunning = 1;
        
        checkForAsyncErrors(errors);
        forceReadBackFromStorage();
        
        errors.clear();
        startReadRecordsThreads(exec, numReadThreads, recordDef, timeStep, errors);
        
        Thread.sleep(testDurationMs);
        numWriteThreadsRunning = 0; // manually stop reading after sleep period
        exec.shutdown();
        exec.awaitTermination(1000, TimeUnit.MILLISECONDS);
        
        checkForAsyncErrors(errors);
        checkRecordsInStorage(recordDef);        
    }
    
    
    @Test
    public void testConcurrentReadWriteRecords() throws Throwable
    {
        final DataComponent recordDef = createDs2();
        final ExecutorService exec = Executors.newCachedThreadPool();
        final Collection<Throwable> errors = Collections.synchronizedCollection(new ArrayList<Throwable>());        
        
        int numWriteThreads = 10;
        int numReadThreads = 10;
        int testDurationMs = 2000;
        double timeStep = 0.1;
        refTime = System.currentTimeMillis();
        
        startWriteRecordsThreads(exec, numWriteThreads, recordDef, timeStep, testDurationMs, errors);       
        startReadRecordsThreads(exec, numReadThreads, recordDef, timeStep, errors);
        
        exec.shutdown();
        exec.awaitTermination(testDurationMs*200, TimeUnit.MILLISECONDS);
        
        forceReadBackFromStorage();
        checkForAsyncErrors(errors);
        checkRecordsInStorage(recordDef);        
    }
    
    
    @Test
    public void testConcurrentReadWriteMetadataAndRecords() throws Throwable
    {
        final DataComponent recordDef = createDs2();
        ExecutorService exec = Executors.newCachedThreadPool();
        final Collection<Throwable> errors = Collections.synchronizedCollection(new ArrayList<Throwable>());        
        
        int numWriteThreads = 10;
        int numReadThreads = 10;
        int testDurationMs = 3000;
        double timeStep = 0.1;
        refTime = System.currentTimeMillis();
        
        startWriteRecordsThreads(exec, numWriteThreads, recordDef, timeStep, testDurationMs, errors);
        startWriteMetadataThreads(exec, numWriteThreads, errors); 
        startReadRecordsThreads(exec, numReadThreads, recordDef, timeStep, errors);
        startReadMetadataThreads(exec, numReadThreads, errors);
        
        exec.shutdown();
        exec.awaitTermination(testDurationMs*2, TimeUnit.MILLISECONDS);

        forceReadBackFromStorage();
        checkForAsyncErrors(errors);
        checkRecordsInStorage(recordDef);
        checkMetadataInStorage();
    }*/
}