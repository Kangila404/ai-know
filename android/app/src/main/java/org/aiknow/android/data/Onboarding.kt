package org.aiknow.android.data

import kotlinx.serialization.json.JsonElement

/** Complete only after notification preferences are saved; a failed save stays in onboarding. */
suspend fun completeOnboarding(api:Gateway,time:String,notifications:Boolean):JsonElement {
    require(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]:00").matches(time))
    api.request("api/v1/notification-setting","PUT",payload("settingTime" to time,"isAllowed" to notifications))
    return api.request("api/v1/users/onboarding","PATCH")
}
