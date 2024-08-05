package ppmappingcompiler.fol;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import ppmappingcompiler.fol.Term.TermTypeException;
import ppmappingcompiler.util.Lambdas;
import ppmappingcompiler.util.OntologyUtils;

import java.net.URISyntaxException;
import java.util.List;

import static ppmappingcompiler.fol.OntologyPredicateAtom.Type.*;
import static ppmappingcompiler.fol.Term.Type.DATA;
import static ppmappingcompiler.fol.Term.Type.OBJECT;

@SuppressWarnings("unused")
public class OntologyPredicateAtom extends PredicateAtom {

    private final Type type;
    private final String predicateIRI;

    public enum Type {
        UNDEFINED, CONCEPT, ROLE, ATTRIBUTE;

        @Override
        public String toString() {
            switch (this) {
                case CONCEPT:
                    return "concept";
                case ROLE:
                    return "role";
                case ATTRIBUTE:
                    return "attribute";
                default:
                    return "undefined";
            }
        }
    }

    public OntologyPredicateAtom(String predicateIRI, List<? extends Term> terms, Type type) throws URISyntaxException, PredicateArityException, TermTypeException {
        super(OntologyUtils.getPredicateNameFromIRI(predicateIRI), terms);
        this.type = type == Type.UNDEFINED ? guessType(this.terms) : type;
        this.predicateIRI = predicateIRI;

        this.arityCheck();
        this.setVariablesType();
        this.termsCheck();
    }

    public OntologyPredicateAtom(String predicateIRI, List<? extends Term> terms, OWLOntology ontology) throws URISyntaxException, PredicateArityException, TermTypeException {
        this(
                OntologyUtils.explicitIRIPrefix(predicateIRI, ontology),
                terms,
                OntologyPredicateAtom.getType(OntologyUtils.explicitIRIPrefix(predicateIRI, ontology), ontology)
        );
    }

    public OntologyPredicateAtom(String predicateIRI, List<? extends Term> terms) throws URISyntaxException, PredicateArityException, TermTypeException {
        this(predicateIRI, terms, Type.UNDEFINED);
    }

    private Type guessType(List<Term> terms) {
        if (terms.size() == 1) return CONCEPT;
        if (terms.size() == 2) {
            if (terms.get(1).getType() == OBJECT) return ROLE;
            if (terms.get(1).getType() == DATA) return ATTRIBUTE;
        }
        return Type.UNDEFINED;
    }

    private void setVariablesType() {
        Term t1 = terms.get(0);
        if (t1 instanceof Variable && t1.getType() == Term.Type.UNDEFINED)
            ((Variable) t1).setType(OBJECT);

        if (this.type != CONCEPT) {
            Term t2 = terms.get(1);
            if (t2 instanceof Variable && t2.getType() == Term.Type.UNDEFINED) {
                switch (this.type) {
                    case ROLE:
                        ((Variable) t2).setType(OBJECT);
                        break;
                    case ATTRIBUTE:
                        ((Variable) t2).setType(DATA);
                        break;
                }
            }
        }
    }

    public void arityCheck() throws PredicateArityException {
        final int termsNum = this.terms.size();

        if ((this.type == CONCEPT && termsNum != 1) || (this.type != CONCEPT && termsNum != 2))
            throw new PredicateArityException(String.format(
                    "Forbidden arity for atom %s: %s <%s> can't have %d variables.",
                    this, this.type, this.predicateIRI, termsNum));
    }

    public void termsCheck() throws TermTypeException {
        Lambdas.Function3<String, Term.Type, TermTypeException> termTypeError = (num, tType) -> new TermTypeException(
                String.format("Forbidden term type found in atom %s: %s term for %s <%s> must be %s %s constant/variable.",
                        this, num, this.type.toString(), this.predicateIRI,
                        tType == OBJECT ? "an" : "a", tType));

        if (getTerm(0).getType() == DATA) throw termTypeError.apply("first", OBJECT);

        switch (this.type) {
            case ROLE:
                if (getTerm(1).getType() == DATA) throw termTypeError.apply("second", OBJECT);
                break;
            case ATTRIBUTE:
                if (getTerm(1).getType() == OBJECT) throw termTypeError.apply("second", DATA);
                break;
        }
    }

    @Override
    public String getPredicateIdentifier() {
        return predicateIRI;
    }

    public String getPredicateIRI() {
        return predicateIRI;
    }

    public Type getType() {
        return type;
    }

    public static Type getType(String predicateIRI, OWLOntology ontology) {
        IRI iri = IRI.create(predicateIRI);
        if (ontology.containsClassInSignature(iri))
            return CONCEPT;
        if (ontology.containsObjectPropertyInSignature(iri))
            return ROLE;
        if (ontology.containsDataPropertyInSignature(iri))
            return ATTRIBUTE;
        return Type.UNDEFINED;
    }

    /**
     * This method is a specialization (not a proper overriding) of {@link PredicateAtom#isSamePredicate(PredicateAtom)}.
     */
    public boolean isSamePredicate(OntologyPredicateAtom atom) {
        return super.isSamePredicate(atom) && getType() == atom.getType();
    }

    @Override
    public OntologyPredicateAtom clone() {
        return (OntologyPredicateAtom) super.clone();
    }

}
