package ppmappingcompiler;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import ppmappingcompiler.fol.*;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class allows to get a Sparql query that is equivalent to a FO formula represented by an instance of {@link Formula}.
 *
 * <ul>
 *     <li><i>Distinguished variable</i>: variable occurring outside the scope of a query.
 *     <li><i>Requested variable</i>: variable that must appear in the SELECT statement of a formula.
 *     <li><i>Shared variable</i>: variable occurring in more than one member of a conjunction.
 * </ul>
 */

public class SparqlCompiler {

    public static String convertToSPARQL(Formula formula) {
        SparqlRewriting rewriting = convertFormulaToSPARQL(formula);

        String varList = rewriting.variables.stream()
                .map(v -> "?" + v)
                .collect(Collectors.joining(" "));

        return "SELECT DISTINCT " + varList + " {\n" + rewriting.string + "\n}";

    }

    private static SparqlRewriting convertFormulaToSPARQL(Formula formula) {
        if (formula instanceof Conjunction) {
            return convertConjunctionToSPARQL((Conjunction) formula);
        } else if (formula instanceof Disjunction) {
            return convertDisjunctionToSPARQL((Disjunction) formula);
        } else if (formula instanceof Negation) {
            return convertNegationToSPARQL((Negation) formula);
        } else if (formula instanceof Exist) {
            return convertExistToSparql((Exist) formula);
        } else if (formula instanceof OntologyPredicateAtom) {
            return convertPredicateAtomToSPARQL((OntologyPredicateAtom) formula);
        }
        throw new Error();
    }

    private static SparqlRewriting convertConjunctionToSPARQL(Conjunction conjunction) {
        Set<Formula> subFormulas = conjunction.getFormulas();

        // Separate positive and negative subformulas
        List<Formula> positiveFormulas = new ArrayList<>();
        List<Formula> negativeFormulas = new ArrayList<>();

        for (Formula subFormula : subFormulas) {
            if (subFormula instanceof Negation) {
                negativeFormulas.add(subFormula);
            } else {
                positiveFormulas.add(subFormula);
            }
        }

        // Convert positive subformulas to SPARQL
        StringBuilder sparqlQuery = new StringBuilder();
        Set<String> projectedVars = new HashSet<>();
        boolean first = true;

        for (Formula Formula : Iterables.concat(positiveFormulas, negativeFormulas)) {
            SparqlRewriting positiveFormulaRewriting = convertFormulaToSPARQL(Formula);
            sparqlQuery.append(first ? "" : "\n");
            sparqlQuery.append(positiveFormulaRewriting.string);
            first = false;
            projectedVars.addAll(positiveFormulaRewriting.variables);
        }

        return new SparqlRewriting(sparqlQuery.toString(), projectedVars);
    }


    private static SparqlRewriting convertDisjunctionToSPARQL(Disjunction disjunction) {
        Set<Formula> subFormulas = disjunction.getFormulas();
        StringBuilder sparqlQuery = new StringBuilder();
        Set<String> projectedVars = new HashSet<>();
        boolean first = true;

        for (Formula subFormula : subFormulas) {

            SparqlRewriting subFormulaRewriting = convertFormulaToSPARQL(subFormula);
            sparqlQuery.append(first ? "" : " UNION ");
            sparqlQuery.append("{ ").append(subFormulaRewriting.string).append(" }");
            first = false;
            projectedVars.addAll(subFormulaRewriting.variables);
        }

        return new SparqlRewriting(sparqlQuery.toString(), projectedVars);
    }

    private static SparqlRewriting convertNegationToSPARQL(Negation negation) {
        Formula content = negation.getContent();
        SparqlRewriting contentRewriting = convertFormulaToSPARQL(content);
        String sparqlQuery = "MINUS { " + contentRewriting.string + " }";
        Set<String> projectedVars = new HashSet<>(contentRewriting.variables);

        return new SparqlRewriting(sparqlQuery, projectedVars);
    }

    public static SparqlRewriting convertExistToSparql(Exist formula) {

        SparqlRewriting contentRewriting = convertFormulaToSPARQL(formula.getContent());
        Set<String> vars = new HashSet<>(contentRewriting.variables); // copy of all variable names produced by the child

        for (Variable existentiallyQuantifiedVar : formula.getQuantifiedVariables()) {
            vars.remove(existentiallyQuantifiedVar.getName()); // remove existentially quantified variables
        }
        if (vars.isEmpty()){
            String sparqlQuery = "{ SELECT DISTINCT (1 AS ?dummy) { " + contentRewriting.string + " }}";
            return new SparqlRewriting(sparqlQuery, vars);
        }

        String varList = vars.stream().map(v -> "?" + v).collect(Collectors.joining(" ")); // "?x ?y ?z"
        String sparqlQuery = " { SELECT DISTINCT " + varList + " { " + contentRewriting.string + " }}";

        return new SparqlRewriting(sparqlQuery, vars);
    }



    private static SparqlRewriting convertPredicateAtomToSPARQL(OntologyPredicateAtom predicateAtom) {
        String predicateIRI = predicateAtom.getPredicateIRI();
        Term[] arguments = predicateAtom.getTerms().toArray(new Term[0]);

        if (arguments.length == 1) {
            // Conversion for Concepts
            Variable variable = (Variable) arguments[0];
            String var = variable.getName();

            String sparqlString = "?" + var + " a <" + predicateIRI + "> .";
            Set <String> projectedVars = ImmutableSet.of(var);
            return new SparqlRewriting(sparqlString, projectedVars);

        } else if (arguments.length == 2) {
            // Conversion for roles
            Variable subject = (Variable) arguments[0];
            Variable object = (Variable) arguments[1];

            String subjectVar = subject.getName();
            String objectVar = object.getName();

            String sparqlString = "?" + subjectVar + " <" + predicateIRI + "> ?" + objectVar + " .";
            Set <String> projectedVars = ImmutableSet.of(subjectVar, objectVar);
            return new SparqlRewriting(sparqlString, projectedVars);
        }
        throw new Error();
    }



    public static final class SparqlRewriting {

        public final String string; // the SPARQL expression corresponding to a formula

        public final Set<String> variables; // the SPARQL variables projected out by the SPARQL expression\

        public SparqlRewriting(String string, Iterable<String> variables) {
            this.string = Objects.requireNonNull(string);
            this.variables = ImmutableSet.copyOf(variables);
        }

    }


}




