package ppmappingcompiler.fol;

import ppmappingcompiler.policy.ConjunctiveQuery;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

public class False extends Atom {

    private static final False instance = new False();

    private False() {
    }

    public static False getInstance() {
        return instance;
    }

    @Override
    public boolean isTautology() {
        return false;
    }

    @Override
    public boolean isContradiction() {
        return true;
    }

    @Override
    public True negate() {
        return True.getInstance();
    }

    @Override
    public Set<Term> getTerms() {
        return java.util.Collections.emptySet();
    }

    @Override
    public Set<Variable> getVariables() {
        return java.util.Collections.emptySet();
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
        return o instanceof False;
    }

    @Override
    public String toString() {
        return "FALSE";
    }

    @Override
    public False clone() {
        return getInstance();
    }

    /*********************
     * AUXILIARY METHODS *
     *********************/

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        // do nothing
    }

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        // do nothing
    }

}