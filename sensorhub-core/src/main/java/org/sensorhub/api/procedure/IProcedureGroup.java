/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2012-2017 Sensia Software LLC. All Rights Reserved.

******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.api.procedure;

import java.util.Map;
import org.sensorhub.api.common.ProcedureId;


/**
 * <p>
 * Interface for groups of procedures (e.g. sensor networks, sensor systems).
 * </p>
 *
 * @author Alex Robin
 * @param <T> Type of procedure composing this group
 * @since Jun 9, 2017
 */
public interface IProcedureGroup<T extends IProcedureWithState> extends IProcedureWithState
{

    /**
     * @return Map of member procedures (ID -> IProcedure object)
     */
    public Map<ProcedureId, ? extends T> getMembers();
}