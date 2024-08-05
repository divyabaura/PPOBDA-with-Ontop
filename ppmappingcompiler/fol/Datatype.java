package ppmappingcompiler.fol;

import org.semanticweb.owlapi.model.HasIRI;
import org.semanticweb.owlapi.model.HasPrefixedName;
import org.semanticweb.owlapi.model.HasShortForm;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import ppmappingcompiler.Logger;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

// Current supported datatypes are the ones included in:
// https://www.w3.org/TR/rdf-sparql-query/#operandDataTypes
public enum Datatype implements HasIRI, HasShortForm, HasPrefixedName {
	XSD_BOOLEAN   (OWL2Datatype.XSD_BOOLEAN),
	XSD_DATE_TIME (OWL2Datatype.XSD_DATE_TIME),
	XSD_DECIMAL   (OWL2Datatype.XSD_DECIMAL),
	XSD_FLOAT     (OWL2Datatype.XSD_FLOAT),
	XSD_INTEGER   (OWL2Datatype.XSD_INTEGER),
	XSD_INT       (OWL2Datatype.XSD_INT),
	XSD_STRING    (OWL2Datatype.XSD_STRING);
	
	private final OWL2Datatype owl2Datatype;
	
	Datatype(OWL2Datatype datatype) {
		this.owl2Datatype = datatype;
	}
	
	@Nonnull
	@Override
	public IRI getIRI() {
		return owl2Datatype.getIRI();
	}
	
	@Nonnull
	@Override
	public String getPrefixedName() {
		return owl2Datatype.getPrefixedName();
	}
	
	@Nonnull
	@Override
	public String getShortForm() {
		return owl2Datatype.getShortForm();
	}
	
	public Pattern getPattern() {
		return owl2Datatype.getPattern();
	}
	
	public static Datatype getDatatypeFromIRI(String datatypeIRI) {
		IRI iri = IRI.create(datatypeIRI);
		for (Datatype datatype : Datatype.values()) {
			if (datatypeIRI.equals("xsd:" + datatype.owl2Datatype.getShortForm()) ||
					iri.equals(datatype.owl2Datatype.getIRI())) {
				return datatype;
			}
		}
		return null;
	}
	
	public static Datatype getDatatype(OWL2Datatype owl2Datatype) {
		for (Datatype datatype : Datatype.values()) {
			if (datatype.owl2Datatype == owl2Datatype) {
				return datatype;
			}
		}
		return null;
	}
}