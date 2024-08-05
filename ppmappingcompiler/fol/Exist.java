package ppmappingcompiler.fol;

import java.util.Set;

/**
 * This class models an existential quantifier. See also {@link Quantifier}.
 */
public class Exist extends Quantifier {

    public Exist(Formula subformula) {
        super(subformula);
    }

    public Exist(Formula subformula, Variable... existentialVars) {
        super(subformula, existentialVars);
    }

    public Exist(Formula subformula, Set<Variable> existentialVars) {
        super(subformula, existentialVars);
    }

    @Override
    public boolean isTautology() {
        return false;
    }

    @Override
    public boolean isContradiction() {
        return content.isContradiction();
    }

    @Override
    public Exist clone() {
        return (Exist) super.clone();
    }

    @Override
    public String toString() {
        return toString("\\exists");
    }

}
