package ppmappingcompiler.fol;

import ppmappingcompiler.Logger;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;

public class Variable extends Term {

    private String name;            // a variable is identified by its name
    private ConjunctiveQuery query;    // and eventually by a query it belongs to
    private Term.Type type = Type.UNDEFINED;
    public static final String BLANK_VAR_SYMBOL = "_";

    @SuppressWarnings("unused")
    public Variable(String variableName) {
        this.name = variableName;
    }

    @SuppressWarnings("unused")
    public Variable(String variableName, ConjunctiveQuery query) {
        this.name = variableName;
        bindToQuery(query);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    @Override
    public Term.Type getType() {
        return this.type;
    }

    public void setType(Term.Type newType) {
        if (this.type != Type.UNDEFINED && this.type != newType)
            Logger.warn(String.format("Type of variable %s have been overwritten with %s (previously was %s).",
                    this, newType, this.type));
        this.type = newType;
    }

    public void bindToQuery(ConjunctiveQuery query) {
        if (query == null)
            throw new RuntimeException("You cannot bind a variable to a 'null' query.");
        this.query = query;

        // deduce variable type from query
        if (query instanceof OntologyConjunctiveQuery) {
            for (OntologyPredicateAtom atom : ((OntologyConjunctiveQuery) query).getAllPredicateAtoms()) {
                if (this.type == Type.UNDEFINED) {
                    for (int index = 0; index < atom.getTerms().size(); index++) {
                        Term t = atom.getTerm(index);
                        if (t instanceof Variable && ((Variable) t).name.equals(this.name)) {
                            this.type = atom.getType() == OntologyPredicateAtom.Type.ATTRIBUTE && index == 1 ?
                                    Type.DATA : Type.OBJECT;
                        }
                    }
                }
            }
        }
    }

    public boolean isUndistinguishedNonShared() {
        return this.name.equals(BLANK_VAR_SYMBOL);
    }

    public static Variable getUndistinguishedNonSharedVariable() {
        return new Variable(BLANK_VAR_SYMBOL);
    }

    public void unbind() {
        this.query = null;
    }

    public ConjunctiveQuery getQuery() {
        return this.query;
    }

    public boolean belongsToQuery(ConjunctiveQuery query) {
        return this.query != null && this.query.equals(query);
    }

    @Override
    public String toSparql() {
        return "?" + this.name;
    }

    @Override
    public int hashCode() {
        // the class is important because a Variable, a DataConstant and a ObjectConstant must always have different hashcodes
        return (getClass() + this.name + (query == null ? 0 : query.hashCode())).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        Variable v = (Variable) obj;

        // Here do NOT use ConjunctiveQuery::equals for checking equality between CQs, because:
        // 1. the query must be the exact same object
        // 2. this would cause a stack overflow (equal CQ must have equal variables...)
        return this.name.equals(v.name) && this.query == v.query;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public Variable clone() {
        return (Variable) super.clone();
    }

}
