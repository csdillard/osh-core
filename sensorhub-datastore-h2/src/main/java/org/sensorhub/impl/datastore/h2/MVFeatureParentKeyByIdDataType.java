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

import org.h2.mvstore.type.DataType;
import org.sensorhub.api.common.BigId;
import org.sensorhub.api.datastore.feature.FeatureKey;


/**
 * <p>
 * DataType implementation for MVFeatureParentKey objects.
 * </p>
 *
 * @author Alex Robin
 * @date Oct 28, 2020
 */
class MVFeatureParentKeyByIdDataType extends MVFeatureParentKeyDataType implements DataType
{
    
    MVFeatureParentKeyByIdDataType(int idScope)
    {
        super(idScope, false);
    }
    

    @Override
    public int compare(Object objA, Object objB)
    {
        long idA = (objA instanceof FeatureKey) ? 
            ((FeatureKey)objA).getInternalID().getIdAsLong() : ((BigId)objA).getIdAsLong();
            
        long idB = (objB instanceof FeatureKey) ? 
            ((FeatureKey)objB).getInternalID().getIdAsLong() : ((BigId)objB).getIdAsLong();
        
        return Long.compare(idA, idB);
    }
}
