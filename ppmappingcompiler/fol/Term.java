package ppmappingcompiler.fol;

/**
 * A term can be a {@link Variable variable} or a {@link Constant constant}.
 */
public abstract class Term implements Cloneable {

    public enum Type {
        UNDEFINED, OBJECT, DATA;

        @Override
        public String toString() {
            switch (this) {
                case OBJECT:
                    return "object";
                case DATA:
                    return "data";
                default:
                    return "undefined";
            }
        }
    }

    /**
     * A type can be object or data:
     * <ul>
     *  <li> concepts and roles use only object variables/constants;
     *  <li> attributes use an object variable/constant in first position and a data variable/constant in second position.
     * </ul>
     */
    abstract public Term.Type getType();

    abstract public String toSparql();

    @Override
    abstract public String toString();

    @Override
    abstract public boolean equals(Object obj);

    @Override
    abstract public int hashCode();

    @Override
    public Term clone() {
        try {
            return (Term) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static class TermTypeException extends Exception {
        public TermTypeException(String s) {
            super(s);
        }
    }

}