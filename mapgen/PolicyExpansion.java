package se.umea.mapgen;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.CharSource;
import com.google.common.io.MoreFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.node.*;
import it.unibz.inf.ontop.model.term.DBConstant;
import it.unibz.inf.ontop.model.term.VariableOrGroundTerm;
import it.unibz.inf.ontop.model.term.impl.DBConstantImpl;
import org.eclipse.rdf4j.query.algebra.ValueConstant;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import ppmappingcompiler.fol.*;
import ppmappingcompiler.parser.BCQParser;
import ppmappingcompiler.parser.DatalogBCQParser;
import ppmappingcompiler.parser.ParserException;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;
import ppmappingcompiler.util.IOUtils;
import se.umea.mapgen.reformulation.OntopReformulationAPI;
import se.umea.mapgen.reformulation.OntopReformulationResult;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Paths;
import java.nio.file.Files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;


public class PolicyExpansion {

    public static void main(String[] args) throws Throwable {

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter the path to the OBDA file (e.g., direct_mappings.obda): ");
        String obdaFile = scanner.nextLine();
        System.out.println("Enter the path to the OWL file (e.g., University.owl): ");
        String owlFile = scanner.nextLine();
        System.out.println("Enter the path to the JSON file (e.g., data.json): ");
        String jsonFile = scanner.nextLine();
        System.out.println("Enter the PolicyFile: ");
        String PolicyFile = scanner.nextLine();
        performReformulation(obdaFile, owlFile, jsonFile, PolicyFile);
        scanner.close();

    }

    public static void performReformulation(String obdaFile, String owlFile, String jsonFile, String PolicyFile) throws Throwable {

        //load the ontology
        OWLOntologyManager ontologyManager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology; // Define the ontology variable

        try (InputStream in = Files.newInputStream(Paths.get(owlFile))) {
            assert in != null;
            ontology = ontologyManager.loadOntologyFromOntologyDocument(in);
        }

        Properties properties = new Properties();
        properties.setProperty("jdbc.url", "jdbc:postgresql://localhost:5432/dummy"); // needed only to determine DB type
        properties.setProperty("ontop.existentialReasoning", "true"); // default is "false"
        properties.setProperty("it.unibz.inf.ontop.iq.planner.QueryPlanner", "se.umea.mapgen.reformulation.OntopUnionLifterPlanner");

        //feeding all input files to OnTop
        CharSource mappingSource = MoreFiles.asCharSource(Paths.get(obdaFile), Charsets.UTF_8);
        CharSource ontologySource = MoreFiles.asCharSource(Paths.get(owlFile), Charsets.UTF_8);
        CharSource metadataSource = MoreFiles.asCharSource(Paths.get(jsonFile), Charsets.UTF_8);

        //Load the policy
        List<OntologyConjunctiveQuery> policyRules = loadPolicyFromJson(PolicyFile, ontology);
        List<OntologyConjunctiveQuery> expandedPolicyRules = new ArrayList<>();

        //Intialize OnTop functionality to rewrite the queries
        OntopReformulationAPI rewriter = new OntopReformulationAPI(properties, mappingSource, ontologySource, metadataSource);

        // Print the policyRules that are converted into SPARQL ASK queries (for feeding into OnTop)
        for (OntologyConjunctiveQuery policyRule : policyRules) {
            //Convert OntologyConjuctiveQuery (Java object) into Sparql Ask Query (which is a string)
            String policyRuleAsSparqlQuery = policyRule.toSparql();
            System.out.println("Datalog rule: " + policyRule);
            System.out.println("Sparql rule: " + policyRuleAsSparqlQuery);
            System.out.println();

            //Call OnTop to rewrite Sparql Ask query w.r.t Ontology and mappings
            OntopReformulationResult result = rewriter.reformulate(policyRule.toSparql());

            //Converting the IQ generated by OnTop into a List of OntologyConjuctiveQuery
            IQ iq = result.getReformulatedQueryIQ();
            List<OntologyConjunctiveQuery> queries = Lists.newArrayList();
            generateOntologyConjunctiveQueries(iq.getTree(), ontology, queries);
            expandedPolicyRules.addAll(queries);

            for (OntologyConjunctiveQuery query : queries) {
                System.out.println(query);
            }

        }

        //Convert (List of) OntologyConjuctiveQuery into Datalog Rule (which are string)
        List<String> rulesAsStrings = new ArrayList<>();
        for (OntologyConjunctiveQuery query : expandedPolicyRules) {
            String ruleAsString = query.toString(); // this produces something like "Q() :- A(x), R(x,y) ."
            rulesAsStrings.add(ruleAsString);
        }

        //Intialize Jackson ObjectMapper require to write json(of expanded policies)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        //Write expanded policies into json file
        try {
            // you may ask the user how to call the output file
            objectMapper.writer(prettyPrinter).writeValue(new File("expanded_policy.json"), rulesAsStrings);
            String Expanded_Policy_Filename = "expanded_policy.json";
            System.out.println("JSON file with expanded policy generated successfully: "+Expanded_Policy_Filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateOntologyConjunctiveQueries(IQTree t, OWLOntology ontology, List<OntologyConjunctiveQuery> outputQueries) throws Exception {

        // cases to be considered of IQs(the OnTop output) to covert it to OntologyConjuctiveQuery:
        //
        //1. UNION
        //      JOIN
        //          EXT1
        //          EXT2
        //      EXT3
        //
        //2. JOIN
        //      EXT1
        //      EXT2
        //
        //3. EXT


        if (t.getRootNode() instanceof UnionNode) {
            for (IQTree c : t.getChildren()) {
                generateOntologyConjunctiveQueries(c, ontology, outputQueries);
            }

        } else if (t.getRootNode() instanceof InnerJoinNode) {
            List<PredicateAtom> atoms = Lists.newArrayList();
            for (IQTree c : t.getChildren()) {
                if (c.getRootNode() instanceof ExtensionalDataNode) {
                    ExtensionalDataNode n = (ExtensionalDataNode) c.getRootNode();
                    atoms.add(generateAtom(n, ontology));
                } else {
                    throw new IllegalArgumentException("IQ contains unsupported node " + c);
                }
            }
            outputQueries.add(new OntologyConjunctiveQuery(atoms));

        } else if (t.getRootNode() instanceof ExtensionalDataNode) {
            ExtensionalDataNode n = (ExtensionalDataNode) t.getRootNode();
            OntologyPredicateAtom atom = generateAtom(n, ontology);
            outputQueries.add(new OntologyConjunctiveQuery(Arrays.asList(atom)));

        } else if (t.getRootNode() instanceof SliceNode || t.getRootNode() instanceof ConstructionNode) {
            generateOntologyConjunctiveQueries(t.getChildren().get(0), ontology, outputQueries); // expect just one child

        } else {
            throw new IllegalArgumentException("IQ contains unsupported node " + t);
        }
    }

    private static final AtomicInteger COUNTER = new AtomicInteger(); // for generating unique variables

    private static OntologyPredicateAtom generateAtom(ExtensionalDataNode n, OWLOntology ontology) throws Exception {

        String csvFile = "iri_relation_names.csv"; // Replace with the path to your CSV file
        Map<String, String> relationToIriMap = new HashMap();

        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                String iri = parts[0].trim();
                String relation = parts[1].trim();
                relationToIriMap.put(relation, iri);
            }
        }

        br.close(); // Close the BufferedReader when done

        // Get the relation name without quotes
        String relation = n.getRelationDefinition().getAtomPredicate().getName();
        if (relation.startsWith("\"")) {
            relation = relation.substring(1, relation.length() - 1);
        }

        // Get the predicate (concept or property) IRI
        String iri = relationToIriMap.get(relation);

        // Get the terms
        List<Term> terms = Lists.newArrayList();
        for (int i = 0; i < n.getRelationDefinition().getAtomPredicate().getArity(); ++i) {
            VariableOrGroundTerm ontopTerm = n.getArgumentMap().get(i);
            if (ontopTerm == null) {
                terms.add(new Variable("x" + COUNTER.incrementAndGet()));
            } else if (ontopTerm instanceof it.unibz.inf.ontop.model.term.Variable) {
                String varName = ((it.unibz.inf.ontop.model.term.Variable) ontopTerm).getName();
                terms.add(new Variable(varName));
            }else if (ontopTerm instanceof it.unibz.inf.ontop.model.term.DBConstant) {
                    String objectConstantIRI = ((it.unibz.inf.ontop.model.term.DBConstant) ontopTerm).getValue();
                    terms.add(new ObjectConstant(objectConstantIRI));
            }
        }

        // Build the atom
        return new OntologyPredicateAtom(iri, terms, ontology);
    }

    public static List<OntologyConjunctiveQuery> loadPolicyFromJson(String PolicyFile, OWLOntology ontology)
            throws IOException, ParserException, Term.TermTypeException {

        // Read the policy JSON file as a list of strings
        String policyJsonString = IOUtils.readFile(PolicyFile);
        JsonArray policyJsonArray = (JsonArray) JsonParser.parseString(policyJsonString);
        List<String> policyRulesAsStrings = new ArrayList<>();
        for (JsonElement e : policyJsonArray) {
            policyRulesAsStrings.add(e.getAsString());
        }

        // Parse the rules from strings to OntologyConjunctiveQuery objects
        BCQParser policyParser = new DatalogBCQParser(ontology);
        List<OntologyConjunctiveQuery> policyRules = new ArrayList<>();
        for (String policyRuleAsString : policyRulesAsStrings) {
            OntologyConjunctiveQuery q = policyParser.OntologyConjunctiveQuery(DatalogBCQParser.toBCQ(policyRuleAsString));
            policyRules.add(q);
        }
        return policyRules;
    }


}

