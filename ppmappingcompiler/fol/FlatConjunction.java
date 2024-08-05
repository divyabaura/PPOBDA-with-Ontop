package ppmappingcompiler.fol;

import ppmappingcompiler.util.Utils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A flat conjunction is a {@link Conjunction conjunction} consisting only of {@link Atom atoms}.<br>
 * It can be used for solving heuristically some reasoning tasks.
 */
@SuppressWarnings("unused")
public class FlatConjunction extends Conjunction {

    public FlatConjunction() {
    }

    public FlatConjunction(Collection<? extends Atom> atoms) {
        this.formulas.addAll(atoms);
    }

    @Override
    public boolean add(Formula atom) {
        if (atom instanceof Atom) return this.formulas.add(atom);
        else
            throw new RuntimeException("It is not possible to add non-atomic subformulas in a flat conjunction. Try to use the unflat() method first.");
    }

    /**
     * @return A generic {@link Conjunction conjunction} of possibly-non-atomic formulas.
     */
    public Conjunction unflat() {
        return new Conjunction(this.getAtoms());
    }

    /**
     * This method returns the conjunction's predicate atoms.
     *
     * @return A {@link Set set} of {@link PredicateAtom predicate atoms}.
     */
    public Set<Atom> getAtoms() {
        Set<Atom> resultSet = new HashSet<>();
        resultSet.addAll(getPredicateAtoms());
        resultSet.addAll(getEqualities());
        resultSet.addAll(getInequalities());
        return resultSet;
    }

    protected <T extends Atom> Set<T> getAtomsByClass(Class<? extends T> tClass) {
        return this.formulas.stream()
                .filter(a -> tClass.isAssignableFrom(a.getClass()))
                .map(Formula::clone)
                .map(tClass::cast)
                .collect(Collectors.toSet());
    }

    /**
     * This method returns the conjunction's predicate atoms.
     *
     * @return A {@link Set set} of {@link PredicateAtom predicate atoms}.
     */
    public Set<PredicateAtom> getPredicateAtoms() {
        return getAtomsByClass(PredicateAtom.class);
    }

    /**
     * This method returns the conjunction's equalities.
     *
     * @return A {@link Set set} of {@link Equality equalities}.
     */
    public Set<Equality> getEqualities() {
        return getAtomsByClass(Equality.class);
    }

    /**
     * This method returns the conjunction's inequalities.
     *
     * @return A {@link Set set} of {@link StrictInequality inequalities}.
     */
    public Set<StrictInequality> getInequalities() {
        return getAtomsByClass(StrictInequality.class);
    }

    /**
     * This method computes the equivalent terms of a given one.<br>E.g.:
     * <ul>
     *  <li> Conjunction: {@code x=y=z AND a=b AND y!=w}
     *  <li> Input: {@code y}
     *  <li> Output: {@code {x,y,z}}
     * </ul>
     *
     * @param term The comparison term.
     * @return A set of terms equivalent to the given one.
     */
    public Set<Term> getEquivalentTerms(Term term) {
        for (Set<Term> terms : getSetsOfEquivalentTerms()) {
            if (terms.contains(term)) return terms;
        }
        return new HashSet<>(Collections.singletonList(term));
    }

    /**
     * This method adds a new {@link PredicateAtom predicate atom} to the conjunction.
     *
     * @param predicateAtom The {@link PredicateAtom predicate atom} to add.
     */
    public void add(PredicateAtom predicateAtom) {
        this.formulas.add(predicateAtom);
    }

    /**
     * This method adds a new binary equality to the conjunction.
     *
     * @param term1 The first term of the equality.
     * @param term2 The second term of the equality.
     */
    public void addEquality(Term term1, Term term2) {
        this.add(new Equality(term1, term2));
    }

    /**
     * This method adds a set new inequalities to the conjunction.
     *
     * @param inequalities A Collection of inequalities (StrictInequality).
     */
    public void addInequalities(Collection<StrictInequality> inequalities) {
        this.addAll(inequalities);
    }

    /**
     * This method adds a new inequality to the conjunction.
     *
     * @param var1 The inequality's first term.
     * @param var2 The inequality's second term.
     */
    public void addInequality(Variable var1, Variable var2) {
        add(new StrictInequality(var1, var2));
    }

    /**
     * This method removes a specified inequality (if present) from the conjunction.
     *
     * @param inequality The inequality to be removed.
     * @return {@code true} if this set contained the specified element.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean removeInequality(StrictInequality inequality) {
        return this.formulas.remove(inequality);
    }

    /**
     * This method removes all the specified inequalities (if present) from the conjunction.
     *
     * @param inequalities A collection of inequalities to be removed.
     * @return {@code true} if this set changed as a result of the call.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean removeInequalities(Collection<StrictInequality> inequalities) {
        return this.formulas.removeAll(inequalities);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean removeInequalities(StrictInequality... inequalities) {
        return this.formulas.removeAll(Arrays.asList(inequalities));
    }

    /**
     * This method removes all the atoms containing such term.
     *
     * @param term The term that must be removed.
     */
    public void removeTerm(Term term) {
        this.saturate();
        this.formulas.removeIf(f -> f.getTerms().contains(term));
        this.optimize();
    }

    /**
     * This method returns a minimal set of equalities implied by this conjunction.
     *
     * @return A set of {@link Equality equalities}.
     */
    private Set<Equality> getMinimalSetOfEqualities() {
        Set<Equality> equalities = new HashSet<>();
        for (Set<Term> terms : getSetsOfEquivalentTerms()) {
            Iterator<Term> iterator = terms.iterator();
            Term prev = iterator.next();
            while (iterator.hasNext()) {
                Term succ = iterator.next();
                equalities.add(new Equality(prev, succ));
            }
        }
        return equalities;
    }

    private void optimizeEqualities() {
        Set<Equality> newEqualities = getMinimalSetOfEqualities();
        this.formulas.removeIf(a -> a instanceof Equality);
        this.addAll(newEqualities);
    }

    /**
     * This method tries to optimize the information contained in the conjunction.
     */
    public void optimize() {
        this.optimizeEqualities();
        super.optimize();
    }

    /**
     * This method returns the saturation for a given {@link PredicateAtom predicate atom},
     * i.e., it takes a preedicate atom and returns all the predicate atoms that are obtainable
     * applying one or more equalities belonging to the conjunction.
     *
     * @param predicateAtom The predicate atom whose saturation must be computed.
     * @return A {@link Set set} of {@link PredicateAtom predicate atoms}.
     */
    public Set<PredicateAtom> predicateAtomSaturation(PredicateAtom predicateAtom) {
        Collection<Term> terms = predicateAtom.getTerms();

        Set<List<Term>> combinations = new HashSet<>();
        combinations.add(new ArrayList<>());
        for (Term t : terms) {
            Set<List<Term>> tempCombinations = new HashSet<>();
            for (Term eqTerm : getEquivalentTerms(t)) {
                for (List<Term> c : combinations) {
                    List<Term> clonedC = new ArrayList<>(c);
                    clonedC.add(eqTerm);
                    tempCombinations.add(clonedC);
                }
            }
            combinations = tempCombinations;
        }

        Set<PredicateAtom> resultSet = new HashSet<>();
        for (List<Term> c : combinations) {
            PredicateAtom atom = predicateAtom.clone();
            try {
                atom.setTerms(c);
            } catch (PredicateAtom.PredicateArityException e) {
                throw new RuntimeException(e);
            }
            resultSet.add(atom);
        }
        return resultSet;
    }

    public void saturate() {
        this.formulas = new ArrayList<>(this.getSaturation());
    }

    /**
     * This method returns all the atoms implied by the conjunction.
     *
     * @return A {@link Set set} of {@link PredicateAtom predicate atoms}.
     */
    public Set<Atom> getSaturation() {
        Set<Atom> resultSet = new HashSet<>();
        resultSet.addAll(equalitiesSaturation());
        resultSet.addAll(predicateAtomsSaturation());
        resultSet.addAll(inequalitiesSaturation());
        return resultSet;
    }

    /**
     * This method returns the saturation of all the conjunction's predicate atoms.
     *
     * @return A {@link Set set} of {@link PredicateAtom predicate atoms}.
     */
    public Set<PredicateAtom> predicateAtomsSaturation() {
        Set<PredicateAtom> resultSet = new HashSet<>();
        getPredicateAtoms().forEach(atom -> resultSet.addAll(this.predicateAtomSaturation(atom)));
        return resultSet;
    }

    /**
     * This method returns the saturation for a given {@link StrictInequality inequality},
     * i.e., it takes an inequality and returns all the inequalities that are obtainable
     * applying one or more equalities belonging to the conjunction.
     *
     * @param inequality The inequality to saturate.
     * @return A {@link Set set} of {@link StrictInequality inequalities}.
     */
    public Set<StrictInequality> inequalitySaturation(StrictInequality inequality) {
        Term t1 = inequality.getLeftTerm();
        Term t2 = inequality.getRightTerm();

        Set<Term> t1EquivalentTerms = getEquivalentTerms(t1);
        Set<Term> t2EquivalentTerms = getEquivalentTerms(t2);

        Set<StrictInequality> resultSet = new HashSet<>();
        for (Term t1et : t1EquivalentTerms) {
            for (Term t2et : t2EquivalentTerms) {
                resultSet.add(new StrictInequality(t1et, t2et));
            }
        }

        return resultSet;
    }

    /**
     * This method returns the saturation of all the conjunction's inequalities.
     *
     * @return A {@link Set set} of {@link StrictInequality inequalities}.
     */
    public Set<StrictInequality> inequalitiesSaturation() {
        Set<StrictInequality> resultSet = new HashSet<>();
        getInequalities().forEach(i -> resultSet.addAll(this.inequalitySaturation(i)));
        return resultSet;
    }

    /**
     * This method returns the saturation of all the conjunction's inequalities.
     *
     * @return A {@link Set set} of {@link StrictInequality inequalities}.
     */
    public Set<Equality> equalitiesSaturation() {
        Set<Equality> equalities = new HashSet<>();
        for (Set<Term> equivalentTerms : getSetsOfEquivalentTerms()) {
            for (Term t1 : equivalentTerms) {
                for (Term t2 : equivalentTerms) {
                    if (!t1.equals(t2)) {
                        equalities.add(new Equality(t1, t2));
                    }
                }
            }
        }
        return equalities;
    }

    public Set<Set<Term>> getSetsOfEquivalentTerms() {
        return Utils.mergeIntersectingSets(getEqualities().stream()
                .map(e -> new HashSet<>(Arrays.asList(e.getLeftTerm(), e.getRightTerm())))
                .collect(Collectors.toSet()));
    }

    /**
     * This method verifies if a given {@link PredicateAtom predicate atom} is implied by the current conjunction.<br>
     * E.g., the atom {@code R(x,x)} is implied by the conjunction {@code R(x,y) AND x=y}.
     *
     * @param predicateAtom The {@link PredicateAtom predicate atom} whose implication must be checked.
     * @return {@code true} if the given predicate atom is implied by {@code this},
     * {@code false} otherwise.
     */
    public boolean implies(PredicateAtom predicateAtom) {
        if (this.isContradiction()) return true;
        return this.predicateAtomsSaturation().contains(predicateAtom);
    }

    /**
     * This method verifies if a given {@link Equality equality} is implied by the current conjunction.<br>
     * E.g., the equality {@code b=c} is implied by the conjunction {@code a=b=c=d AND e=f}.
     *
     * @param equality The {@link Equality equality} whose implication must be checked.
     * @return {@code true} if the given equality is implied by {@code this},
     * {@code false} otherwise.
     */
    public boolean implies(Equality equality) {
        if (this.isContradiction()) return true;
        for (Set<Term> equivalentTerms : getSetsOfEquivalentTerms()) {
            if (equivalentTerms.containsAll(equality.getTerms())) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method verifies if a given {@link StrictInequality inequality} is implied by the current conjunction.<br>
     * E.g., the inequality {@code a!=c} is implied by the conjunction {@code a=b AND b!=c}.
     *
     * @param inequality The {@link StrictInequality inequality} whose implication must be checked.
     * @return {@code true} if the given inequality is implied by {@code this},
     * {@code false} otherwise.
     */
    public boolean implies(StrictInequality inequality) {
        if (this.isContradiction()) return true;
        return this.inequalitiesSaturation().contains(inequality);
    }

    /**
     * This method verifies if a given conjunction is implied by the current one.<br>
     * E.g., the conjunction {@code a=b AND a!=c} is implied by {@code a=b AND b!=c AND d!=e}.<br>
     * β is implied by α iff, for each atom B of β, B is contained in the set of all the possible atoms
     * obtainable applying α's equalities to all the other α's atoms.
     *
     * @param conjunction2 The conjunction whose implication must be checked.
     * @return {@code true} if the given conjunction is implied by {@code this},
     * {@code false} otherwise.
     */
    public boolean implies(FlatConjunction conjunction2) {
        // inconsistent conjunctions (i.e., always false) imply everything
        // tautological conjunction (i.e., always true) are implied by everything
        if (this.isContradiction() || conjunction2.isTautology()) return true;

        // all the predicate atoms must be implied by this
        for (PredicateAtom predicateAtom : conjunction2.getPredicateAtoms()) {
            if (!this.implies(predicateAtom)) return false;
        }

        // all the equalities must be implied by at least one equality of this
        for (Equality equality : conjunction2.getEqualities()) {
            if (!this.implies(equality)) return false;
        }

        // all the inequalities must be implied by this
        for (StrictInequality inequality : conjunction2.getInequalities()) {
            if (!this.implies(inequality)) return false;
        }

        return true;
    }

    /**
     * The method returns {@code true} iff {@code this} implies the input conjunction under some substitution
     * of variables with terms.<br>
     * Let α be {@code this} and β be the input formula. The function checks whether there exists a substitution
     * τ of β's variables which do not appear in α such that τ(β) is implied by α.<br>
     * E.g., {@code R(z,z), C(z)} implies {@code R(x,y), R(y,z)} under the substitution {@code τ={x\z, y\z}},
     * because {@code τ(R(x,y), R(y,z)) = R(z,z)} is contained in {@code R(z,z), C(z)}.
     *
     * @param conjunction2 The conjunction whose implication must be checked.
     * @return {@code true} only if the input formula is implied by {@code this} under some substitution of
     * variables with terms.
     */
    public boolean impliesUnderSomeSubstitution(FlatConjunction conjunction2) {
        // inconsistent conjunctions (i.e., always false) imply everything
        // tautological conjunctions (i.e., always true) are implied by everything
        if (this.isContradiction() || conjunction2.isTautology()) return true;

        Set<Term> c1Terms = this.getTerms();
        Set<Variable> c2Vars = conjunction2
                .getPredicateAtoms().stream()
                .flatMap(a -> a.getVariables().stream())
                .filter(v -> !c1Terms.contains(v))
                .collect(Collectors.toSet());

        for (List<Term> combination : Utils.combinationsWithRepetitions(c1Terms, c2Vars.size())) {
            Map<Variable, Term> tau = new HashMap<>();
            int count = 0;
            for (Variable v : c2Vars) {
                tau.put(v, combination.get(count++));
            }
            FlatConjunction clonedC2 = conjunction2.clone();
            clonedC2.replaceVariables(tau);
            if (this.implies(clonedC2)) return true;
        }

        return false;
    }

    /**
     * Checks if the conjunction is a tautology, i.e., if it consists only of tautological equalities.
     *
     * @return {@code true} if {@code this} is consistent, {@code false} otherwise (or if it cannot be determined).
     */
    @Override
    public boolean isTautology() {
        for (Set<Term> s : getSetsOfEquivalentTerms()) {
            if (s.size() > 1) return false;
        }
        return getPredicateAtoms().isEmpty() && getInequalities().isEmpty();
    }

    /**
     * Checks if the conjunction is a contradiction, i.e., if:<br>
     * <ul>
     *  <li> no equality is contradicted by an inequality;
     *  <li> no equality contains two distinct constants;
     *  <li> no inequality compares the same term.
     * </ul>
     *
     * @return {@code true} if {@code this} is a contradiction, {@code false} otherwise.
     */
    @Override
    public boolean isContradiction() {
        for (Set<Term> equivalentTerms : getSetsOfEquivalentTerms()) {
            // no equality is contradicted by an inequality
            for (StrictInequality inequality : getInequalities()) {
                if (equivalentTerms.containsAll(inequality.getTerms())) return true;
            }

            //no set of equivalent terms contains two or more constants
            if (Utils.filterByClass(equivalentTerms, Constant.class, Collectors.toSet()).size() > 1) return true;
        }

        // no inequality compares the same term
        for (StrictInequality inequality : getInequalities()) {
            if (inequality.right.equals(inequality.left)) return true;
        }

        return false;
    }

    public static FlatConjunction join(FlatConjunction conjunction1, FlatConjunction conjunction2) {
        FlatConjunction conj = new FlatConjunction();
        conjunction1.getAtoms().forEach(conj::add);
        conjunction2.getAtoms().forEach(conj::add);
        conj.optimize();
        return conj;
    }

    /**
     * This method checks if the given inequality makes the conjunction inconsistent.
     *
     * @return {@code true} if the conjunction is consistent with the given inequality, {@code false} otherwise.
     */
    public boolean consistentWith(StrictInequality inequality) {
        for (Set<Term> equivalentTerms : getSetsOfEquivalentTerms()) {
            if (equivalentTerms.containsAll(inequality.getTerms())) return false;
        }
        return true;
    }

    /**
     * This method checks if at least one of the given inequalities make the conjunction inconsistent.
     *
     * @return {@code true} if the conjunction is consistent with all the given inequalities, {@code false} otherwise.
     */
    public boolean consistentWith(Collection<StrictInequality> inequalities) {
        for (StrictInequality i : inequalities) {
            if (!consistentWith(i)) return false;
        }
        return true;
    }

    @Override
    public FlatConjunction clone() {
        return (FlatConjunction) super.clone();
    }

}
