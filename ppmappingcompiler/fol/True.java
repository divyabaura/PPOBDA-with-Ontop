package ppmappingcompiler.fol;

import ppmappingcompiler.policy.ConjunctiveQuery;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class True extends Atom {

    private static final True emptySet = new True();

    private True() {
    }

    public static True getInstance() {
        return emptySet;
    }

    @Override
    public boolean isTautology() {
        return true;
    }

    @Override
    public boolean isContradiction() {
        return false;
    }

    @Override
    public False negate() {
        return False.getInstance();
    }

    @Override
    public Set<Term> getTerms() {
        return Collections.emptySet();
    }

    @Override
    public Set<Variable> getVariables() {
        return Collections.emptySet();
    }

    @Override
    public boolean hasVariable(Variable var) {
        return false;
    }

    @Override
    public boolean replaceVariable(Variable oldVar, Term newTerm) {
        return false;
    }

    @Override
    public void bindVariablesToQuery(ConjunctiveQuery cq) {
    }

    @Override
    public void unbindVariables() {
    }

    /**
     * This method overrides the {@link Object#hashCode()} one.<br>
     * Notice that, since we can have only one instance, the hashcode is unique.
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof True;
    }

    @Override
    public String toString() {
        return "TRUE";
    }

    @Override
    public True clone() {
        return getInstance();
    }

    /*===================*
     * AUXILIARY METHODS *
     *===================*/

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        // do nothing
    }

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        // do nothing
    }

}