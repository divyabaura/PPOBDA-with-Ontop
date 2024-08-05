package ppmappingcompiler.fol;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static ppmappingcompiler.util.Utils.filterByClass;

@SuppressWarnings("unused")
public class Disjunction extends ManyFormulasContainer {

    /**
     * Creates an empty disjunction (i.e., {@code false}).
     */
    public Disjunction() {
        super();
    }

    /**
     * Creates a disjunction of {@link Formula formulas}.
     *
     * @param formulas The formulas the disjunction consists of.
     */
    public Disjunction(Formula... formulas) {
        super(formulas);
    }

    /**
     * Creates a disjunction of {@link Formula formulas}.
     *
     * @param formulas The formulas the disjunction consists of.
     */
    public Disjunction(Collection<? extends Formula> formulas) {
        super(formulas);
    }

    /**
     * This method uses some heuristics for removing redundant sub-formulas:
     * <ul>
     *  <li> A(x) OR B(x) OR     FALSE           -> A(x) OR B(x)
     *  <li> A(x) OR B(x) OR     (C(x) AND B(x)) -> A(x) OR B(x)
     *  <li> A(x) OR B(x) OR NOT (C(x) OR  B(x)) -> A(x) OR B(x) OR NOT ( C(x) )
     * </ul>
     */
    @Override
    protected void removeRedundantFormulas() {
        Set<PredicateAtom> pAtoms = filterByClass(this.formulas, PredicateAtom.class, Collectors.toSet());
        List<Formula> newFormulas = this.formulas.stream()
                .filter(f -> {
                    f = removeContainer(f, Exist.class);
                    return !(f instanceof Conjunction && ((Conjunction) f).containsAny(pAtoms));
                })
                .map(f -> {
                    Consumer<Formula> removeAtoms = sf -> {
                        if (sf instanceof Disjunction) ((Disjunction) sf).removeAll(pAtoms);
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
                .filter(f -> !f.isContradiction())
                .collect(Collectors.toList());
        this.clear();
        this.addAll(newFormulas);
    }

    @Override
    public boolean isTautology() {
        for (Formula sf : this) {
            if (sf.isTautology()) return true;
        }
        return false;
    }

    @Override
    public boolean isContradiction() {
        for (Formula sf : this) {
            if (!sf.isContradiction()) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        if (this.isEmpty()) return False.getInstance().toString();
        return toString("OR");
    }

    @Override
    public Disjunction clone() {
        return (Disjunction) super.clone();
    }

}
