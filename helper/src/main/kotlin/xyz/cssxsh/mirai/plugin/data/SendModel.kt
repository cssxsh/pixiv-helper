package xyz.cssxsh.mirai.plugin.data

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable(SendModel.Companion::class)
sealed class SendModel {
    override fun toString(): String = this::class.simpleName!!

    object Normal : SendModel()
    object Flash : SendModel()
    data class Recall(val ms: Long) : SendModel()

    @Serializable
    data class Info(
        val type: String,
        val ms: Long = 60_000L
    )

    companion object : KSerializer<SendModel> {
        @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
        override val descriptor: SerialDescriptor =
            buildSerialDescriptor(SendModel::class.qualifiedName!!, StructureKind.OBJECT)

        operator fun invoke(type: String, ms: Long = 60_000L): SendModel {
            return when (type.uppercase()) {
                "NORMAL" -> Normal
                "FLASH" -> Flash
                "RECALL" -> Recall(ms)
                else -> throw IllegalArgumentException("不支持的发送类型 $type")
            }
        }

        override fun deserialize(decoder: Decoder): SendModel {
            return decoder.decodeSerializableValue(Info.serializer()).let { info -> invoke(info.type, info.ms) }
        }

        override fun serialize(encoder: Encoder, value: SendModel) {
            encoder.encodeSerializableValue(
                Info.serializer(),
                when (value) {
                    is Normal -> Info("NORMAL")
                    is Flash -> Info("FLASH")
                    is Recall -> Info("RECALL", value.ms)
                }
            )
        }
    }
}