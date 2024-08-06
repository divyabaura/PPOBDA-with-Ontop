package ppmappingcompiler.policy;

import ppmappingcompiler.fol.*;
import ppmappingcompiler.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A conjunctive query is of the form:
 * {@code Q() :- C(x), R(x, y), J(x, "abc") .}<br>
 * with:
 * <ul>
 *  <li> {@link PredicateAtom predicate atoms}   = {@code { C(x), R(x, y), J(x, "abc") }}
 *  <li> {@link Variable variables} = {@code { x, y }}
 *  <li> {@link Constant constants} = {@code { "abc" }}
 * </ul>
 * It may also contain inequalities, e.g., "{@code T(x,a), T(x,b), T(x,c), a != b, a != c, b != c}".
 */
@SuppressWarnings("unused")
public class ConjunctiveQuery implements Cloneable {

    protected final FlatConjunction body = new FlatConjunction();

    public ConjunctiveQuery(Collection<? extends Atom> atoms) {
        this.body.addAll(atoms);
        bindVariablesToQuery();
    }

    private void bindVariablesToQuery() {
        this.body.bindVariablesToQuery(this);
    }

    /**
     * This method returns all the CQ atoms.
     */
    public Set<Atom> getAtoms() {
        return this.body.getAtoms();
    }

    /**
     * This method returns a {@link Conjunction conjunction} of all the CQ atoms.
     */
    public Conjunction getBody() {
        return this.body.unflat();
    }

    public void explicitNondistinguishedNonsharedVariables() {
        this.explicitNondistinguishedNonsharedVariables(Formula.LC_LATIN_ALPHABET);
    }

    /**
     * This method make all the variables explicit according to a given alphabet.<br>
     * E.g., {@code A(x,_), B(_,y) -> A(x,z), B(w,y)}
     *
     * @param alphabet A set of allowed variable names.
     */
    public void explicitNondistinguishedNonsharedVariables(@Nonnull Set<String> alphabet) {
        assert !alphabet.isEmpty();

        Set<String> reservedNames = getVariables()
                .stream()
                .map(Variable::getName)
                .collect(Collectors.toSet());

        this.body.explicitVariables(alphabet, reservedNames);
    }

    /**
     * This method returns ALL the CQ's {@link PredicateAtom predicate atoms}, regardless they are (or not) part of a number restriction.
     */
    public Set<? extends PredicateAtom> getPredicateAtoms() {
        return Utils.filterByClass(getAtoms(), PredicateAtom.class, Collectors.toSet());
    }

    /**
     * This method returns all the CQ's {@link StrictInequality inequalities}.
     */
    public Set<StrictInequality> getInequalities() {
        return this.body.getInequalities();
    }

    @SuppressWarnings("unused")
    public Set<PredicateAtom> getPredicateAtomsByPredicate(String predicateIdentifier) {
        return getPredicateAtoms().stream()
                .filter(a -> a.getPredicateIdentifier().equals(predicateIdentifier))
                .map(PredicateAtom::clone)
                .collect(Collectors.toSet());
    }

    /**
     * This method checks if the CQ contains or not a given predicate.
     *
     * @return {@code true} if at least one of the CQ atoms have the specified predicate, {@code false} otherwise.
     */
    public boolean hasPredicate(String predicateIdentifier) {
        for (PredicateAtom a : getPredicateAtoms()) {
            if (a.getPredicateIdentifier().equals(predicateIdentifier)) return true;
        }
        return false;
    }

    public int size() {    //TODO: what's the best way of representing the size of a CQ?
        return getAtoms().size();
    }

    public void replaceVariable(Variable oldVar, Variable newVar) {
        this.body.replaceVariables(Collections.singletonMap(oldVar, newVar));
    }

    /**
     * This method returns all the variables used by the CQ atoms.
     *
     * @return A Set of variables.
     */
    public Set<Variable> getVariables() {
        Set<Variable> variables = new HashSet<>();
        getAtoms().stream()
                .map(Atom::getVariables)
                .forEach(variables::addAll);
        return variables;
    }

    public boolean isInconsistent() {
        return new FlatConjunction(getAtoms()).isContradiction();
    }

    @Override
    public String toString() {
        List<String> atomStrings = getAtoms().stream()
                .map(Atom::toString)
                .collect(Collectors.toList());
        return "Q() :- " + String.join(", ", atomStrings) + ".";
    }

    @Override
    public ConjunctiveQuery clone() {
        try {
            super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        Collection<Atom> atoms = getAtoms();
        atoms.forEach(Formula::unbindVariables);
        return new ConjunctiveQuery(atoms);
    }

    /**
     * Two CQs are equal if they are the same object.<br>
     * This is fundamental, since two variables must be bound to the same query for being considered equal.<br>
     * Furthermore, inspecting CQ content in this method may cause a stack-overflow error.
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * Two CQs are equal if they are the same object.<br>
     * This is fundamental, since two variables must be bound to the same query for being considered equal.<br>
     * Furthermore, inspecting CQ content in this method may cause a stack-overflow error.
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

}
