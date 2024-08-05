package ppmappingcompiler;

import org.ini4j.Ini;
import org.ini4j.IniPreferences;
import ppmappingcompiler.util.IOUtils;
import ppmappingcompiler.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

public class Configuration {

    public String inputPath;
    public String policyFilename;
    public String refinedPolicyFilename;
    public String testQueryFilename;
    public String queryOutputFilename;

    // only for OBDA mappings
    public String ontologyFilename;
    public String generatedMappingsFilename;

    // only for SQL views
    public String tablesFilename;
    public String fkFilename;
    public String generatedViewsFilename;

    // for priority-based rewriting
    public String priorityFilename;
    public int kValue = 1;
    public boolean transitivePriority = false;

    // other flags
    public boolean numberRestrictionsCheck = false;
    public boolean comparisonAtomsCheck = false;

    public String dbUser;
    public String dbPassword;
    public String dbName;
    public String dbPortNumber;
    public String jdbcDriver;
    public String jdbcConnectionOptions;

    public String viewSuffix = "";
    public String columnNamePrefix = "obj";
    public String columnAliasPrefix = "X";

    public final String defaultJdbcDriver = "com.mysql.cj.jdbc.Driver";

    private String defaultJdbcDriver() {
        Logger.info("Setting default JDBC driver: " + defaultJdbcDriver);
        return defaultJdbcDriver;
    }

    /**
     * This method tries to initialize the local fields from a configuration file.
     *
     * @param configFilePath The path of the configuration file.
     */
    public Configuration(String configFilePath) throws IOException {
        File f = new File(configFilePath);
        if (!f.exists() || !f.isFile()) {
            throw new FileNotFoundException("Configuration file '" + configFilePath + "' not found");
        }

        Ini ini = new Ini(f);
        java.util.prefs.Preferences prefs = new IniPreferences(ini);

        Preferences dbPrefs = prefs.node("db-settings");    // header [db-settings]
        dbUser = dbPrefs.get("dbUser", null);
        dbPassword = dbPrefs.get("dbPassword", null);
        dbName = dbPrefs.get("dbName", null);
        dbPortNumber = dbPrefs.get("dbPortNumber", "3306");
        jdbcDriver = dbPrefs.get("jdbcDriver", defaultJdbcDriver());
        jdbcConnectionOptions = dbPrefs.get("jdbcConnectionOptions", null);

        Preferences ioPrefs = prefs.node("io-settings");    // header [io-settings]
        inputPath = Utils.appendSlashIfMissing(ioPrefs.get("resourcesPath", null));
        ontologyFilename = ioPrefs.get("ontologyFilename", null);
        policyFilename = ioPrefs.get("policyFilename", null);
        refinedPolicyFilename = ioPrefs.get("refinedPolicyFilename", null);
        generatedMappingsFilename = ioPrefs.get("generatedMappingsFilename", null);
        generatedViewsFilename = ioPrefs.get("generatedViewsFilename", null);
        tablesFilename = ioPrefs.get("tablesFilename", null);
        fkFilename = ioPrefs.get("fkFilename", null);
        testQueryFilename = ioPrefs.get("testQueryFilename", null);
        queryOutputFilename = ioPrefs.get("queryOutputFilename", null);

        // if the refined policy set filename hasn't been specified,
        // take the original policy set filename and append "_refined" before the file extension
        if ((refinedPolicyFilename == null || refinedPolicyFilename.trim().isEmpty())
                && policyFilename != null && policyFilename.contains(".")) {
            refinedPolicyFilename = IOUtils.addSuffixToFilePath(policyFilename, "_refined");
        }

        Preferences priorityPrefs = prefs.node("priority-settings");        // header [priority-settings]
        priorityFilename = priorityPrefs.get("priorityFilename", null);
        kValue = Integer.parseInt(priorityPrefs.get("kValue", "" + kValue));
        transitivePriority = getBooleanPreference(priorityPrefs, "transitivePriority", transitivePriority);

        Preferences flagsPrefs = prefs.node("flags");        // header [flags]
        numberRestrictionsCheck = getBooleanPreference(flagsPrefs, "numberRestrictionsCheck", numberRestrictionsCheck);
        comparisonAtomsCheck = getBooleanPreference(flagsPrefs, "comparisonAtomsCheck", comparisonAtomsCheck);

        Preferences namingPrefs = prefs.node("naming");        // header [naming]
        viewSuffix = namingPrefs.get("viewSuffix", viewSuffix);
        columnNamePrefix = namingPrefs.get("columnNamePrefix", columnNamePrefix);
        columnAliasPrefix = namingPrefs.get("columnAliasPrefix", columnAliasPrefix);
    }

    public Configuration(String ontologyFile, String policyFile) throws IOException {

        // Initialize using default values
        dbUser = null;
        dbPassword = null;
        dbName = null;
        dbPortNumber = "3306";
        jdbcDriver = defaultJdbcDriver();
        jdbcConnectionOptions = null;

        inputPath = "";
        ontologyFilename = Paths.get(ontologyFile).toAbsolutePath().toString();
        policyFilename = Paths.get(policyFile).toAbsolutePath().toString();
        refinedPolicyFilename = IOUtils.addSuffixToFilePath(policyFilename, "_refined");
        generatedMappingsFilename = null;
        generatedViewsFilename = null;
        tablesFilename = null;
        fkFilename = null;
        testQueryFilename = null;
        queryOutputFilename = null;

        priorityFilename = null;
        kValue = 1;
        transitivePriority = false;

        numberRestrictionsCheck = false;
        comparisonAtomsCheck = false;

        viewSuffix = "";
        columnNamePrefix = "obj";
        columnAliasPrefix = "X";
    }

    private static boolean getBooleanPreference(Preferences prefs, String key, boolean def) {
        return Boolean.parseBoolean(prefs.get(key, "" + def));
    }

    public String prependInputPath(String fileName) {
        return Utils.appendSlashIfMissing(inputPath) + fileName;
    }

}
