package org.aiknow.android.notifications

import org.junit.Assert.*
import org.junit.Test

class PushDestinationTest {
    @Test fun adminWinsWhenBothIdsArePresent() {
        assertEquals(PushDestination.AdminAlert(16),PushDestination.parse("16","23"))
    }
    @Test fun invalidAdminPayloadDoesNotOpenCardInstead() {
        for(value in listOf("", "0", "-1", "abc", "99999999999999999999999")) assertNull(PushDestination.parse(value,"23"))
    }
    @Test fun onlyPositiveCardIdsNavigate() {
        assertEquals(PushDestination.Card(23),PushDestination.parse(null,"23"))
        for(value in listOf(null,"0","-1","abc")) assertNull(PushDestination.parse(null,value))
    }
}
