package dev.kdrag0n.patreondl.telemetry.db

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

/**
 * JSON data type for Exposed, by qoomon @ GitHub
 * Source: https://gist.github.com/qoomon/70bbbedc134fd2a149f1f2450667dc9d
 */
class JsonbColumnType<T : Any>(
    private val stringify: (T) -> String,
    private val parse: (String) -> T
) : ColumnType() {
    override fun sqlType() = JSONB

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        super.setParameter(stmt, index, value.let {
            PGobject().apply {
                this.type = sqlType()
                this.value = value as String?
            }
        })
    }

    override fun valueFromDB(value: Any): Any {
        return if (value is PGobject) parse(value.value!!) else value
    }

    override fun valueToString(value: Any?): String = when (value) {
        is Iterable<*> -> nonNullValueToString(value)
        else -> super.valueToString(value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: Any) = stringify(value as T)

    companion object {
        const val JSONB = "JSONB"
        const val TEXT = "TEXT"
    }
}

fun <T : Any> Table.jsonb(name: String, stringify: (T) -> String, parse: (String) -> T): Column<T> =
    registerColumn(name, JsonbColumnType(stringify, parse))

/**
 * jsonb column with kotlinx.serialization as JSON serializer
 */
fun <T : Any> Table.jsonb(
    name: String,
    serializer: KSerializer<T>,
    json: Json = Json { ignoreUnknownKeys = false }
): Column<T> = jsonb(
    name = name,
    stringify = { json.encodeToString(serializer, it) },
    parse = { json.decodeFromString(serializer, it) }
)


class JsonValue<T>(
    val expr: Expression<*>,
    override val columnType: ColumnType,
    val jsonPath: List<String>
) : Function<T>(columnType) {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val castJson = columnType.sqlType() != JsonbColumnType.JSONB
        if (castJson) append("(")
        append(expr)
        append(" #>")
        if (castJson) append(">")
        append(" '{${jsonPath.joinToString { escapeFieldName(it) }}}'")
        if (castJson) append(")::${columnType.sqlType()}")
    }

    companion object {

        private fun escapeFieldName(value: String) = value.map {
            fieldNameCharactersToEscape[it] ?: it
        }.joinToString("").let { "\"$it\"" }

        private val fieldNameCharactersToEscape = mapOf(
            // '\"' to "\'\'", // no need to escape single quote as we put string in double quotes
            '\"' to "\\\"",
            '\r' to "\\r",
            '\n' to "\\n"
        )
    }
}

inline fun <reified T> Column<*>.json(vararg jsonPath: String): JsonValue<T> {
    val columnType = when (T::class) {
        Boolean::class -> BooleanColumnType()
        Int::class -> IntegerColumnType()
        Float::class -> FloatColumnType()
        String::class -> TextColumnType()
        else -> JsonbColumnType({ error("Unexpected call") }, { error("Unexpected call") })
    }
    return JsonValue(this, columnType, jsonPath.toList())
}


class JsonContainsOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "??")

/** Checks if this expression contains some [t] value. */
infix fun <T> JsonValue<Any>.contains(t: T): JsonContainsOp =
    JsonContainsOp(this, SqlExpressionBuilder.run { this@contains.wrap(t) })

/** Checks if this expression contains some [other] expression. */
infix fun <T> JsonValue<Any>.contains(other: Expression<T>): JsonContainsOp =
    JsonContainsOp(this, other)
