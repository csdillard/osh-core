/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License Version
 1.1 (the "License"); you may not use this file except in compliance with
 the License. You may obtain a copy of the License at
 http://www.mozilla.org/MPL/MPL-1.1.html
 
 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.
 
 The Initial Developer of the Original Code is SENSIA SOFTWARE LLC.
 Portions created by the Initial Developer are Copyright (C) 2012
 the Initial Developer. All Rights Reserved.

 Please Contact Alexandre Robin for more
 information.
 
 Contributor(s): 
    Alexandre Robin
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.service.sos;

import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import java.io.IOException;
import org.sensorhub.impl.service.swe.RecordTemplate;
import org.vast.ogc.om.IObservation;
import org.vast.ows.OWSException;


/**
 * <p>
 * Interface to be implemented for consuming data received by the SOS-T interface.
 * One data consumer is mapped for each SOS offering and receives data when 
 * transactional operations are used (InsertSensor, UpdateSensor, DeleteSensor,
 * InsertObservation, InsertResultTemplate, InsertResult).
 * </p>
 *
 * @author Alex Robin
 * @date March 1, 2014
 * */
public interface ISOSDataConsumer
{     
    
    /**
     * Requests consumer to update sensor description
     * @param newSensorDescription
     * @throws IOException
     * @throws OWSException 
     */
    public void updateSensor(AbstractProcess newSensorDescription) throws IOException, OWSException;
    
    
    /**
     * Requests consumer to prepare for receiving new result records with given
     * data structure and encoding
     * @param component
     * @param encoding
     * @param obsTemplate 
     * @return new template ID
     * @throws IOException
     * @throws OWSException 
     */
    public String newResultTemplate(DataComponent component, DataEncoding encoding, IObservation obsTemplate) throws IOException, OWSException;
    
    
    /**
     * Requests consumer to process a list of new result records
     * @param templateID 
     * @param datablocks 
     * @throws IOException 
     * @throws OWSException 
     */
    public void newResultRecord(String templateID, DataBlock... datablocks) throws IOException, OWSException;
    
    
    /**
     * Requests consumer to process a list of new observations
     * @param observations 
     * @throws IOException
     * @throws OWSException 
     */
    public void newObservation(IObservation... observations) throws IOException, OWSException;
    
    
    /**
     * Retrieve previously registered template information
     * @param templateID
     * @return stream description (structure +encoding)
     * @throws IOException 
     * @throws OWSException 
     */
    public RecordTemplate getTemplate(String templateID) throws IOException, OWSException;
    
    
    /**
     * Called when the consumer is removed
     */    
    public void cleanup();
}
