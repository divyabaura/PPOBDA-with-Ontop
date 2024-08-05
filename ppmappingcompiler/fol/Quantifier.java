package ppmappingcompiler.fol;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class models a generic quantifier of variables.
 */
public abstract class Quantifier extends SingleFormulaContainer {
    private final Set<Variable> quantifiedVars;

    public Quantifier(Formula subformula) {
        this(subformula, new HashSet<>());
    }

    public Quantifier(Formula subformula, Variable... quantifiedVars) {
        this(subformula, new HashSet<>(Arrays.asList(quantifiedVars)));
    }

    public Quantifier(Formula subformula, Set<Variable> quantifiedVars) {
        this.content = subformula;
        this.quantifiedVars = new HashSet<>(quantifiedVars);
    }

    @Override
    public Collection<Term> getTerms() {
        return Utils.setUnion(
                new HashSet<>(this.getQuantifiedVariables()),
                content.getTerms());
    }

    @Override
    public Collection<Variable> getVariables() {
        return Utils.setUnion(
                this.getQuantifiedVariables(),
                content.getVariables());
    }

    @Override
    public Set<Variable> getFreeVariables() {
        return Utils.setDifference(this.content.getFreeVariables(), this.quantifiedVars);
    }

    public Set<Variable> getQuantifiedVariables() {
        return this.quantifiedVars.stream()
                .map(Variable::clone)
                .collect(Collectors.toSet());
    }

    @Override
    public void unbindVariables() {
        this.quantifiedVars.forEach(Variable::unbind);
        content.unbindVariables();
    }

    @Override
    public boolean replaceVariable(Variable variable, Term term) {
        boolean replaced = false;
        if (quantifiedVars.contains(variable)) {
            quantifiedVars.remove(variable);
            if (term instanceof Variable) {
                quantifiedVars.add((Variable) term);
            }
            replaced = true;
        }
        return replaced || content.replaceVariable(variable, term);
    }

    @Override
    public void optimize() {
        this.optimizeContent();
        this.flatten(false);    // no recursion, since the content has already been optimized
        Set<Variable> newVars = ImmutableSet.copyOf(Sets.intersection(this.quantifiedVars, new HashSet<>(content.getVariables())));
        this.quantifiedVars.clear();
        this.quantifiedVars.addAll(newVars);
    }

    @Override
    protected Formula pushDownNegationAux(boolean negated) {
        Formula newContent = this.content.pushDownNegationAux(negated);
        return negated == (this instanceof Exist) // XNOR
                ? new ForAll(newContent, this.getQuantifiedVariables())
                : new Exist(newContent, this.getQuantifiedVariables());
    }

    @Override
    public void flatten(boolean recursiveFlag) {
        if (recursiveFlag) //noinspection ConstantConditions
            this.content.flatten(recursiveFlag);
        if (getClass() == this.content.getClass()) {
            Quantifier containedQuantifier = ((Quantifier) this.content);
            this.quantifiedVars.addAll(containedQuantifier.quantifiedVars);
            this.content = containedQuantifier.content;
        } else this.content = removeUnnecessaryContainer(this.content);
    }

    @Override
    public void bindVariablesToQuery(ConjunctiveQuery cq) {
        this.quantifiedVars.forEach(v -> v.bindToQuery(cq));
        content.bindVariablesToQuery(cq);
    }

    @Override
    public void distinguishQuantifiedVariables(Set<String> variablesAlphabet, Set<String> reservedNames, boolean update) {
        for (Variable v : this.getQuantifiedVariables()) {
            if (reservedNames.contains(v.getName())) {
                Variable freshVariable = getFreshVariable(variablesAlphabet, reservedNames, update);
                this.replaceVariables(new HashMap<Variable, Variable>() {{
                    put(v, freshVariable);
                }});
            } else {
                if (update) reservedNames.add(v.getName());
            }
        }
        this.content.distinguishQuantifiedVariables(variablesAlphabet, reservedNames, update);
    }

    @Override
    public int hashCode() {
        return (getClass().toString() + Objects.hash(quantifiedVars) + content.hashCode()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        Quantifier q = (Quantifier) obj;
        return this.quantifiedVars.equals(q.quantifiedVars) && this.content.equals(q.content);
    }

    @Override
    public Quantifier clone() {
        return (Quantifier) super.clone();
    }

    public String toString(String quantifierSymbol) {
        String varString = this.quantifiedVars
                .stream()
                .map(Variable::toString)
                .collect(Collectors.joining(", "));
        return String.format("%s %s (%s)", quantifierSymbol, varString, content.toString());
    }


    /*===================*
     * AUXILIARY METHODS *
     *===================*/

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        content.replaceVariablesNoChain(substitution);

        Set<Variable> toRemove = new HashSet<>();
        Set<Variable> toAdd = new HashSet<>();
        for (Variable var : quantifiedVars) {
            if (substitution.containsKey(var)) {
                toRemove.add(var);
                Term replacement = substitution.get(var);
                if (replacement instanceof Variable) {
                    toAdd.add((Variable) replacement);
                }
            }
        }
        quantifiedVars.removeAll(toRemove);
        quantifiedVars.addAll(toAdd);
    }

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        this.content.explicitVariablesAux(alphabet, reservedNames);
    }

    @Override
    protected Formula getPrenexFormAux() {
        this.content = this.content.getPrenexForm();
        this.flatten(false);
        return this;
    }
}
