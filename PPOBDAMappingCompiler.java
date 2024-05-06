package ppmappingcompiler;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import org.semanticweb.owlapi.model.OWLOntology;
import ppmappingcompiler.fol.*;
import ppmappingcompiler.fol.PredicateAtom.PredicateArityException;
import ppmappingcompiler.fol.Term.TermTypeException;
import ppmappingcompiler.parser.BCQParser;
import ppmappingcompiler.parser.DatalogBCQParser;
import ppmappingcompiler.parser.ParserException;
import ppmappingcompiler.policy.ConjunctiveQuery;
import ppmappingcompiler.policy.OntologyConjunctiveQuery;
import ppmappingcompiler.util.IOUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static ppmappingcompiler.fol.Formula.LC_LATIN_ALPHABET;
import static ppmappingcompiler.util.IOUtils.loadJsonArrayAsStringList;
import static ppmappingcompiler.util.OntologyUtils.*;
import static ppmappingcompiler.util.Utils.formatTemplate;

// [FC] Adapted from original PPOBDAMappingCompiler by removing code depending on Mastro

public class PPOBDAMappingCompiler extends CensoredViewCompiler {

    public PPOBDAMappingCompiler(Configuration config) {
        super(config);
    }

    public static void main(String[] args) throws Exception {

        Scanner scanner = new Scanner(System.in);

        // Prompt the user for policy filename
        System.out.print("Enter the policy filename: ");
        String policyFilename = scanner.nextLine();

        // Prompt the user for ontology filename
        System.out.print("Enter the ontology filename: ");
        String ontologyFilename = scanner.nextLine();

        // Close the scanner to avoid resource leaks
        scanner.close();

        // Print help message
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            printHelpMessage();
            System.exit(1);
        }

        // Default values for command-line arguments
        String EXEC_MODE = null;
        String CONFIG_FILENAME = "resources/ppmappingcompiler-test/config.ini";
        boolean STORE_LOG = false;

        // Read command-line arguments
        for (String arg : args) {
            if (arg.startsWith("-sl:")) STORE_LOG = !arg.substring(4).equalsIgnoreCase("false");
            else if (arg.startsWith("-c:")) CONFIG_FILENAME = arg.substring(3);
            else if (arg.equals("-h") || arg.equals("--help")) printHelpMessage();
        }

        // Import configurations
        // Configuration config = new Configuration(CONFIG_FILENAME);
        Configuration config = new Configuration(ontologyFilename, policyFilename);

        if (STORE_LOG) Logger.setLogPath(config.inputPath);
        Logger.setLogLevel(Logger.INFO);
        Logger.info("Start (execution mode: " + EXEC_MODE + ")");

        new PPOBDAMappingCompiler(config).run();

        Logger.info("Done");
    }

    public static void printHelpMessage() {
        System.out.println("Please call this program as follows:" +
                "\n\tjava -jar ppmappingcompiler.jar -m:EXEC_MODE [-c:CONFIG_FILE_NAME] [-sl:STORE_LOG]" +
                "\nwhere:" +
                "\n- EXEC_MODE must be \"mappings\" or \"query\"" +
                "\n- CONFIG_FILE_NAME is the name (or full path) of the configuration file (default: \"./config.ini\")" +
                "\n- STORE_LOG can be \"true\" or \"false\" (default is true)"
        );
    }

    public void run() throws Exception {

        Logger.info("Loading ontology");
        OWLOntology ontology = loadOntology(config.prependInputPath(config.ontologyFilename));
        Logger.drawLine();

        Logger.info("Reading denials");
        Set<OntologyConjunctiveQuery> policySet = loadPolicyFromJson(prependInputPath(config.policyFilename), ontology);
        Logger.drawLine();

        PriorityManager pm = new PriorityManager(ontology, config.transitivePriority);
        if (config.priorityFilename != null) {
            Logger.info("Reading priority relation");
            pm.loadPrioritiesFromJson(prependInputPath(config.priorityFilename));
            Logger.drawLine();
        }

        Logger.info("Finding redundancy conditions");
        Map<OntologyConjunctiveQuery, Set<FlatConjunction>> policyToRedundancyConditions = PolicyRefine.getRedundancyConditions(policySet);
        serializeRefinedQueries(policyToRedundancyConditions, prependInputPath(config.refinedPolicyFilename));

        // building FO formulas from CQ + RC
        Set<Conjunction> refinedPolicySet = new HashSet<>();
        policyToRedundancyConditions.forEach((query, redundancyConditions) -> {
            Conjunction conj = query.getBody();
            conj.add(new Negation(new Disjunction(redundancyConditions)));
            refinedPolicySet.add(conj);
        });

        Logger.drawLine();

        Logger.info("Encoding the policy into a 1-to-1 predicate mapping");
        PrioritizedRewriter pr = new PrioritizedRewriter(refinedPolicySet, pm, LC_LATIN_ALPHABET);
        Map<OntologyPredicateAtom, Formula> mappings = new HashMap<>(); // [FC] this map contains the redefined concepts
        for (OntologyPredicateAtom atom : getAtomsToRewrite(ontology, config.columnAliasPrefix)) {
            mappings.put(atom, pr.rewrite(atom, config.kValue));
        }

        // [FC] Anticipating optimization of formulae (is done after anyway) and logging of generated predicate mappings
        mappings.values().forEach(Formula::optimize);
        Logger.info("Resulting predicate mappings:\n\n" + Joiner.on('\n').withKeyValueSeparator(" <-- ").join(mappings) + "\n");

        Logger.drawLine();

        // [FC] This code was using Mastro to expand atoms w.r.t. the TBox. Commented out since we do that in Ontop
        // Logger.info("Rewriting atoms for simulating GA closure");
        // IMastroOntologyAPI mastro = MastroBasicClient.getMastroInstance();
        // AtomRewriter ar = new AtomRewriter(mastro, ontology);
        // //if(false)
        // for (Map.Entry<OntologyPredicateAtom, Formula> entry : mappings.entrySet()) {
        //     Formula rewriting = ar.rewrite(entry.getValue());
        //     Set<Variable> freeVars = rewriting.getFreeVariables();
        //     rewriting.explicitVariables(LC_LATIN_ALPHABET);
        //     Set<Variable> newVars = setDifference(rewriting.getFreeVariables(), freeVars);
        //     if (!newVars.isEmpty()) rewriting = new Exist(rewriting, newVars);
        //     mappings.replace(entry.getKey(), rewriting);
        // }
        // Logger.drawLine();

        // [FC] Code commented out since we generate SPARQL and not SQL against a DB schema
        // Logger.info("Compiling XML mappings");
        // DBSchema schema = new DBSchema(config.dbName);
        // String prefix = config.columnNamePrefix;
        // for (OntologyPredicateAtom atom : mappings.keySet()) {
        //     List<String> columnNames;
        //     switch (atom.getType()) {
        //         case CONCEPT:
        //             columnNames = Collections.singletonList(prefix);
        //             break;
        //         case ROLE:    // same below
        //         case ATTRIBUTE:
        //             columnNames = Arrays.asList(prefix + 1, prefix + 2);
        //             break;
        //         default:
        //             throw new IllegalStateException();
        //     }
        //     schema.addTable(new Table(atom.getPredicateName(), columnNames));
        // }
        // Map<OntologyPredicateAtom, String> viewsByAtom = getCensoredViews(mappings, schema); // original code that works

        // [FC] Here we convert the concepts redefinitions (map entries) into SPARQL SELECT queries
        Map<OntologyPredicateAtom, String> viewsByAtom = getCensoredViews(mappings);
        String outputJsonFilename = "predicatetosparql.json";
        writeMapToJsonFile(viewsByAtom, outputJsonFilename);

        // [FC] Log generated views
        Logger.info("Resulting views:\n\n" + Joiner.on("\n\n").withKeyValueSeparator("\n").join(viewsByAtom) + "\n");
    }

    private static <L extends PredicateAtom> Map<L, String> getCensoredViews(Map<L, Formula> mappings){
        Map<L, String> predicateToQuery = new HashMap<>();
        int numMappings = mappings.size();
        Logger.info("Number of views to generate:" + numMappings);
        int mappingCounter = 0;
        SparqlCompiler compiler = new SparqlCompiler();
        for (L atom : mappings.keySet()) {
            Logger.info(String.format("View #%d (of %d): %s", ++mappingCounter, numMappings, atom));
            Formula f = mappings.get(atom);
            f.optimize();
            predicateToQuery.put(atom, compiler.convertToSPARQL(f));
        }
        return predicateToQuery;
    }

    /*=====================*
     *  LOADING FUNCTIONS  *
     *=====================*/

    /**
     * This method reads the policy from a JSON file.
     *
     * @param policyFilePath The JSON file where the policy is stored.
     * @return A {@link List list} of {@link ConjunctiveQuery CQs}.
     */
    Set<OntologyConjunctiveQuery> loadPolicyFromJson(String policyFilePath, OWLOntology ontology) throws IOException, ParserException, TermTypeException, OntologyConjunctiveQuery.UnsafePolicyException {
        BCQParser policyParser = new DatalogBCQParser(ontology);
        Set<OntologyConjunctiveQuery> policySet = new HashSet<>();
        for (String s : loadJsonArrayAsStringList(policyFilePath)) {
            OntologyConjunctiveQuery q = policyParser.OntologyConjunctiveQuery(DatalogBCQParser.toBCQ(s));
            if (config.numberRestrictionsCheck) q.numberRestrictionsSafetyCheck();
            if (config.comparisonAtomsCheck) q.comparisonAtomsSafetyCheck();
            policySet.add(q);
        }
        return policySet;
    }

    /*=====================*
     *  ALGORITHM MODULES  *
     *=====================*/

    Set<OntologyPredicateAtom> getAtomsToRewrite(OWLOntology ontology, String varPrefix) throws PredicateArityException, TermTypeException, URISyntaxException {
        Map<String, Integer> predicateToArity = getPredicateToArityMap(ontology);

        Set<OntologyPredicateAtom> predicateAtoms = new HashSet<>();
        for (String predicate : predicateToArity.keySet()) {
            List<Term> variables = new ArrayList<>();
            Integer arity = predicateToArity.get(predicate);
            for (int i = 1; i <= arity; i++) {
                variables.add(new Variable(varPrefix + i));
            }
            OntologyPredicateAtom.Type type = OntologyPredicateAtom.getType(predicate, ontology);
            predicateAtoms.add(new OntologyPredicateAtom(predicate, variables, type));
        }
        predicateAtoms.removeIf(pa -> pa.getPredicateIRI().equals("http://www.w3.org/2002/07/owl#Thing"));
        return predicateAtoms;
    }

    private static Map<String, Integer> getPredicateToArityMap(OWLOntology tbox) {
        Set<String> fullPredicates = getOntologyPredicates(tbox);
        Map<String, Integer> resultMap = new HashMap<>();
        for (String predicate : fullPredicates) {
            OntologyPredicateAtom.Type type = OntologyPredicateAtom.getType(predicate, tbox);
            resultMap.put(predicate, type == OntologyPredicateAtom.Type.CONCEPT ? 1 : 2);
        }
        return resultMap;
    }

    private static void writeMapToJsonFile(Map<OntologyPredicateAtom, String> viewsByAtom, String outputJsonFilename) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        ArrayNode jsonArray = objectMapper.getNodeFactory().arrayNode();

        for (Map.Entry<OntologyPredicateAtom, String> entry : viewsByAtom.entrySet()) {
            OntologyPredicateAtom atom = entry.getKey();
            String sparqlQuery = entry.getValue();

            // Create a JSON object for each entry
            ObjectNode queryObject = objectMapper.getNodeFactory().objectNode();
            queryObject.put("predicate", atom.toString());
            queryObject.put("sparqlQuery", sparqlQuery);

            jsonArray.add(queryObject);
        }
        // Create a JSON object to store the array
        ObjectNode jsonOutput = objectMapper.getNodeFactory().objectNode();
        jsonOutput.put("queries", jsonArray);
        objectMapper.writer(prettyPrinter).writeValue(new File(outputJsonFilename), jsonOutput);
        System.out.println("Successfully wrote SPARQL queries to " + outputJsonFilename);

    }
}
