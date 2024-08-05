package ppmappingcompiler.fol;

/**
 * Object constants (a.k.a. individuals) are concept instances.
 */
@SuppressWarnings("unused")
public class ObjectConstant extends Constant {

    private final String iri;

    @SuppressWarnings("unused")
    public ObjectConstant(String iri) {
        this.iri = iri;
    }

    public String getIRI() {
        return this.iri;
    }

    @Override
    public Type getType() {
        return Type.OBJECT;
    }

    @Override
    public String toSparql() {
        return String.format("<%s>", this.iri);
    }

    @Override
    public int hashCode() {
        // the class is important because a Variable, a DataConstant and a ObjectConstant must always have different hashcodes
        return (getClass() + this.iri).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        return this.iri.equals(((ObjectConstant) obj).iri);
    }

    @Override
    public String toString() {
        return this.iri;
    }

    @Override
    public ObjectConstant clone() {
        return (ObjectConstant) super.clone();
    }

}
