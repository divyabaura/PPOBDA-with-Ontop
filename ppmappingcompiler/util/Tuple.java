package ppmappingcompiler.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

@SuppressWarnings("unused")
public class Tuple {

    @Nonnull
    private final Object[] values;

    public Tuple(@Nullable Object... values) {
        // if the input array is null, it is interpreted as a single-value array containing null
        if (values == null) this.values = new Object[]{null};
        else this.values = values;
    }

    public int size() {
        return this.values.length;
    }

    public Object get(int index) {
        return this.values[index];
    }

    public Object set(int index, Object element) {
        Object oldValue = this.values[index];
        this.values[index] = element;
        return oldValue;
    }

    public Object[] toArray() {
        return this.values.clone();
    }

    @Override
    public String toString() {
        return Arrays.asList(this.values).toString();
    }

    @Override
    public int hashCode() {
        return this.getClass().hashCode() ^ Arrays.hashCode(this.values);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tuple)) return false;
        Tuple otherTuple = (Tuple) o;
        if (otherTuple.values.length != this.values.length) return false;
        for (int i = 0; i < this.values.length; i++) {
            // the i-th value may be null, so the equals() method may return a NullPointerException
            if (this.values[i] != otherTuple.values[i] &&
                    !this.values[i].equals(otherTuple.values[i])) return false;
        }
        return true;
    }

}
