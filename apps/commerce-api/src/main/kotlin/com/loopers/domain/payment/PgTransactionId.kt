package com.loopers.domain.payment

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@JvmInline
value class PgTransactionId(val value: String)

@Converter(autoApply = true)
class PgTransactionIdConverter : AttributeConverter<PgTransactionId?, String?> {
    override fun convertToDatabaseColumn(attribute: PgTransactionId?): String? = attribute?.value
    override fun convertToEntityAttribute(dbData: String?): PgTransactionId? = dbData?.let { PgTransactionId(it) }
}
