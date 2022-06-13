package xyz.cssxsh.mirai.pixiv.task

import com.cronutils.model.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(with = DataCron.Serializer::class)
public data class DataCron(public val delegate: Cron) : Cron by delegate {

    public companion object Serializer : KSerializer<DataCron> {

        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor(this::class.qualifiedName!!, PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: DataCron) {
            encoder.encodeString(value.asString())
        }

        override fun deserialize(decoder: Decoder): DataCron {
            return DataCron(delegate = DefaultCronParser.parse(decoder.decodeString()))
        }
    }
}