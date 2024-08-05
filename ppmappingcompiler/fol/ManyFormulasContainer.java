package ppmappingcompiler.fol;

import com.google.common.collect.ImmutableSet;
import ppmappingcompiler.policy.ConjunctiveQuery;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstraction of {@link Conjunction conjunction} and {@link Disjunction disjunction}.
 */
@SuppressWarnings("unused")
public abstract class ManyFormulasContainer extends FormulaContainer implements Iterable<Formula> {

    protected List<Formula> formulas = new ArrayList<>();
    private final List<Class<? extends ManyFormulasContainer>> DIRECT_SUBCLASSES = Arrays.asList(Conjunction.class, Disjunction.class);

    public ManyFormulasContainer() {
    }

    public ManyFormulasContainer(Formula... formulas) {
        this.addAll(Arrays.asList(formulas));
    }

    public ManyFormulasContainer(Collection<? extends Formula> formulas) {
        if (formulas instanceof ManyFormulasContainer) {
            this.add((ManyFormulasContainer) formulas);
        } else this.addAll(formulas);
    }

    /**
     * This method returns a set of clones of the subformulas of {@code this}.
     *
     * @return A {@link Set set} of {@link Formula formulas}.
     */
    public Set<Formula> getFormulas() {
        return formulas
                .stream()
                .map(Formula::clone)
                .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * This method returns the {@link Term terms} occurring in this formula (and in all its eventual sub-formulas).<br>
     * It is overridden in order to remove duplicates.
     *
     * @return A {@link Set set} of {@link Variable variables}.
     */
    @Override
    public Set<Term> getTerms() {
        return getFormulas()    // cloned formulas
                .stream()
                .flatMap(f -> f.getTerms().stream())    // retrieving sub-formulas' terms
                .collect(Collectors.toSet());
    }

    /**
     * This method returns the {@link Variable variables} occurring in this formula (and in all its eventual sub-formulas).<br>
     * It is overridden in order to remove duplicates.
     *
     * @return A {@link Set set} of {@link Variable variables}.
     */
    @Override
    public Set<Variable> getVariables() {
        return getFormulas()    // cloned formulas
                .stream()
                .flatMap(f -> f.getVariables().stream())    // retrieving sub-formulas' variables
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Variable> getFreeVariables() {
        return getFormulas()    // cloned formulas
                .stream()
                .flatMap(f -> f.getFreeVariables().stream())    // retrieving sub-formulas' variables
                .collect(Collectors.toSet());
    }

    public void forEach(Consumer<? super Formula> action) {
        this.formulas.forEach(action);
    }

    @Override
    public boolean replaceVariable(Variable variable, Term term) {
        boolean replaced = false;
        for (Formula f : this.formulas) {
            replaced = f.replaceVariable(variable, term) || replaced;
        }
        return replaced;
    }

    /**
     * This method forces the flattening optimization for the current set of formulas.
     * <ul>
     *  <li> Example 1: f1 AND (f2 AND f3) -> f1 AND f2 AND f3
     *  <li> Example 2: f1 OR (f2 OR f3)   -> f1 OR f2 OR f3
     * </ul>
     */
    @Override
    public void flatten(boolean recursiveFlag) {
        if (recursiveFlag) this.forEach(sf -> sf.flatten(true));
        this.formulas = this.formulas.stream()
                .flatMap(sf -> {
                    Class<? extends ManyFormulasContainer> subClass = this.getClass();
                    return sf.getClass().isAssignableFrom(subClass)
                            ? subClass.cast(sf).formulas.stream()
                            : Stream.of(sf);
                })
                .map(Formula::removeUnnecessaryContainer)
                .collect(Collectors.toList());
    }


    /**
     * This methods implements De Morgan's law for pushing down negation into a conjunction or a disjunction.
     *
     * @return A new set of formulas where negation is pushed down.
     */
    @Override
    protected Formula pushDownNegationAux(boolean negated) {
        Set<Formula> newFormulas = this.formulas.stream()
                .map(sf -> sf.pushDownNegationAux(negated))
                .collect(Collectors.toSet());
        return negated == (this instanceof Conjunction) // XNOR
                ? new Disjunction(newFormulas)
                : new Conjunction(newFormulas);
    }

    abstract protected void removeRedundantFormulas();

    /*
     * TODO: Other possible optimizations (they require the method not to be side-effect-based):
     *  - A(x) OR  NOT (A(x) AND B(x)) -> true
     *  - A(x) AND NOT (A(x) OR  B(x)) -> false
     */
    @Override
    public void optimize() {
        this.forEach(Formula::optimize);    // first optimize children (recursion step)
        this.replaceAll(Formula::removeUnnecessaryContainer);
        this.flatten(false);        // recursion is already done in this method
        List<Formula> distinctFormulas = new ArrayList<>(new LinkedHashSet<>(this.formulas));
        this.clear();
        this.addAll(distinctFormulas);
        this.removeRedundantFormulas();
    }

    /**
     * This method applies the mapping function for replacing in-place all the subformulas
     * with other formulas.
     */
    public void replaceAll(Function<Formula, Formula> mapper) {
        List<Formula> toAdd = this.formulas.stream().map(mapper).collect(Collectors.toList());
        this.clear();
        this.addAll(toAdd);
    }

    @Override
    public void replace(Function<Formula, Formula> mapper, RecursionMethod recursionMethod) {
        Consumer<Formula> recursionReplacement = f -> {
            if (f instanceof FormulaContainer) ((FormulaContainer) f).replace(mapper, recursionMethod);
        };
        if (recursionMethod == RecursionMethod.DFS) this.formulas.forEach(recursionReplacement);
        replaceAll(mapper);
        if (recursionMethod == RecursionMethod.BFS) this.formulas.forEach(recursionReplacement);
    }

    @Override
    public void apply(Consumer<? super Formula> action, RecursionMethod recursionMethod) {
        Consumer<Formula> recursionAction = f -> {
            if (f instanceof FormulaContainer) ((FormulaContainer) f).apply(action, recursionMethod);
        };
        if (recursionMethod == RecursionMethod.DFS) this.formulas.forEach(recursionAction);
        forEach(action);
        if (recursionMethod == RecursionMethod.BFS) this.formulas.forEach(recursionAction);
    }

    /**
     * This method uses the input map for replacing in-place all the subformulas
     * with other formulas.
     */
    public void replaceAll(Map<? extends Formula, ? extends Formula> map) {
        for (int i = 0; i < this.formulas.size(); i++) {
            Formula sf = this.formulas.get(i);
            for (Map.Entry<? extends Formula, ? extends Formula> e : map.entrySet()) {
                if (sf.equals(e.getKey())) {
                    this.formulas.set(i, e.getValue());
                    break; // exit only the inner loop
                }
            }
        }
    }

    public boolean replace(Formula target, Formula replacement) {
        boolean result = false;
        for (int i = 0; i < this.formulas.size(); i++) {
            if (this.formulas.get(i).equals(target)) {
                this.formulas.set(i, replacement);
                result = true;
            }
        }
        return result;
    }

    @Override
    public void bindVariablesToQuery(ConjunctiveQuery cq) {
        this.forEach(f -> f.bindVariablesToQuery(cq));
    }

    @Override
    public void unbindVariables() {
        this.forEach(Formula::unbindVariables);
    }

    @Override
    public void distinguishQuantifiedVariables(Set<String> variablesAlphabet, Set<String> reservedNames, boolean update) {
        this.formulas.forEach(
                f -> f.distinguishQuantifiedVariables(variablesAlphabet, reservedNames, update)
        );
    }

    public int size() {
        return this.formulas.size();
    }

    public boolean isEmpty() {
        return this.formulas.isEmpty();
    }

    public boolean contains(Formula formula) {
        return this.formulas.contains(formula);
    }

    @Nonnull
    public Iterator<Formula> iterator() {
        return getFormulas().iterator();
    }

    @Nonnull
    public Object[] toArray() {
        return getFormulas().toArray();
    }

    public boolean add(Formula formula) {
        return this.formulas.add(formula);
    }

    public boolean addAll(Formula... formulas) {
        return this.addAll(Arrays.asList(formulas));
    }

    public boolean addAll(@Nonnull Collection<? extends Formula> formulas) {
        boolean res = false;
        for (Formula f : formulas) res |= this.add(f);
        return res;
    }

    public boolean remove(Formula formula) {
        return this.formulas.remove(formula);
    }

    @Override
    public int depth() {
        int maxDepth = -1;
        for (Formula sf : this.formulas) {
            maxDepth = Math.max(maxDepth, sf.depth());
        }
        assert maxDepth > 0;
        return maxDepth + 1;
    }

    public boolean containsAny(@Nonnull Collection<? extends Formula> c) {
        for (Formula f : c) {
            if (this.formulas.contains(f)) return true;
        }
        return false;
    }

    public boolean containsAll(@Nonnull Collection<? extends Formula> c) {
        return this.formulas.containsAll(c);
    }

    public boolean retainAll(@Nonnull Collection<? extends Formula> c) {
        return this.formulas.retainAll(c);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean removeAll(@Nonnull Collection<? extends Formula> c) {
        return this.formulas.removeAll(c);
    }

    public void clear() {
        this.formulas.clear();
    }

    public String toString(String operator) {
        if (this.size() == 1) return this.formulas.iterator().next().toString();

        return this.formulas
                .stream()
                .map(f -> (f instanceof ManyFormulasContainer && ((ManyFormulasContainer) f).size() > 1)
                        ? wrap(f.toString()) : f.toString())
                .collect(Collectors.joining(" " + operator + " "));
    }

    @Override
    public ManyFormulasContainer clone() {
        ManyFormulasContainer clone = (ManyFormulasContainer) super.clone();
        clone.formulas = this.formulas.stream()
                .map(Formula::clone)
                .collect(Collectors.toList());
        return clone;
    }

    @Override
    public int hashCode() {
        for (Class<? extends ManyFormulasContainer> tClass : DIRECT_SUBCLASSES) {
            if (tClass.isAssignableFrom(this.getClass()))
                return (tClass.toString() + Objects.hash(formulas)).hashCode();
        }
        throw new RuntimeException();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ManyFormulasContainer) {
            ManyFormulasContainer sof = (ManyFormulasContainer) o;
            for (Class<? extends ManyFormulasContainer> tClass : DIRECT_SUBCLASSES) {
                if (tClass.isAssignableFrom(this.getClass()) &&
                        tClass.isAssignableFrom(o.getClass())) {
                    return this.formulas.equals(sof.formulas);
                }
            }
            return this.size() == 1 && this.iterator().next().equals(sof) ||
                    sof.size() == 1 && sof.iterator().next().equals(this);
        } else return false;
    }


    /*===================*
     * AUXILIARY METHODS *
     *===================*/

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        this.forEach(f -> f.explicitVariablesAux(alphabet, reservedNames));
    }

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        this.forEach(f -> f.replaceVariablesNoChain(substitution));
    }

    @Override
    protected Formula getPrenexFormAux() {
        Set<Variable> existentialVars = new HashSet<>();
        Set<Variable> universalVars = new HashSet<>();
        List<Formula> subformulas = new ArrayList<>();

        this.forEach(sf -> {
            Formula currentContent = sf.getPrenexForm();
            while (currentContent != null) {
                if (currentContent instanceof Quantifier) {
                    Quantifier quantifier = (Quantifier) currentContent;
                    if (currentContent instanceof Exist) {
                        existentialVars.addAll(quantifier.getQuantifiedVariables());
                    } else if (currentContent instanceof ForAll) {
                        universalVars.addAll(quantifier.getQuantifiedVariables());
                    }
                    currentContent = quantifier.getContent();
                } else {
                    subformulas.add(currentContent);
                    break;
                }
            }
        });

        ManyFormulasContainer result = this.clone();
        result.clear();
        result.addAll(subformulas);

        boolean exists = !existentialVars.isEmpty();
        boolean forall = !universalVars.isEmpty();
        if (exists && forall) return new Exist(new ForAll(result, universalVars), existentialVars);
        if (exists) return new Exist(result, existentialVars);
        if (forall) return new ForAll(result, universalVars);
        return result;
    }

}
