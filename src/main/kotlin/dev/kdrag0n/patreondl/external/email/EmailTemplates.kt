package dev.kdrag0n.patreondl.external.email

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import dev.kdrag0n.patreondl.config.Config
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

class EmailTemplates(
    config: Config,
    private val mustacheFactory: MustacheFactory,
) {
    private val templates = config.external.email.messageTemplates

    val telegramWelcome by CompileDelegate()
    val singlePurchaseSuccessful by CompileDelegate()
    val multiPurchaseSuccessful by CompileDelegate()
    val declinedReminder1 by CompileDelegate()
    val declinedReminder2 by CompileDelegate()
    val declinedReminder3 by CompileDelegate()
    val declinedReminder4 by CompileDelegate()

    private inner class CompileDelegate {
        private lateinit var property: KProperty<*>

        private val value by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            @Suppress("unchecked_cast")
            val property = templates::class.declaredMemberProperties
                .first { it.name == property.name } as KProperty1<Config.External.Email.MessageTemplates, String>
            val template = property.get(templates)

            mustacheFactory.compile(StringReader(template), property.name)
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Mustache {
            this.property = property
            return value
        }
    }

    class MustacheFactory : DefaultMustacheFactory() {
        override fun encode(value: String, writer: Writer) {
            writer.write(value)
        }
    }

    companion object {
        fun Mustache.execute(scopes: Map<String, Any?>): String {
            val writer = StringWriter()
            execute(writer, scopes)
            return writer.toString()
        }
    }
}
