package org.vitrivr.cottontail.model.values

import org.vitrivr.cottontail.model.values.types.NumericValue
import org.vitrivr.cottontail.model.values.types.RealValue
import org.vitrivr.cottontail.model.values.types.Value
import java.util.*

/**
 * This is an abstraction over a [Long].
 *
 * @author Ralph Gasser
 * @version 1.3.1
 */
inline class LongValue(override val value: Long): RealValue<Long> {

    companion object {
        val ZERO = LongValue(0L)
        val ONE = LongValue(1L)

        /**
         * Generates a random [LongValue].
         *
         * @param rnd A [SplittableRandom] to generate the random numbers.
         * @return Random [LongValue]
         */
        fun random(rnd: SplittableRandom = Value.RANDOM) = LongValue(rnd.nextLong())
    }

    /**
     * Constructor for an arbitrary [Number].
     *
     * @param number The [Number] that should be converted to a [LongValue]
     */
    constructor(number: Number) : this(number.toLong())

    /**
     * Constructor for an arbitrary [NumericValue].
     *
     * @param number The [NumericValue] that should be converted to a [LongValue]
     */
    constructor(number: NumericValue<*>) : this(number.value.toLong())

    override val logicalSize: Int
        get() = -1

    override val real: RealValue<Long>
        get() = this

    override val imaginary: RealValue<Long>
        get() = ZERO

    /**
     * Compares this [LongValue] to another [Value]. Returns -1, 0 or 1 of other value is smaller,
     * equal or greater than this value. [LongValue] can only be compared to other [NumericValue]s.
     *
     * @param other Value to compare to.
     * @return -1, 0 or 1 of other value is smaller, equal or greater than this value
     */
    override fun compareTo(other: Value): Int = when (other) {
        is ByteValue -> this.value.compareTo(other.value)
        is ShortValue -> this.value.compareTo(other.value)
        is IntValue -> this.value.compareTo(other.value)
        is LongValue -> this.value.compareTo(other.value)
        is DoubleValue -> this.value.compareTo(other.value)
        is FloatValue -> this.value.compareTo(other.value)
        is Complex32Value -> this.value.compareTo(other.data[0])
        is Complex64Value -> this.value.compareTo(other.data[0])
        else -> throw IllegalArgumentException("LongValues can only be compared to other numeric values.")
    }

    /**
     * Checks for equality between this [LongValue] and the other [Value]. Equality can only be
     * established if the other [Value] is also a [LongValue] and holds the same value.
     *
     * @param other [Value] to compare to.
     * @return True if equal, false otherwise.
     */
    override fun isEqual(other: Value): Boolean = (other is LongValue) && (other.value == this.value)

    override fun asDouble(): DoubleValue = DoubleValue(this.value.toDouble())
    override fun asFloat(): FloatValue = FloatValue(this.value.toFloat())
    override fun asInt(): IntValue = IntValue(this.value.toInt())
    override fun asLong(): LongValue = this
    override fun asShort(): ShortValue = ShortValue(this.value.toShort())
    override fun asByte(): ByteValue = ByteValue(this.value.toByte())
    override fun asComplex32(): Complex32Value = Complex32Value(this.asFloat(), FloatValue(0.0f))
    override fun asComplex64(): Complex64Value = Complex64Value(this.asDouble(), DoubleValue(0.0))

    override fun unaryMinus(): LongValue = LongValue(-this.value)

    override fun plus(other: NumericValue<*>) = LongValue(this.value + other.value.toLong())
    override fun minus(other: NumericValue<*>) = LongValue(this.value - other.value.toLong())
    override fun times(other: NumericValue<*>) = LongValue(this.value * other.value.toLong())
    override fun div(other: NumericValue<*>) = LongValue(this.value / other.value.toLong())

    override fun abs() = LongValue(kotlin.math.abs(this.value))

    override fun pow(x: Int) = this.asDouble().pow(x)
    override fun pow(x: Double) = this.asDouble().pow(x)
    override fun sqrt() = this.asDouble().sqrt()
    override fun exp() = this.asDouble().exp()
    override fun ln() = this.asDouble().ln()

    override fun cos() = this.asDouble().cos()
    override fun sin() = this.asDouble().sin()
    override fun tan() = this.asDouble().tan()
    override fun atan() = this.asDouble().atan()
}