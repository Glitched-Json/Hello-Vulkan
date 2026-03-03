package engine;

import org.joml.*;

import java.lang.Math;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class Vector {
    private final int size;
    private final Number[] values;
    private int type = 4;

    public Vector(Vector2d vector) { this(vector.x, vector.y); }
    public Vector(Vector2f vector) { this(vector.x, vector.y); }
    public Vector(Vector2i vector) { this(vector.x, vector.y); }
    public Vector(Vector3d vector) { this(vector.x, vector.y, vector.z); }
    public Vector(Vector3f vector) { this(vector.x, vector.y, vector.z); }
    public Vector(Vector3i vector) { this(vector.x, vector.y, vector.z); }
    public Vector(Vector4d vector) { this(vector.x, vector.y, vector.z, vector.w); }
    public Vector(Vector4f vector) { this(vector.x, vector.y, vector.z, vector.w); }
    public Vector(Vector4i vector) { this(vector.x, vector.y, vector.z, vector.w); }
    public Vector(Vector vector) { this(vector.values); this.type = vector.type; }
    public Vector(Number... values) {
        this.values = Arrays.copyOf(values, size = values.length);
    }

    public static Vector ofSize(int size) {
        return new Vector(size);
    }
    private Vector(int size) {
        this.values = new Number[this.size = size];
    }

    public Vector setAll(byte[] values) {return setAll(IntStream.range(0, values.length).mapToObj(i -> values[i]).toArray(Byte[]::new));}
    public Vector setAll(short[] values) {return setAll(IntStream.range(0, values.length).mapToObj(i -> values[i]).toArray(Short[]::new));}
    public Vector setAll(int[] values) {return setAll(Arrays.stream(values).boxed().toArray(Integer[]::new));}
    public Vector setAll(long[] values) {return setAll(Arrays.stream(values).boxed().toArray(Long[]::new));}
    public Vector setAll(double[] values) {return setAll(Arrays.stream(values).boxed().toArray(Double[]::new));}
    public Vector setAll(float[] values) {return setAll(IntStream.range(0, values.length).mapToDouble(i -> values[i]).boxed().toArray(Double[]::new));}
    public Vector setAll(Number[] values) {
        IntStream.range(0, size).forEach(i -> {
            if (i < values.length) this.values[i] = values[i];
            else this.values[i] = 0;
        });
        return this;
    }

    public double toRadians() { Vector v = new Vector(this).normalize(); return Math.atan2(v.yd(), v.xd()); }
    public double toDegrees() { return Math.toDegrees(toRadians()); }

    private Vector addScalar(Number value) { for (int i=0; i<values.length; i++) values[i] = values[i].doubleValue() + value.doubleValue(); return this; }
    private Vector subScalar(Number value) { for (int i=0; i<values.length; i++) values[i] = values[i].doubleValue() - value.doubleValue(); return this; }
    private Vector mulScalar(Number value) { for (int i=0; i<values.length; i++) values[i] = values[i].doubleValue() * value.doubleValue(); return this; }
    private Vector divScalar(Number value) { for (int i=0; i<values.length; i++) values[i] = values[i].doubleValue() / value.doubleValue(); return this; }

    public Vector add(Number... values) { if (values.length == 1) addScalar(values[0]); else if (values.length > 1) add(new Vector(values)); return this; }
    public Vector sub(Number... values) { if (values.length == 1) subScalar(values[0]); else if (values.length > 1) sub(new Vector(values)); return this; }
    public Vector mul(Number... values) { if (values.length == 1) mulScalar(values[0]); else if (values.length > 1) mul(new Vector(values)); return this; }
    public Vector div(Number... values) { if (values.length == 1) divScalar(values[0]); else if (values.length > 1) div(new Vector(values)); return this; }

    public Vector invert(Number... mask) {
        boolean allMode = mask.length == 0;
        int elements = allMode ? values.length : Math.min(mask.length, values.length);
        for (int i=0; i<elements; i++) values[i] = allMode || (int) Logic.clamp(mask[i], 0, 1) == 1 ?
                -values[i].doubleValue() : values[i].doubleValue();
        return this;
    }

    public Vector flip(Number... mask) {
        return invert(mask).add(mask);
    }

    public Vector add(Vector v) { for (int i=0; i<size; i++) values[i] = values[i].doubleValue() + v.getDouble(i); return this; }
    public Vector sub(Vector v) { for (int i=0; i<size; i++) values[i] = values[i].doubleValue() - v.getDouble(i); return this; }
    public Vector mul(Vector v) { for (int i=0; i<size; i++) values[i] = values[i].doubleValue() * v.getDouble(i); return this; }
    public Vector div(Vector v) { for (int i=0; i<size; i++) try {values[i] = values[i].doubleValue() / v.getDouble(i);} catch (ArithmeticException ignored) {} return this; }

    public Vector normalize(Number length) { return normalize().mul(length); }
    public Vector normalize() { double l = length(); if (l != 0) for (int i=0; i<size; i++) values[i] = values[i].doubleValue() / l; return this; }

    public Vector swap(Number indexA, Number indexB) { double t = getDouble(indexA.intValue()); set(indexA.intValue(), getDouble(indexB.intValue())); set(indexB.intValue(), t); return this; }

    public Vector2d toVector2d() { return new Vector2d(Arrays.copyOf(toDoubleArray(), 2)); }
    public Vector3d toVector3d() { return new Vector3d(Arrays.copyOf(toDoubleArray(), 3)); }
    public Vector4d toVector4d() { return new Vector4d(Arrays.copyOf(toDoubleArray(), 4)); }
    public Vector2f toVector2f() { return new Vector2f( Arrays.copyOf(toFloatArray(), 2)); }
    public Vector3f toVector3f() { return new Vector3f( Arrays.copyOf(toFloatArray(), 3)); }
    public Vector4f toVector4f() { return new Vector4f( Arrays.copyOf(toFloatArray(), 4)); }
    public Vector2i toVector2i() { return new Vector2i(   Arrays.copyOf(toIntArray(), 2)); }
    public Vector3i toVector3i() { return new Vector3i(   Arrays.copyOf(toIntArray(), 3)); }
    public Vector4i toVector4i() { return new Vector4i(   Arrays.copyOf(toIntArray(), 4)); }

    public static Vector concatenate(Vector... vectors) { return concatenate(Arrays.asList(vectors)); }
    public static Vector concatenate(List<Vector> vectors) {
        int size = vectors.stream().mapToInt(Vector::size).sum();
        Vector result = ofSize(size);
        int offset = 0;

        for (Vector v: vectors) {
            for (int i=0; i<v.size; i++)
                result.set(offset+i, v.get(i));
            offset += v.size;
        }
        return result;
    }

    public static Vector fromRadians(Number radians) { return new Vector(Math.cos(radians.doubleValue()), Math.sin(radians.doubleValue()), 0); }
    public static Vector fromDegrees(Number degrees) { return fromRadians(Math.toRadians(degrees.doubleValue())); }

    /**
     * @param type The primitive type to become default.
     *             <p>0 - Byte</p>
     *             <p>1 - Short</p>
     *             <p>2 - Int</p>
     *             <p>3 - Long</p>
     *             <p>4 - Float</p>
     *             <p>5 - Double</p>
     * @return This Vector instance.
     */
    public Vector setType(int type) {
        this.type = type;
        return this;
    }

    /** @noinspection UnusedReturnValue*/
    public Vector set(int index, Number value) {
        try { values[index] = value; }
        catch (IndexOutOfBoundsException ignored) {}
        return this;
    }

    public Vector set(Vector vector) {
        System.arraycopy(vector.values, 0, values, 0, Math.min(size, vector.size));
        return this;
    }

    // --- Getter Methods ----------------------------------------------------------------------------------------------

    public byte xb() { return getByte(0); }
    public byte yb() { return getByte(1); }
    public byte zb() { return getByte(2); }
    public byte wb() { return getByte(3); }

    public short xs() { return getShort(0); }
    public short ys() { return getShort(1); }
    public short zs() { return getShort(2); }
    public short ws() { return getShort(3); }

    public int xi() { return getInt(0); }
    public int yi() { return getInt(1); }
    public int zi() { return getInt(2); }
    public int wi() { return getInt(3); }

    public long xl() { return getLong(0); }
    public long yl() { return getLong(1); }
    public long zl() { return getLong(2); }
    public long wl() { return getLong(3); }

    public float xf() { return getFloat(0); }
    public float yf() { return getFloat(1); }
    public float zf() { return getFloat(2); }
    public float wf() { return getFloat(3); }

    public double xd() { return getDouble(0); }
    public double yd() { return getDouble(1); }
    public double zd() { return getDouble(2); }
    public double wd() { return getDouble(3); }

    public double length() { return Math.sqrt(Arrays.stream(values).mapToDouble(n -> n.doubleValue() * n.doubleValue()).sum()); }

    public byte   dotByte  (Vector vector) { return (byte)  IntStream.range(0, Math.max(size, vector.size)).map(i -> (byte) (getByte(i) * vector.getByte(i)))     .reduce(0, (a, b) -> (byte) (a + b)); }
    public short  dotShort (Vector vector) { return (short) IntStream.range(0, Math.max(size, vector.size)).map(i -> (short) (getShort(i) * vector.getShort(i)))  .reduce(0, (a, b) -> (short) (a + b)); }
    public int    dotInt   (Vector vector) { return         IntStream.range(0, Math.max(size, vector.size)).map(i -> (getInt(i) * vector.getInt(i)))              .sum(); }
    public long   dotLong  (Vector vector) { return         IntStream.range(0, Math.max(size, vector.size)).mapToLong(i -> (getLong(i) * vector.getLong(i)))      .sum(); }
    public float  dotFloat (Vector vector) { return (float) IntStream.range(0, Math.max(size, vector.size)).mapToDouble(i -> (getFloat(i) * vector.getFloat(i)))  .reduce(0, (a, b) -> (float) (a + b)); }
    public double dotDouble(Vector vector) { return         IntStream.range(0, Math.max(size, vector.size)).mapToDouble(i -> (getDouble(i) * vector.getDouble(i))).sum(); }

    public byte[] toByteArray() {
        byte[] result = new byte[size];
        for (int i=0; i<size; i++)
            try { result[i] = values[i].byteValue(); }
            catch (NullPointerException ignored) { result[i] = 0; }
        return result;
    }
    public short[] toShortArray() {
        short[] result = new short[size];
        for (int i=0; i<size; i++)
            try { result[i] = values[i].shortValue(); }
            catch (NullPointerException ignored) { result[i] = 0; }
        return result;
    }
    public int[] toIntArray() {
        int[] result = new int[size];
        for (int i=0; i<size; i++)
            try { result[i] = values[i].intValue(); }
            catch (NullPointerException ignored) { result[i] = 0; }
        return result;
    }
    public long[] toLongArray() {
        long[] result = new long[size];
        for (int i=0; i<size; i++)
            try { result[i] = values[i].longValue(); }
            catch (NullPointerException ignored) { result[i] = 0; }
        return result;
    }
    public float[] toFloatArray() {
        float[] result = new float[size];
        for (int i=0; i<size; i++)
            try { result[i] = values[i].floatValue(); }
            catch (NullPointerException ignored) { result[i] = 0; }
        return result;
    }
    public double[] toDoubleArray() {
        double[] result = new double[size];
        for (int i=0; i<size; i++)
            try { result[i] = values[i].doubleValue(); }
            catch (NullPointerException ignored) { result[i] = 0; }
        return result;
    }

    public int size() {return size;}

    public Number get(int index) {
        try { return values[index]; }
        catch (IndexOutOfBoundsException | NullPointerException ignored) { return 0; }
    }
    public Number getFirst() { return get(0); }
    public Number getLast() { return get(size - 1); }

    public byte getByte(int index) {
        try {return values[index].byteValue();}
        catch (IndexOutOfBoundsException | NullPointerException ignored) {return 0;}
    }
    public byte getFirstByte() { return getByte(0); }
    public byte getLastByte() { return getByte(size-1); }

    public short getShort(int index) {
        try {return values[index].shortValue();}
        catch (IndexOutOfBoundsException | NullPointerException ignored) {return 0;}
    }
    public short getFirstShort() { return getShort(0); }
    public short getLastShort() { return getShort(size-1); }

    public int getInt(int index) {
        try {return values[index].intValue();}
        catch (IndexOutOfBoundsException | NullPointerException ignored) {return 0;}
    }
    public int getFirstInt() { return getInt(0); }
    public int getLastInt() { return getInt(size-1); }

    public long getLong(int index) {
        try {return values[index].longValue();}
        catch (IndexOutOfBoundsException | NullPointerException ignored) {return 0;}
    }
    public long getFirstLong() { return getLong(0); }
    public long getLastLong() { return getLong(size-1); }

    public float getFloat(int index) {
        try {return values[index].floatValue();}
        catch (IndexOutOfBoundsException | NullPointerException ignored) {return 0;}
    }
    public float getFirstFloat() { return getFloat(0); }
    public float getLastFloat() { return getFloat(size-1); }

    public double getDouble(int index) {
        try {return values[index].doubleValue();}
        catch (IndexOutOfBoundsException | NullPointerException ignored) {return 0;}
    }
    public double getFirstDouble() { return getDouble(0); }
    public double getLastDouble() { return getDouble(size-1); }

    public String toString() {
        return Arrays.toString(Arrays.stream(values)
                .map(n -> n == null ? 0 : n)
                .map(n -> switch (type) {
                    case 0 -> Byte.toString(n.byteValue());
                    case 1 -> Short.toString(n.shortValue());
                    case 2 -> Integer.toString(n.intValue());
                    case 3 -> Long.toString(n.longValue());
                    case 5 -> Double.toString(n.doubleValue());
                    default -> Float.toString(n.floatValue());
                }).toArray());
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;

        Vector vector = (Vector) object;
        for (int i=0; i<Math.max(size, vector.size); i++)
            if (!Objects.equals(get(i), vector.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = size;
        result = 31 * result + Arrays.hashCode(values);
        result = 31 * result + type;
        return result;
    }
}
