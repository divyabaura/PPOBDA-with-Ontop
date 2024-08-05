package ppmappingcompiler.db;

import ppmappingcompiler.fol.*;
import ppmappingcompiler.fol.FormulaContainer.RecursionMethod;
import ppmappingcompiler.util.Utils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ppmappingcompiler.fol.Formula.removeUnnecessaryContainer;
import static ppmappingcompiler.fol.Formula.wrap;
import static ppmappingcompiler.util.Utils.*;

/**
 * This class allows to get a SQL query that is equivalent to a FO formula represented by an instance of {@link Formula}.
 *
 * <ul>
 *     <li><i>Distinguished variable</i>: variable occurring outside the scope of a query.
 *     <li><i>Requested variable</i>: variable that must appear in the SELECT statement of a formula.
 *     <li><i>Shared variable</i>: variable occurring in more than one member of a conjunction.
 * </ul>
 */
public class SQLCompiler {

    private final DBSchema schema;

    public static final String BOOLEAN_WILDCARD = "1";    // wildcard value to be returned for Boolean queries evaluating to true
    private static final String FALSE_VALUE = "FALSE";
    private static final String TRUE_VALUE = "TRUE";

    public SQLCompiler(DBSchema schema) {
        this.schema = schema;
    }

    /**
     * This method returns a SQL query corresponding to FOL query represented by this object.
     * Warning: in order the output SQL query to be meaningful, the given FOL query must be domain independent.
     * <p>
     * Examples of domain-independent FOL queries:
     * <ul>
     *     <li> {@code q() :- ∃x A(x)}
     *     <li> {@code q(x) :- A(x) AND ¬B(x)}
     *     <li> {@code q(x) :- A(x) AND ¬∃y R(x,y)} (equivalent to: {@code q(x) :- A(x) AND ∀y ¬R(x,y))}
     *     <li> {@code q(x,y) :- R(x,y) OR (A(x) AND B(y))}
     * </ul>
     * <p>
     * Examples of domain-dependent FOL queries:
     * <ul>
     *     <li> {@code q() :- ∀x A(x)}
     *     <li> {@code q(x) :- ¬A(x)}
     *     <li> {@code q(x) :- A(x) AND ¬∀y R(x,y)} (equivalent to: {@code q(x) :- A(x) AND ∃y ¬R(x,y))}
     *     <li> {@code q(x,y) :- R(x,y) OR A(x)}
     * </ul>
     *
     * @param formula  The formula to be expressed in SQL.
     * @param distinct {@code true} if the query should not return duplicates, {@code false} otherwise (default: {@code false}).
     * @return A {@link String string} in SQL format.
     */
    public String sqlify(@Nonnull Formula formula, @Nonnull Map<Variable, String> aliases, boolean distinct) throws SQLificationException {
        return new SQLificationInstance(schema, formula, aliases, distinct).exec();
    }

    /**
     * See {@link #sqlify(Formula, Map, boolean)}.
     */
    public String sqlify(@Nonnull Formula formula) throws SQLificationException {
        return sqlify(formula, new HashMap<>(), false);
    }

    /*****************
     * PRIVATE CLASS *
     *****************/

    private static class SQLificationInstance {
        private final DBSchema schema;
        private final Formula mainFormula;
        private final boolean distinct;

        private boolean outermostQuery = true;
        private int subqueryCounter = 0;
        private final Map<String, Integer> tableCounters = new HashMap<>();
        private final Map<Formula, String> queryAliases = new HashMap<>();
        private final Map<Variable, String> variableAliases; // mapping used for forcing variables to have an alias in the output view

        private static final int NO_LIMIT = -1;

        // SQL keywords
        private static final String AND = "AND";
        private static final String NOT = "NOT";
        private static final String OR = "OR";

        SQLificationInstance(@Nonnull DBSchema schema,
                             @Nonnull Formula formula,
                             @Nonnull Map<Variable, String> aliases,
                             boolean distinct) {
            Formula f = formula.clone();
            //Set<String> variablesAlphabet = Formula.LC_LATIN_ALPHABET;
            //f = explodePredicateAtoms(f, variablesAlphabet);
            //f = f.getPrenexForm(variablesAlphabet);
            f = replaceUniversalQuantifiers(f);
            f.flatten(true);

            this.mainFormula = removeUnnecessaryContainer(f); // e.g. NOT(NOT(R(x,y))) -> R(x,y)
            this.schema = schema;
            this.variableAliases = aliases;
            this.distinct = distinct;
        }

        private String exec() throws SQLificationException {
            Set<Variable> freeVars = this.mainFormula.getFreeVariables();
            Set<Variable> selectableVars = getSelectableVariables(this.mainFormula);
            if (!setDifference(selectableVars, freeVars).isEmpty()) {
                throw new RuntimeException(); // this case should not be possible
            }
            Set<Variable> badVars = setDifference(freeVars, selectableVars);
            if (!badVars.isEmpty()) {
                throw new UnsafeQueryException("Some free variables can't be actually selected: \n\t" +
                        badVars + "\nYou may need to add a quantifier for such variables. " +
                        "Please reformulate the query.", this.mainFormula);
            }
            return sqlify(this.mainFormula, freeVars, new HashMap<>()).toString();
        }

        /**
         * This method recursively replaces the predicate atoms contained in the formula with
         * a conjunction possibly with new existential variables and separated equalities,
         * in order to getting rid of constants and self-joins inside predicate atoms.
         */
        private Formula explodePredicateAtoms(Formula formula, Set<String> variablesAlphabet) {
            Set<String> activeVariablesAlphabet = formula.getActiveVariablesAlphabet();
            Function<Formula, Formula> explode = f -> {
                if (f instanceof PredicateAtom) return ((PredicateAtom) f)
                        .explode(variablesAlphabet, activeVariablesAlphabet, true);
                else return f;
            };
            if (formula instanceof PredicateAtom) {
                return explode.apply(formula);
            }
            if (formula instanceof FormulaContainer) {
                ((FormulaContainer) formula).replace(explode, RecursionMethod.DFS);
            }
            return formula;
        }

        private Formula replaceUniversalQuantifiers(Formula f) {
            if (f instanceof FormulaContainer) {
                ((FormulaContainer) f).replace(this::replaceUniversalQuantifiers, RecursionMethod.DFS);
            }
            if (f instanceof ForAll) {
                ForAll forall = (ForAll) f;
                return new Exist(forall.getContent().negate(), forall.getQuantifiedVariables()).negate();
            } else return f;
        }

        /**
         * @param f                 The formula to be expressed in SQL.
         * @param requestedVars     A set of variables that must be selected.
         * @param distinguishedVars A mapping from distinguished variables to one of the SQL attributes
         *                          that has already been assigned to them.
         * @return A {@link StringBuilder string builder} representing a text in SQL format.
         */
        private StringBuilder sqlify(@Nonnull final Formula f,
                                     @Nonnull final Set<Variable> requestedVars,
                                     @Nonnull final Map<Variable, Formula> distinguishedVars) throws SQLificationException {
            if (f instanceof ComparisonAtom) return sqlify((ComparisonAtom) f, distinguishedVars);
            if (f instanceof Conjunction) return sqlify((Conjunction) f, requestedVars, distinguishedVars);
            if (f instanceof Disjunction) return sqlify((Disjunction) f, requestedVars, distinguishedVars);
            if (f instanceof Exist) return sqlify((Exist) f, requestedVars, distinguishedVars);
            if (f instanceof False) return sqlify((False) f);
            if (f instanceof ForAll) return sqlify((ForAll) f);
            if (f instanceof Negation) return sqlify((Negation) f, distinguishedVars);
            if (f instanceof PredicateAtom) return sqlify((PredicateAtom) f, requestedVars, distinguishedVars);
            if (f instanceof True) return sqlify((True) f);
            throw new RuntimeException(String.format("No available SQLification function found for formula %s of type %s",
                    f, f.getClass()));
        }

        private StringBuilder buildSelectStatement(String select, StringBuilder from, StringBuilder where, boolean distinct, int limit) {
            if (select == null) select = BOOLEAN_WILDCARD;
            return new StringBuilder("SELECT ")
                    .append(distinct ? "DISTINCT " : "")
                    .append(select)
                    .append(from == null ? "" : " FROM ")
                    .append(from == null ? "" : from)
                    .append(where == null ? "" : ((from == null ? " " : "\n") + "WHERE "))
                    .append(where == null ? "" : where)
                    .append(limit == NO_LIMIT ? "" : "\nLIMIT " + limit);
        }

        private StringBuilder buildSelectStatement(String select, StringBuilder from, StringBuilder where, boolean isOutermost) {
            boolean isBoolean = (select == null);
            boolean distinct = this.distinct && isOutermost;
            int limit = isBoolean && isOutermost ? 1 : NO_LIMIT;
            StringBuilder sql = buildSelectStatement(select, from, where, distinct, limit);
            return isBoolean && !isOutermost ? addExistsWrapper(sql) : sql;
        }

        private StringBuilder buildSelectStatement(@Nonnull Map<String, Variable> columnToVar, StringBuilder from, StringBuilder where, boolean isOutermost) {
            String projectedAttributes = columnToVar.isEmpty() ? null :
                    sortColumnsAlphabetically(columnToVar.entrySet().stream()
                            .map(e -> {
                                Variable v = e.getValue();
                                String aka = isOutermost ? variableAliases.getOrDefault(v, v.toString()) : v.toString();
                                return e.getKey() + (aka == null || aka.equals("") ? "" : " AS ".concat(aka));
                            })
                            .collect(Collectors.toList()));
            return buildSelectStatement(projectedAttributes, from, where, isOutermost);
        }

        private StringBuilder buildSelectStatement(@Nonnull Set<Variable> variables, StringBuilder from, StringBuilder where, boolean isOutermost) {
            String select = variables.isEmpty() ? null :
                    sortColumnsAlphabetically(variables.stream()
                            .map(v -> isOutermost ? variableAliases.getOrDefault(v, v.toString()) : v.toString())
                            .collect(Collectors.toList()));
            return buildSelectStatement(select, from, where, isOutermost);
        }

        /**
         * This function wraps a Boolean SQL assertion (e.g. a comparison, an OR disjunction, a negation, etc.)
         * into a SELECT statement, if the subquery is the outermost one.
         */
        private StringBuilder selectIfOutermost(StringBuilder sql, boolean isOutermostQuery) {
            return isOutermostQuery ? buildSelectStatement((String) null, null, sql, true) : sql;
        }

        /**
         * This method sorts attribute names alphabetically, in order to select them according to a predictable order.
         */
        private String sortColumnsAlphabetically(@Nonnull Collection<String> namedColumns) {
            List<String> list = new ArrayList<>(namedColumns);
            list.sort(String::compareToIgnoreCase);
            return String.join(", ", list);
        }

        private StringBuilder addExistsWrapper(@Nonnull StringBuilder selectStatement) {
            return prependSB(wrap(selectStatement, false), "EXISTS ");
        }

        private String getTableAlias(@Nonnull PredicateAtom predicateAtom) {
            String tableName = predicateAtom.getPredicateName();
            Integer counter = tableCounters.getOrDefault(tableName, 0) + 1;
            String alias = tableName + counter;
            tableCounters.put(tableName, counter);
            return alias;
        }

        private String getQueryAlias(@Nonnull Formula query) {
            //return "q_" + System.identityHashCode(query);
            if (queryAliases.containsKey(query)) return queryAliases.get(query);
            else {
                String alias = "q" + subqueryCounter++;
                queryAliases.put(query, alias);
                return alias;
            }
        }

        private String termToSQL(Term term, Map<Variable, Formula> distinguishedVariables) {
            if (term instanceof Variable && distinguishedVariables.containsKey(term)) {
                String queryAlias = getQueryAlias(distinguishedVariables.get(term));
                return queryAlias + "." + term;
            } else return term.toString();
        }

        private static Set<Variable> getSelectableVariables(Formula formula) {
            Set<Variable> allVariables = new HashSet<>(formula.getVariables());
            if (formula instanceof PredicateAtom) {
                return allVariables;
            }
            if (formula instanceof Conjunction) {
                return ((Conjunction) formula).getFormulas().stream()
                        .flatMap(f -> getSelectableVariables(f).stream())
                        .collect(Collectors.toSet());
            }
            if (formula instanceof Disjunction) {
                return ((Disjunction) formula).getFormulas().stream()
                        .map(SQLificationInstance::getSelectableVariables)
                        .reduce(allVariables, Utils::setIntersection);
            }
            if (formula instanceof Quantifier) {
                return setDifference(
                        getSelectableVariables(((Quantifier) formula).getContent()),
                        ((Quantifier) formula).getQuantifiedVariables()
                );
            }
            return new HashSet<>();
        }


        /**************************
         * SPECIFIC SQLIFICATIONS *
         **************************/

        private StringBuilder sqlify(@Nonnull final ComparisonAtom atom,
                                     @Nonnull final Map<Variable, Formula> distinguishedVariables) {
            String left = termToSQL(atom.getLeftTerm(), distinguishedVariables);
            String right = termToSQL(atom.getRightTerm(), distinguishedVariables);
            StringBuilder sql = new StringBuilder(left).append(atom.getSqlOperator()).append(right);
            return selectIfOutermost(sql, this.outermostQuery);
        }

        /**
         * Expressing a conjunctive query in SQL as:
         * <pre>
         *     SELECT X FROM
         *       ( jsq_1 ) sq_1
         *     NATURAL JOIN
         *       ( jsq_2 ) sq_2
         *     NATURAL JOIN
         *       ...
         *     [ WHERE wsq_1 AND wsq_2 AND ... ]</pre>
         */
        private StringBuilder sqlify(@Nonnull final Conjunction conjunction,
                                     @Nonnull final Set<Variable> requestedVariables,
                                     @Nonnull final Map<Variable, Formula> distinguishedVariables) throws SQLificationException {
            if (conjunction.isEmpty()) return sqlify(True.getInstance());
            if (conjunction.size() == 1)
                return sqlify(conjunction.getFormulas().iterator().next(), requestedVariables, distinguishedVariables);
            boolean isOutermostQuery = this.outermostQuery;
            this.outermostQuery = false;

            // find variables to request to JOIN subqueries, which are the variables requested to this query
            // plus the (selectable) variables shared between join subqueries (except if they are already distinguished)
            Set<Variable> joinableVariables = setIntersection(getSelectableVariables(conjunction), conjunction.getSharedVariables());
            Set<Variable> furtherVarsToRequest = setDifference(joinableVariables, distinguishedVariables.keySet());
            Set<Variable> allVarsToRequest = setUnion(requestedVariables, furtherVarsToRequest);

            // distinguished variables to be passed to WHERE subqueries
            // this map will be updated with variables that are distinguished through join subqueries
            Map<Variable, Formula> whereDistinguishedVars = new HashMap<>(distinguishedVariables);

            // classify subqueries (JOIN vs. WHERE)
            Set<Formula> joinSubqueries = new HashSet<>();
            Set<Formula> whereSubqueries = new HashSet<>();
            for (Formula sf : conjunction) {
                Set<Variable> varsToRequest = setIntersection(allVarsToRequest, sf.getFreeVariables());
                if (varsToRequest.isEmpty() || !getSelectableVariables(sf).containsAll(varsToRequest)) {
                    whereSubqueries.add(sf);
                } else {
                    joinSubqueries.add(sf);
                    sf.getVariables().forEach(v -> whereDistinguishedVars.putIfAbsent(v, sf));
                }
            }

            // Check that every variable to request occurs free in at least one join subquery.
            // This does not happen, for example, in q(x,y) = A(x) AND (A(y) OR R(x, y)).
            // In this case, only A(x) is a join subquery, because x is requested but not selectable from A(y) OR R(x, y).
            // Since the second conjunct is not a join subquery, we can't select the variable y.
            // The solution is to rephrase the formula as (A(x) OR A(y)) AND (A(x) OR R(x, y)).
            Set<Variable> requestableVars = joinSubqueries.stream()
                    .flatMap(sf -> sf.getFreeVariables().stream())
                    .collect(Collectors.toSet());
            if (!requestableVars.containsAll(allVarsToRequest)) {
                throw new SQLificationException(String.format(
                        "Impossible to select or join variables %s within conjunction %s" +
                                "\nYou may need to rephrase such conjunction (maybe distributing it).",
                        setDifference(allVarsToRequest, requestableVars), conjunction
                ), this.mainFormula);
            }

            // build SQL query
            List<StringBuilder> joinSQLSubqueries = new ArrayList<>();
            List<StringBuilder> whereSQLSubqueries = new ArrayList<>();
            for (Formula sf : joinSubqueries) {
                Set<Variable> varsToRequest = setIntersection(allVarsToRequest, getSelectableVariables(sf));
                joinSQLSubqueries.add(wrap(sqlify(sf, varsToRequest, distinguishedVariables), sf instanceof PredicateAtom)
                        .append(" ")
                        .append(getQueryAlias(sf)));
            }
            for (Formula sf : whereSubqueries) {
                StringBuilder sql = sqlify(sf, new HashSet<>(), whereDistinguishedVars);
                if (sf instanceof ManyFormulasContainer && whereSubqueries.size() > 1) {
                    sql = wrap(sql);
                }
                whereSQLSubqueries.add(sql);
            }

            StringBuilder whereClause = whereSubqueries.isEmpty() ? null : wrap(joinSB(" " + AND + " ", whereSQLSubqueries));

            if (!joinSubqueries.isEmpty()) {
                /* TODO: seleziona direttamente dalla tabella se valgono TUTTE queste condizioni:
                 *  - la join-subquery è una sola
                 *  - la join-subquery è un predicate atom
                 *  - le variabili distinguished tramite la join-subquery non occorrono nelle where-subqueries
                 */
                StringBuilder joinStatement = joinSQLSubqueries.size() > 1
                        ? prependSB(Utils.indent(joinSB("\nNATURAL JOIN\n", joinSQLSubqueries)), "\n")
                        : joinSQLSubqueries.get(0);

                return buildSelectStatement(requestedVariables, joinStatement, whereClause, isOutermostQuery);
            } else {
                assert !whereSubqueries.isEmpty();
                return selectIfOutermost(whereClause, this.outermostQuery);
            }
        }

        /**
         * Expressing a disjunctive query in SQL as:
         * <pre>
         *     sq_1 [ UNION ALL sq_2 [ UNION ALL ... ] ]
         * </pre>
         * if at least one variable is requested (to be selected), or as:
         * <pre>
         *     sq_1 [ OR sq_2 [ OR ... ] ]</pre>
         * otherwise.
         */
        private StringBuilder sqlify(@Nonnull final Disjunction disjunction,
                                     @Nonnull final Set<Variable> requestedVariables,
                                     @Nonnull final Map<Variable, Formula> distinguishedVariables) throws SQLificationException {
            if (disjunction.isEmpty()) return sqlify(False.getInstance());
            if (disjunction.size() == 1)
                return sqlify(disjunction.getFormulas().iterator().next(), requestedVariables, distinguishedVariables);

            boolean isOutermostQuery = this.outermostQuery;
            this.outermostQuery = false;

            List<StringBuilder> subqueries = new ArrayList<>();
            for (Formula sf : disjunction.getFormulas()) {
                subqueries.add(sqlify(sf, requestedVariables, distinguishedVariables));
            }

            boolean isBoolean = requestedVariables.isEmpty();
            if (isBoolean) {
                StringBuilder whereClause = joinSB(" " + OR + " ", subqueries);
                return selectIfOutermost(whereClause, isOutermostQuery);
            } else {
                StringBuilder sql = joinSB("\nUNION ALL\n", subqueries);

                // if the query is used for a mapping, there must be a SELECT wrapping the UNION ALL chain
                if (isOutermostQuery) {
                    StringBuilder from = wrap(sql).append(" AS ").append(getQueryAlias(disjunction));
                    return buildSelectStatement(requestedVariables, from, null, true);
                } else return sql;
            }
        }

        private StringBuilder sqlify(@Nonnull final Exist exist,
                                     @Nonnull final Set<Variable> requestedVariables,
                                     @Nonnull final Map<Variable, Formula> distinguishedVariables) throws SQLificationException {
            Collection<Variable> quantifiedVars = exist.getQuantifiedVariables();
            return sqlify(exist.getContent(), requestedVariables, distinguishedVariables
                    .entrySet().stream()
                    .filter(k -> !quantifiedVars.contains(k.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (k, v) -> v, HashMap::new)));
        }

        private StringBuilder sqlify(@Nonnull final ForAll forall) {
            if (forall.getContent().isTautology()) {
                return sqlify(True.getInstance());
            }
            throw new RuntimeException(); // at this point universal quantifiers should have been replaced
        }

        @SuppressWarnings("unused")
        private StringBuilder sqlify(@Nonnull final False bottom) {
            return selectIfOutermost(new StringBuilder(FALSE_VALUE), this.outermostQuery);
        }

        private StringBuilder sqlify(@Nonnull final PredicateAtom predicateAtom,
                                     @Nonnull final Set<Variable> requestedVariables,
                                     @Nonnull final Map<Variable, Formula> distinguishedVariables) throws SQLificationException {
            boolean isOutermostQuery = this.outermostQuery;
            this.outermostQuery = false;

            Table table = getTableByPredicateAtom(predicateAtom);
            List<String> columnNames = table.getAttributes();

            Map<Variable, String> varToColName = new HashMap<>();
            Map<String, Variable> attributesToSelect = new HashMap<>();
            List<StringBuilder> equalities = new ArrayList<>();
            for (int index = 0; index < predicateAtom.getArity(); index++) {
                Term t = predicateAtom.getTerm(index);
                String colName = columnNames.get(index); // this must be immutable, do not use StringBuilder
                if (t instanceof Variable) {
                    if (requestedVariables.contains(t)) {
                        attributesToSelect.put(colName, (Variable) t);
                    }
                    if (varToColName.containsKey(t)) {
                        equalities.add(equalitySB(colName, varToColName.get(t)));
                    } else {
                        varToColName.put((Variable) t, colName);
                        if (distinguishedVariables.containsKey(t) && !distinguishedVariables.get(t).equals(predicateAtom)) {
                            equalities.add(equalitySB(colName, termToSQL(t, distinguishedVariables)));
                        }
                    }
                }
                if (t instanceof Constant) {
                    equalities.add(equalitySB(colName, t.toString()));
                }
            }

            StringBuilder tableReference = new StringBuilder(SQLUtils.quoteSqlWord(table.getTableName()))
                    .append(" ")
                    .append(getTableAlias(predicateAtom));
            StringBuilder whereClause = equalities.isEmpty() ? null : joinSB(" " + AND + " ", equalities);
            return buildSelectStatement(attributesToSelect, tableReference, whereClause, isOutermostQuery);
        }

        private StringBuilder equalitySB(String t1, String t2) {
            return new StringBuilder(t1).append("=").append(t2);
        }

        private Table getTableByPredicateAtom(PredicateAtom predicateAtom) throws SQLificationException {
            String predicateName = predicateAtom.getPredicateName();
            Table table = this.schema.getTableByName(predicateName);
            if (table == null) throw new NoSQLTableFoundException(String.format(
                    "No SQL table has been associated to predicate %s.", predicateName
            ), this.mainFormula);
            if (predicateAtom.getArity() != table.getArity()) throw new SQLificationException(String.format(
                    "Atom %s and table %s have different arities (respectively, %d and %d)",
                    predicateAtom, table, predicateAtom.getArity(), table.getArity()
            ), this.mainFormula);
            return table;
        }

        private StringBuilder sqlify(@Nonnull final Negation negation,
                                     @Nonnull final Map<Variable, Formula> distinguishedVariables) throws SQLificationException {
            boolean isOutermostQuery = this.outermostQuery;
            this.outermostQuery = false;
            Formula sf = negation.getContent();
            StringBuilder sql;

            // inspecting negation's content for handling specific cases
            if (sf instanceof ComparisonAtom) sql = sqlify(((ComparisonAtom) sf).negate(), distinguishedVariables);
            else if (sf instanceof Negation)
                sql = sqlify(((Negation) sf).getContent(), new HashSet<>(), distinguishedVariables);
            else if (sf instanceof ManyFormulasContainer) sql = new StringBuilder(NOT)
                    .append(" ")
                    .append(wrap(sqlify(sf, new HashSet<>(), distinguishedVariables)));
            else sql = new StringBuilder(NOT)
                        .append(" (")                // some parsers want "NOT" keyword followed by parenthesis even in some trivial case
                        .append(sqlify(sf, new HashSet<>(), distinguishedVariables))
                        .append(")");

            return selectIfOutermost(sql, isOutermostQuery);
        }

        @SuppressWarnings("unused")
        private StringBuilder sqlify(@Nonnull final True top) {
            return selectIfOutermost(new StringBuilder(TRUE_VALUE), this.outermostQuery);
        }
    }

    /**************
     * EXCEPTIONS *
     **************/

    @SuppressWarnings("unused")
    public static class SQLificationException extends Exception {
        public SQLificationException(String s) {
            super(s);
        }

        public SQLificationException(String s, Formula q) {
            super(String.format("Error with query:\n%s\n%s", q, s));
        }
    }

    @SuppressWarnings("unused")
    public static class NoSQLTableFoundException extends SQLificationException {
        public NoSQLTableFoundException(String s) {
            super(s);
        }

        public NoSQLTableFoundException(String s, Formula q) {
            super(s, q);
        }
    }

    @SuppressWarnings("unused")
    public static class UnsafeQueryException extends SQLificationException {
        public UnsafeQueryException(String s) {
            super(s);
        }

        public UnsafeQueryException(String s, Formula q) {
            super(s, q);
        }
    }
	
}
