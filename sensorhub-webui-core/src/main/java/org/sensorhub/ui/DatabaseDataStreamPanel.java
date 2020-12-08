/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.sensorhub.api.database.IProcedureObsDatabase;
import org.sensorhub.api.datastore.obs.ObsFilter;
import org.sensorhub.api.datastore.obs.ObsStatsQuery;
import org.sensorhub.api.obs.IDataStreamInfo;
import org.sensorhub.ui.api.UIConstants;
import org.sensorhub.ui.chartjs.Chart;
import org.sensorhub.ui.chartjs.Chart.SliderChangeListener;
import org.vast.swe.ScalarIndexer;
import org.vast.util.DateTimeFormat;
import org.vast.util.TimeExtent;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.vaadin.v7.data.Item;
import com.vaadin.v7.data.Property.ValueChangeEvent;
import com.vaadin.v7.data.Property.ValueChangeListener;
import com.vaadin.v7.data.util.converter.Converter;
import com.vaadin.server.FontAwesome;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.v7.ui.Table;
import com.vaadin.v7.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vividsolutions.jts.geom.Coordinate;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.HasUom;
import net.opengis.swe.v20.ScalarComponent;
import net.opengis.swe.v20.Time;
import net.opengis.swe.v20.Vector;


/**
 * <p>
 * This represents the panel displaying info for a single data stream
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Jun 24, 2019
 */
public class DatabaseDataStreamPanel extends VerticalLayout
{
    private static final long serialVersionUID = 6169765057074245360L;
    static final int SECONDS_PER_HOUR = 3600;
    static final int SECONDS_PER_DAY = 3600*24;
    
    Chart detailChart;
    Chart navigatorChart;
    Table table;
    
    IProcedureObsDatabase db;
    IDataStreamInfo dsInfo;
    long dataStreamID;
    TimeExtent fullTimeRange;
    TimeExtent timeRange;
    Set<String> producerIDs = null;
        
    
    public DatabaseDataStreamPanel(IProcedureObsDatabase db, IDataStreamInfo dsInfo, long dataStreamID)
    {
        this.db = db;
        this.dsInfo = dsInfo;
        this.dataStreamID = dataStreamID;
        
        setCaption(getPrettyName(dsInfo.getRecordStructure()));
        setMargin(true);
        setSpacing(true);
        
        // producer selector (if multisource storage)
        Component form = buildForm();
        addComponent(form);
        
        // grid layout for data structure and table
        GridLayout grid = new GridLayout(2, 2);
        grid.setWidth(100.0f, Unit.PERCENTAGE);
        grid.setSpacing(true);
        grid.setColumnExpandRatio(0, 0.2f);
        grid.setColumnExpandRatio(1, 0.8f);
        grid.setDefaultComponentAlignment(Alignment.TOP_LEFT);
        addComponent(grid);
        
        // data structure
        DataComponent dataStruct = dsInfo.getRecordStructure();
        Component sweForm = new SWECommonForm(dataStruct);
        grid.addComponent(sweForm);
        
        // data table
        Component tableArea = buildTable();
        grid.addComponent(tableArea);        
    }
    
    
    @SuppressWarnings("serial")
    protected Component buildTimeRangeRow()
    {
        final HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(true);
        layout.setDefaultComponentAlignment(Alignment.MIDDLE_LEFT);
        
        timeRange = dsInfo.getPhenomenonTimeRange();
        String timeRangeText = timeRange.isoStringUTC(false);
        Label timeRangeLabel = new Label(timeRangeText);
        timeRangeLabel.setContentMode(ContentMode.HTML);
        layout.addComponent(timeRangeLabel);
        layout.setCaption("Time Range");
        
        final Button btn = new Button(FontAwesome.BAR_CHART);
        btn.setDescription("Show Histogram");
        btn.addStyleName(UIConstants.STYLE_SMALL);
        btn.addStyleName(UIConstants.STYLE_QUIET);
        btn.addClickListener(new ClickListener()
        {            
            @Override
            public void buttonClick(ClickEvent event)
            {
                final VerticalLayout panelLayout = (VerticalLayout)layout.getParent().getParent();
                
                if (detailChart == null)
                {
                    // add histogram time line
                    Component timeline = buildHistogram();
                    int idx = panelLayout.getComponentIndex(layout.getParent());
                    panelLayout.addComponent(timeline, idx+1);
                    btn.setDescription("Hide Histogram");
                }
                else
                {
                    // remove histogram                    
                    panelLayout.removeComponent(detailChart.getParent());
                    btn.setDescription("Show Histogram");
                    detailChart = null;
                    navigatorChart = null;
                }
            }
        });
        layout.addComponent(btn);
        
        return layout;
    }
    
    
    @SuppressWarnings("serial")
    protected Component buildForm()
    {
        FormLayout formLayout = new FormLayout();
        formLayout.setMargin(false);
        formLayout.setSpacing(false);
        
        /*// FOI filter
        if (db.getFoiStore().)
        {
            final TextField producerFilter = new TextField("Producers IDs");
            producerFilter.addStyleName(UIConstants.STYLE_SMALL);
            producerFilter.setDescription("Comma separated list of data producer IDs to view data for");
            producerFilter.setValue("ALL");
            formLayout.addComponent(producerFilter);
            
            producerFilter.addValueChangeListener(new ValueChangeListener() {
                @Override
                public void valueChange(ValueChangeEvent event)
                {
                    String tx = producerFilter.getValue().trim();
                    String[] items = tx.replaceAll(" ", "").split(",");
                    if (items.length > 0)
                    {
                        producerIDs = Sets.newTreeSet(Arrays.asList(items));
                        producerIDs.retainAll(((IMultiSourceStorage<?>) storage).getProducerIDs());
                        if (producerIDs.isEmpty())
                            producerIDs = null;
                    }
                    else
                        producerIDs = null;
                    
                    if (producerIDs == null)
                        producerFilter.setValue("ALL");
                    
                    updateTable();
                    updateHistogram(detailChart, timeRange);
                    updateHistogram(navigatorChart, fullTimeRange);
                }                        
            });            
        }*/
        
        // time range panel
        formLayout.addComponent(buildTimeRangeRow());
        
        return formLayout;
    }
    
    
    @SuppressWarnings("serial")
    protected Component buildHistogram()
    {
        String dsName = dsInfo.getName();
                    
        VerticalLayout layout = new VerticalLayout();
        //layout.setMargin(false);
        layout.setMargin(new MarginInfo(false, true, true, false));
        layout.setSpacing(true);
        
        try
        {
            fullTimeRange = timeRange = dsInfo.getPhenomenonTimeRange();
            fullTimeRange = roundTimePeriod(fullTimeRange);
            String fullRangeData = getHistogramData(fullTimeRange);
            
            // detail chart
            detailChart = new Chart("chart-" + dsName);
            detailChart.setWidth(100, Unit.PERCENTAGE);
            detailChart.setHeight(120, Unit.PIXELS);
            String jsConfig = Resources.toString(getClass().getResource("chartjs_timeline_chart.js"), StandardCharsets.UTF_8);
            detailChart.setChartConfig(jsConfig, fullRangeData);
                        
            // rangeslider chart
            navigatorChart = new Chart("slider-" + dsInfo.getName(), true);
            navigatorChart.addStyleName("storage-navslider");
            navigatorChart.setWidth(100, Unit.PERCENTAGE);
            navigatorChart.setHeight(52, Unit.PIXELS);
            jsConfig = Resources.toString(getClass().getResource("chartjs_timeline_rangeslider.js"), StandardCharsets.UTF_8);
            navigatorChart.setChartConfig(jsConfig, fullRangeData);
            
            navigatorChart.addSliderChangeListener(new SliderChangeListener()
            {                
                @Override
                public void onSliderChange(double min, double max)
                {
                    timeRange = TimeExtent.period(
                        Instant.ofEpochSecond(Math.round(min)),
                        Instant.ofEpochSecond(Math.round(max)));
                    updateTable();
                    updateHistogram(detailChart, timeRange);
                }
            });
            
            layout.addComponent(detailChart);
            layout.addComponent(navigatorChart);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        
        return layout;
    }
    
    
    TimeExtent roundTimePeriod(TimeExtent timeRange)
    {
        var duration = timeRange.duration();
        
        if (duration.toDays() > 5)
        {
            // round time period to a full number of days
            var begin = timeRange.begin().truncatedTo(ChronoUnit.DAYS);
            var end = timeRange.end().truncatedTo(ChronoUnit.DAYS);
            if (timeRange.end().getEpochSecond() % 86400 != 0)
                end.plus(1, ChronoUnit.DAYS);
            return TimeExtent.period(begin, end);
        }
        else
        {
            // round time period to a full number of hours
            var begin = timeRange.begin().truncatedTo(ChronoUnit.HOURS);
            var end = timeRange.end().truncatedTo(ChronoUnit.HOURS);
            if (timeRange.end().getEpochSecond() % 3600 != 0)
                end = end.plus(1, ChronoUnit.HOURS);
            return TimeExtent.period(begin, end);
        }
    }
    
    
    void updateHistogram(Chart chart, TimeExtent timeRange)
    {
        if (chart != null)
        {
            String jsData = getHistogramData(timeRange);
            chart.updateChartData(0, jsData);
        }
    }
    
    
    String getHistogramData(TimeExtent timeRange)
    {
        StringBuilder jsData = new StringBuilder("[");
        Coordinate[] counts = getEstimatedCounts(timeRange);
        for (Coordinate p: counts)
        {
            jsData.append("{t:").append(p.x).append(',');
            jsData.append("y:").append(p.y).append("},");
        }
        jsData.append("]");
        return jsData.toString();
    }
    
    
    Coordinate[] getEstimatedCounts(TimeExtent timeRange)
    {
        // compute number of bins to obtain round time slots
        long duration = timeRange.duration().getSeconds();
        int binSize = getBinSize(duration);        
        var binDuration = Duration.ofSeconds(binSize);
        
        var results = db.getObservationStore().getStatistics(new ObsStatsQuery.Builder()
            .selectObservations(new ObsFilter.Builder()
                .withDataStreams(dataStreamID)
                .withPhenomenonTime().fromTimeExtent(timeRange).done()
                .build())
            .withHistogramBinSize(binDuration)
            .aggregateFois(true)
            .build());
        
        var stats = results.findFirst();
        
        if (stats.isPresent())
        {
            var counts = stats.get().getObsCountsByTime();
            
            // create series item array
            // set time coordinate as unix timestamp in millis
            Coordinate[] items = new Coordinate[counts.length];
            long time = timeRange.begin().toEpochMilli();
            for (int i = 0; i < counts.length; i++)
            {
                items[i] = new Coordinate(time, (double)counts[i]);
                time += binSize*1000;
            }
            return items;
        }
        
        return new Coordinate[0];
    }
    
    
    static int[] POSSIBLE_BIN_SIZES = new int[] {
        1, 5, 10, 20, 30, 60, 120, 300, 600, 900, 1200, 1800,
        3600, 3600*2, 3600*4, 3600*6, 3600*8, 3600*12,
        86400, 86400*2, 86400*4, 86400*7, 86400*14, 86400*30,
        86400*30*2, 86400*30*3, 86400*30*4, 86400*30*6, 86400*365
    };
    
    int getBinSize(long duration)
    {
        // we want approx 100 bins, but rounded to full days, hours or minutes
        int exactBinSize = (int)Math.round(duration / 200.);
        
        int idx = Arrays.binarySearch(POSSIBLE_BIN_SIZES, exactBinSize);
        if (idx < 0)
            idx = Math.min(-(idx+1), POSSIBLE_BIN_SIZES.length-1);
        
        return POSSIBLE_BIN_SIZES[idx];
    }
    
    
    protected void updateTable()
    {
        List<ScalarIndexer> indexers = (List<ScalarIndexer>)table.getData();
        table.removeAllItems();
        
        AtomicInteger count = new AtomicInteger();
        db.getObservationStore().select(new ObsFilter.Builder()
                .withDataStreams(dataStreamID)
                .withPhenomenonTime().fromTimeExtent(timeRange).done()
                .build())
            .forEach(obs -> {
                var dataBlk = obs.getResult();
                Item item = table.addItem(count.incrementAndGet());
                int i = 0;
                for (Object colId: table.getContainerPropertyIds())
                {
                    String value = indexers.get(i).getStringValue(dataBlk);
                    item.getItemProperty(colId).setValue(value);
                    i++;
                } 
            });
    }
    
    
    protected Component buildTable()
    {
        table = new Table();
        table.setWidth(100, Unit.PERCENTAGE);
        table.setPageLength(10);
        
        // add column names
        List<ScalarIndexer> indexers = new ArrayList<>();
        DataComponent recordDef = dsInfo.getRecordStructure();
        addColumns(recordDef, recordDef, table, indexers);
        table.setData(indexers);
        
        // populate table data
        updateTable();
        
        return table;
    }
    
    
    @SuppressWarnings("serial")
    protected void addColumns(DataComponent recordDef, DataComponent component, Table table, List<ScalarIndexer> indexers)
    {
        if (component instanceof ScalarComponent)
        {
            // add column names
            String propId = component.getName();
            String label = getPrettyName(component);
            table.addContainerProperty(propId, String.class, null, label, null, null);
            
            // correct time formatting
            String uomUri = (component instanceof HasUom) ? ((HasUom)component).getUom().getHref() : null;
            if (Time.ISO_TIME_UNIT.equals(uomUri))
            {
                table.setConverter(propId, new Converter<String, String>() {
                    DateTimeFormat dateFormat = new DateTimeFormat();
                    
                    @Override
                    public String convertToPresentation(String value, Class<? extends String> targetType, Locale locale) throws ConversionException
                    {
                        if (value == null)
                            return "";
                        return dateFormat.formatIso(Double.parseDouble(value), 0);
                    }
                    
                    @Override
                    public String convertToModel(String value, Class<? extends String> targetType, Locale locale)
                    {
                        return null;
                    }
                    
                    @Override
                    public Class<String> getModelType()
                    {
                        return String.class;
                    }
                    
                    @Override
                    public Class<String> getPresentationType()
                    {
                        return String.class;
                    }                        
                });
            }
            
            // prepare indexer for reading from datablocks
            indexers.add(new ScalarIndexer(recordDef, (ScalarComponent)component));
        }
        
        // call recursively for records
        else if (component instanceof DataRecord || component instanceof Vector)
        {
            for (int i = 0; i < component.getComponentCount(); i++)
            {
                DataComponent child = component.getComponent(i);
                addColumns(recordDef, child, table, indexers);
            }
        }
    }
    
    
    protected String getPrettyName(DataComponent dataComponent)
    {
        String label = dataComponent.getLabel();
        if (label == null)
            label = DisplayUtils.getPrettyName(dataComponent.getName());
        return label;
    }
}
