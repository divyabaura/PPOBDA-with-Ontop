package ppmappingcompiler.fol;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ppmappingcompiler.util.Utils.filterByClass;

public class Conjunction extends ManyFormulasContainer {

    /**
     * This method creates an empty conjunction (i.e., {@code true}).
     */
    public Conjunction() {
        super();
    }

    /**
     * This method creates a conjunction of {@link Formula formulas}.
     *
     * @param formulas The formulas the conjunction consists of.
     */
    public Conjunction(Formula... formulas) {
        super(formulas);
    }

    /**
     * This method creates a conjunction of {@link Formula formulas}.
     *
     * @param formulas The formulas the conjunction consists of.
     */
    public Conjunction(Collection<? extends Formula> formulas) {
        super(formulas);
    }

    /**
     * This method returns the set of {@link Variable variables} occurring free in more than one conjunct.
     *
     * @return A {@link Set set} of {@link Variable variables}.
     */
    public Set<Variable> getSharedVariables() {
        Map<Variable, Integer> variablesCounter = new HashMap<>();
        getFormulas()
                .stream()
                .flatMap(f -> f.getFreeVariables().stream())
                .forEach(v -> variablesCounter.merge(v, 1, Integer::sum)); // increment the variable counter by 1

        return variablesCounter
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * This method uses some heuristics for removing redundant sub-formulas:
     * <ul>
     *  <li> A(x) AND B(x) AND     TRUE            -> A(x) AND B(x)
     *  <li> A(x) AND B(x) AND     (C(x) OR  B(x)) -> A(x) AND B(x)
     *  <li> A(x) AND B(x) AND NOT (C(x) AND B(x)) -> A(x) AND B(x) AND NOT ( C(x) )
     * </ul>
     */
    @Override
    protected void removeRedundantFormulas() {
        Set<PredicateAtom> pAtoms = filterByClass(this.formulas, PredicateAtom.class, Collectors.toSet());
        List<Formula> newFormulas = this.formulas.stream()
                .filter(f -> {
                    f = removeContainer(f, Exist.class);
                    return !(f instanceof Disjunction && ((Disjunction) f).containsAny(pAtoms));
                })
                .map(f -> {
                    Consumer<Formula> removeAtoms = sf -> {
                        if (sf instanceof Conjunction) ((Conjunction) sf).removeAll(pAtoms);
                    };
                    Formula newFormula = f.clone();
                    if (newFormula instanceof Negation) {
                        Negation neg = ((Negation) newFormula);
                        neg.apply(removeAtoms);
                        neg.apply(sf -> {
                            if (sf instanceof Exist) ((Exist) sf).apply(removeAtoms);
                        });
                        neg.replaceContent(Formula::removeUnnecessaryContainer);
                    }
                    return removeUnnecessaryContainer(newFormula);
                })
                .filter(f -> !f.isTautology())
                .collect(Collectors.toList());
        this.clear();
        this.addAll(newFormulas);
    }

    @Override
    public boolean isTautology() {
        for (Formula sf : this) {
            if (!sf.isTautology()) return false;
        }
        return true;
    }

    @Override
    public boolean isContradiction() {
        for (Formula sf : this) {
            if (sf.isContradiction()) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        if (this.isEmpty()) return True.getInstance().toString();
        return toString("AND");
    }

    @Override
    public Conjunction clone() {
        return (Conjunction) super.clone();
    }

}
