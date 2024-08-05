package ppmappingcompiler.fol;

/**
 * A strict inequality is a construct of the form {@code not(x = y)} or {@code x \= y}, where {@code x} and {@code y} are {@link Term terms}.
 */
public class StrictInequality extends ComparisonAtom {

    public StrictInequality(Term left, Term right) {
        super(left, right, "!=", true);
    }

    @Override
    public boolean isTautology() {
        return false;
    }

    @Override
    public boolean isContradiction() {
        return left.equals(right);
    }

    @Override
    public Equality negate() {
        return new Equality(this.left, this.right);
    }

    @Override
    public StrictInequality clone() {
        return (StrictInequality) super.clone();
    }

}
