package ppmappingcompiler.fol;

import org.semanticweb.owlapi.vocab.OWL2Datatype;

import static org.semanticweb.owlapi.vocab.OWL2Datatype.XSD_STRING;

// TODO: support other datatypes
//  cfr. https://www.w3.org/TR/rdf-sparql-query/#operandDataTypes
@SuppressWarnings("unused")
public class DataConstant extends Constant {
    private final String value;
    private final OWL2Datatype datatype;

    @SuppressWarnings("unused")
    //TODO: make public when also other data types will be accepted
    private DataConstant(String value) {
        this(value, null);
    }

    @SuppressWarnings("unused")
    public DataConstant(String value, OWL2Datatype datatype) {
        this.value = value;
        this.datatype = datatype != null ? datatype : XSD_STRING;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public Term.Type getType() {
        return Type.DATA;
    }

    public OWL2Datatype getDatatype() {
        return this.datatype;
    }

    @Override
    public String toSparql() {
        return String.format("\"%s\"^^<%s>", this.value, this.datatype.getIRI());
    }

    @Override
    public int hashCode() {
        // the class is important because a Variable, a DataConstant and a ObjectConstant must always have different hashcodes
        return (getClass() + this.value).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        return this.value.equals(((DataConstant) obj).value);
    }

    @Override
    // TODO: escape string constants (use Utils.escape???)
    public String toString() {
        switch (this.datatype) {
            case XSD_INT:
            case XSD_INTEGER:
            case XSD_DECIMAL:
                return this.value;
            case XSD_STRING:
                return "\"" + this.value + "\"";
        }
        throw new RuntimeException();
    }

    @Override
    public DataConstant clone() {
        return (DataConstant) super.clone();
    }

}
