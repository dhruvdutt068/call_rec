package com.example.callog.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PersonWithDetails(
    @Embedded val person: PersonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "personId"
    )
    val phoneNumbers: List<PhoneNumberEntity> = emptyList(),
    @Relation(
        parentColumn = "id",
        entityColumn = "personId"
    )
    val aliases: List<ContactAliasEntity> = emptyList()
)
