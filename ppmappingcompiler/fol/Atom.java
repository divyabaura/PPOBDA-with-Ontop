package ppmappingcompiler.fol;

import ppmappingcompiler.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An atom can be:
 * <ul>
 *  <li> a {@link PredicateAtom}
 *  <li> a {@link ComparisonAtom}
 * </ul>
 */
public abstract class Atom extends Formula {
    public abstract boolean hasVariable(Variable var);

    @Override
    public Set<Variable> getFreeVariables() {
        return new HashSet<>(getVariables());
    }

    @SuppressWarnings("UnusedReturnValue")
    public abstract boolean replaceVariable(Variable oldVar, Term newTerm);

    @Override
    public void optimize() { /* how do you wish to optimize an atomic formula? */ }

    @Override
    public void flatten(boolean recursiveFlag) { /* an atomic formula is already flat */ }

    @Override
    public int depth() {
        return 0;
    }

    /**
     * The method applies one or more replacements basing on a sequence of equalities.<br>
     * The method is thought to be deterministic, since the order of the input list matters.
     * <p>E.g., the {@link List list} {@code [x,y,z,w]} represents the sequence {@code [x=y, y=z, z=w]}.<br>
     * In this case, the method will first apply the replacement {@code x->y},
     * then {@code y->z} and finally {@code z->w}.</p>
     * An eventual {@link Constant constant} will be the last to be replaced.
     *
     * @param replacementsSequence A {@link List list} of {@link Term terms} used for replacements.
     */
    public void multipleReplacement(List<Term> replacementsSequence) throws OperationNotAllowedException {
        List<Constant> constants = Utils.filterByClass(replacementsSequence, Constant.class, Collectors.toList());
        if (constants.size() > 1)
            throw new OperationNotAllowedException("The list of unifying terms can contain at most one constant.");

        // add eventual constant at the end of the list
        replacementsSequence.removeAll(constants);
        replacementsSequence.addAll(constants);

        for (int i = 1; i < replacementsSequence.size(); i++) {
            Term oldTerm = replacementsSequence.get(i - 1);
            Term newTerm = replacementsSequence.get(i);
            this.replaceVariable((Variable) oldTerm, newTerm);
        }
    }

    @Override
    public Atom clone() {
        return (Atom) super.clone();
    }
	
    /*********************
     * AUXILIARY METHODS *
     *********************/

    @Override
    public Formula pushDownNegationAux(boolean negate) {
        return negate ? new Negation(this) : this;
    }

    @Override
    protected Formula getPrenexFormAux() {
        return this;
    }

    @Override
    public void distinguishQuantifiedVariables(Set<String> variablesAlphabet, Set<String> reservedNames, boolean updateReservedNames) {
        // do nothing
    }

}