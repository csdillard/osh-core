/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sweapi.procedure;

import java.io.IOException;
import java.util.Map;
import org.sensorhub.api.database.IProcedureObsDatabase;
import org.sensorhub.api.datastore.feature.FeatureKey;
import org.sensorhub.api.datastore.obs.IDataStreamStore;
import org.sensorhub.api.datastore.procedure.IProcedureStore;
import org.sensorhub.api.datastore.procedure.ProcedureFilter;
import org.sensorhub.api.event.IEventBus;
import org.sensorhub.api.procedure.IProcedureWithDesc;
import org.sensorhub.impl.procedure.wrapper.ProcedureUtils;
import org.sensorhub.impl.procedure.wrapper.ProcedureWrapper;
import org.sensorhub.impl.service.sweapi.IdEncoder;
import org.sensorhub.impl.service.sweapi.InvalidRequestException;
import org.sensorhub.impl.service.sweapi.feature.AbstractFeatureHandler;
import org.sensorhub.impl.service.sweapi.resource.ResourceContext;
import org.sensorhub.impl.service.sweapi.resource.ResourceFormat;
import org.sensorhub.impl.service.sweapi.resource.ResourceBinding;
import org.sensorhub.impl.service.sweapi.resource.ResourceContext.ResourceRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.util.Asserts;
import net.opengis.sensorml.v20.AbstractProcess;


public class ProcedureDetailsHandler extends AbstractFeatureHandler<IProcedureWithDesc, ProcedureFilter, ProcedureFilter.Builder, IProcedureStore>
{
    static final Logger log = LoggerFactory.getLogger(ProcedureDetailsHandler.class);
    public static final String[] NAMES = { "details", "specsheet" }; //"fullDescription"; //"specs"; //"specsheet"; //"metadata";
    
    IDataStreamStore dataStreamStore;
    
    
    public ProcedureDetailsHandler(IEventBus eventBus, IProcedureObsDatabase db)
    {
        super(db.getProcedureStore(), new IdEncoder(ProcedureHandler.EXTERNAL_ID_SEED));
        this.dataStreamStore = db.getDataStreamStore();
    }


    @Override
    protected ResourceBinding<FeatureKey, IProcedureWithDesc> getBinding(ResourceContext ctx, boolean forReading) throws IOException
    {
        var format = ctx.getFormat();
        
        if (format.isOneOf(ResourceFormat.JSON, ResourceFormat.SML_JSON))
            return new ProcedureBindingSmlJson(ctx, idEncoder, forReading);
        else
            throw new InvalidRequestException(UNSUPPORTED_FORMAT_ERROR_MSG + format);
    }
    
    
    @Override
    protected boolean isValidID(long internalID)
    {
        return dataStore.contains(internalID);
    }
    
    
    @Override
    public boolean doPost(ResourceContext ctx) throws IOException
    {
        return ctx.sendError(405, "Cannot POST here, use PUT on main resource URL");
    }
    
    
    /*@Override
    public boolean doPut(final ResourceContext ctx) throws IOException
    {
        return ctx.sendError(405, "Cannot PUT here, use PUT on main resource URL");
    }*/
    
    
    @Override
    public boolean doDelete(final ResourceContext ctx) throws IOException
    {
        return ctx.sendError(405, "Cannot DELETE here, use DELETE on main resource URL");
    }
    
    
    @Override
    public boolean doGet(ResourceContext ctx) throws IOException
    {
        try
        {
            if (ctx.isEmpty())
                return getById(ctx, "");
            else
                return ctx.sendError(404, "Invalid resource URL");
        }
        catch (InvalidRequestException e)
        {
            return ctx.sendError(400, e.getMessage());
        }
        catch (SecurityException e)
        {
            return ctx.sendError(403, ACCESS_DENIED_ERROR_MSG);
        }
    }
    
    
    @Override
    protected boolean getById(final ResourceContext ctx, final String id) throws InvalidRequestException, IOException
    {
        ResourceRef parent = ctx.getParentRef();
        Asserts.checkNotNull(parent, "parent");
        
        // internal ID & version number
        long internalID = parent.internalID;
        long version = parent.version;
        if (version < 0)
            return false;
        
        var key = getKey(internalID, version);
        AbstractProcess sml = dataStore.get(key).getFullDescription();
        if (sml == null)
            return ctx.sendError(404, String.format(NOT_FOUND_ERROR_MSG, id));
        
        // generate outputs from datastreams
        // + override ID
        var idStr = Long.toString(idEncoder.encodeID(internalID), 36);
        sml = ProcedureUtils.addOutputsFromDatastreams(internalID, sml, dataStreamStore)
            .withId(idStr);
        
        var queryParams = ctx.getRequest().getParameterMap();
        var responseFormat = parseFormat(queryParams);
        ctx.setFormatOptions(responseFormat, parseSelectArg(queryParams));
        var binding = getBinding(ctx, false);
        
        ctx.getResponse().setStatus(200);
        ctx.getResponse().setContentType(responseFormat.getMimeType());
        
        binding.serialize(key, new ProcedureWrapper(sml), true);
        
        return true;
    }


    @Override
    protected void buildFilter(final ResourceRef parent, final Map<String, String[]> queryParams, final ProcedureFilter.Builder builder) throws InvalidRequestException
    {
        super.buildFilter(parent, queryParams, builder);
        
        // TODO implement select by sections
    }


    @Override
    protected void validate(IProcedureWithDesc resource)
    {
        // TODO Auto-generated method stub
        
    }
    
    
    @Override
    public String[] getNames()
    {
        return NAMES;
    }
}