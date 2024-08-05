package ppmappingcompiler.fol;

import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.util.Utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A predicate atom establishes a relationship among one or more {@link Term terms} by means of a predicate ({@link String}).
 */
@SuppressWarnings("unused")
public class PredicateAtom extends Atom {

    protected final String predicateName;
    protected List<Term> terms;    // the order matters!

    public PredicateAtom(String predicateName, List<? extends Term> terms) {
        this.predicateName = predicateName;
        this.terms = terms.stream().map(t -> (Term) t).collect(Collectors.toList());
    }

    public int getArity() {
        return terms.size();
    }

    /**
     * This method returns the atom's predicate name.<br>
     * E.g., given the atom {@code http://example.org/#R(x,y)}, it returns {@code R}.
     */
    public String getPredicateName() {
        return this.predicateName;
    }

    /**
     * This method returns a string representing an identifier for atom's predicate.<br>
     * In the case of ontologies, it returns the predicate's IRI.<br>
     * E.g., given the atom {@code http://example.org/#R(x,y)}, it returns {@code http://example.org/#R}.
     */
    public String getPredicateIdentifier() {
        return this.predicateName;
    }

    public void setTerms(List<Term> terms) throws PredicateArityException {
        if (terms.size() != this.getArity())
            throw new PredicateArityException("When setting new terms for a PredicateAtom you cannot change its arity!");
        this.terms = terms;
    }

    /**
     * This method returns the {@link Term terms} occurring in this atom.<br>
     * It is overridden for returning a {@link List list}, in order to:
     * <ul>
     *  <li> preserving the terms' order (which matters);
     *  <li> allowing duplicates (e.g., consider the atom {@code R(x,x)}).
     * </ul>
     *
     * @return A {@link List list} of {@link Term terms}.
     */
    @Override
    public List<Term> getTerms() {
        return this.terms.stream()
                .map(Term::clone)
                .collect(Collectors.toList());
    }

    /**
     * This method returns the {@link Variable variables} occurring in this atom.<br>
     * It is overridden for preserving the variables order.
     *
     * @return A {@link List list} of {@link Variable variables}.
     */
    @Override
    public List<Variable> getVariables() {
        return Utils.filterByClass(getTerms(), Variable.class, Collectors.toList());
    }

    @Override
    public boolean isTautology() {
        return false;
    }

    @Override
    public boolean isContradiction() {
        return false;
    }

    @Override
    public void unbindVariables() {
        terms.forEach(t -> {
            if (t instanceof Variable) ((Variable) t).unbind();
        });
    }

    public boolean unifiesWith(PredicateAtom otherPredicateAtom) {
        return getUnificationMapping(otherPredicateAtom) != null;
    }

    /**
     * This method returns a homomorphism (if any) μ of the variables occurring in the other atom (say β)
     * to the terms occurring in the current atom (say α) such that μ(β)=α.
     *
     * @return A {@link Map map} from the variables of {@code this} to the terms of {@code otherPredicateAtom}.<br>
     * The return value is an empty map if the two atoms are equal.<br>
     * The return value is {@code null} if the two atoms are not unifiable.
     */
    @Nullable
    public <T extends PredicateAtom> Map<Variable, Term> getUnificationMapping(T otherAtom) {
        if (!this.isSamePredicate(otherAtom)
                || this.getArity() != otherAtom.getArity()) return null;
        Map<Variable, Term> unificationMapping = new HashMap<>();

        // term 1
        Term t1_a = this.getTerm(0);
        Term t1_b = otherAtom.getTerm(0);
        if (t1_a instanceof Constant && !t1_a.equals(t1_b)) return null;
        if (t1_a instanceof Variable && !t1_a.equals(t1_b)) unificationMapping.put((Variable) t1_a, t1_b);

        // term 2
        if (this.getArity() == 2) {
            Term t2_a = this.getTerm(1);
            Term t2_b = otherAtom.getTerm(1);
            if ((t2_a instanceof Constant && !t2_a.equals(t2_b))
                    || (t1_a.equals(t2_a) && !t1_b.equals(t2_b))) return null;
            if (t2_a instanceof Variable && !t2_a.equals(t2_b)) unificationMapping.put((Variable) t2_a, t2_b);
        }

        return unificationMapping;
    }

    public Term getTerm(int index) {
        return this.terms.get(index).clone();
    }

    public void bindVariablesToQuery(ConjunctiveQuery cq) {
        terms.forEach(t -> {
            if (t instanceof Variable) ((Variable) t).bindToQuery(cq);
        });
    }

    /**
     * This methods remove constants from the atom's terms replacing them from fresh variables
     * and returns the implied equalities with the new variables.<br>
     * Example:
     * <ul>
     *  <li> input:  {@code Color(x,'yellow')}
     *  <li> output: {@code ∃y(Color(x,y) AND y = 'yellow')}
     * </ul>
     * It analogously separates variables from self-joining atoms (e.g., {@code A(x,x,y)} becomes
     * {@code ∃z(A(x,z,y) AND x=z)}).<br>
     * The method does not make any side effect on the atom, but
     * it does side effect on the collection of reserved variable names.
     */
    public Formula explode(Set<String> variablesAlphabet, Set<String> reservedNames, boolean updateReservedNames) {
        PredicateAtom newPredicateAtom = this.clone();
        Set<Variable> newVariables = new HashSet<>();
        Set<Equality> equalities = new HashSet<>();
        Set<Variable> foundVariables = new HashSet<>();

        for (int index = 0; index < newPredicateAtom.getArity(); index++) {
            Term term = newPredicateAtom.getTerm(index);
            if (term instanceof Variable && !foundVariables.contains(term)) {
                foundVariables.add((Variable) term);
            } else {
                Variable newVar = Formula.getFreshVariable(variablesAlphabet, reservedNames, updateReservedNames);
                newVariables.add(newVar);
                newPredicateAtom.terms.set(index, newVar);
                equalities.add(new Equality(term, newVar));
            }
        }

        Conjunction conj = new Conjunction();
        conj.add(newPredicateAtom);
        conj.addAll(equalities);
        if (conj.size() == 1) return newPredicateAtom;
        else return new Exist(conj, newVariables);
    }

    public Formula explode(Set<String> variablesAlphabet) {
        return explode(variablesAlphabet, this.getActiveVariablesAlphabet(), false);
    }

    @Override
    public boolean replaceVariable(Variable oldVar, Term newTerm) {
        return Collections.replaceAll(this.terms, oldVar, newTerm);
    }

    public boolean hasVariable(Variable var) {
        return this.terms.contains(var);
    }

    public boolean isSamePredicate(PredicateAtom atom) {
        return getPredicateIdentifier().equals(atom.getPredicateIdentifier())
                && getArity() == atom.getArity();
    }

    @Override
    public PredicateAtom clone() {
        PredicateAtom clone = (PredicateAtom) super.clone();
        clone.terms = this.terms.stream().map(Term::clone).collect(Collectors.toList());
        return clone;
    }

    @Override
    public int hashCode() {
        return (getClass().toString() + getPredicateIdentifier() + Objects.hash(terms)).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        PredicateAtom atom = (PredicateAtom) obj;
        return this.isSamePredicate(atom) && this.terms.equals(atom.terms);
    }

    public String toSparql() {
        String subject = this.getTerm(0).toSparql(),
                predicate,
                object;
        switch (this.getArity()) {
            case 1:
                predicate = "a";
                object = "<" + getPredicateIdentifier() + ">";
                break;
            case 2:
                predicate = "<" + getPredicateIdentifier() + ">";
                object = this.getTerm(1).toSparql();
                break;
            default:
                throw new IllegalStateException();
        }
        return String.format("%s %s %s", subject, predicate, object);
    }

    @Override
    public String toString() {
        return String.format("%s(%s)",
                this.predicateName,
                this.terms.stream().map(Object::toString).collect(Collectors.joining(", "))
        );
    }

    public static class PredicateArityException extends Exception {
        public PredicateArityException(String s) {
            super(s);
        }
    }


    /*===================*
     * AUXILIARY METHODS *
     *===================*/

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        for (int i = 0; i < terms.size(); i++) {
            Term t = terms.get(i);
            if (t instanceof Variable && substitution.containsKey(t)) {
                terms.set(i, substitution.get(t));
            }
        }
    }

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        for (int index = 0; index < this.terms.size(); index++) {
            Term term = this.terms.get(index);
            if (term instanceof Variable) {
                Variable oldVar = ((Variable) term);
                if (oldVar.isUndistinguishedNonShared()) {
                    String newVarName = Formula.getFreshVariableName(alphabet, reservedNames);
                    Variable newVar = oldVar.clone();
                    newVar.setName(newVarName);
                    this.terms.set(index, newVar);
                    reservedNames.add(newVarName);
                }
            }
        }
    }

}
