package ppmappingcompiler.policy;

import ppmappingcompiler.fol.*;
import ppmappingcompiler.fol.Term.TermTypeException;
import ppmappingcompiler.util.Utils;

import java.util.*;
import java.util.stream.Collectors;

import static ppmappingcompiler.fol.OntologyPredicateAtom.Type.ATTRIBUTE;
import static ppmappingcompiler.fol.OntologyPredicateAtom.Type.ROLE;
import static ppmappingcompiler.fol.Term.Type.DATA;
import static ppmappingcompiler.fol.Term.Type.OBJECT;
import static ppmappingcompiler.util.Utils.setIntersection;

@SuppressWarnings("unused")
public class OntologyConjunctiveQuery extends ConjunctiveQuery {

    public OntologyConjunctiveQuery(Collection<? extends Atom> atoms) throws TermTypeException {
        super(atoms);
        variablesTypeCheck();
    }

    public boolean hasInequalities() {
        return this.getInequalities().size() > 0;
    }

    public Set<OntologyPredicateAtom> getAllPredicateAtoms() {
        return Utils.filterByClass(getAtoms(), OntologyPredicateAtom.class, Collectors.toSet());
    }

    public Set<OntologyPredicateAtom> getStandardPredicateAtoms() {
        return Utils.setDifference(getAllPredicateAtoms(), getInequalityJoiningPredicateAtom());
    }

    public Set<OntologyPredicateAtom> getInequalityJoiningPredicateAtom() {
        Set<Variable> inequalityVariables = getInequalityVariables();
        return this.getAllPredicateAtoms().stream()
                .filter(a -> !Collections.disjoint(a.getVariables(), inequalityVariables))
                .collect(Collectors.toSet());
    }

    public void removeInequalitiesAndTheirJoiningAtoms() {
        this.body.removeAll(getInequalityJoiningPredicateAtom());
        this.body.removeAll(getInequalities());
    }

    /**
     * This method returns all the variables used only for expressing number restrictions.<br>
     * E.g., given a CQ {@code A(x), R(x,y), R(x,z), y\=z}, it returns {@code {y,z}}.
     *
     * @return A {@link Set set} of {@link Variable variables}.
     */
    public Set<Variable> getInequalityVariables() {
        Set<Variable> inequalityVariables = new HashSet<>();
        getInequalities().stream()
                .map(StrictInequality::getVariables)
                .forEach(inequalityVariables::addAll);
        return inequalityVariables;
    }

    /**
     * This method returns all the variables used only for expressing number restrictions (NRs), divided by NR.<br>
     * E.g., given a CQ {@code R(x,y), R(x,z), R(x,w), R(a,x), R(b,x), y\=z, z\=w, y\=w, a\=b},
     * it returns {@code { {y,z,w}, {a,b} }}.<br>
     * It assumes that the sets of inequalities are transitively closed.
     *
     * @return A {@link Set set} of {@link Variable variables}.
     */
    public Set<Set<Variable>> getSameNRVariables() {
        Set<Set<Variable>> unequalVariables = getInequalities()
                .stream()
                .map(i -> new HashSet<>(i.getVariables()))
                .collect(Collectors.toSet());

        return Utils.mergeIntersectingSets(unequalVariables);
    }

    /**
     * This method makes additional semantic checks with respect to the language's syntactic ones.
     * 1. Every term appearing in a concept, in a role or in the first position of an attribute
     * must be an object variable or an object constant.
     * 2. Every term appearing in the second position of an Attribute must be a data variable or a data constant.
     *
     * @throws TermTypeException if one of the above conditions is violated.
     */
    private void variablesTypeCheck() throws TermTypeException {
        Set<Term> objectTerms = new HashSet<>();
        Set<Term> dataTerms = new HashSet<>();
        for (OntologyPredicateAtom a : getAllPredicateAtoms()) {
            a.getTerms().forEach(t -> {
                if (t.getType() == OBJECT) objectTerms.add(t);
            });
            a.getTerms().forEach(t -> {
                if (t.getType() == DATA) dataTerms.add(t);
            });
        }
        // undistinguished non-shared variables may appear both in data and object position
        Set<Term> intersection = setIntersection(objectTerms, dataTerms).stream()
                .filter(t -> !(t instanceof Variable && ((Variable) t).isUndistinguishedNonShared()))
                .collect(Collectors.toSet());
        if (!intersection.isEmpty()) throw new RuntimeException();

        for (OntologyPredicateAtom atom : this.getAllPredicateAtoms()) {
            for (int index = 0; index < atom.getArity(); index++) {
                Term t = atom.getTerm(index);
                if (t.getType() == Term.Type.UNDEFINED)
                    throw new RuntimeException();
                if ((index == 0 && t.getType() == DATA)
                        || (index == 1 && t.getType() == DATA && atom.getType() == ROLE)
                        || (index == 1 && t.getType() == OBJECT && atom.getType() == ATTRIBUTE))
                    throw new TermTypeException(String.format(
                            "Wrong type for data/object constant/variables in the following atom: %s.", this));
            }
        }
    }

    /**
     * [ISWC21] If comparison atoms are allowed:
     * <ul>
     *     <li> every variable belonging to an inequality occurs at least once in Pos(q)
     *    <li> every variable belonging to an inequality occurs only in attribute range positions
     * </ul>
     *
     * @throws UnsafePolicyException if one of the above conditions is violated.
     */
    public void numberRestrictionsSafetyCheck() throws UnsafePolicyException {
        // Numerical restriction axioms must be of the form
        // A_1, ..., A_k, (>=n_1 R), ..., (>=n_h R) -> ⊥

        Set<Variable> inequalityVariables = getInequalityVariables();
        if (!inequalityVariables.isEmpty()) {
            // Searching for the NR's main variable (i.e., the query's main variable)
            Variable mainVariable = null;
            for (OntologyPredicateAtom atom : getInequalityJoiningPredicateAtom()) {
                List<Variable> variables = atom.getVariables();

                if (variables.size() != 2) throw new UnsafePolicyException(this, String.format(
                        "Predicate atom %s seems to participate to a number restriction, " +
                                "therefore it should have two variables.", atom
                ));

                if (mainVariable == null) {
                    Variable var1 = variables.get(0);
                    Variable var2 = variables.get(1);
                    if (inequalityVariables.contains(var2)) mainVariable = var1;
                    if (inequalityVariables.contains(var1)) mainVariable = var2;
                }
            }

            // every predicate atom in the query must assert something about the main variable
            // and every variable that is not the main one can appear in only one predicate atom
            Set<Variable> variablesToBeConsumed = new HashSet<>(this.getVariables());    // non-main variables can be "consumed" only once
            for (OntologyPredicateAtom atom : this.getAllPredicateAtoms()) {
                boolean mainVariableFound = false;
                for (Variable v : atom.getVariables()) {
                    // main variable
                    if (v.equals(mainVariable)) {
                        if (!mainVariableFound) mainVariableFound = true;
                        else throw new UnsafePolicyException(
                                "Self-joins are not expressible in numerical restriction axioms."
                        );
                    }
                    // non-main variable
                    else if (!variablesToBeConsumed.contains(v))
                        throw new UnsafePolicyException(this, String.format(
                                "Variable %s is not the main one, " +
                                        "therefore it cannot appear multiple times.", v
                        ));
                    variablesToBeConsumed.remove(v);
                }
                if (!mainVariableFound)
                    throw new UnsafePolicyException(this, "If there is a number restriction about a main variable x, " +
                            "every other predicate atom in the query must assert something about x."
                    );
            }
            if (!variablesToBeConsumed.isEmpty())
                throw new UnsafePolicyException(this, String.format(
                        "Variables %s do not occur in any predicate atom.", variablesToBeConsumed
                ));

            // variables of standard predicate atoms do not occur in any NR inequality
            for (OntologyPredicateAtom atom : getStandardPredicateAtoms()) {
                for (StrictInequality si : getInequalities()) {
                    if (!Collections.disjoint(atom.getVariables(), si.getVariables())) {
                        throw new UnsafePolicyException(this, String.format(
                                "Predicate atom %s doesn't seem to participate to a number restriction, " +
                                        "therefore its variables cannot be part of an inequality.", atom
                        ));
                    }
                }
            }

            // NR predicate atoms have two variables, exactly one of which appearing in some inequality
            for (OntologyPredicateAtom atom : getInequalityJoiningPredicateAtom()) {
                if (atom.getType() != ROLE)
                    throw new UnsafePolicyException(this, String.format("Atom %s is not a role.", atom));

                int count = 0;
                for (Variable var : atom.getVariables()) {
                    if (inequalityVariables.contains(var)) count++;
                }
                if (count == 0) {
                    throw new UnsafePolicyException(this, String.format(
                            "Predicate atom %s seems to participate to a number restriction, " +
                                    "therefore its variables should be part of at least one inequality.", atom
                    ));
                }
                if (count > 1) {
                    throw new UnsafePolicyException(this, String.format(
                            "Only one variable for atom %s can be part of an inequality.", atom
                    ));
                }
            }

            // same-NR variables appear in atom with same predicate, same position and same main variable
            Map<Variable, String> variableToPredicate = new HashMap<>();    // condition violated by T(x,y), S(x,z), y\=z
            Map<Variable, Integer> variableToPosition = new HashMap<>();    // condition violated by T(x,y), T(z,x), y\=z
            Map<Variable, Variable> variableToMainVar = new HashMap<>();    // condition violated by T(x,y), T(w,z), y\=z
            for (Variable var : inequalityVariables) {
                int count = 0;
                for (OntologyPredicateAtom atom : getInequalityJoiningPredicateAtom()) {
                    for (int i = 0; i < 2; i++) {
                        Variable lVar = atom.getVariables().get(i);
                        if (lVar.equals(var)) {
                            count++;
                            variableToPredicate.put(var, atom.getPredicateIdentifier());
                            variableToPosition.put(var, i);
                            variableToMainVar.put(var, atom.getVariables().get((i + 1) % 2));
                        }
                    }
                }

                // NR variables appear in exactly one predicate atom
                if (count != 1) {
                    throw new UnsafePolicyException(this, String.format(
                            "Variable %s appears in an inequality, so it must be used by exactly one predicate atom.", var
                    ));
                }
            }
            for (StrictInequality si : getInequalities()) {
                Term t1 = si.getLeftTerm();
                Term t2 = si.getRightTerm();

                if (t1 instanceof Variable && t2 instanceof Variable) {
                    Variable var1 = (Variable) t1;
                    Variable var2 = (Variable) t2;

                    if (!variableToPredicate.get(var1).equals(variableToPredicate.get(var2))) {
                        throw new UnsafePolicyException(this, String.format(
                                "Variables of inequality %s should belong to atoms with same predicate.", si
                        ));
                    }

                    if (!variableToPosition.get(var1).equals(variableToPosition.get(var2))) {
                        throw new UnsafePolicyException(this, String.format(
                                "Variables of inequality %s should occur in the same position within the respective predicate atoms.", si
                        ));
                    }

                    if (!variableToMainVar.get(var1).equals(variableToMainVar.get(var2))) {
                        throw new UnsafePolicyException(this, String.format(
                                "Variables of inequality %s should belong to the same variable's number restriction.", si
                        ));
                    }
                }
            }

            for (Set<Variable> set : getSameNRVariables()) {
                for (Variable var1 : set) {
                    for (Variable var2 : set) {
                        if (!getInequalities().contains(new StrictInequality(var1, var2))
                                && !var1.equals(var2)) {
                            throw new UnsafePolicyException(this, "The set of inequalities which share some variables must be closed.");
                        }
                    }
                }
            }
        }
    }

    /**
     * [AIKE21] If there are one or more number restrictions about a main variable x:
     * <ul>
     *     <li> every predicate atom of the query must assert something about x;
     *     <li> every variable different from x can appear in only one predicate atom.
     *     <li> for each cluster of variables related by some inequality:
     *     <ul>
     *         <li> every variable must appear in exactly one role and in only once in it;
     *         <li> every variable must belong to a atom with same role predicate and same main variable;
     *         <li> every variable must always occur in the same position;
     *         <li> the set of inequalities must be closed (e.g., "a \= b, b \= c" is NOT valid),
     *         because inequality is not a transitive relation.
     *     </ul>
     * </ul>
     *
     * @throws UnsafePolicyException if one of the above conditions is violated.
     */
    public void comparisonAtomsSafetyCheck() throws UnsafePolicyException {
        //TODO: extend to all kind of inequalities (!=, <, >, <=, >=)
        Set<Variable> inequalityVariables = getInequalityVariables();
        for (Variable v : inequalityVariables) {
            boolean found = false;
            for (OntologyPredicateAtom atom : getAllPredicateAtoms()) {
                if (atom.hasVariable(v)) {
                    found = true;
                    if (atom.getType() != ATTRIBUTE || atom.getVariables().get(0).equals(v)) {
                        throw new UnsafePolicyException(this, "Variable " + v +
                                " belongs to an inequality but it doesn't appear in a safe attribute range position.");
                    }
                }
            }

            if (!found) throw new UnsafePolicyException(this, "Variable " + v +
                    " belongs to an inequality but it cannot be found in any predicate atom.");
        }
    }

    public String toSparql() {
		/* TODO: che farne?
		// forcing a variable to be bound if:
		// 1. it belongs to a NR atom
		// 2. it appears only once in the remaining predicate atoms
		// e.g., variable x must be bound for denial C(x), T(x,y), T(x,z), y \= z -> ⊥
		Set<Variable> inequalitiesVariables = getInequalityVariables();
		Set<Variable> boundVariables = new HashSet<>();
		getAllPredicateAtoms().forEach( pa -> {
			for (Variable var : inequalitiesVariables) {
				if (pa.hasVariable(var)) boundVariables.addAll(pa.getVariables());
			}
		});
		
		boundVariables.removeAll(inequalitiesVariables);
		
		String headBlock;
		if (boundVariables.size() == 0) headBlock = "ASK";
		else headBlock = "SELECT " + String.join(" ",
				boundVariables.stream().map(e -> "?" + e).collect(Collectors.toSet()));
		
		List<String> atomsList = this.standardPredicateAtoms.stream().map(OntologyPredicateAtom::toSparql).collect(Collectors.toList());
		return headBlock + " WHERE { " + String.join(". ", atomsList) + ". }";
		*/

        String triples = getAllPredicateAtoms().stream()
                .map(atom -> atom.toSparql() + ". ")
                .collect(Collectors.joining(" "));
        String filter = getInequalities().isEmpty() ? "" :
                String.format("FILTER (%s) ",
                        getInequalities().stream()
                                .map(StrictInequality::toSparql)
                                .collect(Collectors.joining(" && ")));

        return "ASK WHERE { "
                + triples
                + filter
                + "}";
    }

    @Override
    public OntologyConjunctiveQuery clone() {
        Collection<Atom> atoms = super.clone().getAtoms();
        atoms.forEach(Formula::unbindVariables);
        try {
            return new OntologyConjunctiveQuery(atoms);
        } catch (TermTypeException e) {
            throw new RuntimeException(e);
        }
    }

    public static class UnsafePolicyException extends Exception {
        public UnsafePolicyException(String message) {
            super(message);
        }

        public UnsafePolicyException(Object object, String message) {
            super(String.format(
                    "Found expression not allowed by the current policy language:\n\t%s\n%s", object.toString(), message));
        }
    }

}
