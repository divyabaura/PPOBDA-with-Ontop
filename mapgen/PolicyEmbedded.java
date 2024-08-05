package se.umea.mapgen;
import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import ppmappingcompiler.fol.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.semanticweb.owlapi.model.OWLOntology;
import ppmappingcompiler.parser.BCQParser;
import ppmappingcompiler.parser.DatalogBCQParser;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;
import ppmappingcompiler.util.OntologyUtils;
import se.umea.mapgen.reformulation.OntopReformulationAPI;
import se.umea.mapgen.reformulation.OntopReformulationResult;

import java.io.IOException;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.io.PrintWriter;

public class PolicyEmbedded {

    public static void main(String[] args) throws Exception{

        // Specify the paths to the ontology, mapping, metadata, and SPARQL queries JSON file

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the path to the ontology file (e.g., University.owl): ");
        String ontologyFilePath = scanner.nextLine();
        System.out.print("Enter the path to the mapping file (e.g., Original_mappings.obda): ");
        String mappingFilePath = scanner.nextLine();
        System.out.print("Enter the path to the original database schema file (e.g., Original_DBschema.json): ");
        String metadataFilePath = scanner.nextLine();
        System.out.print("Enter the path to the SPARQL JSON file (e.g., predicatetosparql.json): ");
        String sparqlJsonFilePath = scanner.nextLine();
        scanner.close();


        //load the ontology
        OWLOntologyManager ontologyManager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology; // Define the ontology variable

        try (InputStream in = Files.newInputStream(Paths.get(ontologyFilePath))) {
            assert in != null;
            ontology = ontologyManager.loadOntologyFromOntologyDocument(in);
        }

        // Load SPARQL queries from JSON file
        Map<OntologyPredicateAtom, String> viewsByAtom = loadSparqlQueriesFromJson(sparqlJsonFilePath, ontology);
        System.out.println(viewsByAtom);

        // Generate SQL queries
        Map<OntologyPredicateAtom, OntopReformulationResult> predicatetoSQLQuery = generateSQL(ontologyFilePath, mappingFilePath, metadataFilePath, viewsByAtom);
        System.out.println("Generated SQL Queries: " + predicatetoSQLQuery);

        // Generate mapping file
        String outputMappingFile = "output_mapping.obda";
        generateMappingFile(predicatetoSQLQuery, outputMappingFile);
    }

    private static Map<OntologyPredicateAtom, String> loadSparqlQueriesFromJson(String filePath, OWLOntology ontology) throws Exception {
        Map<OntologyPredicateAtom, String> predicatetoSparqlQuery = new HashMap<>();
        try (FileReader reader = new FileReader(filePath)) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(reader);
            JsonNode queriesNode = root.path("queries");

            if (queriesNode.isArray()) {
                ArrayNode queriesArray = (ArrayNode) queriesNode;
                for (JsonNode queryNode : queriesArray) {
                    String predicate = queryNode.path("predicate").asText();
                    String sparqlQuery = queryNode.path("sparqlQuery").asText();

                    // Parse the predicate using DatalogBCQParser
                    OntologyPredicateAtom atom = parsePredicate(predicate, ontology);

                    predicatetoSparqlQuery.put(atom, sparqlQuery);
                }
            }
        }
        return predicatetoSparqlQuery;
    }

    private static OntologyPredicateAtom parsePredicate(String predicate, OWLOntology ontology) throws Exception{
        BCQParser bcqParser = new DatalogBCQParser(ontology);
        return bcqParser.OntologyPredicateAtom(predicate);
    }

    private static Map<OntologyPredicateAtom,OntopReformulationResult> generateSQL(String ontologyFile, String mappingFile, String metadataFile, Map<OntologyPredicateAtom,String>predicatetoSparqlQuery) throws Exception{
        Properties properties = new Properties();
        properties.setProperty("jdbc.url", "jdbc:postgresql://localhost:5432/dummy"); //ToDO: handle database could be different from postgres    //needed only to determine DB type
        properties.setProperty("ontop.existentialReasoning", "false"); // default is "false"
        properties.setProperty("ontop.reformulateToFullNativeQuery", "true"); // default is "false"

        //feeding all input files to OnTop
        CharSource mappingSource = MoreFiles.asCharSource(Paths.get(mappingFile), Charsets.UTF_8);
        CharSource ontologySource = MoreFiles.asCharSource(Paths.get(ontologyFile), Charsets.UTF_8);
        CharSource metadataSource = MoreFiles.asCharSource(Paths.get(metadataFile), Charsets.UTF_8);

        //Intialize OnTop functionality to rewrite the queries
        OntopReformulationAPI rewriter = new OntopReformulationAPI(properties, mappingSource, ontologySource, metadataSource);
        Map<OntologyPredicateAtom, OntopReformulationResult> predicatetoSQLQuery = new HashMap<>();

        // Iterate over each entry in the predicatetoSparqlQuery map
        for (Map.Entry<OntologyPredicateAtom, String> entry : predicatetoSparqlQuery.entrySet()) {
            OntologyPredicateAtom atom = entry.getKey();
            String sparqlQuery = entry.getValue();

            // Using the OntopReformulationAPI to rewrite the SPARQL query to SQL
            OntopReformulationResult result = rewriter.reformulate(sparqlQuery);
            //String sqlQuery = result.getNativeQuerySQL();

            // Add the result to the map of SQL queries
            predicatetoSQLQuery.put(atom, result);
        }

        // Return the map of generated SQL queries
        return predicatetoSQLQuery;
    }

    private static void generateMappingFile(Map<OntologyPredicateAtom, OntopReformulationResult> predicatetoSQLQuery, String outputMappingFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputMappingFile, StandardCharsets.UTF_8)) {

            // Emit prefixes block, currently empty
            writer.println("[PrefixDeclaration]\n");

            // Emit mappings block
            writer.println("[MappingDeclaration] @collection [[");
            boolean firstMapping = true;
            for (Map.Entry<OntologyPredicateAtom, OntopReformulationResult> e : predicatetoSQLQuery.entrySet()) {
                OntologyPredicateAtom predicate = e.getKey();
                OntopReformulationResult result = e.getValue();

                // Skip the mapping for this predicate in case the reformulated query is empty (no results possible)
                if (result.isEmpty()) {
                    continue;
                }

                String sqlQuery = result.getNativeQuerySQL();
                Map<String, String> targetMap = result.getNativeQueryTargetMap();

                String target = null;
                if (predicate.getArity() == 1) {
                    // handle a class
                    String subjVar = predicate.getVariables().get(0).getName();
                    target = targetMap.get(subjVar) + " a <" + predicate.getPredicateIRI() + "> .";
                } else {
                    // handle a property
                    String subjVar = predicate.getVariables().get(0).getName();
                    String objVar = predicate.getVariables().get(1).getName();
                    target = targetMap.get(subjVar) + " <" + predicate.getPredicateIRI() + "> " + targetMap.get(objVar) + " .";
                }

                if (!firstMapping) {
                    writer.println();
                }
                firstMapping = false;

                String mapping = "" +
                        "mappingId  " + predicate.toString() + "\n" +
                        "target     " + target + "\n" +
                        "source     " + sqlQuery.trim().replace("\n", "\n           ");

                writer.println(mapping);
            }
            writer.println("]]");
        }
    }
}




