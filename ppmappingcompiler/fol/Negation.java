package ppmappingcompiler.fol;

import ppmappingcompiler.policy.ConjunctiveQuery;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Negation extends SingleFormulaContainer {

    public Negation(Formula formula) {
        this.content = formula;
    }

    @Override
    public Collection<Term> getTerms() {
        return this.content.getTerms();
    }

    @Override
    public Collection<Variable> getVariables() {
        return this.content.getVariables();
    }

    @Override
    public Set<Variable> getFreeVariables() {
        return this.content.getFreeVariables();
    }

    @Override
    public void flatten(boolean recursiveFlag) {
        if (recursiveFlag) this.content.flatten(true);
        this.content = removeUnnecessaryContainer(this.content);
    }

    @Override
    public void optimize() {
        this.optimizeContent();
        this.flatten(false);    // no recursion, since the content has already been optimized
    }

    @Override
    public void bindVariablesToQuery(ConjunctiveQuery cq) {
        this.content.bindVariablesToQuery(cq);
    }

    @Override
    public void unbindVariables() {
        this.content.unbindVariables();
    }

    @Override
    public Formula pushDownNegationAux(boolean negate) {
        return this.content.pushDownNegationAux(!negate);
    }

    @Override
    public Formula negate() {
        return this.getContent();
    }

    @Override
    public boolean replaceVariable(Variable variable, Term term) {
        return this.content.replaceVariable(variable, term);
    }

    @Override
    public void distinguishQuantifiedVariables(Set<String> variablesAlphabet, Set<String> reservedNames, boolean update) {
        this.content.distinguishQuantifiedVariables(variablesAlphabet, reservedNames, update);
    }

    @Override
    public boolean isTautology() {
        return this.content.isContradiction();
    }

    @Override
    public boolean isContradiction() {
        return this.content.isTautology();
    }

    @Override
    public String toString() {
        return "NOT (" + this.content + ")";
    }

    @Override
    public int hashCode() {
        // the class makes the hashcode be different from the content's one
        return (getClass().toString() + Objects.hash(this.content)).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Negation)) return false;
        return this.content.equals(((Negation) o).content);
    }

    @Override
    public Negation clone() {
        Negation clone = (Negation) super.clone();
        clone.content = this.content.clone();
        return clone;
    }


    /*===================*
     * AUXILIARY METHODS *
     *===================*/

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        this.content.replaceVariablesNoChain(substitution);
    }

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        this.content.explicitVariablesAux(alphabet, reservedNames);
    }

    @Override
    protected Formula getPrenexFormAux() {
        Formula PNFcontent = this.content.getPrenexForm();
        return popOutQuantifiers(PNFcontent);
    }

    /**
     * Example input:  ∃x∀y R(x,y)
     * Example output: ∀x∃y ¬R(x,y)
     */
    private static Formula popOutQuantifiers(Formula formula) {
        if (formula instanceof Quantifier) {
            Quantifier quantifier = (Quantifier) formula;
            Formula content = popOutQuantifiers(quantifier.getContent());
            if (formula instanceof ForAll) return new Exist(content, quantifier.getQuantifiedVariables());
            else if (formula instanceof Exist) return new ForAll(content, quantifier.getQuantifiedVariables());
            else throw new RuntimeException();
        } else return removeUnnecessaryContainer(new Negation(formula));
    }

}
