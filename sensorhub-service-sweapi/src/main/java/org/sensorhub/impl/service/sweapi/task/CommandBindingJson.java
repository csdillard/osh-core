/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sweapi.task;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.sensorhub.api.command.CommandData;
import org.sensorhub.api.command.ICommandData;
import org.sensorhub.api.command.ICommandStreamInfo;
import org.sensorhub.api.datastore.command.CommandStreamKey;
import org.sensorhub.api.datastore.command.ICommandStore;
import org.sensorhub.impl.service.sweapi.IdEncoder;
import org.sensorhub.impl.service.sweapi.ResourceParseException;
import org.sensorhub.impl.service.sweapi.feature.FoiHandler;
import org.sensorhub.impl.service.sweapi.resource.PropertyFilter;
import org.sensorhub.impl.service.sweapi.resource.RequestContext;
import org.sensorhub.impl.service.sweapi.resource.ResourceLink;
import org.sensorhub.impl.service.sweapi.task.CommandHandler.CommandHandlerContextData;
import org.sensorhub.impl.service.sweapi.resource.ResourceBinding;
import org.sensorhub.impl.service.sweapi.resource.ResourceBindingJson;
import org.vast.cdm.common.DataStreamWriter;
import org.vast.swe.BinaryDataWriter;
import org.vast.swe.IComponentFilter;
import org.vast.swe.SWEConstants;
import org.vast.swe.fast.JsonDataParserGson;
import org.vast.swe.fast.JsonDataWriterGson;
import org.vast.util.ReaderException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.opengis.swe.v20.BinaryBlock;
import net.opengis.swe.v20.BinaryEncoding;
import net.opengis.swe.v20.DataComponent;


public class CommandBindingJson extends ResourceBindingJson<BigInteger, ICommandData>
{
    CommandHandlerContextData contextData;
    ICommandStore cmdStore;
    JsonReader reader;
    JsonWriter writer;
    JsonDataParserGson paramsReader;
    Map<Long, DataStreamWriter> paramsWriters;
    IdEncoder dsIdEncoder = new IdEncoder(CommandStreamHandler.EXTERNAL_ID_SEED);
    IdEncoder foiIdEncoder = new IdEncoder(FoiHandler.EXTERNAL_ID_SEED);
    String userID;

    
    CommandBindingJson(RequestContext ctx, IdEncoder idEncoder, boolean forReading, ICommandStore cmdStore) throws IOException
    {
        super(ctx, idEncoder);
        this.contextData = (CommandHandlerContextData)ctx.getData();
        this.cmdStore = cmdStore;
        
        if (forReading)
        {
            InputStream is = new BufferedInputStream(ctx.getInputStream());
            this.reader = getJsonReader(is);
            this.paramsReader = getSweCommonParser(contextData.dsInfo, reader);
            
            var user = ctx.getSecurityHandler().getCurrentUser();
            this.userID = user != null ? user.getId() : "api";
        }
        else
        {
            var os = ctx.getOutputStream();
            this.writer = getJsonWriter(os, ctx.getPropertyFilter());
            this.paramsWriters = new HashMap<>();
            
            // init params writer only in case of single command stream
            // otherwise we'll do it later
            if (contextData.dsInfo != null)
            {
                var resultWriter = getSweCommonWriter(contextData.dsInfo, writer, ctx.getPropertyFilter());
                paramsWriters.put(ctx.getParentID(), resultWriter);
            }
        }
    }
    
    
    @Override
    public ICommandData deserialize() throws IOException
    {
        if (reader.peek() == JsonToken.END_DOCUMENT || !reader.hasNext())
            return null;
        
        var cmd = new CommandData.Builder()
            .withCommandStream(contextData.streamID)
            .withSender(userID);
        
        try
        {
            reader.beginObject();
            
            while (reader.hasNext())
            {
                var propName = reader.nextName();
                
                if ("issueTime".equals(propName))
                    cmd.withIssueTime(OffsetDateTime.parse(reader.nextString()).toInstant());
                //else if ("foi".equals(propName))
                //    obs.withFoi(id)
                else if ("params".equals(propName))
                {
                    var result = paramsReader.parseNextBlock();
                    cmd.withParams(result);
                }
                else
                    reader.skipValue();
            }
            
            reader.endObject();
        }
        catch (DateTimeParseException e)
        {
            throw new ResourceParseException(INVALID_JSON_ERROR_MSG + "Invalid ISO8601 date/time at " + reader.getPath());
        }
        catch (IllegalStateException | ReaderException e)
        {
            throw new ResourceParseException(INVALID_JSON_ERROR_MSG + e.getMessage());
        }
        
        if (contextData.foiId != 0)
            cmd.withFoi(contextData.foiId);
        
        // also set timestamp
        return cmd.build();
    }


    @Override
    public void serialize(BigInteger key, ICommandData cmd, boolean showLinks) throws IOException
    {
        writer.beginObject();
        
        if (key != null)
            writer.name("id").value(key.toString(ResourceBinding.ID_RADIX));
        
        var dsID = cmd.getCommandStreamID();
        var externalDsId = dsIdEncoder.encodeID(dsID);
        writer.name("commandstream").value(Long.toString(externalDsId, ResourceBinding.ID_RADIX));
        
        if (cmd.hasFoi())
        {
            var externalfoiId = foiIdEncoder.encodeID(cmd.getFoiID());
            writer.name("foi").value(Long.toString(externalfoiId, ResourceBinding.ID_RADIX));
        }
        
        writer.name("issueTime").value(cmd.getIssueTime().toString());
        writer.name("userId").value(cmd.getSenderID());
        
        // create or reuse existing params writer and write param data
        writer.name("params");
        var paramWriter = paramsWriters.computeIfAbsent(dsID,
            k -> getSweCommonWriter(k, writer, ctx.getPropertyFilter()) );
        
        // write if JSON is supported, otherwise print warning message
        if (paramWriter instanceof JsonDataWriterGson)
            paramWriter.write(cmd.getParams());
        else
            writer.value("Compressed binary result not shown in JSON");
        
        writer.endObject();
        writer.flush();
    }
    
    
    protected DataStreamWriter getSweCommonWriter(long dsID, JsonWriter writer, PropertyFilter propFilter)
    {
        var dsInfo = cmdStore.getCommandStreams().get(new CommandStreamKey(dsID));
        return getSweCommonWriter(dsInfo, writer, propFilter);
    }
    
    
    protected DataStreamWriter getSweCommonWriter(ICommandStreamInfo dsInfo, JsonWriter writer, PropertyFilter propFilter)
    {        
        if (!allowNonBinaryFormat(dsInfo))
            return new BinaryDataWriter();
        
        // create JSON SWE writer
        var sweWriter = new JsonDataWriterGson(writer);
        sweWriter.setDataComponents(dsInfo.getRecordStructure());
        
        // filter out time component since it's already included in O&M
        sweWriter.setDataComponentFilter(getTimeStampFilter());        
        return sweWriter;
    }
    
    
    protected JsonDataParserGson getSweCommonParser(ICommandStreamInfo dsInfo, JsonReader reader)
    {
        // create JSON SWE parser
        var sweParser = new JsonDataParserGson(reader);
        sweParser.setDataComponents(dsInfo.getRecordStructure());
        
        // filter out time component since it's already included in O&M
        sweParser.setDataComponentFilter(getTimeStampFilter());
        return sweParser;
    }
    
    
    protected IComponentFilter getTimeStampFilter()
    {
        return new IComponentFilter() {
            @Override
            public boolean accept(DataComponent comp)
            {
                if (comp.getParent() == null ||
                    SWEConstants.DEF_PHENOMENON_TIME.equals(comp.getDefinition()) ||
                    SWEConstants.DEF_SAMPLING_TIME.equals(comp.getDefinition()))
                    return false;
                else
                    return true;
            }            
        };
    }
    
    
    protected boolean allowNonBinaryFormat(ICommandStreamInfo dsInfo)
    {
        if (dsInfo.getRecordEncoding() instanceof BinaryEncoding)
        {
            var enc = (BinaryEncoding)dsInfo.getRecordEncoding();
            for (var member: enc.getMemberList())
            {
                if (member instanceof BinaryBlock)
                    return false;
            }
        }
        
        return true;
    }


    @Override
    public void startCollection() throws IOException
    {
        startJsonCollection(writer);
    }


    @Override
    public void endCollection(Collection<ResourceLink> links) throws IOException
    {
        endJsonCollection(writer, links);
    }
}