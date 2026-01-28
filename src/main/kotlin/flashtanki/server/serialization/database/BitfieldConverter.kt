package flashtanki.server.serialization.database

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import flashtanki.server.client.Bitfield
import flashtanki.server.client.IBitfield

@Converter
class BitfieldConverter<T : IBitfield> : AttributeConverter<Bitfield<T>, Long> {
  override fun convertToDatabaseColumn(value: Bitfield<T>?): Long? = value?.bitfield
  override fun convertToEntityAttribute(value: Long?): Bitfield<T>? = value?.let(::Bitfield)
}
