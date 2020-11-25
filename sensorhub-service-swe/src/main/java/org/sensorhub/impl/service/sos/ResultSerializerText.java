/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2020 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sos;

import java.io.IOException;
import javax.servlet.AsyncContext;
import org.sensorhub.impl.service.swe.RecordTemplate;
import org.vast.data.TextEncodingImpl;
import org.vast.ogc.OGCRegistry;
import org.vast.ows.OWSUtils;
import org.vast.ows.sos.GetResultRequest;
import org.vast.ows.sos.SOSException;
import org.vast.ows.sos.SOSUtils;
import net.opengis.swe.v20.TextEncoding;


/**
 * <p>
 * Result serializer implementation for text (DSV) format
 * </p>
 *
 * @author Alex Robin
 * @date Apr 5, 2020
 */
public class ResultSerializerText extends AbstractResultSerializerSwe
{

    @Override
    public void init(SOSServlet servlet, AsyncContext asyncCtx, GetResultRequest req, RecordTemplate resultTemplate) throws SOSException, IOException
    {
        if (!allowNonBinaryFormat(resultTemplate))
            throw new SOSException(SOSException.invalid_param_code, "responseFormat", req.getFormat(), UNSUPPORTED_FORMAT);
        
        if (!(resultTemplate.getDataEncoding() instanceof TextEncoding))
        {
            var textEncoding = new TextEncodingImpl();
            resultTemplate = new RecordTemplate(resultTemplate.getDataStructure(), textEncoding);
        }
        
        super.init(servlet, asyncCtx, req, resultTemplate);        
        
        if (request.isXmlWrapper())
            asyncCtx.getResponse().setContentType(OWSUtils.XML_MIME_TYPE);
        else
            asyncCtx.getResponse().setContentType(OWSUtils.TEXT_MIME_TYPE);
    }
    
    
    @Override
    protected void beforeRecords() throws IOException
    {
        // write small xml wrapper if requested
        if (request.isXmlWrapper())
        {
            String nsUri = OGCRegistry.getNamespaceURI(SOSUtils.SOS, request.getVersion());
            os.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes());
            os.write(("<GetResultResponse xmlns=\"" + nsUri + "\">\n<resultValues>\n").getBytes());
        }
    }
    

    @Override
    protected void afterRecords() throws IOException
    {
        // close xml wrapper if needed
        if (request.isXmlWrapper())
            os.write("\n</resultValues>\n</GetResultResponse>".getBytes());
        
        writer.endStream();
        writer.flush();
    }

}
