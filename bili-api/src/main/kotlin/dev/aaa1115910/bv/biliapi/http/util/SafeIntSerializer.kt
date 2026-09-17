package dev.aaa1115910.biliapi.http.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 容错的 Int 反序列化器。
 *
 * B 站部分接口对同一字段在不同登录态下返回类型不一致：
 * 例如动态接口的 module_author.following，登录态可能返回数字（0/1），
 * 未登录（游客）时返回布尔 true/false。直接声明为 Int 会在游客态抛
 * JsonDecodingException（Unexpected symbol 't' in numeric literal），
 * 导致整页动态解析失败。
 *
 * 本序列化器兼容：数字、布尔（true=1/false=0）、数字字符串、null，
 * 无法解析时回退为默认值 0，保证页面不崩。
 */
object SafeIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.aaa1115910.biliapi.http.util.SafeIntSerializer", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        return when (val element = decoder.decodeJsonElement()) {
            is JsonNull -> 0
            is JsonPrimitive -> when {
                element.isString -> element.content.trim().toIntOrNull() ?: 0
                else -> element.intOrNull
                    ?: element.booleanOrNull?.let { if (it) 1 else 0 }
                    ?: 0
            }
            else -> 0
        }
    }
}
