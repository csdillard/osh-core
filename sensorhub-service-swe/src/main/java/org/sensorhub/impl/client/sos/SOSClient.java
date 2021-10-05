/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.client.sos;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.sensorhub.api.common.SensorHubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.cdm.common.DataStreamParser;
import org.vast.ogc.om.IObservation;
import org.vast.ogc.om.OMUtils;
import org.vast.ows.OWSException;
import org.vast.ows.OWSExceptionReader;
import org.vast.ows.sos.GetObservationRequest;
import org.vast.ows.sos.GetResultRequest;
import org.vast.ows.sos.GetResultTemplateRequest;
import org.vast.ows.sos.GetResultTemplateResponse;
import org.vast.ows.sos.SOSUtils;
import org.vast.ows.swe.DescribeSensorRequest;
import org.vast.sensorML.SMLUtils;
import org.vast.swe.SWEHelper;
import org.vast.util.Asserts;
import org.vast.util.TimeExtent;
import org.vast.xml.DOMHelper;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


/**
 * <p>
 * Implementation of an SOS client that connects to a remote SOS to download
 * real-time observations and make them available on the local node as data
 * events.<br/>
 * </p>
 *
 * @author Alex Robin
 * @since Aug 25, 2015
 */
public class SOSClient
{
    protected static final Logger log = LoggerFactory.getLogger(SOSClient.class);

    SOSUtils sosUtils = new SOSUtils();
    GetResultRequest grRequest;
    WebSocketClient wsClient;
    DataComponent dataDescription;
    DataEncoding dataEncoding;
    boolean useWebsockets;
    volatile boolean started;
    
    
    public interface SOSRecordListener
    {
        public void newRecord(DataBlock data);
    }


    public SOSClient(GetResultRequest request, boolean useWebsockets)
    {
        this.grRequest = request;
        this.useWebsockets = useWebsockets;
    }
    
    
    public AbstractProcess getSensorDescription(String sensorUID) throws SensorHubException
    {
        return getSensorDescription(sensorUID, DescribeSensorRequest.DEFAULT_FORMAT);
    }
    
    
    public AbstractProcess getSensorDescription(String sensorUID, String format) throws SensorHubException
    {
        Asserts.checkNotNull(sensorUID, "sensorUID");
        Asserts.checkNotNull(format, "format");
        
        try
        {
            DescribeSensorRequest req = new DescribeSensorRequest();
            req.setGetServer(grRequest.getGetServer());
            req.setVersion(grRequest.getVersion());
            req.setProcedureID(sensorUID);
            req.setFormat(format);
            
            InputStream is = sosUtils.sendGetRequest(req).getInputStream();
            DOMHelper dom = new DOMHelper(new BufferedInputStream(is), false);
            OWSExceptionReader.checkException(dom, dom.getBaseElement());
            Element smlElt = dom.getElement("description/SensorDescription/data/*");
            
            SMLUtils smlUtils;
            if (format.equals(DescribeSensorRequest.FORMAT_SML_V1) ||
                format.equals(DescribeSensorRequest.FORMAT_SML_V1_01))
            {
                smlUtils = new SMLUtils(SMLUtils.V1_0);
                if ("SensorML".equals(smlElt.getLocalName()))
                    smlElt = dom.getElement(smlElt, "member/*");
            }
            else if (format.equals(DescribeSensorRequest.FORMAT_SML_V2) ||
                format.equals(DescribeSensorRequest.FORMAT_SML_V2_1))
            {
                smlUtils = new SMLUtils(SMLUtils.V2_0);
            }
            else
                throw new SensorHubException("Unsupported response format: " + format);
            
            AbstractProcess smlDesc = smlUtils.readProcess(dom, smlElt);
            log.debug("Retrieved sensor description for sensor {}", sensorUID);
            
            return smlDesc;
        }
        catch (Exception e)
        {
            throw new SensorHubException("Cannot fetch SensorML description for sensor " + sensorUID, e);
        }
    }
    
    
    public void retrieveStreamDescription() throws SensorHubException
    {
        // create output definition
        try
        {
            GetResultTemplateRequest req = new GetResultTemplateRequest();
            req.setGetServer(grRequest.getGetServer());
            req.setVersion(grRequest.getVersion());
            req.setOffering(grRequest.getOffering());
            req.getObservables().addAll(grRequest.getObservables());
            GetResultTemplateResponse resp = sosUtils.<GetResultTemplateResponse> sendRequest(req, false);
            this.dataDescription = resp.getResultStructure();
            this.dataEncoding = resp.getResultEncoding();
            log.debug("Retrieved observation result template from {}", sosUtils.buildURLQuery(req));
        }
        catch (Exception e)
        {
            throw new SensorHubException("Error while getting observation result template", e);
        }
    }
    
    
    public Collection<IObservation> getObservations(String sysUID, TimeExtent timeRange) throws SensorHubException
    {
        try
        {
            GetObservationRequest req = new GetObservationRequest();
            req.setGetServer(grRequest.getGetServer());
            req.setVersion(grRequest.getVersion());
            req.setOffering(grRequest.getOffering());
            req.getObservables().addAll(grRequest.getObservables());
            req.getProcedures().add(sysUID);
            req.setTime(timeRange);
            
            // parse observations
            InputStream is = sosUtils.sendGetRequest(req).getInputStream();
            DOMHelper dom = new DOMHelper(new BufferedInputStream(is), false);
            OWSExceptionReader.checkException(dom, dom.getBaseElement());
            NodeList obsElts = dom.getElements("observationData/*");
            
            ArrayList<IObservation> obsList = new ArrayList<>();
            OMUtils omUtils = new OMUtils(OMUtils.V2_0);
            for (int i = 0; i < obsElts.getLength(); i++)
            {
                Element obsElt = (Element)obsElts.item(i);
                IObservation obs = omUtils.readObservation(dom, obsElt);
                obsList.add(obs);
            }
            
            log.debug("Retrieved observations from {}", sosUtils.buildURLQuery(req));
            return obsList;
        }
        catch (Exception e)
        {
            throw new SensorHubException("Error fetching observations", e);
        }
    }


    public void startStream(SOSRecordListener listener) throws SensorHubException
    {
        if (started)
            return;
        
        // prepare parser
        DataStreamParser parser = SWEHelper.createDataParser(dataEncoding);
        parser.setDataComponents(dataDescription);
        parser.setRenewDataBlock(true);

        if (useWebsockets)
            connectWithWebsockets(parser, listener);
        else
            connectWithPersistentHttp(parser, listener);
    }


    protected void connectWithPersistentHttp(final DataStreamParser parser, final SOSRecordListener listener) throws SensorHubException
    {
        // connect to data stream
        try
        {
            log.debug("Connecting to {}", sosUtils.buildURLQuery(grRequest));
            grRequest.setConnectTimeOut(60000);
            HttpURLConnection conn = sosUtils.sendGetRequest(grRequest);
            InputStream is = new BufferedInputStream(conn.getInputStream());
            parser.setInput(is);
        }
        catch (Exception e)
        {
            throw new SensorHubException("Error while connecting to SOS data stream", e);
        }

        // start parsing data
        Thread parseThread = new Thread()
        {
            public void run()
            {
                started = true;
                DataBlock data;

                try
                {
                    while (started && (data = parser.parseNextBlock()) != null)
                        listener.newRecord(data);
                }
                catch (IOException e)
                {
                    if (started)
                        log.error("Error while parsing SOS data stream", e);
                }
                finally
                {
                    try
                    {
                        parser.close();
                    }
                    catch (IOException e)
                    {
                        log.trace("Cannot close SOS connection", e);
                    }
                    
                    started = false;
                }
            }
        };

        parseThread.start();
    }


    protected void connectWithWebsockets(final DataStreamParser parser, final SOSRecordListener listener) throws SensorHubException
    {
        String destUri = null;
        try
        {
            destUri = sosUtils.buildURLQuery(grRequest);
            destUri = destUri.replace("http://", "ws://")
                             .replace("https://", "wss://");
        }
        catch (OWSException e)
        {
            throw new SensorHubException("Error while generating websocket SOS request", e);
        }

        WebSocketListener socket = new WebSocketAdapter() {            
            
            @Override
            public void onWebSocketBinary(byte[] payload, int offset, int len)
            {
                try
                {
                    // skip if no payload
                    if (payload == null || payload.length == 0)
                        return;
                    
                    ByteArrayInputStream is = new ByteArrayInputStream(payload, offset, len);
                    parser.setInput(is);
                    DataBlock data = parser.parseNextBlock();
                    listener.newRecord(data);
                }
                catch (IOException e)
                {
                    log.error("Error while parsing websocket packet", e);
                }
            }

            @Override
            public void onWebSocketClose(int statusCode, String reason)
            {
                
            }

            @Override
            public void onWebSocketError(Throwable cause)
            {
                log.error("Error connecting to websocket", cause);
            }            
        };
        
        try
        {
            // init WS client with optional auth
            wsClient = new WebSocketClient();
            PasswordAuthentication auth = Authenticator.requestPasswordAuthentication(grRequest.getGetServer(), null, 0, null, null, null);
            if (auth != null) {
                wsClient.getHttpClient().getAuthenticationStore().addAuthenticationResult(
                        new BasicAuthentication.BasicResult(new URI(destUri), auth.getUserName(), new String(auth.getPassword())));
            }
            started = true;
            wsClient.start();
            URI wsUri = new URI(destUri);
            wsClient.connect(socket, wsUri);
            log.debug("Connecting to {}", destUri);
        }
        catch (Exception e)
        {
            throw new SensorHubException("Error while connecting to SOS data stream", e);
        }
    }


    public void stopStream()
    {
        started = false;
        
        if (wsClient != null)
        {
            try
            {
                wsClient.stop();
            }
            catch (Exception e)
            {
                log.trace("Cannot close websocket client", e);
            }
        }
    }


    public DataComponent getRecordDescription()
    {
        return dataDescription;
    }


    public DataEncoding getRecommendedEncoding()
    {
        return dataEncoding;
    }
}
