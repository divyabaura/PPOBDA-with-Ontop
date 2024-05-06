package se.umea.mapgen;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class DummyMappingGenerator {
    public static void main(String[] args) throws OWLOntologyCreationException, IOException {
        // Create an OWLOntologyManager
        OWLOntologyManager ontologyManager = OWLManager.createOWLOntologyManager();

        // Load the ontology
        OWLOntology ontology = ontologyManager.loadOntologyFromOntologyDocument(new File("dummy2.owl"));

        // Create a map to store the mapping of IRIs to relation names
        Map<String, String> iriToRelationMap = new LinkedHashMap<>();
        Map<String, String> relationToIriMap = new LinkedHashMap<>();

        // Extract classes and roles as relation names
        List<String> relationNames = new ArrayList<>();
        List<String> twoAttributeRelationNames = new ArrayList<>();

        int counter = 0;
        for (OWLClass cls : ontology.getClassesInSignature()) {
            // Skip the "owl:Thing" class
            if (cls.isOWLThing()) {
                continue;
            }
            String iri = cls.getIRI().getIRIString();
            String relationName = cls.getIRI().getFragment() + (++counter);
            relationNames.add(relationName);
            iriToRelationMap.put(iri, relationName);
            relationToIriMap.put(relationName, iri);
        }

        // Process relations with two attributes
        for (OWLObjectProperty property : ontology.getObjectPropertiesInSignature()) {
            String iri = property.getIRI().getIRIString();
            String twoAttributeRelationName = property.getIRI().getFragment() + (++counter);
            twoAttributeRelationNames.add(twoAttributeRelationName);
            iriToRelationMap.put(iri, twoAttributeRelationName);
            relationToIriMap.put(twoAttributeRelationName, iri);
        }

        // Create an SQL file to write the CREATE TABLE and INSERT statements
        String sqlFileName = "dummy_tables.sql";
        BufferedWriter sqlFileWriter = new BufferedWriter(new FileWriter(sqlFileName));

        // Create an OBDA file to write the mappings
        String obdaFileName = "dummy_mappings.obda";
        BufferedWriter obdaFileWriter = new BufferedWriter(new FileWriter(obdaFileName));

        // Define the ontology prefix
        obdaFileWriter.write("[PrefixDeclaration]\n");
        obdaFileWriter.write(":     http://example.org/my-ontology#\n");
        obdaFileWriter.write("owl:  http://www.w3.org/2002/07/owl#\n");
        obdaFileWriter.write("rdf:  http://www.w3.org/1999/02/22-rdf-syntax-ns#\n");
        obdaFileWriter.write("xml:  http://www.w3.org/XML/1998/namespace\n");
        obdaFileWriter.write("xsd:  http://www.w3.org/2001/XMLSchema#\n");
        obdaFileWriter.write("obda: https://w3id.org/obda/vocabulary#\n");
        obdaFileWriter.write("rdfs: http://www.w3.org/2000/01/rdf-schema#\n\n");

        // Initialize the MappingDeclaration section
        obdaFileWriter.write("[MappingDeclaration] @collection [[\n");

        // Iterate through classes and generate CREATE TABLE statements
        for (String relationName : relationNames) {
            String tableName = relationName;
            String iri = relationToIriMap.get(relationName);
            String createTableSQL = "CREATE TABLE \"" + tableName + "\" (\n"
                    + "    attribute VARCHAR(255) NOT NULL\n"
                    + ");\n";

            // Write CREATE TABLE statement to the SQL file
            sqlFileWriter.write(createTableSQL);


            // Generate INSERT statements with unique values
            StringBuilder insertDataSQL = new StringBuilder();
            for (int i = 1; i <= 3; i++) {
                insertDataSQL.append("INSERT INTO \"").append(tableName).append("\" (attribute) VALUES\n");
                insertDataSQL.append(" ('").append(tableName.toLowerCase()).append("_").append(i).append("');\n");
            }

            // Write INSERT statements to the SQL file
            sqlFileWriter.write(insertDataSQL.toString());

            // Generate OBDA mappings for the class
            obdaFileWriter.write("mappingId   " + tableName + "\n");
            obdaFileWriter.write("target      :{attribute} a <" + iri + "> .\n");
            obdaFileWriter.write("source      SELECT attribute FROM \"" + tableName + "\"\n\n");
        }

        // Iterate through roles and generate CREATE TABLE statements
        for (String relationName : twoAttributeRelationNames) {
            String tableName = relationName;
            String iri = relationToIriMap.get(relationName);
            String createTableSQL = "CREATE TABLE \"" + tableName + "\" (\n"
                    + "    attribute1 VARCHAR(255) NOT NULL,\n"
                    + "    attribute2 VARCHAR(255) NOT NULL\n"
                    + ");\n";

            // Write CREATE TABLE statement to the SQL file
            sqlFileWriter.write(createTableSQL);

            // Generate INSERT statements with unique values
            StringBuilder insertDataSQL = new StringBuilder();
            for (int i = 1; i <= 3; i++) {
                insertDataSQL.append("INSERT INTO \"").append(tableName).append("\" (attribute1, attribute2) VALUES\n");
                insertDataSQL.append(" ('").append(tableName.toLowerCase()).append("_").append(i).append("_1', '").append(tableName.toLowerCase()).append("_").append(i).append("_2');\n");
            }

            // Write INSERT statements to the SQL file
            sqlFileWriter.write(insertDataSQL.toString());

            // Generate OBDA mappings for the relation
            obdaFileWriter.write("mappingId   " + relationName + "\n");
            obdaFileWriter.write("target      :{attribute1} <" + iri + ">  :{attribute2} .\n");
            obdaFileWriter.write("source      SELECT attribute1, attribute2 FROM \"" + tableName + "\"\n\n");
        }

        // Close the MappingDeclaration section
        obdaFileWriter.write("]]\n");

        // Close the SQL file
        sqlFileWriter.close();
        System.out.println("SQL file generated: " + sqlFileName);

        // Close the OBDA file
        obdaFileWriter.close();
        System.out.println("OBDA file generated: " + obdaFileName);


        // Generate JSON file
        generateJsonFile(relationNames, twoAttributeRelationNames);

        // Generate CSV file
        generateCsvFile(iriToRelationMap);
    }

    public static void generateJsonFile(List<String> relationNames, List<String> twoAttributeRelationNames) {
        // Create an ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Create a list to hold all the relation objects
        List<Map<String, Object>> relations = new ArrayList<>();

        // Create a map for metadata
        Map<String, Object> metadata = Map.of(
                "dbmsProductName", "PostgreSQL",
                "dbmsVersion", "14.9",
                "driverName", "PostgreSQL JDBC Driver",
                "driverVersion", "42.3.1",
                "quotationString", "\"",
                "extractionTime", "2023-10-06T15:34:33",
                "idFactoryType", "POSTGRESQL"
        );

        // Iterate through the provided relation names with one attribute and create relation objects
        for (String relationName : relationNames) {
            List<Map<String, Object>> columns = new ArrayList<>();
            Map<String, Object> column = Map.of(
                    "name", "\"attribute\"",
                    "datatype", "varchar",
                    "isNullable", false
            );
            columns.add(column);

            Map<String, Object> relationObject = Map.of(
                    "columns", columns,
                    "name", List.of("\"" + relationName + "\""),
                    "otherNames", List.of(),
                    "uniqueConstraints", List.of(),
                    "foreignKeys", List.of()
            );

            relations.add(relationObject);
        }

        // Iterate through the relation names with two attributes and create relation objects
        for (String relationName : twoAttributeRelationNames) {
            List<Map<String, Object>> columns = new ArrayList<>();
            Map<String, Object> column1 = Map.of(
                    "name", "\"attribute1\"",
                    "datatype", "varchar",
                    "isNullable", false
            );
            Map<String, Object> column2 = Map.of(
                    "name", "\"attribute2\"",
                    "datatype", "varchar",
                    "isNullable", false
            );
            columns.add(column1);
            columns.add(column2);

            Map<String, Object> relationObject = Map.of(
                    "columns", columns,
                    "name", List.of("\"" + relationName + "\""),
                    "otherNames", List.of(),
                    "uniqueConstraints", List.of(),
                    "foreignKeys", List.of()
            );

            relations.add(relationObject);
        }

        // Create a JSON object that represents the "relations" and "metadata" keys
        Map<String, Object> jsonStructure = new HashMap<>();
        jsonStructure.put("relations", relations);
        jsonStructure.put("metadata", metadata);

        try {
            // Convert the JSON object to JSON and write it to a file
            objectMapper.writeValue(new File("data.json"), jsonStructure);
            String JsonFileName = "data.json";
            System.out.println("JSON file generated successfully: "+ JsonFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void generateCsvFile(Map<String, String> iriToRelationMap) throws IOException {
        String csvFileName = "iri_relation_names.csv";
        BufferedWriter csvFileWriter = new BufferedWriter(new FileWriter(csvFileName));

        // Write header to the CSV file
        csvFileWriter.write("IRI,RelationName\n");

        // Process the map and write IRIs and relation names
        for (Map.Entry<String, String> entry : iriToRelationMap.entrySet()) {
            String iri = entry.getKey();
            String relationName = entry.getValue();
            csvFileWriter.write(iri + "," + relationName + "\n");
        }

        csvFileWriter.close();
        System.out.println("CSV file generated: " + csvFileName);
    }
}

