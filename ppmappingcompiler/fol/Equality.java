package ppmappingcompiler.fol;

/**
 * An equality is a construct of the form {@code x = y}, where {@code x} and {@code y} are {@link Term terms}.
 */
public class Equality extends ComparisonAtom {

    public Equality(Term left, Term right) {
        super(left, right, "=", true);
    }

    @Override
    public boolean isTautology() {
        return left.equals(right);
    }

    @Override
    public boolean isContradiction() {
        return false;
    }

    @Override
    public StrictInequality negate() {
        return new StrictInequality(this.left, this.right);
    }

    @Override
    public Equality clone() {
        return (Equality) super.clone();
    }

}
