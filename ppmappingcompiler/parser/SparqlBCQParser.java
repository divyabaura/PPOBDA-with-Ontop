package ppmappingcompiler.parser;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import ppmappingcompiler.fol.*;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;
import ppmappingcompiler.util.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.semanticweb.owlapi.vocab.OWL2Datatype.XSD_STRING;

@SuppressWarnings("unused")
public class SparqlBCQParser extends BCQParser {

    public static final String DATA_CONSTANT_PATTERN = String.format("%s(|\\^\\^(?:[^\\s<>,()]+|<[^<>]+>)|@\\w+)", STRING_PATTERN);
    private static final String VARIABLE_PATTERN = "\\?([a-zA-Z]\\w+)";

    public SparqlBCQParser() {
    }

    public SparqlBCQParser(OWLOntology ontology) {
        super(ontology);
    }

    @Override
    public OntologyConjunctiveQuery OntologyConjunctiveQuery(String datalogClause) {
        // TODO
        return null;
    }

    @Override
    public ConjunctiveQuery ConjunctiveQuery(String datalogClause) {
        // TODO
        return null;
    }

    @Override
    public OntologyPredicateAtom OntologyPredicateAtom(String datalogSource) {
        // TODO
        return null;
    }

    @Override
    public PredicateAtom PredicateAtom(String datalogSource) {
        // TODO
        return null;
    }

    @Override
    public StrictInequality StrictInequality(String source) {
        // TODO
        return null;
    }

    @Override
    public Equality Equality(String source) {
        // TODO
        return null;
    }

    /**
     * @param sparqlSource An object constant following the pattern "<http://my.iri>"
     * @return An instance of ObjectConstant
     */
    @Override
    public ObjectConstant ObjectConstant(String sparqlSource) {
        // TODO
        return null;
    }

    /**
     * N.B. In SPARQL, constants are called "literals"
     * (cfr. https://www.w3.org/TR/rdf11-concepts/#section-Graph-Literal)
     */
    @Override
    public DataConstant DataConstant(String sparqlPredicateAtom) throws ParserException {
        Matcher matcher = Pattern.compile(DATA_CONSTANT_PATTERN).matcher(sparqlPredicateAtom);
        if (!matcher.matches())
            throw new ParserException(sparqlPredicateAtom + " is not a valid data constant.");
        String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        OWL2Datatype datatype = null;
        if (matcher.groupCount() > 1 && matcher.group(3) != null) {
            String s = matcher.group(3);
            if (s.startsWith("^^<") && s.endsWith(">")) datatype = getDatatypeFromIRI(s.substring(3, s.length() - 1));
            else if (s.startsWith("^^")) datatype = getDatatypeFromIRI(s.substring(2));
            else if (s.startsWith("@")) throw new ParserException(s, "Language tags are not supported");
            else if (!s.isEmpty()) throw new ParserException(sparqlPredicateAtom + " is not a valid data constant.");
        }

        if (datatype == null || datatype.equals(XSD_STRING)) {
            value = Utils.unescape(value, sparqlPredicateAtom.substring(0, 1));
        }

        return new DataConstant(value, datatype);
    }

    private static OWL2Datatype getDatatypeFromIRI(String datatypeIRI) {
        return OWL2Datatype.getDatatype(IRI.create(
                datatypeIRI.replace("xsd:", "http://www.w3.org/2001/XMLSchema#")
        ));
    }

    @Override
    public Variable Variable(String variableString) throws ParserException {
        Matcher matcher = Pattern.compile(VARIABLE_PATTERN).matcher(variableString);
        if (!matcher.matches())
            throw new ParserException("\"" + variableString + "\" is not a valid name for a variable.");
        return new Variable(matcher.group(1));
    }

}
