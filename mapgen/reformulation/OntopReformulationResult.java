package se.umea.mapgen.reformulation;

import com.google.common.collect.Maps;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.UnaryIQTree;
import it.unibz.inf.ontop.iq.node.ConstructionNode;
import it.unibz.inf.ontop.iq.node.NativeNode;
import it.unibz.inf.ontop.iq.node.QueryNode;
import it.unibz.inf.ontop.model.template.Template;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.NonGroundFunctionalTerm;
import it.unibz.inf.ontop.model.term.RDFTermTypeConstant;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.term.functionsymbol.FunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.RDFTermFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.db.DBTypeConversionFunctionSymbol;
import it.unibz.inf.ontop.model.term.functionsymbol.db.IRIStringTemplateFunctionSymbol;
import it.unibz.inf.ontop.model.type.RDFDatatype;
import it.unibz.inf.ontop.model.type.RDFTermType;
import it.unibz.inf.ontop.model.type.impl.IRITermType;
import it.unibz.inf.ontop.substitution.Substitution;
import org.eclipse.jdt.annotation.NonNullByDefault;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of the reformulation of a VKG SPARQL query performed by Ontop.
 * <p>
 * Instances of this class capture the input, intermediate result, and native output of the reformulation process. The
 * input consists in the SPARQL expression and corresponding Ontop IQ for the VKG query. The output consists in the SQL
 * expression and corresponding (native) IQ for the generated native query. The intermediate result consists in the IQ
 * for the query following rewriting w.r.t. the ontology, mapping unfolding, and optimization, but prior to generating
 * the native query SQL.
 * </p>
 * <p>
 * A helper method {@link #getNativeQueryTargetMap()} is also included for analyzing the generated native IQ and\
 * producing a map associating each projected SPARQL variable to the corresponding expression to include in a target
 * clause of an Ontop {@code .obda} mapping file.
 * </p>
 */
@SuppressWarnings("unused")
@NonNullByDefault
public final class OntopReformulationResult {

    private final String vkgQuerySPARQL;

    private final IQ vkgQueryIQ;

    private final @Nullable IQ reformulatedQueryIQ; // null if empty

    private final @Nullable String nativeQuerySQL; // null if empty

    private final @Nullable IQ nativeQueryIQ; // null if empty

    private transient @Nullable Map<String, String> nativeQueryTargetMap; // null if empty

    /**
     * Creates a new reformulation results object for an empty query, that is, a query determined to return empty
     * results. In such case, a native query is not needed and thus not generated.
     *
     * @param vkgQuerySPARQL the SPARQL expression for the input VKG query
     * @param vkgQueryIQ     the IQ for the input VKG query
     */
    public OntopReformulationResult(String vkgQuerySPARQL, IQ vkgQueryIQ) {
        this.vkgQuerySPARQL = Objects.requireNonNull(vkgQuerySPARQL);
        this.vkgQueryIQ = Objects.requireNonNull(vkgQueryIQ);
        this.reformulatedQueryIQ = null;
        this.nativeQueryIQ = null;
        this.nativeQuerySQL = null;
    }

    /**
     * Creates a new reformulation results object for a non-empty query, that is, a query not determined to return empty
     * results. In such case, a native query and all its associated information are generated and available through this
     * result object.
     *
     * @param vkgQuerySPARQL      the SPARQL expression for the input VKG query
     * @param vkgQueryIQ          the IQ for the input VKG query
     * @param reformulatedQueryIQ the IQ for the reformulated query, prior to its conversion to a NativeNode IQ
     * @param nativeQueryIQ       the IQ for the native query, consisting primarily of a NativeNode
     * @param nativeQuerySQL      the SQL for the native query
     */
    public OntopReformulationResult(String vkgQuerySPARQL, IQ vkgQueryIQ, IQ reformulatedQueryIQ, IQ nativeQueryIQ,
                                    String nativeQuerySQL) {
        this.vkgQuerySPARQL = Objects.requireNonNull(vkgQuerySPARQL);
        this.vkgQueryIQ = Objects.requireNonNull(vkgQueryIQ);
        this.reformulatedQueryIQ = Objects.requireNonNull(reformulatedQueryIQ);
        this.nativeQueryIQ = Objects.requireNonNull(nativeQueryIQ);
        this.nativeQuerySQL = Objects.requireNonNull(nativeQuerySQL);
    }

    /**
     * Returns the SPARQL expression of the input VKG query.
     *
     * @return the SPARQL VKG query, never null
     */
    public String getVkgQuerySPARQL() {
        return vkgQuerySPARQL;
    }

    /**
     * Returns the IQ for the input VKG query. This is the first IQ generated following the parsing of the input VKG=
     * SPARQL query.
     *
     * @return the IQ of the VKG query, never null
     */
    public IQ getVkgQueryIQ() {
        return vkgQueryIQ;
    }

    /**
     * Returns the IQ of the reformulated query, if not empty, prior to the generation of the native query (that is, the
     * SQL for the specific native data source). Note that the SQL may return different columns w.r.t. the ones of the
     * source SPARQL query, unless property {@code ontop.reformulateToFullNativeQuery} is set to true prior to
     * instantiating the {@link OntopReformulationAPI} object.
     *
     * @return the IQ for the reformulated query, or {@code null} if the query was determined to produce empty results
     */
    public @Nullable IQ getReformulatedQueryIQ() {
        return reformulatedQueryIQ;
    }

    /**
     * Returns the IQ for the resulting native query, if not empty. This will consist primarily of a NativeNode IQ node, containing
     * the SQL to submit to the native data source along with information about how to convert its results into RDF
     * values of the SPARQL results.
     *
     * @return the IQ for the resulting native query, or {@code null} if the query was determined to produce empty results
     */
    public @Nullable IQ getNativeQueryIQ() {
        return nativeQueryIQ;
    }

    /**
     * Returns the SQL expression of the resulting native query, if not empty. Note that the SQL may return different
     * columns w.r.t. the ones of the source SPARQL query, unless property {@code ontop.reformulateToFullNativeQuery} is
     * set to true prior to instantiating the {@link OntopReformulationAPI} object.
     *
     * @return the SQL for the resulting native query, or {@code null} if the query was determined to produce empty results
     */
    public @Nullable String getNativeQuerySQL() {
        return nativeQuerySQL;
    }

    /**
     * Returns a map associating each projected variable of the input VKG SPARQL query to the corresponding string to
     * include in a <i>target</i> mapping clause. This string follows the Ontop {@code .obda} mapping syntax and includes
     * information about whether an IRI, a BNode or a literal with a certain datatype or language are generated and how
     * for the given projected SPARQL variable.
     *
     * @return a map from SPARQL projected variable to corresponding mapping target expression, or {@code null} if the
     * query was determined to produce empty results
     */
    public @Nullable Map<String, String> getNativeQueryTargetMap() {
        if (nativeQueryIQ == null) {
            return null;
        } else if (nativeQueryTargetMap == null) {
            nativeQueryTargetMap = extractNativeQueryTargetMap(nativeQueryIQ);
        }
        return nativeQueryTargetMap;
    }

    /**
     * Tests whether the query was determined to return empty results during reformulation. In this case there is no
     * need to query the native data source, hence a native query and all the associated information are not returned.
     *
     * @return true, if the query was determined to return empty results
     */
    public boolean isEmpty() {
        return nativeQueryIQ == null;
    }

    /**
     * {@inheritDoc} Returns a string representation of this result object, including all of its properties
     */
    @Override
    public String toString() {
        String reformulatedQuery = reformulatedQueryIQ != null ? reformulatedQueryIQ.toString().trim() : null;
        String nativeQuery = nativeQuerySQL != null && nativeQueryIQ != null
                ? nativeQuerySQL.trim() + "\n\n" + nativeQueryIQ.toString().trim() + "\n\n" + getNativeQueryTargetMap()
                : null;
        return "=== VKG QUERY ===\n\n" + vkgQuerySPARQL.trim() + "\n\n" + vkgQueryIQ.toString().trim()
                + "\n\n=== REFORMULATED QUERY ===\n\n" + reformulatedQuery
                + "\n\n=== NATIVE QUERY ===\n\n" + nativeQuery;
    }

    private static Map<String, String> extractNativeQueryTargetMap(@Nullable IQ iq) {

        // Retrieve the root node of the supplied IQ
        QueryNode root = Optional.ofNullable(iq)
                .map(IQ::getTree)
                .map(IQTree::getRootNode)
                .orElse(null);

        // Return an empty result if it was not possible to extract a root ConstructionNode
        if (!(root instanceof ConstructionNode)) {
            return Collections.emptyMap();
        }

        // Cast to ConstructionNode and extract its Substitution attribute
        ConstructionNode c = (ConstructionNode) root;
        Substitution<ImmutableTerm> s = c.getSubstitution();

        // Iterate over projecte variables, mapping each one to a mapping target expression and returning them in a map
        Map<String, String> map = Maps.newHashMap();
        for (Variable v : c.getVariables()) {
            ImmutableTerm t = s.apply(v);
            if (t instanceof NonGroundFunctionalTerm) {
                NonGroundFunctionalTerm ft = (NonGroundFunctionalTerm) t;
                if (ft.getFunctionSymbol() instanceof RDFTermFunctionSymbol
                        && ft.getArity() == 2
                        && ft.getTerm(1) instanceof RDFTermTypeConstant) {

                    // Try to extract the target expression
                    ImmutableTerm value = ft.getTerm(0);
                    RDFTermType type = ((RDFTermTypeConstant) ft.getTerm(1)).getRDFTermType();
                    String targetExpr = extractNativeQueryTargetMapHelper(value, type);

                    // Add the expression to the map, upon success
                    if (targetExpr != null) {
                        map.put(v.getName(), targetExpr);
                    }
                }
            }
        }
        return map;
    }

    private static @Nullable String extractNativeQueryTargetMapHelper(ImmutableTerm value, RDFTermType type) {

        // Discard any implicit cast function from the value
        value = discardCasts(value);

        // Handle a IRI
        if (type instanceof IRITermType) {

            // Handle a IRI string fully generated within the IQ
            if (value instanceof Variable) {
                return "<{" + ((Variable) value).getName() + "}>";
            }

            // Handle a IRI template
            if (value instanceof NonGroundFunctionalTerm) {
                NonGroundFunctionalTerm ft = (NonGroundFunctionalTerm) value;
                if (ft.getFunctionSymbol() instanceof IRIStringTemplateFunctionSymbol) {
                    StringBuilder sb = new StringBuilder("<");
                    IRIStringTemplateFunctionSymbol template = (IRIStringTemplateFunctionSymbol) ft.getFunctionSymbol();
                    for (Template.Component component : template.getTemplateComponents()) {
                        if (!component.isColumnNameReference()) {
                            sb.append(component);
                        } else {
                            ImmutableTerm arg = discardCasts(ft.getTerm(component.getIndex()));
                            if (!(arg instanceof Variable)) {
                                return null;
                            }
                            String var = ((Variable) arg).getName();
                            sb.append("{").append(var).append("}");
                        }
                    }
                    sb.append(">");
                    return sb.toString();
                }
            }
        }

        // Handle a literal template
        if (type instanceof RDFDatatype) {
            String datatype = ((RDFDatatype) type).getIRI().getIRIString();
            if (value instanceof Variable) {
                String var = ((Variable) value).getName();
                return "{" + var + "}^^<" + datatype + ">";
            }
        }

        // Return null for all unsupported cases
        return null;
    }

    private static @Nullable ImmutableTerm discardCasts(@Nullable ImmutableTerm term) {
        if (term instanceof NonGroundFunctionalTerm) {
            NonGroundFunctionalTerm ft = (NonGroundFunctionalTerm) term;
            FunctionSymbol s = ft.getFunctionSymbol();
            if (s instanceof DBTypeConversionFunctionSymbol && ft.getArity() == 1) {
                return ft.getTerm(0);
            }
        }
        return term;
    }

}
