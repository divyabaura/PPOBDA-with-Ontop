package ppmappingcompiler.fol;

import java.util.Set;

/**
 * This class models an universal quantifier. See also {@link Quantifier}.
 */
@SuppressWarnings("unused")
public class ForAll extends Quantifier {

    public ForAll(Formula subformula) {
        super(subformula);
    }

    public ForAll(Formula subformula, Variable... universalVars) {
        super(subformula, universalVars);
    }

    public ForAll(Formula subformula, Set<Variable> universalVars) {
        super(subformula, universalVars);
    }

    @Override
    public boolean isTautology() {
        return content.isTautology();
    }

    @Override
    public boolean isContradiction() {
        return false;
    }

    @Override
    public ForAll clone() {
        return (ForAll) super.clone();
    }

    @Override
    public String toString() {
        return toString("\\forall");
    }

}
