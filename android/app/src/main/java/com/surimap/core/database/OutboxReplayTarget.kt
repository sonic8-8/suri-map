package com.surimap.core.database

import androidx.room.ColumnInfo

data class OutboxReplayTarget(
    @ColumnInfo(name = "incident_id")
    val incidentId: String,
    @ColumnInfo(name = "police_phone_id")
    val policePhoneId: String
)
