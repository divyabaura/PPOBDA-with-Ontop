package se.umea.mapgen.reformulation;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import it.unibz.inf.ontop.answering.logging.QueryLogger;
import it.unibz.inf.ontop.answering.reformulation.QueryReformulator;
import it.unibz.inf.ontop.exception.OBDASpecificationException;
import it.unibz.inf.ontop.exception.OntopKGQueryException;
import it.unibz.inf.ontop.exception.OntopReformulationException;
import it.unibz.inf.ontop.injection.OntopMappingSQLAllOWLAPIConfiguration;
import it.unibz.inf.ontop.injection.OntopReformulationSQLConfiguration;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.UnaryIQTree;
import it.unibz.inf.ontop.iq.node.NativeNode;
import it.unibz.inf.ontop.query.KGQueryFactory;
import it.unibz.inf.ontop.query.SPARQLQuery;
import it.unibz.inf.ontop.spec.OBDASpecification;
import it.unibz.inf.ontop.spec.ontology.InconsistentOntologyException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Simple wrapper of Ontop reformulation logics.
 * <p>
 * This class takes care of initializing and invoking the necessary bits of Ontop involved in reformulating a VKG SPARQL
 * query w.r.t. an OBDA specification, which involves rewriting the query w.r.t. the ontology and performing mapping
 * unfolding.
 * </p>
 * <p>
 * Instances of this class can be built starting from mapping, ontology, database metadata and Ontop configuration
 * properties. For the latter, consider setting the following properties:
 * <ul>
 * <li>{@code jdbc.url} (mandatory)
 * - needed only to determine DB type and use the corresponding dialect;</li>
 * <li>{@code ontop.existentialReasoning} (optional)
 * - set to "true" to enable existential reasoning (default is "false");</li>
 * <li>{@code ontop.reformulateToFullNativeQuery} (optional)
 * - set to "true" to force Ontop to generate a native query returning a column for each variable projected in the
 * input VKG SPARQL query, delegating to the DB the task of applying IRI templates (default is "false");</li>
 * <li>{@code it.unibz.inf.ontop.iq.planner.QueryPlanner} (optional)
 * - set to "se.umea.mapgen.reformulation.OntopUnionLifterPlanner" to always move unions above joins;</li>
 * </ul>
 * </p>
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
@NonNullByDefault
public final class OntopReformulationAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger(OntopReformulationAPI.class);

    private final OBDASpecification obdaSpecification;

    private final QueryReformulator queryReformulator;

    /**
     * Creates a new {@code OntopReformulationAPI} object for the supplied properties, mapping, ontology and database
     * metadata files.
     *
     * @param propertiesFile the Ontop properties
     * @param mappingFile    the mappings
     * @param ontologyFile   the ontology
     * @param metadataFile   the database metadata
     * @throws IOException                if an I/O error occurs when accessing mapping/ontology/metadata
     * @throws OBDASpecificationException if the supplied mapping/ontology/metadata define an invalid OBDA specification
     */
    public OntopReformulationAPI(Path propertiesFile, Path mappingFile, @Nullable Path ontologyFile,
                                 @Nullable Path metadataFile) throws IOException, OBDASpecificationException {

        // Load properties and map paths to corresponding file sources, assuming UTF-8 encoding
        this(loadProperties(propertiesFile),
                MoreFiles.asCharSource(mappingFile, Charsets.UTF_8),
                ontologyFile == null ? null : MoreFiles.asCharSource(ontologyFile, Charsets.UTF_8),
                metadataFile == null ? null : MoreFiles.asCharSource(metadataFile, Charsets.UTF_8));
    }

    /**
     * Creates a new {@code OntopReformulationAPI} object for the supplied Ontop properties and mapping, ontology and
     * database metadata {@code CharSource}s. A {@code CharSource} can accommodate a generic source of character data, including
     * a file or a plain Java String.
     *
     * @param properties     the Ontop properties
     * @param mappingSource  the mappings
     * @param ontologySource the ontology
     * @param metadataSource the database metadata
     * @throws IOException                if an I/O error occurs when accessing mapping/ontology/metadata
     * @throws OBDASpecificationException if the supplied mapping/ontology/metadata define an invalid OBDA specification
     */
    public OntopReformulationAPI(Properties properties, CharSource mappingSource, @Nullable CharSource ontologySource,
                                 @Nullable CharSource metadataSource) throws IOException, OBDASpecificationException {

        // Check input parameters
        Objects.requireNonNull(properties);
        Objects.requireNonNull(mappingSource);

        // Variables for Reader objects allocated on demand and closed at the end
        Reader mappingReader = null, ontologyReader = null, metadataReader = null;

        try {
            // Obtain the OBDASpecification object, possibly leveraging a DB metadata file to avoid connecting to the DB
            OntopMappingSQLAllOWLAPIConfiguration.Builder<?> mappingConfigBuilder = OntopMappingSQLAllOWLAPIConfiguration.defaultBuilder();
            mappingConfigBuilder.properties(properties);
            mappingReader = mappingSource.openBufferedStream();
            mappingConfigBuilder.nativeOntopMappingReader(mappingReader);
            if (ontologySource != null) {
                ontologyReader = ontologySource.openBufferedStream();
                mappingConfigBuilder.ontologyReader(ontologyReader);
            }
            if (metadataSource != null) {
                metadataReader = metadataSource.openBufferedStream();
                mappingConfigBuilder.dbMetadataReader(metadataReader);
            }
            OntopMappingSQLAllOWLAPIConfiguration mappingConfig = mappingConfigBuilder.build(); // may use this config to get other Ontop objects
            obdaSpecification = mappingConfig.loadSpecification();

            // Obtain the Ontop QueryReformulator object
            OntopReformulationSQLConfiguration reformulationConfig = OntopReformulationSQLConfiguration.defaultBuilder()
                    .properties(properties)
                    .enableExistentialReasoning(true)
                    .obdaSpecification(obdaSpecification)
                    .build(); // may use this object to get other Ontop objects if needed
            queryReformulator = reformulationConfig.loadQueryReformulator();

        } finally {
            // Close all readers to free underlying files/resources
            closeQuietly(ontologyReader);
            closeQuietly(mappingReader);
            closeQuietly(metadataReader);
        }
    }

    /**
     * Returns the wrapped Ontop {@code OBDASpecification} object.
     *
     * @return the OBDA specification
     */
    public OBDASpecification getOBDASpecification() {
        return obdaSpecification;
    }

    /**
     * Returns the wrapped Ontop {@code QueryReformulator} object.
     *
     * @return the query reformulator object
     */
    public QueryReformulator getQueryReformulator() {
        return queryReformulator;
    }

    /**
     * Reformulates the supplied VKG SPARQL query using Ontop.
     *
     * @param vkgQuerySPARQL the SPARQL expression for the input VKG query to reformulate
     * @return a result object containing input, output and intermediate results of Ontop reformulation
     * @throws OntopKGQueryException       if the input query is not valid
     * @throws OntopReformulationException if query reformulation fails
     */
    public OntopReformulationResult reformulate(String vkgQuerySPARQL) throws OntopKGQueryException, OntopReformulationException {

        // Check input parameter
        Objects.requireNonNull(vkgQuerySPARQL);

        // Parse the input SPARQL query (will fail if invalid)
        KGQueryFactory kgQueryFactory = queryReformulator.getInputQueryFactory();
        SPARQLQuery<?> vkgQuery = kgQueryFactory.createSPARQLQuery(vkgQuerySPARQL);

        // Reformulate the query, collecting final and intermediate results into a OntopReformulationResult object
        ResultLogger resultLogger = new ResultLogger();
        resultLogger.setSparqlQuery(vkgQuerySPARQL);
        try {
            queryReformulator.reformulateIntoNativeQuery(vkgQuery, resultLogger);
        } catch (OntopReformulationException ex) {
            if (!ex.getMessage().contains("IQ: EMPTY")) {
                throw ex; // TODO: remove once ToFullNativeQueryReformulator is fixed
            }
        }
        return resultLogger.toResult();
    }

    private static Properties loadProperties(Path propertiesFile) throws IOException {
        Properties properties = new Properties();
        try (Reader in = Files.newBufferedReader(propertiesFile)) {
            properties.load(in);
        }
        return properties;
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable ex) {
                // Ignore
            }
        }
    }

    private static class ResultLogger implements QueryLogger {

        private @Nullable String vkgQuerySPARQL;

        private @Nullable IQ vkgQueryIQ;

        private @Nullable IQ reformulatedQueryIQ;

        private @Nullable IQ nativeQueryIQ;

        @Override
        public void setSparqlQuery(String sparql) {
            this.vkgQuerySPARQL = sparql;
        }

        @Override
        public void setSparqlIQ(IQ iq) {
            this.vkgQueryIQ = iq;
        }

        @Override
        public void setPlannedQuery(IQ iq) {
            this.reformulatedQueryIQ = iq;
        }

        @Override
        public void declareReformulationFinishedAndSerialize(IQ iq, boolean wasCached) {
            this.nativeQueryIQ = iq;
        }

        @Override
        public void declareReformulationException(OntopReformulationException e) {
            // ignored, will handle exception elsewhere
        }

        @Override
        public void declareConversionException(InconsistentOntologyException e) {
            // ignored, will handle exception elsewhere
        }

        @Override
        public void setPredefinedQuery(String queryId, ImmutableMap<String, String> bindings) {
            assert false; // Not expected to be called
        }

        @Override
        public void declareResultSetUnblockedAndSerialize() {
            assert false; // Not expected to be called
        }

        @Override
        public void declareLastResultRetrievedAndSerialize(long rowCount) {
            assert false; // Not expected to be called
        }

        @Override
        public void declareConnectionException(Exception e) {
            assert false; // Not expected to be called
        }

        @Override
        public void declareEvaluationException(Exception e) {
            assert false; // Not expected to be called
        }

        public OntopReformulationResult toResult() {

            // Required fields expected to be all available here (if not, execution fails before calling toResult)
            assert vkgQuerySPARQL != null;
            assert vkgQueryIQ != null;

            // Retrieve the SQL for the native query, if possible
            String nativeQuerySQL = Optional.ofNullable(nativeQueryIQ)
                    .map(IQ::getTree)
                    .filter(t -> t instanceof UnaryIQTree)
                    .map(t -> ((UnaryIQTree) t).getChild().getRootNode())
                    .filter(n -> n instanceof NativeNode)
                    .map(n -> ((NativeNode) n).getNativeQueryString())
                    .orElse(null);

            // Return a result object for either an empty query (only input fields) or a non-empty one (all fields)
            if (nativeQueryIQ == null || nativeQuerySQL == null) {
                return new OntopReformulationResult(vkgQuerySPARQL, vkgQueryIQ);
            } else {
                // Recover the reformulatedQueryIQ from the nativeQueryIQ, in case not explicitly returned by Ontop
                // (this may happen in certain unexpected cases, e.g., if a cached native query is reused)
                IQ reformulatedQueryIQ = MoreObjects.firstNonNull(this.reformulatedQueryIQ, nativeQueryIQ);
                return new OntopReformulationResult(vkgQuerySPARQL, vkgQueryIQ, reformulatedQueryIQ,
                        nativeQueryIQ, nativeQuerySQL);
            }
        }

    }

}
