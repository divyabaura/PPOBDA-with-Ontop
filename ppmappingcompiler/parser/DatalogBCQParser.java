package ppmappingcompiler.parser;

import com.google.common.collect.ImmutableMap;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import ppmappingcompiler.Logger;
import ppmappingcompiler.fol.*;
import ppmappingcompiler.fol.PredicateAtom.PredicateArityException;
import ppmappingcompiler.fol.Term.TermTypeException;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;
import ppmappingcompiler.util.OntologyUtils;
import ppmappingcompiler.util.Utils;

import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.semanticweb.owlapi.vocab.OWL2Datatype.*;
import static ppmappingcompiler.fol.OntologyPredicateAtom.Type.*;
import static ppmappingcompiler.util.OntologyUtils.explicitIRIPrefix;

public class DatalogBCQParser extends BCQParser {

    private static final Map<OWL2Datatype, String> DATA_CONSTANT_PATTERNS = ImmutableMap.of(
            XSD_INTEGER, "[-+]?\\d+",
            XSD_DECIMAL, "\\d+\\.\\d+",
            XSD_BOOLEAN, "(true|false)",
            XSD_STRING, STRING_PATTERN
    );

    private static final String MASTRO_INEQUALITY_PREDICATE = "_not_equal";
    private static final String OBJECT_CONSTANT_PATTERN = String.format("IRI_FUNCT_\\d+ *\\( *%s *\\)", STRING_PATTERN);
    private static final String DATA_CONSTANT_PATTERN = String.join("|", DATA_CONSTANT_PATTERNS.values());
    private static final String VARIABLE_PATTERN = "_|[a-zA-Z]\\w*";
    private static final String CONSTANT_PATTERN = String.format("(?:%s|%s)", OBJECT_CONSTANT_PATTERN, DATA_CONSTANT_PATTERN);
    private static final String TERM_PATTERN = String.format("(?:%s|%s)", CONSTANT_PATTERN, VARIABLE_PATTERN);
    private static final String STRICT_INEQUALITY_PATTERN = String.format("(?:%s *\\( *(\\w+) *, *(\\w+) *\\)|not *\\( *(\\w+) *= *(\\w+) *\\)|(\\w+) *[!\\\\]= *(\\w+))", MASTRO_INEQUALITY_PREDICATE);
    private static final String EQUALITY_PATTERN = String.format("(%s) *= *(%s)", TERM_PATTERN, TERM_PATTERN);
    private static final String TERMS_SEPARATOR_PATTERN = " *, *";
    private static final String PREDICATE_ATOM_PATTERN = String.format("((?!%s)[^\\s,()]+) *\\( *(%s(?:%s%s)*) *\\)", MASTRO_INEQUALITY_PREDICATE, TERM_PATTERN, TERMS_SEPARATOR_PATTERN, TERM_PATTERN);
    private static final String ATOM_PATTERN = String.format("(?:%s|%s)", PREDICATE_ATOM_PATTERN, STRICT_INEQUALITY_PATTERN);
    private static final String CQ_PATTERN = String.format("[Qq] *\\( *\\) *:- *(%s(?: *, *%s)*) *\\.", ATOM_PATTERN, ATOM_PATTERN);

    public DatalogBCQParser() {
        super();
    }

    public DatalogBCQParser(OWLOntology ontology) {
        super(ontology);
    }

    @Override
    public OntologyConjunctiveQuery OntologyConjunctiveQuery(String datalogClause) throws ParserException, TermTypeException {
        if (ontology == null) throw new ParserException("No ontology available. " +
                "The string cannot be parsed as OntologyConjunctiveQuery.");
        Matcher matcher = Pattern.compile("^ *" + CQ_PATTERN + " *$").matcher(datalogClause);
        if (!matcher.find())
            throw new ParserException("query", datalogClause);

        Set<Atom> atoms = new HashSet<>();
        // split by comma and iterate over atoms
        for (String s : matcher.group(1).split(" *, *(?![^(]*\\))")) {
            if (canBeParsedAs(s, OntologyPredicateAtom.class)) atoms.add(OntologyPredicateAtom(s));
            else if (canBeParsedAs(s, StrictInequality.class)) atoms.add(StrictInequality(s));
            else
                throw new RuntimeException("String '" + s + "' cant be parsed neither as ontology predicate atom nor as inequality.");
        }
        return new OntologyConjunctiveQuery(atoms);
    }

    @Override
    public ConjunctiveQuery ConjunctiveQuery(String datalogClause) throws ParserException {
        Matcher matcher = Pattern.compile("^ *" + CQ_PATTERN + " *$").matcher(datalogClause);
        if (!matcher.find())
            throw new ParserException("query", datalogClause);

        Set<Atom> atoms = new HashSet<>();
        // split by comma and iterate over atoms
        for (String s : matcher.group(1).split(" *, *(?![^(]*\\))")) {
            if (canBeParsedAs(s, PredicateAtom.class)) atoms.add(PredicateAtom(s));
            else if (canBeParsedAs(s, StrictInequality.class)) atoms.add(StrictInequality(s));
            else
                throw new RuntimeException("String '" + s + "' cant be parsed neither as predicate atom nor as inequality.");
        }
        return new ConjunctiveQuery(atoms);
    }

    @Override
    public OntologyPredicateAtom OntologyPredicateAtom(String datalogSource) throws ParserException {
        if (ontology == null)
            throw new ParserException("No ontology available. The string cannot be parsed as OntologyPredicateAtom.");

        // [FC] fix to handle default namespace
        String prefixExpandedDatalogSource = explicitIRIPrefix(datalogSource, ontology);
        if (prefixExpandedDatalogSource.equals(datalogSource)) {
            String namespace = OntologyUtils.getPrefixMap(ontology).get(":");
            if (namespace != null && !datalogSource.startsWith(namespace)) {
                prefixExpandedDatalogSource = namespace + datalogSource;
            }
        }

        PredicateAtom atom = PredicateAtom(prefixExpandedDatalogSource);

        String predicateIRI = atom.getPredicateIdentifier();
        if (!OntologyUtils.isValidIRI(predicateIRI) && !predicateIRI.contains(":")) {
            Logger.warn("Following IRI don't match the standard pattern: " + predicateIRI);
        }

        OntologyPredicateAtom.Type atomType = OntologyPredicateAtom.getType(predicateIRI, ontology);
        if (atomType != CONCEPT && atomType != ROLE && atomType != ATTRIBUTE)
            throw new ParserException("No element can be found in the given ontology for the following predicate: " + predicateIRI);
        else {
            try {
                return new OntologyPredicateAtom(predicateIRI, atom.getTerms(), atomType);
            } catch (URISyntaxException | PredicateArityException | TermTypeException e) {
                throw new ParserException(e.toString());
            }
        }
    }

    @Override
    public PredicateAtom PredicateAtom(String datalogSource) throws ParserException {
        Matcher datalogMatcher = Pattern.compile("^ *" + PREDICATE_ATOM_PATTERN + " *$").matcher(datalogSource);
        if (!datalogMatcher.matches()) {
            throw new ParserException("predicate atom", datalogSource);
        }
        String predicateName = datalogMatcher.group(1);
        List<Term> terms = new ArrayList<>();
        for (String s : datalogMatcher.group(2).split(TERMS_SEPARATOR_PATTERN)) {
            terms.add(Term(s));
        }
        return new PredicateAtom(predicateName, terms);
    }

    @Override
    public StrictInequality StrictInequality(String source) throws ParserException {
        Pattern pattern = Pattern.compile("^ *" + STRICT_INEQUALITY_PATTERN + " *$");
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            String left = Utils.coalesce(matcher.group(1), matcher.group(3), matcher.group(5));
            String right = Utils.coalesce(matcher.group(2), matcher.group(4), matcher.group(6));

            return new StrictInequality(Term(left), Term(right));
        } else {
            throw new ParserException(source);
        }
    }

    @Override
    public Equality Equality(String source) throws ParserException {
        Pattern pattern = Pattern.compile("^ *" + EQUALITY_PATTERN + " *$");
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            String left = matcher.group(1);
            String right = matcher.group(7);
            return new Equality(Term(left), Term(right));
        } else {
            throw new ParserException(source);
        }
    }

    @Override
    public DataConstant DataConstant(String datalogConstant) throws ParserException {
        for (Map.Entry<OWL2Datatype, String> entry : DATA_CONSTANT_PATTERNS.entrySet()) {
            OWL2Datatype datatype = entry.getKey();
            String value = Utils.getFirstMatchingGroup(entry.getValue(), datalogConstant);
            if (value != null) {
                if (datatype.equals(XSD_STRING)) {
                    value = Utils.unescape(value, datalogConstant.substring(0, 1));
                }
                return new DataConstant(value, datatype);
            }
        }
        throw new ParserException(datalogConstant + " is not a valid data constant.");
    }

    @Override
    public ObjectConstant ObjectConstant(String datalogConstant) throws ParserException {
        String iri = Utils.getFirstMatchingGroup(OBJECT_CONSTANT_PATTERN, datalogConstant);
        if (OntologyUtils.isValidIRI(iri)) return new ObjectConstant(iri);
        throw new ParserException(iri + " is not a valid object constant.");
    }

    /**
     * This method converts a datalog CQ string into a BCQ string.<br>
     * Example:
     * <ul>
     *  <li> input:  {@code Q(x,y) :- C(x), R(x,y) .}
     *  <li> output: {@code Q() :- C(x), R(x,y) .}
     * </ul>
     *
     * @param conjunctiveQueryString the CQ string to convert.
     * @return A new string without any variable in the query's head.
     */
    public static String toBCQ(String conjunctiveQueryString) {
        return conjunctiveQueryString.replaceAll("^(Q *\\()[^()]*(?=\\) *:-)", "$1");
    }

    @Override
    public Variable Variable(String variableString) throws ParserException {
        Matcher matcher = Pattern.compile(VARIABLE_PATTERN).matcher(variableString);
        if (!matcher.matches())
            throw new ParserException("\"" + variableString + "\" is not a valid name for a variable.");
        return new Variable(matcher.group(0));
    }

}
