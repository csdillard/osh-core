/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.
 
Copyright (C) 2019 Sensia Software LLC. All Rights Reserved.
 
******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.feature;

import org.sensorhub.api.feature.IFeatureStoreBase.FeatureField;
import org.vast.ogc.gml.IGeoFeature;


/**
 * <p>
 * Generic interface for all feature stores
 * </p>
 *
 * @author Alex Robin
 * @date Mar 19, 2018
 */
public interface IFeatureStore extends IFeatureStoreBase<IGeoFeature, FeatureField, FeatureFilter>
{
 
    @Override
    public default FeatureFilter.Builder filterBuilder()
    {
        return new FeatureFilter.Builder();
    }
}
