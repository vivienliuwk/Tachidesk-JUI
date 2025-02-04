/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ca.gosyer.jui.domain.source.model.sourcefilters

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CheckBox")
data class CheckBoxFilter(
    override val filter: CheckBoxProps
) : SourceFilter() {
    @Serializable
    data class CheckBoxProps(
        override val name: String,
        override val state: Boolean
    ) : Props<Boolean>
}
