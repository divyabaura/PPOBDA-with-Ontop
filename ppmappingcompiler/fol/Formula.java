package ppmappingcompiler.fol;

import com.google.common.collect.ImmutableSet;
import ppmappingcompiler.db.SQLUtils;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static ppmappingcompiler.fol.Variable.BLANK_VAR_SYMBOL;

/**
 * A formula can be:
 * <ul>
 *  <li> an {@link Atom}
 *  <li> a {@link FormulaContainer}
 * </ul>
 */
@SuppressWarnings("unused")
public abstract class Formula implements Cloneable {

    public static final Set<String> LC_LATIN_ALPHABET = ImmutableSet.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "z");

    /**
     * This method returns the {@link Term terms} occurring this formula (and in all its eventual sub-formulas).
     *
     * @return A {@link Collection collection} of {@link Term terms}.
     */
    public abstract Collection<Term> getTerms();

    /**
     * This method returns the {@link Variable variables} occurring this formula (and in all its eventual sub-formulas).
     *
     * @return A {@link Collection collection} of {@link Variable variables}.
     */
    public abstract Collection<Variable> getVariables();

    /**
     * This method returns the {@link Variable variables} of the formula which do not occur in the scope of a quantifier.
     *
     * @return A {@link Collection collection} of {@link Variable variables}.
     */
    public abstract Set<Variable> getFreeVariables();

    /**
     * This method returns the names of all the variables occurring in this formula.
     *
     * @return A {@link Set set} of {@link String string} representing variables names.
     */
    public Set<String> getVariablesNames() {
        return this.getVariables()
                .stream()
                .map(Variable::getName)
                .collect(Collectors.toSet());
    }

    public boolean hasVariableWithName(String varName) {
        for (Variable v : getVariables()) {
            if (v.getName().equals(varName)) return true;
        }
        return false;
    }

    public Formula getPrenexForm() {
        return this.getPrenexForm(this.getActiveVariablesAlphabet());
    }

    public Formula getPrenexForm(Set<String> variablesAlphabet) {
        Formula clone = this.clone();
        clone.distinguishQuantifiedVariables(variablesAlphabet);
        return clone.getPrenexFormAux();
    }

    protected abstract Formula getPrenexFormAux();

    /**
     * Example input:  ∃x,y (R(x,y) v ∀y (S(x,y)))
     * Example output: ∃x,y (R(x,y) v ∀z (S(x,z)))
     *
     * @param variablesAlphabet The variables' alphabet.
     * @param reservedNames     Variables names that should not be chosen.
     * @param update            Update the set of reserved variable names (default: {@code false}).
     */
    public abstract void distinguishQuantifiedVariables(Set<String> variablesAlphabet,
                                                        Set<String> reservedNames,
                                                        boolean update);

    public void distinguishQuantifiedVariables(Set<String> variablesAlphabet) {
        Set<String> reservedNames = getFreeVariables().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
        distinguishQuantifiedVariables(variablesAlphabet, reservedNames, true);
    }

    public void distinguishQuantifiedVariables() {
        distinguishQuantifiedVariables(getActiveVariablesAlphabet());
    }

    /**
     * This method checks if the formula is a tautology.
     * It is sound (not complete), therefore should be used only for optimization purposes.
     *
     * @return {@code true} if the formula is a tautology, {@code false} otherwise (or if it cannot be determined).
     */
    public abstract boolean isTautology();

    /**
     * This method checks if the formula is a contradiction.
     * It is sound (not complete), therefore should be used only for optimization purposes.
     *
     * @return {@code true} if the formula is a contradiction, {@code false} otherwise (or if it cannot be determined).
     */
    public abstract boolean isContradiction();

    public abstract void unbindVariables();

    /**
     * The method applies a substitution, consisting of a variable-to-term replacement map.<br>
     * If chained replacement is allowed, the substitution will be applied replacement-by-replacement; otherwise, it
     * will be applied variable-by-variable.<br>
     * E.g., given a {@link Map map} {@code {x->y, y->x}}, the formula {@code C(x), R(x,y)} would become
     * {@code C(y), R(y,x)} if chained replacement is not allowed, or {@code C(x), R(x,x)} otherwise.
     *
     * @param substitution       A {@link Map map} containing {@link Variable variable}-to-{@link Term term} replacements.
     * @param chainedReplacement A Boolean value (default: {@code false}).
     */
    public void replaceVariables(Map<Variable, ? extends Term> substitution, boolean chainedReplacement) {
        if (chainedReplacement) {
            substitution.forEach((var, term) -> {
                if (getVariables().contains(var)) {
                    this.replaceVariable(var, term);
                }
            });
        } else {
            this.replaceVariablesNoChain(substitution);
        }
    }

    public void replaceVariables(Map<Variable, ? extends Term> substitution) {
        replaceVariables(substitution, false);
    }

    public abstract boolean replaceVariable(Variable variable, Term term);

    protected abstract void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution);

    /**
     * The method applies a substitution, consisting of a variable-name-to-term replacement map.<br>
     * If chained replacement is allowed, the substitution will be applied replacement-by-replacement; otherwise, it
     * will be applied term-by-term.<br>
     * E.g., given a {@link Map map} {@code {"x"->"y", "y"->"x"}}, the formula {@code C(x), R(x,y)} would become
     * * {@code C(y), R(y,x)} if chained replacement is not allowed, or {@code C(x), R(x,x)} otherwise.
     *
     * @param substitution       A {@link Map map} containing {@link String string}-to-{@link String string} replacements.
     * @param chainedReplacement A Boolean value (default: {@code false}).
     */
    public void replaceVariablesByName(Map<String, ? extends Term> substitution, boolean chainedReplacement) {
        Map<Variable, Term> varSubstitution = new HashMap<>();
        for (Map.Entry<String, ? extends Term> e : substitution.entrySet()) {
            varSubstitution.put(getVariableByName(e.getKey()), e.getValue());
        }
        replaceVariables(varSubstitution, chainedReplacement);
    }

    public void replaceVariablesByName(Map<String, ? extends Term> substitution) {
        replaceVariablesByName(substitution, false);
    }

    public Variable getVariableByName(String name) {
        for (Variable v : this.getVariables()) {
            if (v.getName().equals(name)) return v;
        }
        return null;
    }

    public Set<String> getActiveVariablesAlphabet() {
        return Arrays.stream(String.join("", getVariablesNames()).split(""))
                .filter(s -> !s.equals(BLANK_VAR_SYMBOL))
                .collect(Collectors.toSet());
    }

    public abstract void optimize();

    public abstract void flatten(boolean recursiveFlag);

    // TODO: remove?
    public static Formula pushDownNegation(Formula formula) {
        return formula.clone().pushDownNegationAux(false);
    }

    protected abstract Formula pushDownNegationAux(boolean negated);

    public Formula negate() {
        return new Negation(this.clone());
    }

    /**
     * This method returns a variable name which is still unused (i.e., not among the reserved names).<br>
     * Comparisons between variable names are case-insensitive.<br>
     * E.g., alphabet: {@code {a,b}}, reservedNames: {@code {A,b,aa}} -> outcome: {@code ab}
     *
     * @param alphabet            An array of allowed variable names.
     * @param reservedNames       A set of already used variable names.
     * @param updateReservedNames Update the set of reserved variable names (default: {@code false}).
     * @return An unused variable name.
     */
    public static String getFreshVariableName(Set<String> alphabet, Set<String> reservedNames, boolean updateReservedNames) {
        Set<String> upperCaseReservedNames = reservedNames.stream().map(String::toUpperCase).collect(Collectors.toSet());
        Set<String> possibleVariables = new HashSet<>(alphabet);
        while (true) {
            for (String newVar : possibleVariables) {
                if (!upperCaseReservedNames.contains(newVar.toUpperCase()) && !SQLUtils.isSQLReservedWord(newVar)) {
                    if (updateReservedNames) reservedNames.add(newVar);
                    return newVar;
                }
            }
            possibleVariables = Utils.stringCartesianProduct(possibleVariables, alphabet);
        }
    }

    public static String getFreshVariableName(Set<String> alphabet, Set<String> reservedNames) {
        return getFreshVariableName(alphabet, reservedNames, false);
    }

    /**
     * This method returns a variable whose name is still unused (i.e., not among the reserved names).
     *
     * @param alphabet            An array of allowed variable names.
     * @param reservedNames       A set of already used variable names.
     * @param updateReservedNames Update the set of reserved variable names (default: {@code false}).
     * @return A fresh variable.
     */
    public static Variable getFreshVariable(Set<String> alphabet, Set<String> reservedNames, boolean updateReservedNames) {
        return new Variable(getFreshVariableName(alphabet, reservedNames, updateReservedNames));
    }

    public static Variable getFreshVariable(Set<String> alphabet, Set<String> reservedNames) {
        return getFreshVariable(alphabet, reservedNames, false);
    }

    /**
     * This method make a single literals' variables explicit according to a specific alphabet.<br>
     * E.g., {@code A(x,_), B(_,y) -> A(x,z), B(w,y)}
     *
     * @param alphabet A set of allowed variable names.
     */
    public void explicitVariables(@Nonnull Set<String> alphabet) {
        this.explicitVariables(alphabet, new HashSet<>());
    }

    /**
     * This method replaces undistinguished-nonshared variables with skolem variables according to a specific alphabet.<br>
     * E.g., {@code A(x,_), B(_,y) -> A(x,z), B(w,y)}
     *
     * @param alphabet      A set of allowed variable names.
     * @param reservedNames A set of already used variable names.
     */
    public void explicitVariables(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        if (alphabet.isEmpty()) {
            alphabet = getActiveVariablesAlphabet();
        }

        if (reservedNames.isEmpty())
            reservedNames = new HashSet<>(getVariablesNames());
        else reservedNames.addAll(getVariablesNames());

        this.explicitVariablesAux(alphabet, reservedNames);
    }

    protected abstract void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames);

    public abstract void bindVariablesToQuery(ConjunctiveQuery cq);

    /**
     * This method returns the specified formula without the eventual unnecessary "container",
     * as in case of double negations and single-atom conjunctions or disjunctions.<br>
     * Example:
     * <ul>
     *  <li> {@code NOT( NOT( R(x,y) ) )} -> {@code R(x,y)}
     *  <li> {@code CONJ( R(x,y) )} -> {@code R(x,y)}
     *  <li> {@code DISJ( R(x,y) )} -> {@code R(x,y)}
     * </ul>
     */
    public static Formula removeUnnecessaryContainer(@Nonnull Formula formula) {
        if (formula instanceof Negation) {
            Formula subFormula = ((Negation) formula).getContent();
            if (subFormula instanceof Negation) {
                return ((Negation) subFormula).getContent();
            }
        } else if (formula instanceof ManyFormulasContainer && ((ManyFormulasContainer) formula).size() == 1) {
            return ((ManyFormulasContainer) formula).getFormulas().iterator().next();
        } else if (formula instanceof Quantifier && formula.getVariables().isEmpty()) {
            return ((Quantifier) formula).getContent();
        }
        return formula;
    }

    public abstract int depth();

    /**
     * This method removes all the single-formula container that are instance of a specific class.
     * For instance, if a formula is {@code NOT(A(x) AND B(x))} and the container to remove is of class {@link Negation},
     * then the output formula will be {@code A(x) AND B(X)}.
     * It is possible to specify multiple classes for the container tob e removed.
     */
    @SafeVarargs
    public static Formula removeContainer(@Nonnull Formula f, Class<? extends SingleFormulaContainer>... classes) {
        if (f instanceof SingleFormulaContainer) {
            for (Class<? extends SingleFormulaContainer> tClass : classes) {
                if (tClass.isAssignableFrom(f.getClass())) {
                    return removeContainer(((SingleFormulaContainer) f).getContent(), classes);
                }
            }
        }
        return f;
    }

    public static StringBuilder wrap(StringBuilder s) {
        return wrap(s, false);
    }

    public static StringBuilder wrap(StringBuilder s, boolean inline) {
        return inline
                ? s.insert(0, "( ").append(" )")
                : Utils.indent(s).insert(0, "(\n").append("\n)");
    }

    public static String wrap(String s) {
        return wrap(s, false);
    }

    public static String wrap(String s, boolean inline) {
        return inline
                ? "( " + s + " )"
                : "(\n" + Utils.indent(s) + "\n)";
    }

    @Override
    public abstract String toString();

    @Override
    abstract public int hashCode();

    @Override
    abstract public boolean equals(Object o);

    @Override
    public Formula clone() {
        try {
            return (Formula) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

}