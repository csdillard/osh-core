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

import org.sensorhub.utils.ObjectUtils;

/**
 * <p>
 * Information about an observation series.<br/>
 * We also store procedure UID and FOI UID so we can reconstruct observation
 * keys when scanning observations.
 * </p>
 *
 * @author Alex Robin
 * @date Sep 12, 2019
 */
class MVObsSeriesInfo
{
    transient MVObsSeriesKey key;
    long id = 0;
        
    
    MVObsSeriesInfo(long id)
    {
        this.id = id;
    }


    @Override
    public String toString()
    {
        return ObjectUtils.toString(this, true);
    }
}
