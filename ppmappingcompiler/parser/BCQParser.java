package ppmappingcompiler.parser;

import org.semanticweb.owlapi.model.OWLOntology;
import ppmappingcompiler.fol.*;
import ppmappingcompiler.fol.Term.TermTypeException;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public abstract class BCQParser {

    protected OWLOntology ontology;
    protected final static String STRING_PATTERN = "(?:\"((?:[^\"]|\\\\\")*)\"|'((?:[^']|\\\\')*)')";

    public static final Set<Class<? extends Cloneable>> parsableClasses = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            ConjunctiveQuery.class,
            Constant.class,
            Equality.class,
            PredicateAtom.class,
            StrictInequality.class,
            Term.class,
            Variable.class
    )));

    public abstract OntologyConjunctiveQuery OntologyConjunctiveQuery(String source) throws ParserException, TermTypeException;

    public abstract ConjunctiveQuery ConjunctiveQuery(String source) throws ParserException;

    public abstract DataConstant DataConstant(String source) throws ParserException;

    public abstract Equality Equality(String source) throws ParserException;

    public abstract OntologyPredicateAtom OntologyPredicateAtom(String source) throws ParserException;

    public abstract PredicateAtom PredicateAtom(String source) throws ParserException;

    public abstract ObjectConstant ObjectConstant(String source) throws ParserException;

    public abstract StrictInequality StrictInequality(String source) throws ParserException;

    public abstract Variable Variable(String source) throws ParserException;

    public BCQParser() {
    }

    public BCQParser(OWLOntology ontology) {
        this.ontology = ontology;
    }

    public static BCQParser guessParserFromSampleQuery(String query) {
        for (BCQParser parser : Arrays.asList(
                new DatalogBCQParser(),
                new SparqlBCQParser()
        )) {
            if (parser.canBeParsedAs(query, ConjunctiveQuery.class)) return parser;
        }
        throw new RuntimeException();
    }

    @SuppressWarnings("UnusedReturnValue")
    public <T> T parseAs(String source, Class<T> tClass) throws ParserException, TermTypeException {
        if (tClass == OntologyConjunctiveQuery.class) return tClass.cast(OntologyConjunctiveQuery(source));
        if (tClass == ConjunctiveQuery.class) return tClass.cast(ConjunctiveQuery(source));
        if (tClass == Constant.class) return tClass.cast(Constant(source));
        if (tClass == DataConstant.class) return tClass.cast(DataConstant(source));
        if (tClass == Equality.class) return tClass.cast(Equality(source));
        if (tClass == OntologyPredicateAtom.class) return tClass.cast(OntologyPredicateAtom(source));
        if (tClass == PredicateAtom.class) return tClass.cast(PredicateAtom(source));
        if (tClass == ObjectConstant.class) return tClass.cast(ObjectConstant(source));
        if (tClass == StrictInequality.class) return tClass.cast(StrictInequality(source));
        if (tClass == Term.class) return tClass.cast(Term(source));
        if (tClass == Variable.class) return tClass.cast(Variable(source));
        throw new RuntimeException("No valid class has been specified. Choose among one of the following:" +
                parsableClasses.stream().map(c -> "\n\t- " + c).collect(Collectors.toList()));
    }

    public <T> boolean canBeParsedAs(String source, Class<T> tClass) {
        try {
            parseAs(source, tClass);
        } catch (TermTypeException | ParserException e) {
            return false;
        }
        return true;
    }

    public Term Term(String source) throws ParserException {
        if (canBeParsedAs(source, Variable.class)) return Variable(source);
        else if (canBeParsedAs(source, Constant.class)) return Constant(source);
        else throw new ParserException("String " + source + " is not a valid variable or constant.");
    }

    public Constant Constant(String source) throws ParserException {
        if (canBeParsedAs(source, ObjectConstant.class)) return ObjectConstant(source);
        else if (canBeParsedAs(source, DataConstant.class)) return DataConstant(source);
        else throw new ParserException("String " + source + " is not a valid object or data constant.");
    }

}
