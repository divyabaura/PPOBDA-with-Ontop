package ppmappingcompiler.fol;

import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO: implement also generic inequality (<, >, <=, >=)
 * A comparison atom is an {@link Atom atom} using a binary comparison operator. It can be:
 * - a {@link Equality equality}
 * - a {@link StrictInequality strict inequality}
 */
public abstract class ComparisonAtom extends Atom {

    private final String sqlOperator;
    private final boolean symmetric;
    @Nonnull
    protected Term left;
    @Nonnull
    protected Term right;

    protected ComparisonAtom(@Nonnull Term left, @Nonnull Term right, String sqlOperator, boolean symmetric) {
        this.left = left;
        this.right = right;
        this.sqlOperator = sqlOperator;
        this.symmetric = symmetric;
    }

    public abstract ComparisonAtom negate();

    public Term getLeftTerm() {
        return left.clone();
    }

    public Term getRightTerm() {
        return right.clone();
    }

    public String getSqlOperator() {
        return this.sqlOperator;
    }

    /**
     * This method returns the {@link Term terms} occurring in this atom.<br>
     * It is overridden for preserving the terms' order.
     *
     * @return A {@link List list} of {@link Term terms}.
     */
    @Override
    public List<Term> getTerms() {
        return Arrays.asList(getLeftTerm(), getRightTerm());
    }

    /**
     * This method returns the {@link Variable variables} occurring in this atom.<br>
     * It is overridden for preserving the variables' order.
     *
     * @return A {@link List list} of {@link Variable variables}.
     */
    @Override
    public List<Variable> getVariables() {
        return Utils.filterByClass(getTerms(), Variable.class, Collectors.toList());
    }

    @Override
    public boolean hasVariable(Variable var) {
        return left.equals(var) || right.equals(var);
    }

    @Override
    public void bindVariablesToQuery(ConjunctiveQuery cq) {
        if (left instanceof Variable) ((Variable) left).bindToQuery(cq);
        if (right instanceof Variable) ((Variable) right).bindToQuery(cq);
    }

    @Override
    public void unbindVariables() {
        if (left instanceof Variable) ((Variable) left).unbind();
        if (right instanceof Variable) ((Variable) right).unbind();
    }

    @Override
    public boolean replaceVariable(Variable oldVar, Term newTerm) {
        boolean replaced = false;
        if (this.left.equals(oldVar)) {
            this.left = newTerm;
            replaced = true;
        }
        if (this.right.equals(oldVar)) {
            this.right = newTerm;
            replaced = true;
        }
        return replaced;
    }

    public String toSparql() {
        return String.format("%s %s %s", this.left.toSparql(), this.sqlOperator, this.right.toSparql());
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", this.left, sqlOperator, this.right);
    }

    @Override
    public int hashCode() {
        List<Object> list = new ArrayList<>(Arrays.asList(left.hashCode(), right.hashCode()));
        // for symmetric comparisons, sort terms for ensuring that "x!=y" and "y!=x" has same hashcode
        if (this.symmetric) list.sort(Comparator.comparing(Object::toString));
        list.add(this.getClass()); // "x>y" and "x<y" must have different hashcodes
        return list.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass())
            return false;
        ComparisonAtom a = (ComparisonAtom) obj;
        return (this.left.equals(a.left) && this.right.equals(a.right)) || (this.symmetric &&
                this.left.equals(a.right) && this.right.equals(a.left));
    }

    @Override
    public ComparisonAtom clone() {
        ComparisonAtom clone = (ComparisonAtom) super.clone();
        clone.left = this.left.clone();
        clone.right = this.right.clone();
        return clone;
    }


    /*********************
     * AUXILIARY METHODS *
     *********************/

    @Override
    protected void replaceVariablesNoChain(Map<Variable, ? extends Term> substitution) {
        if (right instanceof Variable && substitution.containsKey(right)) right = substitution.get(right);
        if (left instanceof Variable && substitution.containsKey(left)) left = substitution.get(left);
    }

    @Override
    protected void explicitVariablesAux(@Nonnull Set<String> alphabet, @Nonnull Set<String> reservedNames) {
        if (left instanceof Variable) {
            Variable oldVar = ((Variable) left);
            if (oldVar.isUndistinguishedNonShared()) {
                String newVarName = Formula.getFreshVariableName(alphabet, reservedNames);
                Variable newVar = oldVar.clone();
                newVar.setName(newVarName);
                left = newVar;
                reservedNames.add(newVarName);
            }
        }

        if (right instanceof Variable) {
            Variable oldVar = ((Variable) right);
            if (oldVar.isUndistinguishedNonShared()) {
                String newVarName = Formula.getFreshVariableName(alphabet, reservedNames);
                Variable newVar = oldVar.clone();
                newVar.setName(newVarName);
                right = newVar;
                reservedNames.add(newVarName);
            }
        }
    }

}
