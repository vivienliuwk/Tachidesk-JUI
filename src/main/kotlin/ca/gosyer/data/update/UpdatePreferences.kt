/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package ca.gosyer.data.update

import ca.gosyer.common.prefs.Preference
import ca.gosyer.common.prefs.PreferenceStore

class UpdatePreferences(private val preferenceStore: PreferenceStore) {
    fun enabled(): Preference<Boolean> {
        return preferenceStore.getBoolean("enabled", true)
    }
}
